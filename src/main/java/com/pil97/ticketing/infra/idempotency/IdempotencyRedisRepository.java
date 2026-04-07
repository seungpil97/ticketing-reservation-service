package com.pil97.ticketing.infra.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 결제 멱등성 보장을 위한 Redis 저장소
 * key 형식: idempotency:payment:{idempotencyKey}
 * TTL: 24시간
 * 저장 값: PaymentResponse를 JSON으로 직렬화
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotencyRedisRepository {

  private static final String KEY_PREFIX = "idempotency:payment:";
  private static final Duration TTL = Duration.ofHours(24);

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;

  /**
   * idempotency key로 기존 결제 결과를 조회
   * 결과가 있으면 기존 응답 반환, 없으면 Optional.empty() 반환
   *
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   * @return 기존 결제 결과 (없으면 Optional.empty())
   */
  public Optional<PaymentResponse> find(String idempotencyKey) {
    String json = stringRedisTemplate.opsForValue().get(buildKey(idempotencyKey));
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, PaymentResponse.class));
    } catch (JsonProcessingException e) {
      // 역직렬화 실패 시 캐시 miss로 처리 - 재결제 허용
      log.warn("idempotency key 역직렬화 실패: key={}", idempotencyKey);
      return Optional.empty();
    }
  }

  /**
   * 결제 성공 결과를 Redis에 저장
   * 결제 실패 시에는 저장하지 않음 - 재시도 허용
   *
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   * @param response       저장할 결제 결과
   */
  public void save(String idempotencyKey, PaymentResponse response) {
    try {
      String json = objectMapper.writeValueAsString(response);
      stringRedisTemplate.opsForValue().set(buildKey(idempotencyKey), json, TTL);
    } catch (JsonProcessingException e) {
      // 직렬화 실패 시 저장 생략 - 다음 요청에서 재처리
      log.warn("idempotency key 직렬화 실패: key={}", idempotencyKey);
    }
  }

  /**
   * Redis key 생성
   * 형식: idempotency:payment:{idempotencyKey}
   */
  private String buildKey(String idempotencyKey) {
    return KEY_PREFIX + idempotencyKey;
  }
}