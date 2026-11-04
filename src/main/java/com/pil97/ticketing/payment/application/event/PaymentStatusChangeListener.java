package com.pil97.ticketing.payment.application.event;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제·환불 후속 상태 변경 리스너 (TASK-032)
 * - 동기 @EventListener: PaymentService의 트랜잭션에 참여한다 (MANDATORY - 트랜잭션 밖 호출 시 예외)
 * - @Order(0) 명시: 기본 order에 의존하지 않고 다른 후속 리스너(order가 더 큰 값)보다 먼저 실행된다
 * - 예외를 삼키지 않는다: 호출자(PaymentService)로 전파되어 결제·환불 트랜잭션 전체가 롤백된다
 * - 잠금은 PaymentService가 TASK-062 순서(예약 → 좌석 → HOLD)로 이미 획득했다
 *   리스너의 findById는 같은 persistence context의 잠긴 엔티티를 돌려주며 새 잠금을 시도하지 않는다
 */
@Component
@RequiredArgsConstructor
public class PaymentStatusChangeListener {

  private final ReservationRepository reservationRepository;
  private final HoldRepository holdRepository;
  private final ShowtimeSeatRepository showtimeSeatRepository;

  /**
   * 결제 성공: 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED
   */
  @EventListener
  @Order(0)
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSucceeded(PaymentSucceededEvent event) {
    Reservation reservation = reservationRepository.findById(event.reservationId()).orElseThrow();
    ShowtimeSeat showtimeSeat = showtimeSeatRepository.findById(event.showtimeSeatId()).orElseThrow();
    Hold hold = holdRepository.findById(event.holdId()).orElseThrow();

    reservation.confirm();
    showtimeSeat.markReserved();
    hold.confirm();
  }

  /**
   * 업무상 결제 실패: 예약 FAILED, HOLD EXPIRED, 해제 권한이 있을 때만 좌석 AVAILABLE
   * - 타인 HOLD·결제 완료 좌석 보존 (TASK-062)
   */
  @EventListener
  @Order(0)
  @Transactional(propagation = Propagation.MANDATORY)
  public void onFailed(PaymentFailedEvent event) {
    Reservation reservation = reservationRepository.findById(event.reservationId()).orElseThrow();
    Hold hold = holdRepository.findById(event.holdId()).orElseThrow();

    reservation.fail();
    hold.expire();
    if (event.releaseSeat()) {
      showtimeSeatRepository.findById(event.showtimeSeatId()).orElseThrow().markAvailable();
    }
  }

  /**
   * 환불 성공: 예약 CANCELLED, HOLD REFUNDED, 좌석 AVAILABLE
   */
  @EventListener
  @Order(0)
  @Transactional(propagation = Propagation.MANDATORY)
  public void onRefunded(PaymentRefundedEvent event) {
    Reservation reservation = reservationRepository.findById(event.reservationId()).orElseThrow();
    Hold hold = reservation.getHold();

    reservation.cancelByRefund();
    hold.refund();
    hold.getShowtimeSeat().markAvailable();
  }
}
