package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.Candidate;
import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.ProbeReport;
import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.Verdict;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PrimaryKeyLockProbe 실제 격리 DB 검증 (Spring 없이 전용 테이블 사용)
 * - 강제 probe 경로: lock_data 값과 무관하게 같은 probe 구현을 호출해 commit·rollback 후 결과를 확인한다
 * - 다른 기본키 probe 거부, probe 도중 관찰 실패·연결 생성 실패의 정리
 */
@Testcontainers
class PrimaryKeyLockProbeIntegrationTest {

  private static final String TABLE = "lock_probe_target";
  private static final Duration WAIT = Duration.ofSeconds(10);

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @BeforeAll
  static void schema() throws SQLException {
    try (Connection root = LockWaitObserver.rootConnection(DB); Statement st = root.createStatement()) {
      st.execute("create table " + TABLE + " (id bigint not null primary key, status varchar(20) not null, "
        + "state_version bigint not null) engine = InnoDB");
    }
  }

  private static Connection direct() throws SQLException {
    return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
  }

  private static final AtomicLong IDS = new AtomicLong(1000);

  private static long failedRow() throws SQLException {
    long id = IDS.incrementAndGet();
    try (Connection c = direct(); PreparedStatement ps = c.prepareStatement(
      "insert into " + TABLE + " (id, status, state_version) values (?, 'FAILED', 5)")) {
      ps.setLong(1, id);
      ps.executeUpdate();
    }
    return id;
  }

  private static final String TRANSITION = "update " + TABLE
    + " set status = 'PENDING', state_version = state_version + 1 where id = ? and status = 'FAILED'";

  /** holder 미커밋 전이 + waiter 같은 전이 대기 상태를 만들고 시나리오를 실행한다 */
  private interface Scenario {
    void run(Connection observer, Connection holder, long holderCid, long waiterCid, Candidate candidate,
             Future<Integer> waiter) throws Exception;
  }

