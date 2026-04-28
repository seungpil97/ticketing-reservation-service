package com.pil97.ticketing.common.ratelimit;

/**
 * Rate Limit 적용 대상 API 식별자 enum
 * - Redis Key의 {api} 세그먼트로 사용된다
 * - 문자열 하드코딩을 방지하고 오타로 인한 key 불일치를 컴파일 타임에 차단한다
 * - 예: rate:limit:42:HOLD
 */
public enum RateLimitApi {

  HOLD,
  RESERVATION,
  PAYMENT
}