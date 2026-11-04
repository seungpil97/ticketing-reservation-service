package com.pil97.ticketing.common.persistence;

import org.springframework.dao.ConcurrencyFailureException;

/**
 * 동시 갱신 충돌 전용 예외 (TASK-062)
 * - MariaDB innodb_snapshot_isolation=ON에서 read view 이후 변경된 행을 잠그거나 갱신하면
 *   ER_CHECKREAD(1020)가 반환되고, DB는 해당 트랜잭션 전체를 rollback한다
 * - 이 1020만 이 예외로 변환한다. 다른 동시성·무결성 오류는 기존 변환을 유지한다
 * - 원인 SQLException을 cause로 보존한다
 */
public class ConcurrentUpdateConflictException extends ConcurrencyFailureException {

  // MariaDB ER_CHECKREAD - "Record has changed since last read"
  public static final int ER_CHECKREAD = 1020;

  public ConcurrentUpdateConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
