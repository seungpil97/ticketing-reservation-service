package com.pil97.ticketing.outbox.domain;

/**
 * Outbox 처리 실패 분류 (TASK-033-1)
 * - 오류 원문 대신 저장하는 허용 목록 값
 * - SQLState·vendor code와의 매핑은 실제 관찰값으로 확정한다 (미구현)
 */
public enum OutboxErrorCode {
  PAYLOAD_INVALID,
  DUPLICATE_KEY,
  FOREIGN_KEY,
  LOCK_WAIT_TIMEOUT,
  DEADLOCK,
  DB_ACCESS,
  UNKNOWN
}
