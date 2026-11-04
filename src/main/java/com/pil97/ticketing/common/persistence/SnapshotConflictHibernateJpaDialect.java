package com.pil97.ticketing.common.persistence;

import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.hibernate.TransactionException;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

import java.sql.SQLException;

/**
 * 1020(ER_CHECKREAD) 전용 변환을 담당하는 HibernateJpaDialect (TASK-062, C3)
 * - JDBCException: getSQLException()의 vendor errorCode가 1020이면 ConcurrentUpdateConflictException
 * - Hibernate TransactionException: 직접 원인(getCause())이 SQLException이고 vendor errorCode가 1020이면 ConcurrentUpdateConflictException
 * - 그 외는 super의 기존 변환을 그대로 따른다 (중간 비SQL wrapper 아래 원인은 탐색하지 않는다)
 *
 * <p>override 지점: spring-orm 6.2.3의 translateExceptionIfPossible은 HibernateException과
 * PersistenceException(cause=HibernateException) 두 경우 모두 convertHibernateAccessException을 호출하므로 여기서 한 번에 처리한다.
 * JpaTransactionManager의 commit 경계(RollbackException의 원인)도 같은 메서드를 거친다.
 * 1020 판정 규칙은 SnapshotConflictExceptionTranslator를 그대로 사용한다. setJdbcExceptionTranslator는 쓰지 않는다.
 * 자동 재시도는 하지 않는다.
 *
 * <p>등록: JpaDialectConfig가 애플리케이션 EntityManagerFactory에 이 인스턴스를 설정하고,
 * JpaTransactionManager는 EntityManagerFactory에서 같은 인스턴스를 가져온다.
 */
public class SnapshotConflictHibernateJpaDialect extends HibernateJpaDialect {

  private final SnapshotConflictExceptionTranslator snapshotConflictTranslator = new SnapshotConflictExceptionTranslator();

  @Override
  protected DataAccessException convertHibernateAccessException(HibernateException ex) {
    SQLException sqlException = targetSqlException(ex);
    if (sqlException != null) {
      DataAccessException conflict = snapshotConflictTranslator.translate(
        "Hibernate operation: " + ex.getMessage(), sqlExceptionSql(ex), sqlException);
      if (conflict != null) {
        return conflict;
      }
    }
    return super.convertHibernateAccessException(ex);
  }

  /**
   * 판정 대상 SQLException - JDBCException의 SQLException, 또는 TransactionException의 직접 원인 SQLException
   */
  private static SQLException targetSqlException(HibernateException ex) {
    if (ex instanceof JDBCException jdbcException) {
      return jdbcException.getSQLException();
    }
    if (ex instanceof TransactionException && ex.getCause() instanceof SQLException direct) {
      return direct;
    }
    return null;
  }

  private static String sqlExceptionSql(HibernateException ex) {
    return ex instanceof JDBCException jdbcException ? jdbcException.getSQL() : null;
  }
}
