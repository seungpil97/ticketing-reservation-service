package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.application.OutboxFailureClassifier.Failure;
import com.pil97.ticketing.outbox.application.OutboxFailureRecorder.Result;
import com.pil97.ticketing.outbox.domain.OutboxErrorCode;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.LockWaitObserver;
import com.pil97.ticketing.support.outbox.MutableClock;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.RetryRow;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TASK-033-1 (#115) 실패 기록 상태·회차 보호 통합 테스트 (격리 MariaDB 컨테이너)
 *
 * <p>계약 (AC-8·AC-9·AC-10a):
 * <ul>
 *   <li>기록기는 행을 잠근 뒤 PENDING이고 state_version이 호출자가 준 값과 같을 때만 반영한다 (APPLIED, 버전 +1).
 *       그 밖(PROCESSED·FAILED·버전 불일치·행 없음)은 아무것도 바꾸지 않고 IGNORED_STALE</li>
 *   <li>재처리로 새 회차가 된 행에 이전 회차의 실패 기록이 늦게 도착해도 새 회차 값을 덮지 않는다</li>
 *   <li>기록기는 판단 전에 행 잠금을 기다리고, 잠금 해제 뒤 최신 상태·버전 기준으로 판단한다</li>
 * </ul>
 * 순서 고정 사례는 기록기를 직접 호출한다. 실제 잠금 사례는 대기자(기록기를 실행하는 worker의 pool connection)·
 * 차단자(테스트 JDBC connection)·대상 행을 INNODB 잠금 표로 연결해 관찰한다.
 * 관찰 전제·권한·정리 실패와 승인되지 않은 worker 예외는 HarnessException이다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.retry.max-attempts=5",
  "outbox.relay.retry.initial-backoff=30s",
  "outbox.relay.retry.max-backoff=10m"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class,
  MutableClock.Config.class})
@DirtiesContext
class OutboxFailureRecordGuardIntegrationTest {

  private static final Failure FAILURE = new Failure(OutboxErrorCode.UNKNOWN, "OTHER", null, null);
  private static final String STUB_PREFIX = "TASK-033-1 STUB: OutboxFailureRecorder.record";
  private static final Pattern RECORDER_LOCK =
    Pattern.compile("from\\s+outbox_event\\b.*\\bfor\\s+update", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
  private static final String WORKER = "ac10a-recorder-worker";

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxFailureRecorder recorder;
  @Autowired private MutableClock clock;
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    outbox.assertIsolated(DB);
    SqlObserver.clear();
    clock.set(MutableClock.START);
  }

  @AfterEach
  void tearDown() {
    outbox.cleanup();
    fixture.cleanup();
    SqlObserver.clear();
  }

  // ===================== AC-8 상태 보호 =====================

  @Test
  @DisplayName("AC-8 PENDING 행은 반영(APPLIED·실패 1·버전 +1), PROCESSED·FAILED 행과 없는 행은 IGNORED_STALE·불변")
  void ac8_onlyPendingRowWithSameVersionIsRecorded() {
    // ① PENDING: 반드시 반영 (no-op 우연 통과 방지)
    Seeded pending = outbox.seedPending();
    LocalDateTime failedAt = MutableClock.START;
    assertThat(recorder.record(pending.outboxId(), 0L, failedAt, FAILURE)).isEqualTo(Result.APPLIED);
    RetryRow applied = outbox.retryRow(pending.outboxId());
    assertThat(applied.status()).isEqualTo("PENDING");
    assertThat(applied.failedAttempts()).isEqualTo(1);
    assertThat(applied.stateVersion()).isEqualTo(1L);
    assertThat(applied.lastFailedAt()).isEqualTo(failedAt);
    assertThat(applied.nextAttemptAt()).isEqualTo(failedAt.plusSeconds(30));
    assertThat(applied.lastErrorCode()).isEqualTo("UNKNOWN");
    assertThat(applied.lastErrorType()).isEqualTo("OTHER");

    // ② PROCESSED·FAILED: 불변
    Seeded processed = outbox.seedPending();
    LocalDateTime processedAt = MutableClock.START.minusMinutes(1);
    outbox.markProcessed(processed.outboxId(), processedAt, 3L);
    RetryRow processedBefore = outbox.retryRow(processed.outboxId());
    assertThat(recorder.record(processed.outboxId(), 3L, failedAt, FAILURE)).isEqualTo(Result.IGNORED_STALE);
    assertThat(outbox.retryRow(processed.outboxId())).as("PROCESSED 행 불변").isEqualTo(processedBefore);

    Seeded failed = outbox.seedPending();
    outbox.setRetryState(failed.outboxId(), "FAILED", 5, null, 5L, "DUPLICATE_KEY", MutableClock.START.minusMinutes(2));
    RetryRow failedBefore = outbox.retryRow(failed.outboxId());
    assertThat(recorder.record(failed.outboxId(), 5L, failedAt, FAILURE)).isEqualTo(Result.IGNORED_STALE);
    assertThat(outbox.retryRow(failed.outboxId())).as("FAILED 행 불변").isEqualTo(failedBefore);

    // ③ 없는 행: 예외 없이 IGNORED_STALE
    assertThatCode(() -> assertThat(recorder.record(Long.MAX_VALUE, 0L, failedAt, FAILURE))
      .isEqualTo(Result.IGNORED_STALE)).doesNotThrowAnyException();
  }