  private static void blocked(long target, Long decoy, Scenario scenario) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicLong waiterCid = new AtomicLong(-1);
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = direct()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      long holderCid = LockWaitObserver.connectionId(holder);
      try (PreparedStatement ps = holder.prepareStatement(TRANSITION)) {
        ps.setLong(1, target);
        assertThat(ps.executeUpdate()).isEqualTo(1);
      }
      if (decoy != null) {
        try (PreparedStatement ps = holder.prepareStatement("select id from " + TABLE + " where id = ? for update")) {
          ps.setLong(1, decoy);
          ps.executeQuery().close();
        }
      }
      Future<Integer> waiter = executor.submit(() -> {
        try (Connection w = direct(); PreparedStatement ps = w.prepareStatement(TRANSITION)) {
          waiterCid.set(LockWaitObserver.connectionId(w));
          ps.setLong(1, target);
          return ps.executeUpdate();
        }
      });
      try {
        long until = System.nanoTime() + WAIT.toNanos();
        while (waiterCid.get() < 0 && System.nanoTime() < until) {
          Thread.sleep(20);
        }
        Candidate candidate = null;
        while (candidate == null && System.nanoTime() < until) {
          Thread.sleep(LockWaitObserver.OBSERVE_INTERVAL.toMillis());
          candidate = PrimaryKeyLockProbe.candidates(observer, waiterCid.get(), holderCid).stream()
            .filter(c -> c.lockTable() != null && c.lockTable().endsWith("`" + TABLE + "`") && "PRIMARY".equals(c.lockIndex()))
            .findFirst().orElse(null);
        }
        assertThat(candidate).as("waiter가 holder 때문에 대상 PRIMARY 잠금을 기다린다").isNotNull();
        scenario.run(observer, holder, holderCid, waiterCid.get(), candidate, waiter);
      } finally {
        holder.rollback();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  private static long state(long id) throws SQLException {
    try (Connection c = direct(); PreparedStatement ps = c.prepareStatement(
      "select state_version from " + TABLE + " where id = ? and status = 'PENDING'")) {
      ps.setLong(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  private static boolean probeWorkerAlive() {
    return Thread.getAllStackTraces().keySet().stream()
      .anyMatch(t -> t.isAlive() && t.getName().startsWith(PrimaryKeyLockProbe.WORKER_THREAD_PREFIX));
  }

  @Test
  @Timeout(60)
  @DisplayName("강제 probe: 대상 기본키 probe가 같은 위치로 인정되고, 정리 후 holder commit이면 waiter 반환 0·버전 1회 증가")
  void forcedProbeThenCommit() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      ProbeReport report = PrimaryKeyLockProbe.probe(observer, waiterCid, holderCid, TABLE, target, target, candidate,
        PrimaryKeyLockProbeIntegrationTest::direct);
      System.out.println("PK-PROBE-OBSERVE commit verdict=" + report.verdict() + " end=" + report.probeEnd()
        + " cleaned=" + report.probeCleanedUp() + " waiter_unchanged=" + report.waiterUnchanged()
        + " lock_data_null_initially=" + (candidate.lockData() == null) + " notes=" + report.notes());
      assertThat(report.verdict()).isEqualTo(Verdict.ACCEPT);
      assertThat(report.probeEnd()).isEqualTo("HY000/1205");
      assertThat(report.probeCleanedUp()).isTrue();
      assertThat(report.waiterUnchanged()).isTrue();
      assertThat(probeWorkerAlive()).isFalse();
      holder.commit();
      assertThat(waiter.get(30, TimeUnit.SECONDS)).as("대기 후 반영 건수").isZero();
      assertThat(state(target)).as("버전은 holder 전이 1회분").isEqualTo(6L);
    });
  }

  @Test
  @Timeout(60)
  @DisplayName("강제 probe: 인정·정리 후 holder rollback이면 waiter가 최신(FAILED) 기준으로 전이해 반환 1")
  void forcedProbeThenRollback() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      ProbeReport report = PrimaryKeyLockProbe.probe(observer, waiterCid, holderCid, TABLE, target, target, candidate,
        PrimaryKeyLockProbeIntegrationTest::direct);
      System.out.println("PK-PROBE-OBSERVE rollback verdict=" + report.verdict() + " end=" + report.probeEnd()
        + " cleaned=" + report.probeCleanedUp() + " waiter_unchanged=" + report.waiterUnchanged()
        + " lock_data_null_initially=" + (candidate.lockData() == null));
      assertThat(report.verdict()).isEqualTo(Verdict.ACCEPT);
      assertThat(report.probeCleanedUp()).isTrue();
      holder.rollback();
      assertThat(waiter.get(30, TimeUnit.SECONDS)).isEqualTo(1);
      assertThat(state(target)).isEqualTo(6L);
    });
  }

  @Test
  @Timeout(60)
  @DisplayName("다른 기본키 probe: holder가 잠근 다른 행을 기다리는 실제 probe는 원래 대상의 대기로 인정하지 않음")
  void otherPrimaryKeyProbeRejected() throws Exception {
    long target = failedRow();
    long decoy = failedRow();
    blocked(target, decoy, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      ProbeReport report = PrimaryKeyLockProbe.probe(observer, waiterCid, holderCid, TABLE, target, decoy, candidate,
        PrimaryKeyLockProbeIntegrationTest::direct);
      System.out.println("PK-PROBE-OBSERVE negative verdict=" + report.verdict() + " end=" + report.probeEnd()
        + " cleaned=" + report.probeCleanedUp() + " waiter_unchanged=" + report.waiterUnchanged()
        + " notes=" + report.notes());
      assertThat(report.verdict()).isEqualTo(Verdict.REJECT);
      assertThat(report.probeEnd()).as("probe는 실제로 holder를 기다리다 timeout").isEqualTo("HY000/1205");
      assertThat(report.probeCleanedUp()).isTrue();
      assertThat(report.waiterUnchanged()).isTrue();
      assertThat(waiter.isDone()).as("waiter는 계속 대기").isFalse();
    });
  }

  /** 모든 호출(Connection·Statement·ResultSet)의 동시 진행 수를 센다 */
  private static final class Tracking {
    final AtomicInteger inFlight = new AtomicInteger();
    final AtomicInteger maxInFlight = new AtomicInteger();
    final AtomicReference<Connection> raw = new AtomicReference<>();
    final AtomicLong cid = new AtomicLong(-1);

    Connection open() throws SQLException {
      Connection c = direct();
      raw.set(c);
      cid.set(LockWaitObserver.connectionId(c));
      return wrap(c, Connection.class);
    }

    @SuppressWarnings("unchecked")
    <T> T wrap(Object target, Class<T> type) {
      InvocationHandler h = (proxy, method, args) -> {
        int now = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(now, Math::max);
        try {
          Object result = method.invoke(target, args);
          if (result instanceof PreparedStatement ps) {
            return wrap(ps, PreparedStatement.class);
          }
          if (result instanceof Statement st) {
            return wrap(st, Statement.class);
          }
          if (result instanceof ResultSet rs) {
            return wrap(rs, ResultSet.class);
          }
          return result;
        } catch (InvocationTargetException e) {
          throw e.getCause();
        } finally {
          inFlight.decrementAndGet();
        }
      };
      return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }
  }

  @Test
  @Timeout(60)
  @DisplayName("probe 관찰 중 observer 오류: 원래 오류를 보존하고, probe 연결을 다른 스레드가 동시에 쓰지 않으며, worker·연결·트랜잭션이 실제로 정리된다")
  void observerFailureDuringProbeCleansUp() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      Tracking tracking = new Tracking();
      SQLException injected = new SQLException("injected observer failure", "08S01", 0);
      Connection failingObserver = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
          if (method.getName().equals("createStatement") || method.getName().equals("prepareStatement")) {
            throw injected;
          }
          try {
            return method.invoke(observer, args);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        });
      assertThatThrownBy(() -> PrimaryKeyLockProbe.probe(failingObserver, waiterCid, holderCid, TABLE, target, target,
        candidate, tracking::open))
        .satisfies(e -> {
          Throwable c = e;
          boolean found = false;
          while (c != null) {
            found |= c == injected;
            c = c.getCause();
          }
          assertThat(found).as("원래 관찰 오류가 cause로 보존").isTrue();
        });
      boolean alive = probeWorkerAlive();
      boolean closed = tracking.raw.get() != null && tracking.raw.get().isClosed();
      long open = LockWaitObserver.awaitNoOpenTransactions(observer, List.of(tracking.cid.get()), Duration.ofSeconds(5));
      System.out.println("PK-PROBE-CLEANUP observer_failure max_in_flight_on_probe_connection=" + tracking.maxInFlight.get()
        + " worker_alive_after_return=" + alive + " probe_connection_closed=" + closed + " probe_open_trx=" + open);
      assertThat(tracking.maxInFlight.get()).as("probe 연결 동시 사용 없음").isEqualTo(1);
      assertThat(alive).as("반환 시점에 worker 실제 종료").isFalse();
      assertThat(closed).as("probe 연결 종료").isTrue();
      assertThat(open).as("probe 트랜잭션 소멸").isZero();
      assertThat(waiter.isDone()).isFalse();
    });
  }

  @Test
  @Timeout(60)
  @DisplayName("probe 연결 생성 실패: 원래 오류를 보존하고 worker를 남기지 않는다")
  void connectionFactoryFailureCleansUp() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      SQLException refused = new SQLException("probe connect refused", "08001", 0);
      assertThatThrownBy(() -> PrimaryKeyLockProbe.probe(observer, waiterCid, holderCid, TABLE, target, target,
        candidate, () -> {
          throw refused;
        }))
        .satisfies(e -> {
          Throwable c = e;
          boolean found = false;
          while (c != null) {
            found |= c == refused;
            c = c.getCause();
          }
          assertThat(found).as("연결 생성 오류가 cause로 보존").isTrue();
        });
      boolean alive = probeWorkerAlive();
      System.out.println("PK-PROBE-CLEANUP connection_failure worker_alive_after_return=" + alive);
      assertThat(alive).isFalse();
    });
  }

  /** 예외와 그 cause·suppressed 전체 */
  private static List<Throwable> chain(Throwable root) {
    List<Throwable> out = new java.util.ArrayList<>();
    java.util.Deque<Throwable> todo = new java.util.ArrayDeque<>(List.of(root));
    while (!todo.isEmpty()) {
      Throwable t = todo.pop();
      if (out.contains(t)) {
        continue;
      }
      out.add(t);
      if (t.getCause() != null) {
        todo.push(t.getCause());
      }
      for (Throwable s : t.getSuppressed()) {
        todo.push(s);
      }
    }
    return out;
  }

  @Test
  @Timeout(60)
  @DisplayName("관찰 기한: probe 증거가 충분해도 await 전체 기한이 지난 뒤에는 성공을 반환하지 않고, 시작한 probe는 정리한 뒤 하네스 오류")
  void probeFinishingAfterDeadlineIsNotSuccess() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      Tracking tracking = new Tracking();
      Duration timeout = Duration.ofMillis(400);
      long start = System.nanoTime();
      AtomicReference<Object> returned = new AtomicReference<>();
      Throwable thrown = null;
      try {
        // probe 경로를 강제로 지나게 한다 (lock_data 값과 무관). probe 자체는 약 2.5초가 걸린다
        returned.set(PrimaryKeyLockProbe.await(observer, waiterCid, holderCid, TABLE, target, tracking::open,
          waiter::isDone, timeout, c -> true));
      } catch (Throwable t) {
        thrown = t;
      }
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;
      boolean alive = probeWorkerAlive();
      boolean closed = tracking.raw.get() != null && tracking.raw.get().isClosed();
      long open = tracking.cid.get() < 0 ? 0
        : LockWaitObserver.awaitNoOpenTransactions(observer, List.of(tracking.cid.get()), Duration.ofSeconds(5));
      System.out.println("PK-PROBE-DEADLINE timeout_ms=" + timeout.toMillis() + " elapsed_ms=" + elapsedMs
        + " returned=" + returned.get() + " thrown=" + (thrown == null ? null : thrown.getClass().getSimpleName())
        + " probe_started=" + (tracking.cid.get() >= 0) + " worker_alive=" + alive + " probe_connection_closed=" + closed
        + " probe_open_trx=" + open);
      assertThat(tracking.cid.get()).as("probe는 실제로 실행됐다").isGreaterThanOrEqualTo(0);
      assertThat(returned.get()).as("기한 이후 성공 반환 금지").isNull();
      assertThat(thrown).isInstanceOf(com.pil97.ticketing.support.consistency.HarnessException.class);
      assertThat(alive).isFalse();
      assertThat(closed).isTrue();
      assertThat(open).isZero();
      assertThat(waiter.isDone()).as("waiter는 계속 대기").isFalse();
    });
  }

  @Test
  @Timeout(60)
  @DisplayName("정리 실패: 실제 연결이 닫혀 isClosed()가 true여도 close 예외가 나면 성공이 아니며, 원래 HY000/1205와 정리 오류를 모두 보존")
  void closeFailureIsNotSuccess() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      AtomicReference<Connection> raw = new AtomicReference<>();
      AtomicLong cid = new AtomicLong(-1);
      SQLException closeFailure = new SQLException("injected close failure", "08003", 0);
      PrimaryKeyLockProbe.ConnectionFactory factory = () -> {
        Connection c = direct();
        raw.set(c);
        cid.set(LockWaitObserver.connectionId(c));
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
          (proxy, method, args) -> {
            try {
              Object result = method.invoke(c, args);
              if (method.getName().equals("close")) {
                throw closeFailure;
              }
              return result;
            } catch (InvocationTargetException e) {
              throw e.getCause();
            }
          });
      };
      AtomicReference<ProbeReport> returned = new AtomicReference<>();
      Throwable thrown = null;
      try {
        returned.set(PrimaryKeyLockProbe.probe(observer, waiterCid, holderCid, TABLE, target, target, candidate, factory));
      } catch (Throwable t) {
        thrown = t;
      }
      List<Throwable> all = thrown == null ? List.of() : chain(thrown);
      boolean timeoutPreserved = all.stream().anyMatch(t -> t instanceof SQLException s
        && "HY000".equals(s.getSQLState()) && s.getErrorCode() == 1205);
      boolean closeFailurePreserved = all.contains(closeFailure);
      boolean alive = probeWorkerAlive();
      boolean closed = raw.get() != null && raw.get().isClosed();
      long open = cid.get() < 0 ? 0
        : LockWaitObserver.awaitNoOpenTransactions(observer, List.of(cid.get()), Duration.ofSeconds(5));
      System.out.println("PK-PROBE-CLOSE-FAILURE returned_verdict=" + (returned.get() == null ? null : returned.get().verdict())
        + " thrown=" + (thrown == null ? null : thrown.getClass().getSimpleName())
        + " timeout_1205_preserved=" + timeoutPreserved + " close_failure_preserved=" + closeFailurePreserved
        + " worker_alive=" + alive + " real_connection_closed=" + closed + " probe_open_trx=" + open);
      assertThat(returned.get()).as("정리 실패 시 성공(보고) 반환 금지").isNull();
      assertThat(thrown).isNotNull();
      assertThat(timeoutPreserved).as("원래 HY000/1205 보존").isTrue();
      assertThat(closeFailurePreserved).as("close 오류 보존").isTrue();
      assertThat(alive).isFalse();
      assertThat(closed).isTrue();
      assertThat(open).isZero();
      assertThat(waiter.isDone()).isFalse();
    });
  }

  /** 실제 연결을 닫은 뒤 close 예외를 던지는 probe 연결 (raw·cid 기록) */
  private static PrimaryKeyLockProbe.ConnectionFactory closeFailing(AtomicReference<Connection> raw, AtomicLong cid,
                                                                    SQLException closeFailure) {
    return () -> {
      Connection c = direct();
      raw.set(c);
      cid.set(LockWaitObserver.connectionId(c));
      return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
        (proxy, method, args) -> {
          try {
            Object result = method.invoke(c, args);
            if (method.getName().equals("close")) {
              throw closeFailure;
            }
            return result;
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        });
    };
  }

  @Test
  @Timeout(60)
  @DisplayName("observer 오류와 probe 연결 close 오류가 같은 실행에서 나면 둘 다 최종 예외(cause·suppressed)에 남고, worker·연결·트랜잭션은 실제로 정리된다")
  void observerFailureKeepsWorkerCleanupFailure() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      AtomicReference<Connection> raw = new AtomicReference<>();
      AtomicLong cid = new AtomicLong(-1);
      SQLException closeFailure = new SQLException("injected close failure", "08003", 0);
      SQLException injected = new SQLException("injected observer failure", "08S01", 0);
      Connection failingObserver = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
          if (method.getName().equals("createStatement") || method.getName().equals("prepareStatement")) {
            throw injected;
          }
          try {
            return method.invoke(observer, args);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        });
      Throwable thrown = null;
      try {
        PrimaryKeyLockProbe.probe(failingObserver, waiterCid, holderCid, TABLE, target, target, candidate,
          closeFailing(raw, cid, closeFailure));
      } catch (Throwable t) {
        thrown = t;
      }
      List<Throwable> all = thrown == null ? List.of() : chain(thrown);
      boolean observerFailurePreserved = all.contains(injected);
      long closeFailureCount = all.stream().filter(t -> t == closeFailure).count();
      boolean selfSuppressed = all.stream().anyMatch(t -> List.of(t.getSuppressed()).contains(t));
      boolean alive = probeWorkerAlive();
      boolean closed = raw.get() != null && raw.get().isClosed();
      long open = cid.get() < 0 ? 0
        : LockWaitObserver.awaitNoOpenTransactions(observer, List.of(cid.get()), Duration.ofSeconds(5));
      System.out.println("PK-PROBE-OBSERVER-CLOSE thrown=" + (thrown == null ? null : thrown.getClass().getSimpleName())
        + " observer_failure_preserved=" + observerFailurePreserved + " close_failure_count=" + closeFailureCount
        + " self_suppressed=" + selfSuppressed + " worker_alive=" + alive + " real_connection_closed=" + closed
        + " probe_open_trx=" + open);
      assertThat(thrown).as("observer 오류 시 성공 반환 금지").isNotNull();
      assertThat(observerFailurePreserved).as("observer 원래 오류 보존").isTrue();
      assertThat(closeFailureCount).as("worker close 오류 보존 (중복 없이 1회)").isEqualTo(1);
      assertThat(selfSuppressed).as("동일 예외 self-suppression 없음").isFalse();
      assertThat(alive).as("worker 실제 종료").isFalse();
      assertThat(closed).as("실제 probe 연결 종료").isTrue();
      assertThat(open).as("probe 트랜잭션 소멸").isZero();
      assertThat(waiter.isDone()).isFalse();
    });
  }

  @Test
  @Timeout(60)
  @DisplayName("관찰 기한: 후보 조회가 기한 전에 시작해 기한 뒤에 끝나면 lock_data가 대상과 일치해도 DIRECT 성공을 반환하지 않고 probe도 시작하지 않는다")
  void directMatchAfterDeadlineIsNotSuccess() throws Exception {
    long target = failedRow();
    blocked(target, null, (observer, holder, holderCid, waiterCid, candidate, waiter) -> {
      if (!String.valueOf(target).equals(candidate.lockData())) {
        throw com.pil97.ticketing.support.consistency.HarnessException.precondition(
          "direct match not observable in this run (lock_data null or other)");
      }
      Duration timeout = Duration.ofMillis(300);
      Duration delay = Duration.ofMillis(900);
      AtomicInteger delayedQueries = new AtomicInteger();
      // 후보 조회(innodb_lock_waits 조인)만 실제 실행 뒤 한 번 지연시켜, 조회 종료가 기한 뒤가 되게 한다
      Connection slowObserver = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
          Object result;
          try {
            result = method.invoke(observer, args);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
          if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql
              && sql.contains("innodb_lock_waits w")) {
            PreparedStatement ps = (PreparedStatement) result;
            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
              (p, m, a) -> {
                try {
                  Object r = m.invoke(ps, a);
                  if (m.getName().equals("executeQuery") && delayedQueries.getAndIncrement() == 0) {
                    Thread.sleep(delay.toMillis());
                  }
                  return r;
                } catch (InvocationTargetException e) {
                  throw e.getCause();
                }
              });
          }
          return result;
        });
      AtomicInteger probeOpens = new AtomicInteger();
      long start = System.nanoTime();
      AtomicReference<Object> returned = new AtomicReference<>();
      Throwable thrown = null;
      try {
        returned.set(PrimaryKeyLockProbe.await(slowObserver, waiterCid, holderCid, TABLE, target, () -> {
          probeOpens.incrementAndGet();
          return direct();
        }, waiter::isDone, timeout));
      } catch (Throwable t) {
        thrown = t;
      }
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;
      System.out.println("PK-PROBE-DIRECT-DEADLINE timeout_ms=" + timeout.toMillis() + " delay_ms=" + delay.toMillis()
        + " elapsed_ms=" + elapsedMs + " delayed_queries=" + delayedQueries.get() + " returned=" + returned.get()
        + " thrown=" + (thrown == null ? null : thrown.getClass().getSimpleName()) + " probe_opens=" + probeOpens.get()
        + " worker_alive=" + probeWorkerAlive());
      assertThat(delayedQueries.get()).as("지연된 후보 조회가 실제로 실행됐다").isGreaterThanOrEqualTo(1);
      assertThat(elapsedMs).as("후보 조회 종료가 기한 뒤").isGreaterThanOrEqualTo(timeout.toMillis());
      assertThat(returned.get()).as("기한 이후 DIRECT 성공 반환 금지").isNull();
      assertThat(thrown).isInstanceOf(com.pil97.ticketing.support.consistency.HarnessException.class);
      assertThat(probeOpens.get()).as("probe 불필요 시작 없음").isZero();
      assertThat(probeWorkerAlive()).isFalse();
      assertThat(waiter.isDone()).as("waiter는 계속 대기").isFalse();
    });
  }
}
