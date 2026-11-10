package com.pil97.ticketing.outbox.application;

/**
 * 결제 성공 Outbox payload (JSON)
 * - 소비 시 결제·예약 행을 다시 조회하지 않고 이 값으로 알림을 만든다
 */
public record PaymentSucceededPayload(Long paymentId, Long reservationId, Long memberId) {
}
