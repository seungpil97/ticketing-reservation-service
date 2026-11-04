package com.pil97.ticketing.payment.application.event;

/**
 * 업무상 결제 실패(forceFailure) 이벤트 (TASK-032)
 * - PaymentService가 Payment FAIL 저장 후 같은 트랜잭션에서 발행한다
 * - releaseSeat: 대상 HOLD가 종료 전 좌석 해제 권한을 가졌는지 (HOLD 종료 전에 서비스가 계산)
 */
public record PaymentFailedEvent(Long paymentId, Long reservationId, Long holdId, Long showtimeSeatId,
                                 boolean releaseSeat) {
}
