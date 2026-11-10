package com.pil97.ticketing.outbox.domain;

/**
 * Outbox 처리 상태
 * - 실패해도 PENDING을 유지하고 다음 순회에서 재시도한다 (최종 실패 상태는 TASK-033-1)
 */
public enum OutboxStatus {
  PENDING,
  PROCESSED
}
