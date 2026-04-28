package com.pil97.ticketing.infra.ratelimit;

import com.pil97.ticketing.common.error.RateLimitErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.ratelimit.RateLimitApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Redis Sorted Set 기반 슬라이딩 윈도우 Rate Limiter 구현체
 *
 * <p>고정 윈도우(Fixed Window) 방식은 윈도우 경계에서 최대 2배 요청이 허용되는 취약점이 있다.
 * 슬라이딩 윈도우는 요청 타임스탬프를 Sorted Set으로 관리하므로
 * 어느 시점을 기준으로 해도 정확히 windowSeconds 이내의 요청 수만 카운트한다.
 *
 * <p>Lua Script를 사용하는 이유:
 * ZREMRANGEBYSCORE, ZCARD, ZADD, EXPIRE 4개 명령을 개별 실행하면
 * 멀티스레드 환경에서 카운트가 틀릴 수 있다.
 * Lua Script는 Redis에서 원자적으로 실행되므로 이 문제를 방지한다.
 *
 * <p>ZSET member를 "timestamp:UUID" 형식으로 저장하는 이유:
 * 동일 밀리초에 요청이 겹치면 ZADD가 score 기준으로 덮어쓰기를 하지 않고
 * member가 중복되면 기존 항목을 업데이트한다.
 * UUID를 붙여 member를 유일하게 만들면 동시 요청이 모두 정확하게 카운트된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

  private final StringRedisTemplate stringRedisTemplate;

  /**
   * Lua Script: 슬라이딩 윈도우 원자적 실행
   * <p>
   * KEYS[1]: Redis Key (rate:limit:{memberId}:{api})
   * ARGV[1]: 현재 타임스탬프 (밀리초)
   * ARGV[2]: 윈도우 시작 타임스탬프 (현재 - windowSeconds * 1000)
   * ARGV[3]: ZSET member (timestamp:UUID)
   * ARGV[4]: 허용 최대 횟수
   * ARGV[5]: Key TTL (초)
   * <p>
   * 반환값: 윈도우 내 현재 요청 수 (ZADD 후 ZCARD)
   */
  private static final String SLIDING_WINDOW_SCRIPT = """
    local key = KEYS[1]
    local now = tonumber(ARGV[1])
    local windowStart = tonumber(ARGV[2])
    local member = ARGV[3]
    local limit = tonumber(ARGV[4])
    local ttl = tonumber(ARGV[5])
                
    -- 윈도우 밖 항목 제거
    redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)
                
    -- 현재 윈도우 내 요청 수 조회
    local count = redis.call('ZCARD', key)
                
    -- 한도 초과 시 즉시 반환 (ZADD 없이)
    if count >= limit then
        return count + 1
    end
                
    -- 요청 기록 추가
    redis.call('ZADD', key, now, member)
                
    -- TTL 갱신 (윈도우 시간으로 자동 정리)
    redis.call('EXPIRE', key, ttl)
                
    return count + 1
    """;

  private static final DefaultRedisScript<Long> REDIS_SCRIPT;

  static {
    REDIS_SCRIPT = new DefaultRedisScript<>();
    REDIS_SCRIPT.setScriptText(SLIDING_WINDOW_SCRIPT);
    REDIS_SCRIPT.setResultType(Long.class);
  }

  /**
   * Rate Limit 검사 및 요청 기록
   *
   * <p>허용 횟수 초과 시 RATE_LIMIT_EXCEEDED (429) 예외를 던진다.
   * <p>Redis 장애 시에는 fail-open 정책으로 요청을 통과시킨다.
   * Rate Limit은 보조 보호 장치이며, 핵심 예약/결제 정합성은 DB Lock, 상태 전이 가드,
   * Idempotency Key로 보호한다.
   *
   * @param memberId      인증된 사용자 ID
   * @param api           적용 대상 API 식별자
   * @param limit         윈도우 내 허용 최대 횟수
   * @param windowSeconds 슬라이딩 윈도우 크기 (초)
   */
  public void check(Long memberId, RateLimitApi api, int limit, int windowSeconds) {
    if (memberId == null || api == null) {
      throw new IllegalArgumentException("memberId and api must not be null");
    }

    if (limit <= 0 || windowSeconds <= 0) {
      throw new IllegalArgumentException("limit and windowSeconds must be positive");
    }

    String key = buildKey(memberId, api);
    long now = System.currentTimeMillis();
    long windowStart = now - (long) windowSeconds * 1000;

    // member: 동일 밀리초 요청 충돌 방지를 위해 UUID 포함
    String member = now + ":" + UUID.randomUUID();

    Long count;
    try {
      count = stringRedisTemplate.execute(
        REDIS_SCRIPT,
        List.of(key),
        String.valueOf(now),
        String.valueOf(windowStart),
        member,
        String.valueOf(limit),
        String.valueOf(windowSeconds)
      );
    } catch (RuntimeException e) {
      log.error("rate limit redis check failed: memberId={}, api={}", memberId, api, e);
      return; // fail-open: Rate Limit 장애가 핵심 API 가용성을 막지 않도록 요청을 통과시킨다.
    }

    if (count != null && count > limit) {
      log.warn("rate limit exceeded: memberId={}, api={}, count={}, limit={}",
        memberId, api, count, limit);
      throw new BusinessException(RateLimitErrorCode.RATE_LIMIT_EXCEEDED);
    }
  }

  /**
   * Redis Key 생성
   * 형식: rate:limit:{memberId}:{api}
   * 예: rate:limit:42:HOLD
   */
  private String buildKey(Long memberId, RateLimitApi api) {
    return "rate:limit:" + memberId + ":" + api.name();
  }
}