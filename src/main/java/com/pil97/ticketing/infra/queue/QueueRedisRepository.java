package com.pil97.ticketing.infra.queue;

import com.pil97.ticketing.queue.domain.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * QueueRepository의 Redis 구현체
 * <p>
 * Redis Key 규칙:
 * - 대기열: queue:event:{eventId} (Sorted Set, score = 진입 timestamp)
 * - 입장 토큰: token:user:{memberId} (String, TTL 30분)
 */
@Repository
@RequiredArgsConstructor
public class QueueRedisRepository implements QueueRepository {

  private static final String QUEUE_KEY_PREFIX = "queue:event:";
  private static final String TOKEN_KEY_PREFIX = "token:user:";
  private static final String ACTIVE_EVENTS_KEY = "queue:active:events";
  private static final Duration ADMISSION_TOKEN_TTL = Duration.ofMinutes(30);

  private final StringRedisTemplate redisTemplate;

  /**
   * 대기열 등록 - ZADD NX 옵션 적용
   * 이미 등록된 memberId는 score 갱신 없이 무시한다.
   */
  @Override
  public boolean addIfAbsent(Long eventId, Long memberId, double score) {
    Boolean result = redisTemplate.opsForZSet()
      .addIfAbsent(queueKey(eventId), String.valueOf(memberId), score);
    return Boolean.TRUE.equals(result);
  }

  /**
   * 대기열 순번 조회 (0-based)
   * ZRANK 명령 사용 - score 오름차순 기준 순번 반환
   */
  @Override
  public Long getRank(Long eventId, Long memberId) {
    return redisTemplate.opsForZSet()
      .rank(queueKey(eventId), String.valueOf(memberId));
  }

  /**
   * 상위 N명 memberId 조회
   * ZRANGE 0 N-1 명령으로 score 오름차순 상위 N명 반환
   */
  @Override
  public Set<String> getTopMembers(Long eventId, long count) {
    Set<String> members = redisTemplate.opsForZSet()
      .range(queueKey(eventId), 0, count - 1);
    return members != null ? members : Collections.emptySet();
  }

  /**
   * 대기열에서 특정 유저 제거
   * ZREM 명령 사용
   */
  @Override
  public void remove(Long eventId, Long memberId) {
    redisTemplate.opsForZSet()
      .remove(queueKey(eventId), String.valueOf(memberId));
  }

  /**
   * 입장 토큰 저장 - TTL 30분
   * SET token:user:{memberId} {token} EX 1800
   */
  @Override
  public void saveAdmissionToken(Long memberId, String token) {
    redisTemplate.opsForValue()
      .set(tokenKey(memberId), token, ADMISSION_TOKEN_TTL);
  }

  /**
   * 입장 토큰 존재 여부 확인
   * Redis key 존재 여부로 토큰 유효성 판단 (TTL 만료 시 자동 삭제됨)
   */
  @Override
  public boolean hasAdmissionToken(Long memberId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(memberId)));
  }

  /**
   * 활성 대기열 이벤트 등록
   * SADD queue:active:events {eventId}
   */
  @Override
  public void addActiveEvent(Long eventId) {
    redisTemplate.opsForSet()
      .add(ACTIVE_EVENTS_KEY, String.valueOf(eventId));
  }

  /**
   * 활성 대기열 이벤트 제거
   * SREM queue:active:events {eventId}
   */
  @Override
  public void removeActiveEvent(Long eventId) {
    redisTemplate.opsForSet()
      .remove(ACTIVE_EVENTS_KEY, String.valueOf(eventId));
  }

  /**
   * 활성 대기열 이벤트 ID 목록 조회
   * SMEMBERS queue:active:events
   */
  @Override
  public Set<String> getActiveEventIds() {
    Set<String> members = redisTemplate.opsForSet()
      .members(ACTIVE_EVENTS_KEY);
    return members != null ? members : Collections.emptySet();
  }

  /**
   * 대기열 key 삭제
   * DEL queue:event:{eventId}
   */
  @Override
  public void deleteQueue(Long eventId) {
    redisTemplate.delete(queueKey(eventId));
  }

  // queue:event:{eventId}
  private String queueKey(Long eventId) {
    return QUEUE_KEY_PREFIX + eventId;
  }

  // token:user:{memberId}
  private String tokenKey(Long memberId) {
    return TOKEN_KEY_PREFIX + memberId;
  }
}