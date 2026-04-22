package com.pil97.ticketing.infra.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 멱등성 보장을 위한 범용 Redis 저장소
 * - fingerprint 비교로 동일 key + 다른 본문 요청 차단
 * - SETNX 기반 in-progress lock으로 동시 신규 요청 차단
 * - 결과는 IdempotencyEntry(fingerprint + responseBody) 형태로 저장
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotencyRedisRepository {

  private static final Duration LOCK_TTL = Duration.ofSeconds(10);

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;

  /**
   * 멱등성 처리 전체 흐름
   * 1. 기존 결과 조회
   * 2. 결과 있음 → fingerprint 검증 후 캐시 응답 반환
   * - 일치 → Optional.of(기존 응답) 반환
   * - 불일치 → IDEMPOTENCY_KEY_PAYLOAD_MISMATCH (409)
   * 3. 결과 없음 → SETNX lock 선점 시도
   * - 선점 실패 → IDEMPOTENCY_IN_PROGRESS (409)
   * - 선점 성공 → Optional.empty() 반환 (신규 처리 진행)
   *
   * @param prefix         도메인별 key prefix (예: "idempotency:payment")
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   * @param fingerprint    요청 본문의 SHA-256 hash
   * @param responseType   응답 DTO 타입
   * @return 기존 처리 결과 (없으면 Optional.empty())
   */
  public <T> Optional<T> find(String prefix, String idempotencyKey,
                              String fingerprint, Class<T> responseType) {
    String lockKey = buildLockKey(prefix, idempotencyKey);
    String resultKey = buildResultKey(prefix, idempotencyKey);

    // 1단계: 기존 결과 먼저 조회
    String json = stringRedisTemplate.opsForValue().get(resultKey);

    if (json != null) {
      // 2단계: 결과 있음 - fingerprint 검증 후 캐시 응답 반환
      try {
        IdempotencyEntry entry = objectMapper.readValue(json, IdempotencyEntry.class);

        if (!entry.getFingerprint().equals(fingerprint)) {
          // 동일 key + 다른 본문 - 409 반환
          throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        }

        T response = objectMapper.readValue(entry.getResponseBody(), responseType);
        return Optional.of(response);

      } catch (JsonProcessingException e) {
        // 역직렬화 실패 시 손상된 캐시로 간주하고 신규 처리 진행 (결과 key는 TTL 만료까지 남을 수 있음)
        log.warn("idempotency entry 역직렬화 실패: prefix={}, key={}", prefix, idempotencyKey);
      }
    }

    // 3단계: 결과 없음 - SETNX lock 선점 시도
    Boolean acquired = stringRedisTemplate.opsForValue()
      .setIfAbsent(lockKey, "processing", LOCK_TTL);

    if (Boolean.FALSE.equals(acquired)) {
      // 다른 요청이 처리 중 - 409 반환
      throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS);
    }

    // lock 선점 성공 - 신규 처리 진행 (처리 후 save() 호출 필요)
    return Optional.empty();
  }

  /**
   * 처리 성공 결과를 Redis에 저장하고 in-progress lock 해제
   * - 결과 저장 후 lock 삭제
   * - 처리 실패 시에는 호출하지 않음 (재시도 허용)
   *
   * @param prefix         도메인별 key prefix
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   * @param fingerprint    요청 본문의 SHA-256 hash
   * @param response       저장할 처리 결과
   * @param ttl            결과 보관 TTL
   */
  public <T> void save(String prefix, String idempotencyKey,
                       String fingerprint, T response, Duration ttl) {
    String lockKey = buildLockKey(prefix, idempotencyKey);
    String resultKey = buildResultKey(prefix, idempotencyKey);

    try {
      String responseBody = objectMapper.writeValueAsString(response);
      IdempotencyEntry entry = new IdempotencyEntry(fingerprint, responseBody);
      String json = objectMapper.writeValueAsString(entry);

      // 결과 저장 후 lock 해제
      stringRedisTemplate.opsForValue().set(resultKey, json, ttl);
      stringRedisTemplate.delete(lockKey);

    } catch (JsonProcessingException e) {
      // 직렬화 실패 시 lock만 해제 - 다음 요청에서 재처리
      log.warn("idempotency entry 직렬화 실패: prefix={}, key={}", prefix, idempotencyKey);
      stringRedisTemplate.delete(lockKey);
    }
  }

  /**
   * 처리 실패 시 in-progress lock 해제
   * - 재시도를 허용하기 위해 결과는 저장하지 않고 lock만 해제
   *
   * @param prefix         도메인별 key prefix
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   */
  public void releaseLock(String prefix, String idempotencyKey) {
    stringRedisTemplate.delete(buildLockKey(prefix, idempotencyKey));
  }

  /**
   * 결과 저장 key 생성
   * 형식: {prefix}:{idempotencyKey}
   * 예: idempotency:payment:uuid-1234
   */
  private String buildResultKey(String prefix, String idempotencyKey) {
    return prefix + ":" + idempotencyKey;
  }

  /**
   * in-progress lock key 생성
   * 형식: {prefix}:lock:{idempotencyKey}
   * 예: idempotency:payment:lock:uuid-1234
   */
  private String buildLockKey(String prefix, String idempotencyKey) {
    return prefix + ":lock:" + idempotencyKey;
  }
}