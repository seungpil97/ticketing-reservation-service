package com.pil97.ticketing.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Rate Limit 도메인 에러코드
 * - RATE_LIMIT_EXCEEDED: 허용 횟수 초과 시 429 반환
 */
@Getter
@RequiredArgsConstructor
public enum RateLimitErrorCode implements ErrorCode {

  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "RATE-LIMIT-001",
    "요청 횟수 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}