package com.pil97.ticketing.outbox.domain;

/**
 * Outbox 처리 상태
 * - PENDING: 처리 대상. 실패하면 기록된 다음 시도 시각 이후 다시 대상이 된다
 * - PROCESSED: 처리 완료 (알림 기록과 같은 커밋)
 * - FAILED: 기록된 실패가 최대 횟수에 도달해 자동 순회에서 제외된 상태. 수동 재처리 프로시저로만 PENDING에 되돌린다 (TASK-033-1)
 */
public enum OutboxStatus {
  PENDING,
  PROCESSED,
  FAILED
}
