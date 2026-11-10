package com.pil97.ticketing.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  // 알림을 만든 Outbox 이벤트
  @Column(name = "outbox_event_id", nullable = false)
  private Long outboxEventId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private NotificationType type;

  @Column(nullable = false)
  private String message;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  private Notification(Long memberId, Long paymentId, Long outboxEventId, NotificationType type, String message) {
    this.memberId = memberId;
    this.paymentId = paymentId;
    this.outboxEventId = outboxEventId;
    this.type = type;
    this.message = message;
  }

  public static Notification paymentCompleted(Long memberId, Long paymentId, Long outboxEventId) {
    return new Notification(memberId, paymentId, outboxEventId, NotificationType.PAYMENT_COMPLETED,
      "결제가 완료되었습니다. (결제 ID: " + paymentId + ")");
  }
}
