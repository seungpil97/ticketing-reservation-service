package com.pil97.ticketing.outbox.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 50)
  private OutboxEventType eventType;

  // 이벤트 대상 ID - 결제 성공 이벤트는 결제 ID (FK 없음)
  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  // 소비에 필요한 값 (JSON) - 소비 시 원본 행을 다시 조회하지 않는다
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  // 처리 상태: PENDING -> PROCESSED
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  // 처리 완료 시각 - 처리 전 null
  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  private OutboxEvent(OutboxEventType eventType, Long aggregateId, String payload) {
    this.eventType = eventType;
    this.aggregateId = aggregateId;
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
  }

  public static OutboxEvent paymentSucceeded(Long paymentId, String payload) {
    return new OutboxEvent(OutboxEventType.PAYMENT_SUCCEEDED, paymentId, payload);
  }

  public boolean isPending() {
    return status == OutboxStatus.PENDING;
  }

  // 처리 완료 표시 - 알림 기록과 같은 트랜잭션에서 호출한다
  public void markProcessed(LocalDateTime processedAt) {
    this.status = OutboxStatus.PROCESSED;
    this.processedAt = processedAt;
  }
}
