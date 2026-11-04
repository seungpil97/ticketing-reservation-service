package com.pil97.ticketing.common.persistence;

import org.hibernate.JDBCException;
import org.hibernate.PessimisticLockException;
import org.hibernate.TransactionException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTransactionRollbackException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-062 (#104) 1020 전용 예외 변환 계약 - 단위 (RED_REQUIRED + GREEN guard)
 *
 * <p>증거 범위: standalone 변환기와 C3 dialect(SnapshotConflictHibernateJpaDialect)의 변환 규칙만 검증한다.
 * 이 테스트가 직접 만든 dialect 인스턴스는 애플리케이션 등록의 증거가 아니다.
 * 실제 등록, 실제 MariaDB 1020, 트랜잭션 종료, HTTP 응답은 SnapshotConflictJpaPathIntegrationTest,
 * PaymentConflictHttpIntegrationTest가 담당한다.
 *
 * <p>연결 방식 C3: HibernateJpaDialect를 상속한 SnapshotConflictHibernateJpaDialect를 그대로 생성해 검증한다.
 * 일반 HibernateJpaDialect에 setJdbcExceptionTranslator를 호출하는 방식(C0)은 쓰지 않는다
 * (spring-orm 6.2.3에서 그 setter는 TransactionException 경로의 기본 SQLExceptionSubclassTranslator까지 대체한다).
 * <ul>
 *   <li>1020 대상: JDBCException의 getSQLException() vendor errorCode 1020,
 *       TransactionException의 <b>직접 원인</b> SQLException vendor errorCode 1020 → 전용 예외, 원인 보존 (현 골격에서 RED 예상)</li>
 *   <li>비대상: 기본 new HibernateJpaDialect() 결과와 같은 타입·원인 (현 골격은 기본 동작만 상속하므로 GREEN guard 예상)</li>
 * </ul>
 * standalone 변환기(SnapshotConflictExceptionTranslator) 테스트는 변환 규칙 단위로 유지한다. C3 dialect와의 연결 여부는 이 테스트가 정하지 않는다.
 */
class SnapshotConflictExceptionTranslatorTest {

  private static final String UPDATE_SQL = "update holds set status=? where id=?";

  private final SnapshotConflictExceptionTranslator translator = new SnapshotConflictExceptionTranslator();

  private static SQLException sqlException(int errorCode, String sqlState) {
    return new SQLException("vendor error " + errorCode, sqlState, errorCode);
  }

  private static SQLException checkRead() {
    return new SQLException("Record has changed since last read in table 'holds'", "HY000", 1020);
  }

  // C3 검증 대상 - 테스트가 직접 생성한 인스턴스 (애플리케이션 등록 증거 아님)
  private static HibernateJpaDialect c3Dialect() {
    return new SnapshotConflictHibernateJpaDialect();
  }

  // 비교 기준 - 기본 HibernateJpaDialect
  private static HibernateJpaDialect defaultDialect() {
    return new HibernateJpaDialect();
  }

  // ===================== 변환기 =====================

  @Test
  @DisplayName("변환기: vendor errorCode 1020은 ConcurrentUpdateConflictException(ConcurrencyFailureException)으로 변환하고 원인 SQLException을 보존한다")
  void translate_checkRead_toConcurrentUpdateConflict() {
    SQLException cause = checkRead();

    DataAccessException translated = translator.translate("update", UPDATE_SQL, cause);

    assertThat(translated)
      .isInstanceOf(ConcurrentUpdateConflictException.class)
      .isInstanceOf(ConcurrencyFailureException.class);
    assertThat(translated.getCause()).isSameAs(cause);
    assertThat(((SQLException) translated.getCause()).getErrorCode()).isEqualTo(1020);
  }

  static Stream<Arguments> nonTargetErrors() {
    return Stream.of(
      Arguments.of(1205, "HY000"),  // lock wait timeout
      Arguments.of(1213, "40001"),  // deadlock
      Arguments.of(1062, "23000"),  // duplicate key
      Arguments.of(1452, "23000"),  // FK violation
      Arguments.of(1146, "42S02"),  // unknown table
      Arguments.of(0, "HY000")      // vendor code 없음
    );
  }

  @ParameterizedTest(name = "errorCode={0}, sqlState={1}")
  @MethodSource("nonTargetErrors")
  @DisplayName("변환기 guard: 1020 이외 오류는 변환하지 않는다 (null 반환 - standalone 변환 규칙)")
  void translate_nonTarget_returnsNull(int errorCode, String sqlState) {
    assertThat(translator.translate("task", "select 1", sqlException(errorCode, sqlState))).isNull();
  }

  // ===================== C3 dialect - JDBCException 경로 =====================

  @Test
  @DisplayName("C3 dialect: GenericJDBCException(getSQLException 1020)은 ConcurrentUpdateConflictException으로 변환되고 원인 체인에 1020 SQLException이 남는다")
  void dialect_genericJdbcCheckRead_translated() {
    SQLException cause = checkRead();

    RuntimeException translated = c3Dialect()
      .translateExceptionIfPossible(new GenericJDBCException("could not execute statement", cause, UPDATE_SQL));

    assertThat(translated).isInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(translated).hasRootCause(cause);
  }

  static Stream<Arguments> nonTargetHibernateExceptions() {
    return Stream.of(
      Arguments.of("deadlock", new LockAcquisitionException("deadlock", sqlException(1213, "40001"), "sql")),
      Arguments.of("lock wait timeout", new PessimisticLockException("timeout", sqlException(1205, "HY000"), "sql")),
      Arguments.of("duplicate key", new ConstraintViolationException("dup", sqlException(1062, "23000"), "uk_x")),
      Arguments.of("other generic", new GenericJDBCException("other", sqlException(1146, "42S02"), "sql"))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonTargetHibernateExceptions")
  @DisplayName("C3 dialect guard: 비1020 JDBCException은 기본 HibernateJpaDialect와 같은 타입·원인으로 변환된다")
  void dialect_nonTarget_preservesExistingTranslation(String label, JDBCException hibernateException) {
    RuntimeException baseline = defaultDialect().translateExceptionIfPossible(hibernateException);
    RuntimeException c3 = c3Dialect().translateExceptionIfPossible(hibernateException);

    assertThat(baseline).as("default dialect result").isNotNull();
    assertThat(c3).isNotNull();
    assertThat(c3).isNotInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(c3.getClass()).as("same type as default dialect (%s)", baseline.getClass().getName())
      .isEqualTo(baseline.getClass());
    SQLException sqlCause = hibernateException.getSQLException();
    assertThat(causeChainContains(baseline, sqlCause)).as("default keeps SQL cause").isTrue();
    assertThat(causeChainContains(c3, sqlCause)).as("C3 keeps SQL cause").isTrue();
  }

  // ===================== C3 dialect - TransactionException 경로 =====================

  private static boolean causeChainContains(Throwable error, Throwable target) {
    Set<Throwable> seen = new HashSet<>();
    for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
      if (t == target) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("C3 dialect: SQLException(1020)을 직접 원인으로 가진 TransactionException은 ConcurrentUpdateConflictException으로 변환되고 원인 1020이 보존된다")
  void dialect_transactionExceptionDirectCheckRead_translated() {
    SQLException cause = checkRead();

    RuntimeException translated = c3Dialect()
      .translateExceptionIfPossible(new TransactionException("Unable to commit against JDBC Connection", cause));

    assertThat(translated).isInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(causeChainContains(translated, cause)).as("cause chain keeps the 1020 SQLException").isTrue();
  }

  /**
   * 비1020 TransactionException - 원인 구조 3종
   * <ul>
   *   <li>direct: SQLException이 직접 원인 (기본 SQLExceptionSubclassTranslator가 적용되는 경로)</li>
   *   <li>wrapped: 중간에 비SQL wrapper가 있음 (spring-orm 6.2.3은 직접 원인만 보므로 기본값에서도 변환기 미적용)</li>
   *   <li>no-sql: SQL 원인이 없음 (변환기 미적용)</li>
   * </ul>
   * 기대값은 모두 기본 new HibernateJpaDialect()의 결과다 (테스트에 하드코딩하지 않는다).
   */
  static Stream<Arguments> nonTargetTransactionExceptions() {
    return Stream.of(
      // direct - SQLExceptionSubclassTranslator subclass 분기
      Arguments.of("direct rollback 40001/1213",
        new SQLTransactionRollbackException("deadlock", "40001", 1213)),
      Arguments.of("direct rollback 40000",
        new SQLTransactionRollbackException("rollback", "40000", 0)),
      Arguments.of("direct integrity 23000/1062",
        new SQLIntegrityConstraintViolationException("duplicate", "23000", 1062)),
      Arguments.of("direct integrity 23000/1452",
        new SQLIntegrityConstraintViolationException("fk", "23000", 1452)),
      Arguments.of("direct syntax 42000/1064",
        new SQLSyntaxErrorException("syntax", "42000", 1064)),
      // direct - 일반 SQLException (SQLState fallback 분기 / 미분류)
      Arguments.of("direct plain 40001/1213", sqlException(1213, "40001")),
      Arguments.of("direct plain HY000/1205", sqlException(1205, "HY000")),
      Arguments.of("direct plain HY000/0 (SQLState만 1020과 같음)", sqlException(0, "HY000")),
      Arguments.of("direct plain 08S01/0", sqlException(0, "08S01"))
    ).map(args -> Arguments.of(args.get()[0],
      new TransactionException("Unable to commit against JDBC Connection", (SQLException) args.get()[1]),
      args.get()[1]));
  }

  static Stream<Arguments> nonDirectTransactionExceptions() {
    SQLException integrity = new SQLIntegrityConstraintViolationException("duplicate", "23000", 1062);
    SQLException rollback = new SQLTransactionRollbackException("deadlock", "40001", 1213);
    SQLException checkRead = checkRead();
    return Stream.of(
      Arguments.of("wrapped integrity 23000/1062",
        new TransactionException("commit failed", new IllegalStateException("wrapper", integrity)), integrity),
      Arguments.of("wrapped rollback 40001/1213",
        new TransactionException("commit failed", new IllegalStateException("wrapper", rollback)), rollback),
      // 직접 원인이 아닌 1020 - 6.2.3은 변환기에 전달하지 않는다. 원인 탐색 범위를 넓히지 않는다
      Arguments.of("wrapped 1020",
        new TransactionException("commit failed", new IllegalStateException("wrapper", checkRead)), checkRead),
      Arguments.of("no-sql non-SQL cause",
        new TransactionException("commit failed", new IllegalStateException("not sql")), null),
      Arguments.of("no-sql no cause",
        new TransactionException("commit failed"), null)
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonTargetTransactionExceptions")
  @DisplayName("C3 dialect guard: 비1020 SQLException을 직접 원인으로 가진 TransactionException은 기본 HibernateJpaDialect와 같은 타입·원인으로 변환된다")
  void dialect_nonTargetTransactionExceptionDirect_preservesDefaultTranslation(
    String label, TransactionException exception, SQLException sqlCause) {
    assertSameAsDefault(exception, sqlCause);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonDirectTransactionExceptions")
  @DisplayName("C3 dialect guard: 직접 SQL 원인이 없는 TransactionException(wrapper 아래 1020 포함)은 기본 HibernateJpaDialect와 같은 타입·원인으로 변환된다 (원인 탐색 범위 확대 없음)")
  void dialect_transactionExceptionWithoutDirectSqlCause_preservesDefaultTranslation(
    String label, TransactionException exception, SQLException sqlCause) {
    assertSameAsDefault(exception, sqlCause);
  }

  private void assertSameAsDefault(TransactionException exception, SQLException sqlCause) {
    RuntimeException baseline = defaultDialect().translateExceptionIfPossible(exception);
    RuntimeException c3 = c3Dialect().translateExceptionIfPossible(exception);

    assertThat(baseline).as("default dialect result").isNotNull();
    assertThat(c3).isNotNull();
    assertThat(c3).isNotInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(c3.getClass()).as("same type as default dialect (%s)", baseline.getClass().getName())
      .isEqualTo(baseline.getClass());
    if (sqlCause != null) {
      assertThat(causeChainContains(baseline, sqlCause)).as("default keeps SQL cause").isTrue();
      assertThat(causeChainContains(c3, sqlCause)).as("C3 keeps SQL cause").isTrue();
    }
    assertThat(causeChainContains(c3, exception) || causeChainContains(c3, sqlCause))
      .as("C3 keeps original exception or its SQL cause").isTrue();
  }
}
