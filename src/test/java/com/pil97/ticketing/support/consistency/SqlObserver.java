package com.pil97.ticketing.support.consistency;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 테스트 전용 JDBC 관찰·제어 지점 (production 코드에는 hook 없음)
 * - ObservingDataSource가 감싼 커넥션의 SQL 이벤트를 (thread 이름, DB connection ID, SQL)과 함께 기록한다
 *   - PREPARED: prepareStatement 시점 (아직 서버에 전송되지 않음)
 *   - EXEC_START: execute 계열 호출 직전 (서버 전송 시작)
 *   - EXEC_END: execute 계열 호출 반환 직후 (성공·실패 무관). 실패면 SQLException의 vendor errorCode·SQLState를 함께 기록한다
 * - CommitGate (kind=BEFORE_COMMIT): 지정 thread의 커넥션이 지정 SQL을 실행한 뒤 commit()을 호출하면, 실제 commit 직전에 대기시킨다
 *   - gate의 상태(ARMED → RELEASED / ABORTED / TIMED_OUT / INTERRUPTED)는 전역 등록과 별개로 유지된다
 *   - abort·timeout·interrupt 이후에는 해당 thread의 모든 commit을 거부한다 (pause 도달 전 abort 포함)
 *   - 등록 해제(clear)는 worker 종료가 확인된 뒤에만 호출한다
 * - StatementGate (같은 CommitGate 타입, kind=AFTER_STATEMENT): 지정 thread가 지정 SQL 실행을 정상 반환한 직후 대기시킨다
 *   - 같은 전역 등록·상태 머신·abort·격리 규칙을 따른다
 *   - abort·timeout·interrupt 이후에는 해당 thread의 모든 SQL 실행과 commit을 거부한다
 * - 세션 진단: ObservingDataSource가 물리 커넥션별로 처음 받을 때 SESSION 변수를 1회 조회해 connection ID별로 보관한다
 *   (물리 커넥션의 사실이므로 clear()로 지우지 않는다)
 * - 모든 대기는 제한 시간을 가진다
 */
public final class SqlObserver {

  private static final Object MONITOR = new Object();
  private static final List<Event> EVENTS = new ArrayList<>();
  private static final AtomicReference<CommitGate> GATE = new AtomicReference<>();

  private SqlObserver() {
  }

  public enum Phase {
    PREPARED, EXEC_START, EXEC_END
  }

  // MariaDB ER_CHECKREAD - snapshot isolation 충돌
  public static final int ER_CHECKREAD = 1020;

  /**
   * @param seq       이벤트 순번
   * @param execId    EXEC_START의 seq (EXEC_START와 EXEC_END 짝 맞춤용, PREPARED는 0)
   * @param errorCode EXEC_END가 SQLException으로 끝났을 때의 vendor errorCode (그 외 0)
   * @param sqlState  EXEC_END가 SQLException으로 끝났을 때의 SQLState (그 외 null)
   */
  public record Event(long seq, Phase phase, String threadName, long connectionId, String sql, long execId,
                      int errorCode, String sqlState) {

    public boolean failed() {
      return phase == Phase.EXEC_END && (errorCode != 0 || sqlState != null);
    }
  }

  private static long nextSeq = 0;

  /**
   * 이벤트 기록 (오류 없음)
   *
   * @param execId EXEC_END일 때 짝이 되는 EXEC_START의 seq. EXEC_START는 자기 seq가 execId가 된다
   * @return 기록한 이벤트의 seq
   */
  static long record(Phase phase, long connectionId, String sql, long execId) {
    return record(phase, connectionId, sql, execId, 0, null);
  }

  /**
   * 실행 종료 기록 - error가 있으면 vendor errorCode·SQLState를 함께 남긴다
   */
  static long recordEnd(long connectionId, String sql, long execId, SQLException error) {
    return error == null
      ? record(Phase.EXEC_END, connectionId, sql, execId, 0, null)
      : record(Phase.EXEC_END, connectionId, sql, execId, error.getErrorCode(), error.getSQLState());
  }

  private static long record(Phase phase, long connectionId, String sql, long execId, int errorCode, String sqlState) {
    synchronized (MONITOR) {
      long seq = ++nextSeq;
      long effectiveExecId = phase == Phase.EXEC_START ? seq : execId;
      EVENTS.add(new Event(seq, phase, Thread.currentThread().getName(), connectionId, normalize(sql), effectiveExecId,
        errorCode, sqlState));
      MONITOR.notifyAll();
      return seq;
    }
  }

