package com.pil97.ticketing.common.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.lang.Nullable;

import java.sql.SQLException;

/**
 * 1020(ER_CHECKREAD) 전용 SQLException 변환 규칙 (TASK-062)
 * - vendor errorCode 1020만 ConcurrentUpdateConflictException으로 변환하고 원인 SQLException을 cause로 보존한다
 * - 그 외에는 null을 반환한다 (호출자가 기존 변환을 그대로 따르게 한다)
 * - SQLState(HY000)는 1020 외에도 쓰이므로 판정에 사용하지 않는다
 *
 * <p>SnapshotConflictHibernateJpaDialect가 판정 대상 SQLException을 골라 이 규칙에 넘긴다.
 * HibernateJpaDialect.setJdbcExceptionTranslator로 등록하지 않는다
 * (그 setter는 TransactionException 경로의 기본 변환기까지 대체한다).
 */
public class SnapshotConflictExceptionTranslator implements SQLExceptionTranslator {

  @Override
  @Nullable
  public DataAccessException translate(String task, @Nullable String sql, SQLException ex) {
    if (ex.getErrorCode() != ConcurrentUpdateConflictException.ER_CHECKREAD) {
      return null;
    }
    return new ConcurrentUpdateConflictException(task + "; snapshot conflict (ER_CHECKREAD)", ex);
  }
}