  // ===================== AC-9 회차 보호 =====================

  @Test
  @DisplayName("AC-9 같은 버전 재기록·이전 회차 기록은 무시되고, 재처리 후 새 회차 값은 이전 회차 기록에 덮이지 않는다")
  void ac9_staleRecordFromPreviousGenerationIgnored() throws SQLException {
    Seeded s = outbox.seedPending();
    LocalDateTime t = MutableClock.START;

    assertThat(recorder.record(s.outboxId(), 0L, t, FAILURE)).as("v0 기록").isEqualTo(Result.APPLIED);
    assertThat(recorder.record(s.outboxId(), 0L, t, FAILURE)).as("같은 v0 재기록").isEqualTo(Result.IGNORED_STALE);
    assertThat(outbox.retryRow(s.outboxId()).failedAttempts()).isEqualTo(1);

    for (long version = 1; version <= 4; version++) {
      assertThat(recorder.record(s.outboxId(), version, t, FAILURE)).as("v%d 기록", version).isEqualTo(Result.APPLIED);
    }
    RetryRow exhausted = outbox.retryRow(s.outboxId());
    assertThat(exhausted.status()).as("기록된 실패 5회").isEqualTo("FAILED");
    assertThat(exhausted.stateVersion()).isEqualTo(5L);

    assertThat(callRequeue("[" + s.outboxId() + "]")).as("재처리 반영 건수").isEqualTo(1);
    RetryRow requeued = outbox.retryRow(s.outboxId());
    assertThat(requeued.status()).isEqualTo("PENDING");
    assertThat(requeued.failedAttempts()).isZero();
    assertThat(requeued.nextAttemptAt()).isNull();
    assertThat(requeued.stateVersion()).isEqualTo(6L);

    assertThat(recorder.record(s.outboxId(), 4L, t.plusMinutes(1), FAILURE)).as("이전 회차 v4 기록")
      .isEqualTo(Result.IGNORED_STALE);
    assertThat(outbox.retryRow(s.outboxId())).as("새 회차 값 유지").isEqualTo(requeued);

    assertThat(recorder.record(s.outboxId(), 6L, t.plusMinutes(1), FAILURE)).as("현재 회차 v6 기록")
      .isEqualTo(Result.APPLIED);
    RetryRow current = outbox.retryRow(s.outboxId());
    assertThat(current.failedAttempts()).isEqualTo(1);
    assertThat(current.stateVersion()).isEqualTo(7L);
  }

  private static int callRequeue(String ids) throws SQLException {
    try (Connection connection = directConnection();
         CallableStatement call = connection.prepareCall("{call outbox_requeue_failed(?, ?)}")) {
      call.setString(1, ids);
      call.registerOutParameter(2, Types.INTEGER);
      call.execute();
      return call.getInt(2);
    }
  }

  // ===================== AC-10a 실제 잠금 경합 =====================