  static String normalize(String sql) {
    return sql == null ? "" : sql.toLowerCase().replaceAll("\\s+", " ").trim();
  }

  /**
   * 지정 thread의 현재 in-flight 실행 (실행 시작 후 아직 반환되지 않은 가장 최근 실행)
   * - worker는 단일 thread이므로 동시에 하나의 실행만 in-flight일 수 있다
   */
  public static Optional<Event> currentInFlight(String threadName) {
    synchronized (MONITOR) {
      Event latest = null;
      for (Event e : EVENTS) {
        if (e.phase() == Phase.EXEC_START && e.threadName().equals(threadName) && !ended(e)) {
          latest = e;
        }
      }
      return Optional.ofNullable(latest);
    }
  }

  /**
   * 지정 실행이 아직 반환되지 않았는지 확인
   */
  public static boolean stillInFlight(Event start) {
    synchronized (MONITOR) {
      return !ended(start);
    }
  }

  private static boolean ended(Event start) {
    return EVENTS.stream().anyMatch(e -> e.phase() == Phase.EXEC_END && e.execId() == start.execId());
  }

  // ===================== 실행 기록 조회 (worker·connection ID·execId·오류 대응) =====================

  /**
   * 지정 thread의 실행 시작 이벤트 목록 (기록 순서)
   */
  public static List<Event> executions(String threadName) {
    synchronized (MONITOR) {
      List<Event> result = new ArrayList<>();
      for (Event e : EVENTS) {
        if (e.phase() == Phase.EXEC_START && e.threadName().equals(threadName)) {
          result.add(e);
        }
      }
      return result;
    }
  }

  /**
   * 실행 시작 이벤트에 대응하는 종료 이벤트 (아직 반환되지 않았으면 empty)
   */
  public static Optional<Event> endOf(Event start) {
    synchronized (MONITOR) {
      for (Event e : EVENTS) {
        if (e.phase() == Phase.EXEC_END && e.execId() == start.execId()) {
          return Optional.of(e);
        }
      }
      return Optional.empty();
    }
  }

  /**
   * 지정 thread에서 vendor errorCode 1020(ER_CHECKREAD)으로 끝난 실행 종료 이벤트
   */
  public static List<Event> snapshotConflicts(String threadName) {
    synchronized (MONITOR) {
      List<Event> result = new ArrayList<>();
      for (Event e : EVENTS) {
        if (e.phase() == Phase.EXEC_END && e.threadName().equals(threadName) && e.errorCode() == ER_CHECKREAD) {
          result.add(e);
        }
      }
      return result;
    }
  }

  /**
   * 지정 thread에서 SQLException으로 끝난 실행 종료 이벤트 (errorCode·SQLState 포함)
   */
  public static List<Event> failedExecutions(String threadName) {
    synchronized (MONITOR) {
      List<Event> result = new ArrayList<>();
      for (Event e : EVENTS) {
        if (e.threadName().equals(threadName) && e.failed()) {
          result.add(e);
        }
      }
      return result;
    }
  }

  /**
   * 지정 thread가 SQL을 실행한 서버 connection ID 집합
   */
  public static Set<Long> connectionsOf(String threadName) {
    Set<Long> ids = new LinkedHashSet<>();
    for (Event e : executions(threadName)) {
      ids.add(e.connectionId());
    }
    return ids;
  }

  // ===================== 세션 진단 (물리 커넥션별, clear 대상 아님) =====================

  /**
   * @param error 조회 실패 시 사유 (성공이면 null)
   */
  public record SessionDiagnostic(long connectionId, String capturedBy, String snapshotIsolation,
                                  String transactionIsolation, String error) {
  }

  private static final Map<Long, SessionDiagnostic> SESSION_DIAGNOSTICS = new ConcurrentHashMap<>();

  static boolean hasSessionDiagnostic(long connectionId) {
    return SESSION_DIAGNOSTICS.containsKey(connectionId);
  }

  static void recordSessionDiagnostic(SessionDiagnostic diagnostic) {
    SESSION_DIAGNOSTICS.putIfAbsent(diagnostic.connectionId(), diagnostic);
  }

  public static Optional<SessionDiagnostic> sessionDiagnostic(long connectionId) {
    return Optional.ofNullable(SESSION_DIAGNOSTICS.get(connectionId));
  }

