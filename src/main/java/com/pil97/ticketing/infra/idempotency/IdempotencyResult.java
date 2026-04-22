package com.pil97.ticketing.infra.idempotency;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 멱등성 처리 결과 래퍼
 * - replayed: true이면 Redis 캐시에서 반환된 재요청 응답
 * - replayed: false이면 신규 처리된 응답
 * - 컨트롤러에서 신규(201) / 재요청(200) 상태코드 분기에 사용
 */
@Getter
@RequiredArgsConstructor
public class IdempotencyResult<T> {

  private final T response;
  // true: 캐시 응답 (재요청), false: 신규 처리
  private final boolean replayed;

  public static <T> IdempotencyResult<T> ofNew(T response) {
    return new IdempotencyResult<>(response, false);
  }

  public static <T> IdempotencyResult<T> ofReplayed(T response) {
    return new IdempotencyResult<>(response, true);
  }
}