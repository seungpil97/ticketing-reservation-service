package com.pil97.ticketing.payment.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.outbox.application.PaymentSucceededPayload;
import com.pil97.ticketing.outbox.domain.OutboxEvent;
import com.pil97.ticketing.outbox.domain.repository.OutboxEventRepository;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 성공 Outbox 저장 리스너 (TASK-033)
 * - 동기 @EventListener: PaymentService의 결제 트랜잭션에 참여한다 (MANDATORY) - 결제와 Outbox가 함께 커밋·롤백된다
 * - @Order(1): 상태 변경 리스너(@Order(0)) 뒤에 실행된다. 상태 변경이 실패하면 Outbox를 쓰지 않는다
 * - 예외를 삼키지 않는다: 호출자로 전파되어 결제 트랜잭션 전체가 롤백되고 Redis 성공 결과도 저장되지 않는다
 * - 새 잠금을 잡지 않는다: 예약은 같은 persistence context의 잠긴 엔티티이고, 회원 ID는 LAZY proxy ID로 읽는다
 */
@Component
@RequiredArgsConstructor
public class PaymentOutboxListener {

  private final OutboxEventRepository outboxEventRepository;
  private final ReservationRepository reservationRepository;
  private final ObjectMapper objectMapper;

  @EventListener
  @Order(1)
  @Transactional(propagation = Propagation.MANDATORY)
  public void onSucceeded(PaymentSucceededEvent event) {
    Reservation reservation = reservationRepository.findById(event.reservationId()).orElseThrow();
    PaymentSucceededPayload payload = new PaymentSucceededPayload(
      event.paymentId(), event.reservationId(), reservation.getMember().getId());

    outboxEventRepository.save(OutboxEvent.paymentSucceeded(event.paymentId(), toJson(payload)));
  }

  private String toJson(PaymentSucceededPayload payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox payload. paymentId=" + payload.paymentId(), e);
    }
  }
}
