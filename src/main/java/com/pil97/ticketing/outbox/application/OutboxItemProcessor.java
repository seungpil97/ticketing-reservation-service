package com.pil97.ticketing.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.notification.domain.Notification;
import com.pil97.ticketing.notification.domain.repository.NotificationRepository;
import com.pil97.ticketing.outbox.domain.OutboxEvent;
import com.pil97.ticketing.outbox.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Outbox 항목 소비 - 항목 하나를 독립 트랜잭션으로 처리한다 (TASK-033)
 * - OutboxRelayService(coordinator)와 다른 bean이어야 트랜잭션 프록시가 적용된다 (self-invocation 금지)
 * - 알림 기록과 처리 완료 표시는 같은 커밋에 포함된다: 실패하면 함께 rollback되고 Outbox는 PENDING으로 남는다
 * - 행을 잠근 뒤 PENDING이고 지금 처리 대상(다음 시도 시각 경과)인지 재확인한다: 이미 처리된 이벤트가 다시 전달되거나
 *   후보 조회 뒤 다음 시도 시각이 미래로 바뀐 행은 알림을 만들지 않고 건너뛴다 (TASK-033-1)
 * - 처리 완료 시각은 완료 표시 시점의 Clock 값이다 (TASK-033-1)
 * - 잠금: Outbox 행을 잠근 뒤 알림 INSERT의 FK 검사가 참조 payment·members 행에 공유 잠금을 요청한다.
 *   해당 행을 다른 트랜잭션이 잠그고 있으면 대기할 수 있다. 예약·좌석·HOLD는 잠그지 않는다
 */
@Component
@RequiredArgsConstructor
public class OutboxItemProcessor {

  private final OutboxEventRepository outboxEventRepository;
  private final NotificationRepository notificationRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /**
   * Outbox 1건 처리
   *
   * @return 처리했으면 true, 처리 대상이 아니어서 건너뛰었으면 false
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean process(Long outboxEventId) {
    Optional<OutboxEvent> locked = outboxEventRepository.findByIdForUpdate(outboxEventId);
    if (locked.isEmpty() || !locked.get().isPending() || !locked.get().isDue(LocalDateTime.now(clock))) {
      return false;
    }
    OutboxEvent event = locked.get();
    PaymentSucceededPayload payload = fromJson(event);

    // 알림 기록 후 처리 완료 표시 - commit flush까지 이 트랜잭션 경계에 포함된다
    notificationRepository.save(
      Notification.paymentCompleted(payload.memberId(), payload.paymentId(), event.getId()));
    event.markProcessed(LocalDateTime.now(clock));
    return true;
  }

  private PaymentSucceededPayload fromJson(OutboxEvent event) {
    try {
      return objectMapper.readValue(event.getPayload(), PaymentSucceededPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid outbox payload. outboxEventId=" + event.getId(), e);
    }
  }
}
