package com.pil97.ticketing.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 멱등성 공통 에러코드
 * - 도메인에 종속되지 않는 인프라 레벨 예외
 * - 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: IDEMPOTENCY-003)
 */
@Getter
@RequiredArgsConstructor
public enum IdempotencyErrorCode implements ErrorCode {

  // 동일 key로 동시 요청이 들어와 처리 중인 상태
  IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "IDEMPOTENCY-001",
    "The same request is already being processed"),

  // 동일 key로 다른 요청 본문이 전달된 경우
  IDEMPOTENCY_KEY_PAYLOAD_MISMATCH(HttpStatus.CONFLICT, "IDEMPOTENCY-002",
    "Idempotency key has already been used with a different request payload"),

  // Idempotency-Key 헤더 누락 시
  IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "IDEMPOTENCY-003",
    "Idempotency-Key header is required");

  private final HttpStatus status;
  private final String code;
  private final String message;
}