  /**
   * 사전조건: 지정 thread가 실제로 사용한 모든 connection의 SESSION 값이
   * innodb_snapshot_isolation=1, transaction_isolation=REPEATABLE-READ로 진단되어 있어야 한다
   * - 진단 누락·조회 실패·다른 값은 HarnessException(PRECONDITION)
   * - 별도 클라이언트나 임의 connection 하나의 값으로 대신하지 않는다
   */
  public static void requireSessionDiagnostics(String... threadNames) {
    for (String threadName : threadNames) {
      Set<Long> connections = connectionsOf(threadName);
      if (connections.isEmpty()) {
        throw HarnessException.precondition("no observed connection for thread " + threadName);
      }
      for (Long connectionId : connections) {
        SessionDiagnostic diagnostic = SESSION_DIAGNOSTICS.get(connectionId);
        if (diagnostic == null || diagnostic.error() != null
          || !"1".equals(diagnostic.snapshotIsolation())
          || !"REPEATABLE-READ".equalsIgnoreCase(diagnostic.transactionIsolation())) {
          throw HarnessException.precondition("session diagnostic mismatch for thread " + threadName
            + " connection " + connectionId + ": " + diagnostic);
        }
      }
      System.out.println("[SessionDiagnostic] thread=" + threadName + " "
        + connections.stream().map(SESSION_DIAGNOSTICS::get).toList());
    }
  }

  /**
   * 지정 thread의 커넥션이 afterSql에 맞는 SQL을 실행한 뒤의 첫 commit()을 실제 commit 직전에 멈춘다 (1회용)
   */
  public static CommitGate armCommitGate(String threadName, Pattern afterSql) {
    return arm(new CommitGate(GateKind.BEFORE_COMMIT, threadName, afterSql));
  }

  /**
   * StatementGate: 지정 thread가 statementSql에 맞는 SQL 실행을 정상 반환한 직후 멈춘다 (1회용)
   * - 실행 종료 기록(EXEC_END) 이후에 멈추므로 probe는 이 실행을 in-flight로 보지 않는다
   */
  public static CommitGate armStatementGate(String threadName, Pattern statementSql) {
    return arm(new CommitGate(GateKind.AFTER_STATEMENT, threadName, statementSql));
  }

  private static CommitGate arm(CommitGate gate) {
    requireNoIsolation();
    if (!GATE.compareAndSet(null, gate)) {
      throw HarnessException.harness("gate already registered: " + GATE.get());
    }
    return gate;
  }

  static CommitGate currentGate() {
    return GATE.get();
  }

  /**
   * teardown 1단계: 등록된 gate를 abort한다 (등록은 유지)
   * - pause 중 worker는 commit하지 않고 실패하고, 아직 pause에 도달하지 않은 worker의 이후 commit도 거부된다
   */
  public static void abortGate() {
    CommitGate gate = GATE.get();
    if (gate != null) {
      gate.abort();
    }
  }

  /**
   * teardown 마지막 단계 또는 테스트 시작 시: gate 등록과 관찰 기록을 지운다
   * - 종료를 확인하지 못한 worker가 격리 목록에 남아 있으면 아무것도 지우지 않고 HarnessException을 던진다
   *   (살아 있는 worker의 ABORTED gate 등록이 사라져 늦은 commit 차단이 풀리는 것을 막는다)
   * - 아직 결정되지 않은 gate가 남아 있으면 abort한 뒤 지운다
   */
  public static void clear() {
    requireNoIsolation();
    CommitGate gate = GATE.getAndSet(null);
    if (gate != null) {
      gate.abort();
    }
    synchronized (MONITOR) {
      EVENTS.clear();
    }
  }

  // ===================== worker 격리 (테스트 인스턴스 간 공유) =====================

  private static final Object ISOLATION_MONITOR = new Object();
  // 종료를 확인하지 못한 worker executor - 참조를 보존하고, 종료가 확인될 때만 제거한다
  private static final List<ExecutorService> UNTERMINATED = new ArrayList<>();
  // 격리를 일으킨 원인 (종료 timeout·interrupt·종료 요청 예외)
  private static final List<Throwable> ISOLATION_CAUSES = new ArrayList<>();

