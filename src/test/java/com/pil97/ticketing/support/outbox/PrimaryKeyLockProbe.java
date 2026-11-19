package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.support.consistency.HarnessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 기본키 행 잠금 대기 관찰 - INNODB_LOCKS.lock_data가 NULL일 때 기본키 probe로 대상 행을 입증한다 (TASK-033-1, AC-10b 전용)
 * <ul>
 *   <li>LockWaitObserver.find와 같은 조인·조건으로 폴링하고, 요청 잠금 lock_data가 대상 기본키면 그대로 인정한다</li>
 *   <li>lock_data가 NULL이면(InnoDB가 페이지를 즉시 얻지 못하면 NULL로 둔다) 별도 연결에서
 *       대상 기본키 바인딩의 SELECT ... FOR UPDATE를 짧은 lock wait timeout으로 실행해,
 *       그 probe의 실제 요청 잠금과 waiter의 요청 잠금이 같은 PRIMARY RECORD 위치인지 대조한다</li>
 *   <li>probe 연결은 worker 스레드만 사용한다 (생성·rollback·종료 포함). 관찰 스레드는 observer 연결만 쓴다</li>
 *   <li>probe worker·연결·트랜잭션·대기 관계가 모두 정리되고 waiter가 처음과 같은 트랜잭션·요청 잠금으로
 *       holder를 기다리는 것을 확인한 뒤에만 인정한다 (holder 해제 전 정리 완료)</li>
 *   <li>여러 I_S 조회가 같은 캐시라고 가정하지 않는다. 연결 관계가 맞지 않으면 다시 관찰한다</li>
 * </ul>
 * waiter 세션 lock wait timeout이 probe 소요(약 2.5초)보다 충분히 긴 호출자에만 쓴다
 */
public final class PrimaryKeyLockProbe {

  public static final int PROBE_LOCK_WAIT_TIMEOUT_SECONDS = 2;
  private static final Duration INTERVAL = LockWaitObserver.OBSERVE_INTERVAL;
  private static final Duration WORKER_START_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration WORKER_FINISH_TIMEOUT = Duration.ofSeconds(PROBE_LOCK_WAIT_TIMEOUT_SECONDS + 10L);
  private static final Duration AFTER_CHECK_TIMEOUT = Duration.ofSeconds(5);
  private static final Pattern TABLE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");
  private static final String LOCK_WAIT_TIMEOUT_SQLSTATE = "HY000";
  private static final int LOCK_WAIT_TIMEOUT_VENDOR_CODE = 1205;
  static final String WORKER_THREAD_PREFIX = "pk-lock-probe-";

  private PrimaryKeyLockProbe() {
  }

  @FunctionalInterface
  public interface ConnectionFactory {
    Connection open() throws SQLException;
  }

  public enum Path { DIRECT, PROBE }

  public record Observed(LockWaitObserver.Wait observedWait, Path path) {
  }

  /** 기존 find 조인에서 얻은 waiter 후보 (요청 잠금 ID 포함) */
  record Candidate(String waiterTrxId, String requestedLockId, String lockTable, String lockIndex, String lockData) {
  }

  /** 한 번의 관찰에서 읽은 잠금 행 (공식 컬럼, LOCK_ID 문자열은 분해하지 않고 키로만 비교) */
  public record LockRow(String lockId, String trxId, String type, String table, String index,
                        String space, String page, String rec) {
    boolean recordPrimaryOf(String targetTable) {
      return "RECORD".equals(type) && table != null && table.endsWith("`" + targetTable + "`") && "PRIMARY".equals(index)
        && space != null && page != null && rec != null;
    }

    boolean samePosition(LockRow other) {
      return other != null && table != null && table.equals(other.table) && index != null && index.equals(other.index)
        && space != null && space.equals(other.space) && page != null && page.equals(other.page)
        && rec != null && rec.equals(other.rec);
    }
  }

  /** 대기 관계 한 행 (requesting → blocking) */
  public record WaitRow(String requestingTrxId, String requestedLockId, String blockingTrxId) {
  }

  /**
   * 판정 입력. 트랜잭션·요청 잠금·대기 관계는 원래 값 그대로 담고 연결 여부는 evaluate가 판정한다
   */
  public record Evidence(boolean probePkIsTarget, String probeTrxId, String probeState, String probeRequestedLockId,
                         boolean probeQueryIsProbe, LockRow probeLock, List<WaitRow> probeWaits,
                         String holderTrxId, String initialWaiterTrxId, String initialWaiterLockId,
                         String waiterTrxId, String waiterState, String waiterRequestedLockId, LockRow waiterLock,
                         List<WaitRow> waiterWaits) {
  }