  @Test
  @Timeout(60)
  @DisplayName("AC-10a 기록기는 차단자의 잠금을 기다린 뒤, 차단자가 커밋한 새 버전을 보고 IGNORED_STALE (차단자 값 그대로)")
  void ac10a_recorderWaitsThenIgnoresCommittedNewerVersion() throws Exception {
    Seeded s = outbox.seedPending();
    LocalDateTime blockerNext = MutableClock.START.plusHours(1);

    RecordOutcome outcome = recordWhileBlocked(s, holder -> {
      try (PreparedStatement ps = holder.prepareStatement(
        "update outbox_event set state_version = state_version + 1, failed_attempts = 2, next_attempt_at = ? where id = ?")) {
        ps.setTimestamp(1, Timestamp.valueOf(blockerNext));
        ps.setLong(2, s.outboxId());
        ps.executeUpdate();
      }
      holder.commit();
    });

    assertThat(outcome.result()).isEqualTo(Result.IGNORED_STALE);
    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.stateVersion()).as("차단자 커밋 버전").isEqualTo(1L);
    assertThat(row.failedAttempts()).as("차단자 커밋 값").isEqualTo(2);
    assertThat(row.nextAttemptAt()).as("차단자 커밋 값").isEqualTo(blockerNext);
    assertThat(row.lastFailedAt()).as("기록기 미반영").isNull();
    assertThat(row.lastErrorCode()).as("기록기 미반영").isNull();
  }

  @Test
  @Timeout(60)
  @DisplayName("AC-10a 기록기는 차단자의 잠금을 기다린 뒤, 차단자가 rollback하면 최신(원래) 버전 기준으로 APPLIED")
  void ac10a_recorderWaitsThenAppliesAfterRollback() throws Exception {
    Seeded s = outbox.seedPending();

    RecordOutcome outcome = recordWhileBlocked(s, Connection::rollback);

    assertThat(outcome.result()).isEqualTo(Result.APPLIED);
    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.stateVersion()).isEqualTo(1L);
    assertThat(row.failedAttempts()).isEqualTo(1);
  }

  private record RecordOutcome(Result result, LockWaitObserver.Wait observedWait) {
  }

  @FunctionalInterface
  private interface Release {
    void apply(Connection holder) throws SQLException;
  }

  /**
   * 차단자가 행을 잠근 상태에서 worker가 기록기를 호출하고, 대기 관찰 후 차단자가 release를 수행한다
   */
  private RecordOutcome recordWhileBlocked(Seeded s, Release release) throws Exception {
    String primaryKey = String.valueOf(s.outboxId());
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, WORKER));
    long holderThreadId = -1;
    long waiterThreadId = -1;
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = directConnection()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      Future<Result> worker = null;
      try {
        holderThreadId = LockWaitObserver.connectionId(holder);
        try (PreparedStatement ps = holder.prepareStatement("select id from outbox_event where id = ? for update")) {
          ps.setLong(1, s.outboxId());
          ps.executeQuery().close();
        }

        worker = executor.submit(() -> recorder.record(s.outboxId(), 0L, MutableClock.START, FAILURE));
        Future<Result> running = worker;
        Optional<LockWaitObserver.Wait> wait = LockWaitObserver.awaitResolvingWaiter(observer,
          LockWaitObserver.inFlightConnection(WORKER, RECORDER_LOCK), holderThreadId, "outbox_event", primaryKey,
          running::isDone, WAIT_TIMEOUT);
        if (wait.isEmpty()) {
          throw LockWaitObserver.failForEarlyFinish(worker, STUB_PREFIX, "기록기는 판단 전에 행 잠금을 기다려야 한다");
        }
        waiterThreadId = wait.get().waiterThreadId();

        release.apply(holder);
        Result result = worker.get(30, TimeUnit.SECONDS);
        return new RecordOutcome(result, wait.get());
      } finally {
        holder.rollback();
        if (worker != null) {
          worker.cancel(true);
        }
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          throw HarnessException.harness("worker did not terminate");
        }
        long open = LockWaitObserver.awaitNoOpenTransactions(observer,
          waiterThreadId < 0 ? List.of(holderThreadId) : List.of(holderThreadId, waiterThreadId), Duration.ofSeconds(5));
        if (open > 0) {
          throw HarnessException.harness("cleanup left open transactions: " + open + LockWaitObserver.diagnostics(observer));
        }
      }
    }
  }

  private static Connection directConnection() throws SQLException {
    return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
  }
}
