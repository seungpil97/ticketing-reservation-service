package com.pil97.ticketing.payment.application.event;

/**
 * 환불 성공 이벤트 (TASK-032)
 * - PaymentService가 소유권·상태 검증 후 Payment REFUNDED 전환 뒤 같은 트랜잭션에서 발행한다
 */
public record PaymentRefundedEvent(Long paymentId, Long reservationId) {
}
