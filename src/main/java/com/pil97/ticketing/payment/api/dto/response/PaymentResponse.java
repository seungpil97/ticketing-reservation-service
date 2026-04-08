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
 * "paidAt": "2026-04-07T10:00:00",
 * "refundedAt": null
 * }
 * JSON 예시(실패):
 * {
 * "paymentId": 2,
 * "status": "FAIL",
 * "paidAt": null,
 * "refundedAt": null
 * }
 * JSON 예시(환불):
 * {
 * "paymentId": 1,
 * "status": "REFUNDED",
 * "paidAt": "2026-04-07T10:00:00",
 * "refundedAt": "2026-04-07T10:20:00"
 * }
 */
public record PaymentResponse(
  Long paymentId,
  String status,
  // 결제 성공 시 완료 시각, 실패 시 null
  LocalDateTime paidAt,
  // 환불 완료 시각, 환불 전 null
  LocalDateTime refundedAt
) {
  // Payment 엔티티를 응답 DTO로 변환
  public static PaymentResponse of(Payment payment) {
    return new PaymentResponse(
      payment.getId(),
      payment.getStatus().name(),
      payment.getPaidAt(),
      payment.getRefundedAt()
    );
  }
}