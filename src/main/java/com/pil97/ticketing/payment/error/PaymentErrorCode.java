package com.pil97.ticketing.payment.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import com.pil97.ticketing.common.error.ErrorCode;

/**
 * 결제 도메인 에러코드
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: PAYMENT-005)
 * 이 파일은 결제 도메인에서 발생하는 비즈니스 예외를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

  // 존재하지 않는 결제 ID로 조회 시
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "Payment not found"),

  // 이미 처리 완료된 결제에 재시도 시 (TASK-036 멱등성 구현 전 방어용)
  PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT-002", "Payment already processed"),

  // 결제 처리 중 실패 (forceFailure 또는 내부 오류)
  PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT-003", "Payment failed"),

  // Idempotency-Key 헤더 누락 시
  IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "PAYMENT-004", "Idempotency-Key header is required"),

  // SUCCESS 상태가 아닌 결제에 환불 시도 시
  REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "PAYMENT-005", "Refund is only allowed for successful payments");


  private final HttpStatus status;
  private final String code;
  private final String message;
}