package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.consistency.SqlObserver;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 격리 MariaDB에서 대기자·차단자·대상 행을 연결해 InnoDB 행 잠금 대기를 관찰한다 (TASK-033-1)
 * - INNODB_LOCK_WAITS·INNODB_TRX·INNODB_LOCKS를 조인한다 (F-1과 같은 방식)
 * - 대기자: trx_state = 'LOCK WAIT'이고 trx_mysql_thread_id가 호출자가 알려준 connection id
 * - 차단자: blocking_trx의 trx_mysql_thread_id가 호출자가 알려준 connection id
 * - 대상: 요청 잠금의 lock_table이 지정 테이블, lock_index = PRIMARY, lock_data = 대상 기본키
 * - 관찰은 컨테이너 root connection으로 한다 (PROCESS 권한). DB 권한은 바꾸지 않는다
 * - 관찰 쿼리 오류·권한 부족·사전 조건 불충족은 HarnessException이다 (RED/GREEN 증거가 아니다)
 */
public final class LockWaitObserver {

  // InnoDB의 information_schema 잠금 표는 마지막 읽기 후 0.1초가 지나야 갱신되므로 관찰 간격을 그보다 길게 둔다
  public static final Duration OBSERVE_INTERVAL = Duration.ofMillis(250);
  public static final int MIN_LOCK_WAIT_TIMEOUT_SECONDS = 30;

  private LockWaitObserver() {
  }

  public record Wait(String requestingTrxId, long waiterThreadId, long blockerThreadId, String table, String index,
                     String lockData) {
  }

  public static Connection rootConnection(MariaDBContainer<?> db) {
    try {
      return DriverManager.getConnection(db.getJdbcUrl(), "root", db.getPassword());
    } catch (SQLException e) {
      throw HarnessException.harness("observer connection failed", e);
    }
  }

  /**
   * 관찰 권한과 잠금 대기 제한 시간 사전 확인
   */
  public static void precheck(Connection observer) {
    long lockWaitTimeout = queryLong(observer, "select @@global.innodb_lock_wait_timeout");
    if (lockWaitTimeout < MIN_LOCK_WAIT_TIMEOUT_SECONDS) {
      throw HarnessException.precondition("innodb_lock_wait_timeout too short: " + lockWaitTimeout);
    }
    queryLong(observer, "select count(*) from information_schema.innodb_trx");
    queryLong(observer, "select count(*) from information_schema.innodb_lock_waits");
    queryLong(observer, "select count(*) from information_schema.innodb_locks");
  }

  public static long connectionId(Connection connection) {
    return queryLong(connection, "select connection_id()");
  }

