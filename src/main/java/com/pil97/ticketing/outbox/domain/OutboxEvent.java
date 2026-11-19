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

  // 처리 상태: PENDING -> PROCESSED, PENDING -> FAILED (기록된 실패 소진), FAILED -> PENDING (수동 재처리 프로시저)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  // 처리 완료 시각 - 처리 전 null
  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  // 재시도 상태 (TASK-033-1)
  // 기록된 실패 횟수 - 성공 후에도 이력으로 남긴다
  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  // 다음 시도 시각 - null이면 즉시 대상
  @Column(name = "next_attempt_at")
  private LocalDateTime nextAttemptAt;

  @Column(name = "last_failed_at")
  private LocalDateTime lastFailedAt;

  // 마지막 오류 분류 - 오류 원문은 저장하지 않는다
  @Enumerated(EnumType.STRING)
  @Column(name = "last_error_code", length = 40)
  private OutboxErrorCode lastErrorCode;

  @Column(name = "last_error_type", length = 100)
  private String lastErrorType;

  @Column(name = "last_sql_state", columnDefinition = "CHAR(5)")
  private String lastSqlState;

  @Column(name = "last_vendor_code")
  private Integer lastVendorCode;

  // 처리 회차 토큰 - 상태가 바뀔 때마다 증가시켜 오래된 실패 기록을 거른다
  @Column(name = "state_version", nullable = false)
  private long stateVersion;

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

  /**
   * 지금 처리 대상인지 - 다음 시도 시각이 없거나 now(초 단위 버림) 이하
   */
  public boolean isDue(LocalDateTime now) {
    return nextAttemptAt == null || !nextAttemptAt.isAfter(OutboxRetryPolicy.floorSeconds(now));
  }

  // 처리 완료 표시 - 알림 기록과 같은 트랜잭션에서 호출한다. 실패 이력은 남기고 다음 시도 시각은 지운다
  public void markProcessed(LocalDateTime processedAt) {
    this.status = OutboxStatus.PROCESSED;
    this.processedAt = OutboxRetryPolicy.floorSeconds(processedAt);
    this.nextAttemptAt = null;
    this.stateVersion++;
  }

  /**
   * 실패 기록 - 행을 잠근 뒤 PENDING·상태 버전 일치를 확인한 호출자만 사용한다 (TASK-033-1)
   *
   * @param failedAt  실패 감지 시각 (T_fail)
   * @param recordedAt 기록기 잠금 획득 시각 (T_rec)
   */
  public void recordFailure(OutboxRetryPolicy policy, LocalDateTime failedAt, LocalDateTime recordedAt,
                            OutboxErrorCode errorCode, String errorType, String sqlState, Integer vendorCode) {
    int attempts = failedAttempts + 1;
    this.failedAttempts = attempts;
    this.lastFailedAt = OutboxRetryPolicy.floorSeconds(failedAt);
    this.lastErrorCode = errorCode;
    this.lastErrorType = errorType;
    this.lastSqlState = sqlState;
    this.lastVendorCode = vendorCode;
    if (policy.isExhausted(attempts)) {
      this.status = OutboxStatus.FAILED;
      this.nextAttemptAt = null;
    } else {
      LocalDateTime base = recordedAt.isAfter(failedAt) ? recordedAt : failedAt;
      this.nextAttemptAt = OutboxRetryPolicy.ceilSeconds(base.plus(policy.delayAfter(attempts)));
    }
    this.stateVersion++;
  }
}