  public enum Verdict {
    /** 정확한 기본키 probe와 waiter가 같은 대상 레코드를 요청 */
    ACCEPT,
    /** 연결 관계는 확인됐지만 대상이 다름 (다른 기본키·위치·인덱스) */
    REJECT,
    /** 연결 관계가 끊겨 판정할 수 없음 - 다시 관찰한다 */
    INCONSISTENT
  }

  /**
   * 대체 판정. 위치 일치만으로는 인정하지 않는다: 정확한 기본키 probe의 트랜잭션·요청 잠금·대기 관계 연결과
   * waiter가 처음 관찰한 트랜잭션·요청 잠금 그대로 holder에 막혀 있다는 연결이 모두 필요하다
   */
  public static Verdict evaluate(String table, Evidence e) {
    if (e == null || e.holderTrxId() == null) {
      return Verdict.INCONSISTENT;
    }
    // waiter: 처음 관찰한 트랜잭션·요청 잠금과 같고, 그 잠금으로 holder에 막혀 있다
    if (e.waiterTrxId() == null || !e.waiterTrxId().equals(e.initialWaiterTrxId())
        || !"LOCK WAIT".equals(e.waiterState()) || e.waiterRequestedLockId() == null
        || !e.waiterRequestedLockId().equals(e.initialWaiterLockId())
        || e.waiterLock() == null || !e.waiterRequestedLockId().equals(e.waiterLock().lockId())
        || !e.waiterTrxId().equals(e.waiterLock().trxId())
        || !blockedOnlyLinked(e.waiterWaits(), e.waiterTrxId(), e.waiterRequestedLockId())
        || e.waiterWaits().stream().noneMatch(w -> e.holderTrxId().equals(w.blockingTrxId()))) {
      return Verdict.INCONSISTENT;
    }
    // probe: 그 연결의 트랜잭션이 probe 문장으로 대기 중이고, 요청 잠금·대기 관계가 그 트랜잭션에 연결된다
    if (e.probeTrxId() == null || !"LOCK WAIT".equals(e.probeState()) || !e.probeQueryIsProbe()
        || e.probeRequestedLockId() == null || e.probeLock() == null
        || !e.probeRequestedLockId().equals(e.probeLock().lockId()) || !e.probeTrxId().equals(e.probeLock().trxId())
        || !blockedOnlyLinked(e.probeWaits(), e.probeTrxId(), e.probeRequestedLockId())) {
      return Verdict.INCONSISTENT;
    }
    // probe 차단자는 holder 또는 waiter만 허용 (실제 관계 그대로)
    for (WaitRow w : e.probeWaits()) {
      if (!e.holderTrxId().equals(w.blockingTrxId()) && !e.waiterTrxId().equals(w.blockingTrxId())) {
        return Verdict.INCONSISTENT;
      }
    }
    if (!e.probePkIsTarget() || !e.waiterLock().recordPrimaryOf(table) || !e.probeLock().recordPrimaryOf(table)
        || !e.probeLock().samePosition(e.waiterLock())) {
      return Verdict.REJECT;
    }
    return Verdict.ACCEPT;
  }

  /** 대기 행이 1개 이상이고 모두 같은 요청 트랜잭션·요청 잠금이다 */
  private static boolean blockedOnlyLinked(List<WaitRow> waits, String trxId, String lockId) {
    return waits != null && !waits.isEmpty()
      && waits.stream().allMatch(w -> trxId.equals(w.requestingTrxId()) && lockId.equals(w.requestedLockId()));
  }

  /** 잠금 대기 timeout: SQLSTATE HY000 + vendor code 1205 */
  public static boolean isLockWaitTimeout(SQLException e) {
    return e != null && LOCK_WAIT_TIMEOUT_SQLSTATE.equals(e.getSQLState())
      && e.getErrorCode() == LOCK_WAIT_TIMEOUT_VENDOR_CODE;
  }

