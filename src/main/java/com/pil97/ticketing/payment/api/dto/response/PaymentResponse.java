package com.pil97.ticketing.payment.api.dto.response;

import com.pil97.ticketing.payment.domain.Payment;

import java.time.LocalDateTime;

/**
 * 결제 처리 결과 응답 DTO
 * <p>
 * JSON 예시(성공):
 * {
 * "paymentId": 1,
 * "status": "SUCCESS",
 * "paidAt": "2026-04-03T10:00:00"
 * }
 * JSON 예시(실패):
 * {
 * "paymentId": 2,
 * "status": "FAIL",
 * "paidAt": null
 * }
 */
public record PaymentResponse(
  Long paymentId,
  String status,
  // 결제 성공 시 완료 시각, 실패 시 null
  LocalDateTime paidAt
) {
  // Payment 엔티티를 응답 DTO로 변환
  public static PaymentResponse of(Payment payment) {
    return new PaymentResponse(
      payment.getId(),
      payment.getStatus().name(),
      payment.getPaidAt()
    );
  }
}