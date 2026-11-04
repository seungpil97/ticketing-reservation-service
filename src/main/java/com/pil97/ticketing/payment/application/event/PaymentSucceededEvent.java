package com.pil97.ticketing.payment.application.event;

/**
 * 결제 성공 이벤트 (TASK-032)
 * - PaymentService가 잠금·검증·Payment SUCCESS 저장 후 같은 트랜잭션에서 발행한다
 * - ID는 PaymentService가 이미 잠근 엔티티를 가리킨다 (리스너는 같은 persistence context에서 조회)
 */
public record PaymentSucceededEvent(Long paymentId, Long reservationId, Long holdId, Long showtimeSeatId) {
}