  /**
   * 제한 시간 안에 waiter가 대상 기본키 행을 holder 때문에 기다리는 것을 관찰하면 반환, waiter가 먼저 끝나면 empty.
   * 직접 일치(lock_data = 대상 기본키)를 우선하고, lock_data가 NULL일 때만 probe를 쓴다.
   * <p>기한: 성공은 timeout 안에 판정·정리·재확인을 모두 마친 경우에만 반환한다. 기한이 지나면 새 관찰·probe를 시작하지
   * 않고, 기한 뒤에 끝난 후보 조회 결과로는 DIRECT도 인정하지 않는다. 이미 시작한 probe는 worker·연결·트랜잭션 정리를 끝낸 뒤 HarnessException으로 끝내므로,
   * 메서드 반환 자체는 timeout보다 늦을 수 있다 (정리 시간, 최대 약 probe timeout + 정리 대기). 늦은 반환은 성공이 아니다</p>
   */
  public static Optional<Observed> await(Connection observer, long waiterThreadId, long holderThreadId, String table,
                                         long primaryKey, ConnectionFactory probeConnections, BooleanSupplier waiterDone,
                                         Duration timeout) {
    return await(observer, waiterThreadId, holderThreadId, table, primaryKey, probeConnections, waiterDone, timeout,
      c -> c.lockData() == null);
  }

