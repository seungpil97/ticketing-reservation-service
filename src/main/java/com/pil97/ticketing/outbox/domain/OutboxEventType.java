package com.pil97.ticketing.outbox.domain;

/**
 * Outbox 이벤트 종류
 */
public enum OutboxEventType {
  // 결제 성공 - aggregate_id는 결제 ID
  PAYMENT_SUCCEEDED
}