  /**
   * 현재 시점에 연결 조건을 모두 만족하는 대기 (없으면 empty)
   */
  public static Optional<Wait> find(Connection observer, long waiterThreadId, long blockerThreadId,
                                    String table, String primaryKey) {
    String sql = """
      select r.trx_id, r.trx_mysql_thread_id, b.trx_mysql_thread_id as blocker, l.lock_table, l.lock_index, l.lock_data
      from information_schema.innodb_lock_waits w
      join information_schema.innodb_trx r on r.trx_id = w.requesting_trx_id
      join information_schema.innodb_trx b on b.trx_id = w.blocking_trx_id
      join information_schema.innodb_locks l on l.lock_id = w.requested_lock_id
      where r.trx_state = 'LOCK WAIT' and r.trx_mysql_thread_id = ? and b.trx_mysql_thread_id = ?
      """;
    try (PreparedStatement ps = observer.prepareStatement(sql)) {
      ps.setLong(1, waiterThreadId);
      ps.setLong(2, blockerThreadId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String lockTable = rs.getString("lock_table");
          String lockIndex = rs.getString("lock_index");
          String lockData = rs.getString("lock_data");
          if (lockTable != null && lockTable.endsWith("`" + table + "`") && "PRIMARY".equals(lockIndex)
              && primaryKey.equals(lockData)) {
            return Optional.of(new Wait(rs.getString("trx_id"), rs.getLong("trx_mysql_thread_id"),
              rs.getLong("blocker"), lockTable, lockIndex, lockData));
          }
        }
      }
      return Optional.empty();
    } catch (SQLException e) {
      throw HarnessException.harness("lock wait observation failed", e);
    }
  }

  /**
   * 제한 시간 안에 연결된 대기를 관찰하면 반환, 대기자가 먼저 끝나면 empty (호출자가 동작 결과로 판정한다).
   * 대기자가 대기하지도 끝나지도 않은 채 제한 시간이 지나면 HarnessException
   */
  public static Optional<Wait> await(Connection observer, long waiterThreadId, long blockerThreadId,
                                     String table, String primaryKey, BooleanSupplier waiterDone, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      Optional<Wait> wait = find(observer, waiterThreadId, blockerThreadId, table, primaryKey);
      if (wait.isPresent()) {
        return wait;
      }
      if (waiterDone.getAsBoolean()) {
        return Optional.empty();
      }
      sleep(OBSERVE_INTERVAL);
    }
    throw HarnessException.harness("lock wait on " + table + " " + primaryKey + " was neither observed nor finished within "
      + timeout.toSeconds() + "s. waiter=" + waiterThreadId + ", blocker=" + blockerThreadId
      + ", diagnostics=" + diagnostics(observer));
  }

  /**
   * 대기자 connection id를 관찰 중에 확보하는 경우 (Spring bean이 pool connection으로 실행하는 처리).
   * waiterThreadId가 아직 비어 있으면 대기자 미확보로 보고 다시 관찰한다
   */
  public static Optional<Wait> awaitResolvingWaiter(Connection observer, Supplier<OptionalLong> waiterThreadId,
                                                    long blockerThreadId, String table, String primaryKey,
                                                    BooleanSupplier waiterDone, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      OptionalLong waiter = waiterThreadId.get();
      if (waiter.isPresent()) {
        Optional<Wait> wait = find(observer, waiter.getAsLong(), blockerThreadId, table, primaryKey);
        if (wait.isPresent()) {
          return wait;
        }
      }
      if (waiterDone.getAsBoolean()) {
        return Optional.empty();
      }
      sleep(OBSERVE_INTERVAL);
    }
    throw HarnessException.harness("lock wait on " + table + " " + primaryKey + " was neither observed nor finished within "
      + timeout.toSeconds() + "s. waiter=" + waiterThreadId.get() + ", blocker=" + blockerThreadId
      + ", diagnostics=" + diagnostics(observer));
  }

  /**
   * SqlObserver로 관찰한 worker thread의 in-flight 실행 중 대상 SQL이면 그 서버 connection id
   * (SqlObserverConfig가 DataSource를 감싸고 있어야 한다)
   */
  public static Supplier<OptionalLong> inFlightConnection(String workerThreadName, Pattern targetSql) {
    return () -> SqlObserver.currentInFlight(workerThreadName)
      .filter(e -> e.connectionId() >= 0 && targetSql.matcher(e.sql()).find())
      .map(e -> OptionalLong.of(e.connectionId()))
      .orElse(OptionalLong.empty());
  }

  /**
   * 대기가 관찰되기 전에 worker가 끝난 경우의 판정 - 반드시 예외를 던진다
   * <ul>
   *   <li>정상 종료: AssertionError (잠금 대기 없이 판단한 동작 결과)</li>
   *   <li>승인된 미구현 메서드의 UnsupportedOperationException(메시지가 approvedStubPrefix로 시작): AssertionError (미구현 동작 결과)</li>
   *   <li>그 밖의 예외(접속·SQL 문법·설정 오류 등): HarnessException (RED/GREEN 증거가 아니다)</li>
   * </ul>
   */
  public static RuntimeException failForEarlyFinish(Future<?> worker, String approvedStubPrefix, String expectation) {
    Object result;
    try {
      result = worker.get(1, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof UnsupportedOperationException && cause.getMessage() != null
          && cause.getMessage().startsWith(approvedStubPrefix)) {
        throw new AssertionError(expectation + " - 잠금 대기 없이 승인된 미구현 메서드로 종료: " + cause.getMessage());
      }
      throw HarnessException.harness("worker failed before lock wait was observed: "
        + (cause == null ? "unknown" : cause.getClass().getName()), cause);
    } catch (Exception e) {
      throw HarnessException.harness("worker outcome unavailable", e);
    }
    throw new AssertionError(expectation + " - 잠금 대기 없이 판단하고 정상 종료: result=" + result);
  }

  /**
   * 대기 중 트랜잭션이 이미 변경한 행 수 (부분 갱신 전제 관찰)
   */
  public static long rowsModified(Connection observer, String trxId) {
    try (PreparedStatement ps = observer.prepareStatement(
      "select trx_rows_modified from information_schema.innodb_trx where trx_id = ?")) {
      ps.setString(1, trxId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw HarnessException.harness("trx not visible: " + trxId);
        }
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      throw HarnessException.harness("rows modified observation failed", e);
    }
  }

  /**
   * 정리 확인 - 지정 connection id들의 트랜잭션이 사라질 때까지 관찰 간격으로 다시 읽는다 (마지막 관찰값 반환).
   * information_schema 잠금 표는 마지막 읽기 후 0.1초 안에는 이전 값을 돌려줄 수 있어 한 번 읽기로 판정하지 않는다
   */
  public static long awaitNoOpenTransactions(Connection observer, List<Long> threadIds, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    long open;
    do {
      sleep(OBSERVE_INTERVAL);
      open = openTransactions(observer, threadIds);
    } while (open > 0 && System.nanoTime() < deadline);
    return open;
  }

  /**
   * 지정 connection id들의 현재 트랜잭션 수
   */
  public static long openTransactions(Connection observer, List<Long> threadIds) {
    if (threadIds.isEmpty()) {
      return 0;
    }
    StringBuilder in = new StringBuilder();
    for (Long id : threadIds) {
      in.append(in.isEmpty() ? "" : ",").append(id);
    }
    return queryLong(observer,
      "select count(*) from information_schema.innodb_trx where trx_mysql_thread_id in (" + in + ")");
  }

  /**
   * 관찰 실패 진단용 - 현재 트랜잭션·잠금 대기·잠금 목록
   */
  public static String diagnostics(Connection observer) {
    StringBuilder out = new StringBuilder();
    for (String sql : List.of(
      "select trx_id, trx_state, trx_mysql_thread_id, trx_query from information_schema.innodb_trx",
      "select requesting_trx_id, requested_lock_id, blocking_trx_id, blocking_lock_id from information_schema.innodb_lock_waits",
      "select lock_id, lock_trx_id, lock_mode, lock_type, lock_table, lock_index, lock_data from information_schema.innodb_locks")) {
      out.append("\n[").append(sql).append("]");
      try (Statement st = observer.createStatement(); ResultSet rs = st.executeQuery(sql)) {
        int columns = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          out.append("\n  ");
          for (int i = 1; i <= columns; i++) {
            out.append(rs.getMetaData().getColumnLabel(i)).append('=').append(rs.getString(i)).append(' ');
          }
        }
      } catch (SQLException e) {
        out.append("\n  error: ").append(e.getClass().getSimpleName());
      }
    }
    return out.toString();
  }

  private static long queryLong(Connection connection, String sql) {
    try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException e) {
      throw HarnessException.harness("observation query failed: " + sql, e);
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw HarnessException.harness("lock wait observation interrupted");
    }
  }
}