  /**
   * probeWhen: 대상 테이블·PRIMARY 후보 중 probe로 판정할 후보 (기본은 lock_data NULL).
   * 격리 DB 테스트가 같은 경로를 강제로 지나게 할 때만 다른 조건을 쓴다
   */
  static Optional<Observed> await(Connection observer, long waiterThreadId, long holderThreadId, String table,
                                  long primaryKey, ConnectionFactory probeConnections, BooleanSupplier waiterDone,
                                  Duration timeout, Predicate<Candidate> probeWhen) {
    checkTable(table);
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      List<Candidate> candidates = candidates(observer, waiterThreadId, holderThreadId);
      // 조회가 기한 뒤에 끝났으면 그 결과로 DIRECT·PROBE 어느 쪽도 성공 판정하지 않는다
      if (System.nanoTime() >= deadline) {
        break;
      }
      Candidate nullData = null;
      for (Candidate c : candidates) {
        if (!shape(c, table)) {
          continue;
        }
        if (probeWhen.test(c)) {
          if (nullData == null) {
            nullData = c;
          }
          continue;
        }
        if (String.valueOf(primaryKey).equals(c.lockData())) {
          return Optional.of(new Observed(new LockWaitObserver.Wait(c.waiterTrxId(), waiterThreadId, holderThreadId,
            c.lockTable(), c.lockIndex(), c.lockData()), Path.DIRECT));
        }
      }
      if (nullData != null && System.nanoTime() < deadline) {
        ProbeReport report = probe(observer, waiterThreadId, holderThreadId, table, primaryKey, primaryKey, nullData,
          probeConnections, deadline);
        if (!report.withinDeadline()) {
          throw HarnessException.harness("lock wait on " + table + " " + primaryKey + " was not confirmed within "
            + timeout.toMillis() + "ms (probe verdict=" + report.verdict() + ", probe cleaned up). waiter="
            + waiterThreadId + ", blocker=" + holderThreadId);
        }
        if (report.verdict() == Verdict.ACCEPT) {
          return Optional.of(new Observed(new LockWaitObserver.Wait(nullData.waiterTrxId(), waiterThreadId,
            holderThreadId, nullData.lockTable(), nullData.lockIndex(), String.valueOf(primaryKey)), Path.PROBE));
        }
      }
      if (waiterDone.getAsBoolean()) {
        return Optional.empty();
      }
      sleepWithin(deadline);
    }
    throw HarnessException.harness("lock wait on " + table + " " + primaryKey + " was neither observed nor finished within "
      + timeout.toSeconds() + "s. waiter=" + waiterThreadId + ", blocker=" + holderThreadId
      + ", diagnostics=" + LockWaitObserver.diagnostics(observer));
  }

  private static boolean shape(Candidate c, String table) {
    return c.lockTable() != null && c.lockTable().endsWith("`" + table + "`") && "PRIMARY".equals(c.lockIndex());
  }

  /** LockWaitObserver.find와 같은 조인·조건 + 요청 잠금 ID */
  static List<Candidate> candidates(Connection observer, long waiterThreadId, long holderThreadId) {
    String sql = """
      select r.trx_id, w.requested_lock_id, l.lock_table, l.lock_index, l.lock_data
      from information_schema.innodb_lock_waits w
      join information_schema.innodb_trx r on r.trx_id = w.requesting_trx_id
      join information_schema.innodb_trx b on b.trx_id = w.blocking_trx_id
      join information_schema.innodb_locks l on l.lock_id = w.requested_lock_id
      where r.trx_state = 'LOCK WAIT' and r.trx_mysql_thread_id = ? and b.trx_mysql_thread_id = ?
      """;
    List<Candidate> out = new ArrayList<>();
    try (PreparedStatement ps = observer.prepareStatement(sql)) {
      ps.setLong(1, waiterThreadId);
      ps.setLong(2, holderThreadId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new Candidate(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
        }
      }
      return out;
    } catch (SQLException e) {
      throw HarnessException.harness("lock wait observation failed", e);
    }
  }

  /**
   * probe 실행 결과. 반환됐다면 worker·연결·트랜잭션 정리와 waiter 재확인이 끝난 상태다.
   * withinDeadline: 판정·정리·재확인이 관찰 기한 안에 끝났는지 (false이면 verdict와 무관하게 성공으로 쓰지 않는다)
   */
  public record ProbeReport(Verdict verdict, long probeConnectionId, String probeEnd, boolean probeCleanedUp,
                            boolean waiterUnchanged, boolean withinDeadline, List<String> notes) {
  }

  /** worker가 끝에 돌려주는 probe 연결 상태 (worker 스레드에서만 만든다) */
  private record WorkerOutcome(boolean rowExists, String explainKey, boolean lockStatementRun, boolean acquired,
                               boolean lockWaitTimeout, String sqlState, int vendorCode, boolean rolledBack,
                               boolean closed, SQLException lockFailure, SQLException stepFailure,
                               SQLException rollbackFailure, SQLException closeFailure) {
  }

  /** 단독 probe(격리 DB 테스트)의 관찰 기한 */
  private static final Duration STANDALONE_DEADLINE = Duration.ofSeconds(30);

  static ProbeReport probe(Connection observer, long waiterThreadId, long holderThreadId, String table, long targetKey,
                           long probeKey, Candidate initial, ConnectionFactory probeConnections) {
    return probe(observer, waiterThreadId, holderThreadId, table, targetKey, probeKey, initial, probeConnections,
      System.nanoTime() + STANDALONE_DEADLINE.toNanos());
  }

  /**
   * 대상(probeKey) 기본키 probe로 waiter 대기를 판정한다. 반환 전에 probe worker·연결·트랜잭션·대기 관계 정리와
   * waiter 재확인을 끝낸다. 정리에 실패하면 HarnessException (원래 오류는 cause, 정리 오류는 suppressed로 보존).
   * deadline(System.nanoTime 기준)이 지나면 새 관찰을 시작하지 않고 정리만 끝낸 뒤 withinDeadline=false로 반환한다
   */
  static ProbeReport probe(Connection observer, long waiterThreadId, long holderThreadId, String table, long targetKey,
                           long probeKey, Candidate initial, ConnectionFactory probeConnections, long deadline) {
    checkTable(table);
    List<String> notes = new ArrayList<>();
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, WORKER_THREAD_PREFIX + waiterThreadId);
      t.setDaemon(true);
      return t;
    });
    CompletableFuture<Long> probeConnectionId = new CompletableFuture<>();
    Future<WorkerOutcome> worker = null;
    long cid = -1;
    Verdict observed = Verdict.INCONSISTENT;
    Throwable primary = null;
    try {
      worker = executor.submit(() -> runWorker(probeConnections, table, probeKey, probeConnectionId));
      cid = waitConnectionId(probeConnectionId);
      observed = observeProbe(observer, waiterThreadId, holderThreadId, cid, table, targetKey == probeKey,
        initial, worker, notes, deadline);
    } catch (Throwable t) {
      primary = t;
    }
    // 정리: worker 실제 종료와 결과(또는 worker 실패) 회수 → executor 종료. 관찰 스레드는 probe 연결을 만지지 않는다
    Shutdown down = shutdown(executor, worker);
    if (primary != null) {
      // 관찰·준비 단계 실패: 원래 오류를 cause로 두고 worker가 보고한 정리 오류를 suppressed로 보존한다.
      // observer를 신뢰할 수 없으므로 사후 확인(afterCheck)은 하지 않는다 - 아래 정리 상태는 worker 보고값이다
      Throwable failure = primary instanceof Error ? primary
        : HarnessException.harness("probe failed before verdict (after-check not run); " + down.describe(), primary);
      for (Throwable e : down.errors()) {
        if (!related(e, primary)) {
          failure.addSuppressed(e);
        }
      }
      if (failure instanceof Error err) {
        throw err;
      }
      throw (HarnessException) failure;
    }
    if (down.terminationFailure() != null || down.workerFailure() != null || down.outcome() == null) {
      HarnessException failure = HarnessException.harness("probe worker cleanup failed; " + down.describe(),
        down.workerFailure() != null ? down.workerFailure() : down.terminationFailure());
      if (down.workerFailure() != null && down.terminationFailure() != null) {
        failure.addSuppressed(down.terminationFailure());
      }
      throw failure;
    }
    WorkerOutcome outcome = down.outcome();
    String end = outcome.lockStatementRun()
      ? (outcome.acquired() ? "acquired" : outcome.sqlState() + "/" + outcome.vendorCode()) : "not-run";
    notes.add("precheck row_exists=" + outcome.rowExists() + " explain_key=" + outcome.explainKey());
    SQLException workerFailure = outcome.stepFailure() != null ? outcome.stepFailure()
      : outcome.lockFailure() != null && !outcome.lockWaitTimeout() ? outcome.lockFailure() : null;
    if (workerFailure != null || outcome.rollbackFailure() != null || outcome.closeFailure() != null) {
      // 원래 오류(예상된 1205 포함)를 cause로, rollback·close 오류를 suppressed로 보존한다. 정리 오류는 무시하지 않는다
      SQLException cause = workerFailure != null ? workerFailure : outcome.lockFailure() != null ? outcome.lockFailure()
        : outcome.rollbackFailure() != null ? outcome.rollbackFailure() : outcome.closeFailure();
      HarnessException failure = HarnessException.harness(workerFailure != null
        ? "probe worker failed: " + workerFailure.getSQLState() + "/" + workerFailure.getErrorCode()
        : "probe connection cleanup failed (rollback=" + (outcome.rollbackFailure() != null) + ", close="
          + (outcome.closeFailure() != null) + ")", cause);
      for (SQLException e : new SQLException[]{outcome.rollbackFailure(), outcome.closeFailure()}) {
        if (e != null && e != cause) {
          failure.addSuppressed(e);
        }
      }
      throw failure;
    }
    if (!outcome.lockWaitTimeout() || !outcome.rolledBack() || !outcome.closed()) {
      notes.add("probe did not end as lock wait timeout with rollback+close: end=" + end
        + " rolledBack=" + outcome.rolledBack() + " closed=" + outcome.closed());
      observed = observed == Verdict.ACCEPT ? Verdict.INCONSISTENT : observed;
    }
    ProbeReport report = new ProbeReport(observed, cid, end, false, false, false, notes);
    // 정리 확인: probe 트랜잭션·대기 관계·연결 소멸 + waiter가 처음과 같은 트랜잭션·요청 잠금으로 holder 대기
    AfterCheck after = afterCheck(observer, waiterThreadId, holderThreadId, report.probeConnectionId(), initial);
    notes.add("after probe_gone=" + after.probeGone() + " waiter_unchanged=" + after.waiterUnchanged());
    if (!after.probeGone()) {
      throw HarnessException.harness("probe transaction or wait relation still visible after cleanup");
    }
    Verdict verdict = report.verdict() == Verdict.ACCEPT && !after.waiterUnchanged() ? Verdict.INCONSISTENT
      : report.verdict();
    // 성공 반환 시점 기준 기한 확인 (정리·재확인을 마친 뒤)
    boolean withinDeadline = System.nanoTime() < deadline;
    notes.add("within_deadline=" + withinDeadline);
    return new ProbeReport(verdict, report.probeConnectionId(), report.probeEnd(), true, after.waiterUnchanged(),
      withinDeadline, notes);
  }

  private static long waitConnectionId(CompletableFuture<Long> probeConnectionId) throws Exception {
    try {
      return probeConnectionId.get(WORKER_START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      throw cause instanceof Exception ex ? ex : e;
    } catch (TimeoutException e) {
      throw HarnessException.harness("probe connection was not ready", e);
    }
  }

  /** worker 스레드: 연결 생성부터 rollback·종료까지 이 스레드만 연결을 쓴다 */
  private static WorkerOutcome runWorker(ConnectionFactory factory, String table, long probeKey,
                                         CompletableFuture<Long> connectionId) {
    Connection c;
    try {
      c = factory.open();
    } catch (SQLException | RuntimeException e) {
      connectionId.completeExceptionally(e);
      throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
    }
    boolean rowExists = false;
    String explainKey = null;
    boolean lockRun = false;
    boolean acquired = false;
    boolean timeout = false;
    String sqlState = null;
    int vendor = 0;
    boolean rolledBack = false;
    SQLException lockFailure = null;
    SQLException stepFailure = null;
    SQLException rollbackFailure = null;
    SQLException closeFailure = null;
    try {
      try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select connection_id()")) {
        rs.next();
        connectionId.complete(rs.getLong(1));
      }
      try (Statement st = c.createStatement()) {
        st.execute("set session innodb_lock_wait_timeout = " + PROBE_LOCK_WAIT_TIMEOUT_SECONDS);
      }
      try (PreparedStatement ps = c.prepareStatement("select count(*) from " + table + " where id = ?")) {
        ps.setLong(1, probeKey);
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          rowExists = rs.getLong(1) == 1;
        }
      }
      // 잠금 절 없이 같은 조건의 실행 계획 (MariaDB는 const 접근 행을 최적화 단계에서 읽는다)
      try (PreparedStatement ps = c.prepareStatement("explain select id from " + table + " where id = ?")) {
        ps.setLong(1, probeKey);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            explainKey = rs.getString("key");
          }
        }
      }
      if (rowExists && "PRIMARY".equals(explainKey)) {
        c.setAutoCommit(false);
        lockRun = true;
        try (PreparedStatement ps = c.prepareStatement("select id from " + table + " where id = ? for update")) {
          ps.setLong(1, probeKey);
          ps.executeQuery().close();
          acquired = true;
        } catch (SQLException e) {
          sqlState = e.getSQLState();
          vendor = e.getErrorCode();
          timeout = isLockWaitTimeout(e);
          lockFailure = e;
        }
      }
    } catch (SQLException e) {
      connectionId.completeExceptionally(e);
      stepFailure = e;
      sqlState = e.getSQLState();
      vendor = e.getErrorCode();
    } finally {
      try {
        if (!c.getAutoCommit()) {
          c.rollback();
        }
        rolledBack = true;
      } catch (SQLException e) {
        rollbackFailure = e;
      }
      try {
        c.close();
      } catch (SQLException e) {
        closeFailure = e;
      }
    }
    boolean closed;
    try {
      closed = c.isClosed();
    } catch (SQLException e) {
      closed = false;
    }
    return new WorkerOutcome(rowExists, explainKey, lockRun, acquired, timeout, sqlState, vendor, rolledBack, closed,
      lockFailure, stepFailure, rollbackFailure, closeFailure);
  }

  /**
   * 정리 결과. outcome: worker가 정상 반환한 연결 상태 (없으면 null), workerFailure: worker 자체 실패,
   * terminationFailure: worker 종료·대기 실패. 결과는 이 한 곳에서만 회수한다
   */
  private record Shutdown(WorkerOutcome outcome, Throwable workerFailure, Throwable terminationFailure) {

    /** worker가 보고한 정리 오류 전체 (예상된 1205 잠금 timeout은 오류가 아니므로 제외) */
    List<Throwable> errors() {
      List<Throwable> out = new ArrayList<>();
      if (outcome != null) {
        if (outcome.stepFailure() != null) {
          out.add(outcome.stepFailure());
        }
        if (outcome.lockFailure() != null && !outcome.lockWaitTimeout()) {
          out.add(outcome.lockFailure());
        }
        if (outcome.rollbackFailure() != null) {
          out.add(outcome.rollbackFailure());
        }
        if (outcome.closeFailure() != null) {
          out.add(outcome.closeFailure());
        }
      }
      if (workerFailure != null) {
        out.add(workerFailure);
      }
      if (terminationFailure != null) {
        out.add(terminationFailure);
      }
      return out;
    }

    String describe() {
      return outcome == null
        ? "worker result unavailable (worker_failed=" + (workerFailure != null) + ", terminated=" + (terminationFailure == null) + ")"
        : "worker reported rolledBack=" + outcome.rolledBack() + " closed=" + outcome.closed() + " rollback_failure="
          + (outcome.rollbackFailure() != null) + " close_failure=" + (outcome.closeFailure() != null)
          + " terminated=" + (terminationFailure == null);
    }
  }

  /** a가 b 자신이거나 b를 원인 체인에 포함하면(같은 오류를 감싼 것) true - 중복·self-suppression 방지 */
  private static boolean related(Throwable a, Throwable b) {
    for (Throwable t = a; t != null; t = t.getCause()) {
      if (t == b) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    for (Throwable t = b; t != null; t = t.getCause()) {
      if (t == a) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }

  /**
   * worker 종료 대기와 결과 회수 → executor 종료. 이미 끝난 worker도 결과를 읽는다.
   * cancel 요청만으로 종료됐다고 보지 않는다
   */
  private static Shutdown shutdown(ExecutorService executor, Future<WorkerOutcome> worker) {
    WorkerOutcome outcome = null;
    Throwable workerFailure = null;
    Throwable terminationFailure = null;
    try {
      if (worker != null) {
        try {
          outcome = worker.get(WORKER_FINISH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
          workerFailure = e.getCause() != null ? e.getCause() : e;
        } catch (TimeoutException e) {
          terminationFailure = HarnessException.harness("probe worker did not finish", e);
        }
      }
      executor.shutdown();
      if (!executor.awaitTermination(WORKER_FINISH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
        if (terminationFailure == null) {
          terminationFailure = HarnessException.harness("probe worker did not terminate");
        }
      } else if (worker != null && !worker.isDone() && terminationFailure == null) {
        terminationFailure = HarnessException.harness("probe worker future not done after termination");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
      terminationFailure = e;
    }
    return new Shutdown(outcome, workerFailure, terminationFailure);
  }

  /** probe 대기를 관찰해 판정 (worker가 끝나거나 probe timeout이 지나면 그때까지의 최선 판정) */
  private static Verdict observeProbe(Connection observer, long waiterThreadId, long holderThreadId, long probeCid,
                                      String table, boolean probePkIsTarget, Candidate initial,
                                      Future<WorkerOutcome> worker, List<String> notes, long deadline)
    throws SQLException {
    long until = Math.min(deadline, System.nanoTime() + Duration.ofSeconds(PROBE_LOCK_WAIT_TIMEOUT_SECONDS).toNanos());
    Verdict last = Verdict.INCONSISTENT;
    int attempts = 0;
    while (System.nanoTime() < until && !worker.isDone()) {
      sleepWithin(until);
      if (System.nanoTime() >= until) {
        break;
      }
      attempts++;
      Snapshot s = snapshot(observer, waiterThreadId, holderThreadId, probeCid);
      Evidence e = s.evidence(probePkIsTarget, initial);
      last = evaluate(table, e);
      if (last != Verdict.INCONSISTENT) {
        notes.add("probe observed attempt=" + attempts + " verdict=" + last);
        return last;
      }
    }
    notes.add("probe not linked within probe timeout or deadline attempts=" + attempts);
    return Verdict.INCONSISTENT;
  }

  private record Snapshot(String holderTrx, String waiterTrx, String waiterState, String waiterRequested,
                          String probeTrx, String probeState, String probeRequested, boolean probeQueryIsProbe,
                          List<WaitRow> waits, List<LockRow> locks) {
    Evidence evidence(boolean probePkIsTarget, Candidate initial) {
      List<WaitRow> probeWaits = probeTrx == null ? List.of()
        : waits.stream().filter(w -> probeTrx.equals(w.requestingTrxId())).toList();
      List<WaitRow> waiterWaits = waiterTrx == null ? List.of()
        : waits.stream().filter(w -> waiterTrx.equals(w.requestingTrxId())).toList();
      return new Evidence(probePkIsTarget, probeTrx, probeState, probeRequested, probeQueryIsProbe,
        lock(probeRequested), probeWaits, holderTrx, initial.waiterTrxId(), initial.requestedLockId(), waiterTrx,
        waiterState, waiterRequested, lock(waiterRequested), waiterWaits);
    }

    LockRow lock(String lockId) {
      return lockId == null ? null : locks.stream().filter(l -> lockId.equals(l.lockId())).findFirst().orElse(null);
    }
  }

  /** INNODB_TRX → INNODB_LOCK_WAITS → INNODB_LOCKS (같은 캐시라고 가정하지 않고 evaluate가 연결을 검증한다) */
  private static Snapshot snapshot(Connection observer, long waiterThreadId, long holderThreadId, long probeCid)
    throws SQLException {
    String holderTrx = null;
    String waiterTrx = null;
    String waiterState = null;
    String waiterRequested = null;
    String probeTrx = null;
    String probeState = null;
    String probeRequested = null;
    boolean probeQueryIsProbe = false;
    List<WaitRow> waits = new ArrayList<>();
    List<LockRow> locks = new ArrayList<>();
    try (Statement st = observer.createStatement()) {
      try (ResultSet rs = st.executeQuery("select trx_id, trx_state, trx_mysql_thread_id, trx_requested_lock_id, "
        + "coalesce(trx_query like 'select id from % where id = % for update', false) from information_schema.innodb_trx")) {
        while (rs.next()) {
          long thread = rs.getLong(3);
          if (thread == waiterThreadId) {
            waiterTrx = rs.getString(1);
            waiterState = rs.getString(2);
            waiterRequested = rs.getString(4);
          } else if (thread == holderThreadId) {
            holderTrx = rs.getString(1);
          } else if (thread == probeCid) {
            probeTrx = rs.getString(1);
            probeState = rs.getString(2);
            probeRequested = rs.getString(4);
            probeQueryIsProbe = rs.getBoolean(5);
          }
        }
      }
      try (ResultSet rs = st.executeQuery("select requesting_trx_id, requested_lock_id, blocking_trx_id "
        + "from information_schema.innodb_lock_waits")) {
        while (rs.next()) {
          waits.add(new WaitRow(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
      }
      try (ResultSet rs = st.executeQuery("select lock_id, lock_trx_id, lock_type, lock_table, lock_index, lock_space, "
        + "lock_page, lock_rec from information_schema.innodb_locks")) {
        while (rs.next()) {
          locks.add(new LockRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getString(6), rs.getString(7), rs.getString(8)));
        }
      }
    }
    return new Snapshot(holderTrx, waiterTrx, waiterState, waiterRequested, probeTrx, probeState, probeRequested,
      probeQueryIsProbe, waits, locks);
  }

  private record AfterCheck(boolean probeGone, boolean waiterUnchanged) {
  }

  /** probe 트랜잭션·대기 관계·연결 소멸과 waiter 불변을 새로 관찰해 확인한다 (제한 시간 안에 다시 읽음) */
  private static AfterCheck afterCheck(Connection observer, long waiterThreadId, long holderThreadId, long probeCid,
                                       Candidate initial) {
    long until = System.nanoTime() + AFTER_CHECK_TIMEOUT.toNanos();
    AfterCheck last = new AfterCheck(false, false);
    try {
      while (System.nanoTime() < until) {
        sleep(INTERVAL);
        Snapshot s = snapshot(observer, waiterThreadId, holderThreadId, probeCid);
        boolean inProcesslist;
        try (PreparedStatement ps = observer.prepareStatement(
          "select count(*) from information_schema.processlist where id = ?")) {
          ps.setLong(1, probeCid);
          try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            inProcesslist = rs.getLong(1) > 0;
          }
        }
        boolean probeGone = s.probeTrx() == null && !inProcesslist;
        List<WaitRow> waiterWaits = s.waiterTrx() == null ? List.of()
          : s.waits().stream().filter(w -> s.waiterTrx().equals(w.requestingTrxId())).toList();
        LockRow waiterLock = s.lock(s.waiterRequested());
        boolean waiterUnchanged = initial.waiterTrxId().equals(s.waiterTrx()) && "LOCK WAIT".equals(s.waiterState())
          && initial.requestedLockId().equals(s.waiterRequested()) && waiterLock != null
          && initial.waiterTrxId().equals(waiterLock.trxId())
          && blockedOnlyLinked(waiterWaits, initial.waiterTrxId(), initial.requestedLockId())
          && s.holderTrx() != null && waiterWaits.stream().anyMatch(w -> s.holderTrx().equals(w.blockingTrxId()));
        last = new AfterCheck(probeGone, waiterUnchanged);
        if (probeGone && waiterUnchanged) {
          return last;
        }
      }
      return last;
    } catch (SQLException e) {
      throw HarnessException.harness("probe cleanup check failed", e);
    }
  }

  private static void checkTable(String table) {
    if (table == null || !TABLE_NAME.matcher(table).matches()) {
      throw HarnessException.precondition("invalid table name for probe");
    }
  }

  /** 관찰 간격과 남은 시간 중 짧은 만큼 쉰다 */
  private static void sleepWithin(long until) {
    long remainingMs = (until - System.nanoTime()) / 1_000_000;
    if (remainingMs > 0) {
      sleep(Duration.ofMillis(Math.min(INTERVAL.toMillis(), remainingMs)));
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw HarnessException.harness("lock probe interrupted");
    }
  }
}