  /**
   * 모든 executor에 먼저 종료를 요청한 뒤, 하나의 deadline 안에서 각 종료 결과를 수집한다
   * - 첫 timeout·예외 때문에 나머지 executor의 종료 요청·확인을 건너뛰지 않는다
   * - 종료를 확인하지 못한 executor는 격리 목록에 보존하고 HarnessException을 던진다 (원인은 suppressed로 보존)
   * - 확인 중 interrupt되면 interrupt 플래그를 복원하고, 그 시점에 종료되지 않은 executor를 모두 격리한다
   */
  public static void terminateWorkers(Collection<ExecutorService> executors, Duration timeout) {
    List<ExecutorService> targets = new ArrayList<>();
    for (ExecutorService executor : executors) {
      if (executor != null) {
        targets.add(executor);
      }
    }
    List<Throwable> errors = new ArrayList<>();

    // 1단계: 모든 executor에 종료 요청
    for (ExecutorService executor : targets) {
      try {
        executor.shutdownNow();
      } catch (RuntimeException e) {
        errors.add(e);
      }
    }

    // 2단계: 하나의 deadline 안에서 종료 결과 수집
    long deadline = System.nanoTime() + timeout.toNanos();
    List<ExecutorService> alive = new ArrayList<>();
    boolean interrupted = false;
    for (ExecutorService executor : targets) {
      if (interrupted) {
        if (!executor.isTerminated()) {
          alive.add(executor);
        }
        continue;
      }
      try {
        long remaining = Math.max(0L, deadline - System.nanoTime());
        if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
          alive.add(executor);
        }
      } catch (InterruptedException e) {
        interrupted = true;
        errors.add(e);
        if (!executor.isTerminated()) {
          alive.add(executor);
        }
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }

    if (!alive.isEmpty()) {
      synchronized (ISOLATION_MONITOR) {
        UNTERMINATED.addAll(alive);
        ISOLATION_CAUSES.addAll(errors);
      }
      HarnessException failure = HarnessException.harness(alive.size() + " of " + targets.size()
        + " worker executor(s) not confirmed terminated within " + timeout
        + (interrupted ? " (interrupted)" : "") + "; gate and observations retained in isolation");
      errors.forEach(failure::addSuppressed);
      throw failure;
    }
    if (!errors.isEmpty()) {
      HarnessException failure = HarnessException.harness("worker termination reported errors (all terminated)");
      errors.forEach(failure::addSuppressed);
      throw failure;
    }
  }

  /**
   * 격리 상태 여부 - 격리 목록 중 종료가 확인된 executor는 제거한 뒤 판단한다
   */
  public static boolean isolationActive() {
    synchronized (ISOLATION_MONITOR) {
      UNTERMINATED.removeIf(ExecutorService::isTerminated);
      if (UNTERMINATED.isEmpty()) {
        ISOLATION_CAUSES.clear();
        return false;
      }
      return true;
    }
  }

  /**
   * 격리 상태이면 원인을 보존한 HarnessException으로 중단한다 (후속 setUp·clear·fixture 정리 차단)
   */
  public static void requireNoIsolation() {
    synchronized (ISOLATION_MONITOR) {
      if (!isolationActive()) {
        return;
      }
      HarnessException failure = HarnessException.harness(UNTERMINATED.size()
        + " previous worker executor(s) still not terminated; gate/observations/fixture retained, scenario not started");
      ISOLATION_CAUSES.forEach(failure::addSuppressed);
      throw failure;
    }
  }

  public enum GateState {
    ARMED, RELEASED, ABORTED, TIMED_OUT, INTERRUPTED
  }

  public enum GateKind {
    // 대상 SQL 실행 후 첫 commit 직전에 멈춤
    BEFORE_COMMIT,
    // 대상 SQL 실행이 정상 반환된 직후 멈춤
    AFTER_STATEMENT
  }

  public static final class CommitGate {

    // 해제 신호가 오지 않을 때 worker가 무한 대기하지 않도록 하는 상한
    private static final long MAX_HOLD_SECONDS = 30;

    private final GateKind kind;
    private final String threadName;
    private final Pattern afterSql;
    private final AtomicReference<GateState> state = new AtomicReference<>(GateState.ARMED);
    private final AtomicBoolean triggered = new AtomicBoolean(false);
    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch decided = new CountDownLatch(1);

    private CommitGate(GateKind kind, String threadName, Pattern afterSql) {
      this.kind = kind;
      this.threadName = threadName;
      this.afterSql = afterSql;
    }

    public GateKind kind() {
      return kind;
    }

    boolean matchesThread() {
      return Thread.currentThread().getName().equals(threadName);
    }

    boolean matchesSql(String sql) {
      return afterSql.matcher(normalize(sql)).find();
    }

    public GateState state() {
      return state.get();
    }

    /**
     * gated thread의 commit 진입 시 호출 (ObservingDataSource)
     *
     * @param armed 이 커넥션이 현재 트랜잭션에서 대상 SQL을 실행했는지
     * @throws HarnessException commit을 거부해야 하는 경우 (abort·timeout·interrupt 이후 또는 pause 중 해당 상태로 결정된 경우)
     */
    void beforeCommit(boolean armed) {
      rejectIfTerminal();
      if (kind == GateKind.BEFORE_COMMIT && armed && triggered.compareAndSet(false, true)) {
        paused.countDown();
        awaitDecision();
        rejectIfTerminal();
      }
      // RELEASED 또는 (아직 대상 SQL 전의) ARMED 상태: commit 허용
    }

    /**
     * gated thread의 SQL 실행 시작 직전 호출 (ObservingDataSource)
     * - StatementGate는 abort·timeout·interrupt 이후 해당 thread의 모든 실행을 거부한다
     * - CommitGate(BEFORE_COMMIT)는 기존(R4) 규칙대로 실행을 막지 않는다
     */
    void beforeStatement() {
      if (kind == GateKind.AFTER_STATEMENT) {
        rejectIfTerminal();
      }
    }

    /**
     * gated thread의 SQL 실행이 정상 반환된 직후 호출 (ObservingDataSource, EXEC_END 기록 이후)
     * - StatementGate 대상 SQL의 첫 정상 반환이면 pause 후 RELEASED일 때만 진행한다
     */
    void afterStatement(String sql) {
      if (kind != GateKind.AFTER_STATEMENT) {
        return;
      }
      rejectIfTerminal();
      if (matchesSql(sql) && triggered.compareAndSet(false, true)) {
        paused.countDown();
        awaitDecision();
        rejectIfTerminal();
      }
    }

    private String label() {
      return kind == GateKind.BEFORE_COMMIT ? "commit gate" : "statement gate";
    }

    // BEFORE_COMMIT은 R4 메시지를 그대로 유지한다
    private String notProceeding() {
      return kind == GateKind.BEFORE_COMMIT ? "commit not performed" : "statement result not handed back";
    }

    private void awaitDecision() {
      boolean signalled;
      try {
        signalled = decided.await(MAX_HOLD_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        terminate(GateState.INTERRUPTED);
        throw HarnessException.harness(label() + " interrupted; " + notProceeding(), e);
      }
      if (!signalled) {
        terminate(GateState.TIMED_OUT);
        throw HarnessException.harness(label() + " timed out after " + MAX_HOLD_SECONDS + "s; " + notProceeding());
      }
    }

    private void rejectIfTerminal() {
      GateState current = state.get();
      if (current == GateState.ABORTED || current == GateState.TIMED_OUT || current == GateState.INTERRUPTED) {
        throw HarnessException.harness(label() + " " + current + "; " + notProceeding() + " (thread=" + threadName + ")");
      }
    }

    private void terminate(GateState terminal) {
      state.compareAndSet(GateState.ARMED, terminal);
      decided.countDown();
    }

    /**
     * 대상 worker가 commit 직전에 멈출 때까지 대기. 시간 안에 도달하지 않으면 HarnessException
     */
    public void requirePaused(Duration timeout) {
      try {
        if (!paused.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
          throw HarnessException.harness("worker " + threadName + " did not reach " + label() + " within " + timeout);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw HarnessException.harness("interrupted while waiting for " + label(), e);
      }
    }

    /**
     * 정상 해제: ARMED 상태일 때만 RELEASED로 전환하고 대기 중인 commit을 진행시킨다
     */
    public void release() {
      if (!state.compareAndSet(GateState.ARMED, GateState.RELEASED)) {
        throw HarnessException.harness("commit gate release rejected; state=" + state.get());
      }
      decided.countDown();
    }

    /**
     * 비정상 해제: 아직 RELEASED가 아니면 ABORTED로 전환한다. 이후 해당 thread의 commit은 모두 거부된다
     * - 이미 RELEASED이면 아무 일도 하지 않는다 (정상 흐름 finally에서 안전하게 호출 가능)
     */
    public void abort() {
      terminate(GateState.ABORTED);
    }

    @Override
    public String toString() {
      return "CommitGate{kind=" + kind + ", thread=" + threadName + ", state=" + state.get()
        + ", triggered=" + triggered.get() + "}";
    }
  }
}
