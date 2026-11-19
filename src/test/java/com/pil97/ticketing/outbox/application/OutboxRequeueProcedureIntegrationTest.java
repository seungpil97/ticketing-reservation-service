package com.pil97.ticketing.outbox.application;

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
import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-033-1 (#115) 수동 재처리 프로시저 outbox_requeue_failed 통합 테스트 (격리 MariaDB 컨테이너)
 *
 * <p>계약 (AC-13·AC-10b):
 * <ul>
 *   <li>입력: JSON 배열 1~100건, 각 원소는 양의 signed BIGINT 범위의 JSON 정수. JSON_TYPE만으로는 지수 표기(1e3)와
 *       BIGINT 초과 정수를 거르지 못하므로(M-2 관찰: 둘 다 INTEGER) 원래 lexeme 형식·길이·범위를 확인한 뒤에만 변환한다.
 *       중복(변환 전 텍스트·변환 후 ID)은 거부. 위반은 SQLSTATE 45000이며 어떤 행도 바꾸지 않는다</li>
 *   <li>반영: 요청 시점에 FAILED인 행만 PENDING·실패 0·next NULL·state_version +1로 전이, last_error_*·last_failed_at 유지</li>
 *   <li>반영 건수(OUT): FAILED 조건에 일치해 PENDING으로 전이된 행 수 (UPDATE 직후 값). 반복 요청은 0,
 *       그 사이 다시 FAILED가 된 행은 다시 전이된다</li>
 *   <li>원자성: 단일 UPDATE 문. 일부 행을 갱신한 뒤 잠금 timeout으로 실패하면 모든 대상 행이 그대로다</li>
 *   <li>프로시저는 내부에서 COMMIT하지 않는다 (호출자 autocommit 또는 호출자 트랜잭션)</li>
 *   <li>잠금 경합(AC-10b): 같은 FAILED 행을 다른 트랜잭션이 잠그고 바꾼 경우, 대기 뒤 최신 상태 기준으로 전이한다</li>
 * </ul>
 * 이 클래스의 행은 결제와 무관한 Outbox 행(OutboxFixture.insertBare)이다.
 */
@Testcontainers
@SpringBootTest(properties = "outbox.relay.batch-size=10")
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class,
  MutableClock.Config.class})
@DirtiesContext
class OutboxRequeueProcedureIntegrationTest {

  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
  private static final LocalDateTime FAILED_AT = MutableClock.START.minusMinutes(10);
  private static final AtomicLong MISSING_ID = new AtomicLong(9_000_000_000L);

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxRelayService relayService;
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

  private Long failedRow() {
    Long id = outbox.insertBare("PENDING", 0, null, 0L, null);
    outbox.setRetryState(id, "FAILED", 5, null, 5L, "DUPLICATE_KEY", FAILED_AT);
    return id;
  }

  private static String ids(List<Long> ids) {
    return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
  }

  // ===================== 정상 전이 =====================

  @Test
  @DisplayName("AC-13 FAILED 3건 재처리: 반환 3, 각 행 PENDING·실패 0·next NULL·버전 +1·오류 정보 유지, 이후 relay 처리")
  void ac13_requeuesFailedRows() throws SQLException {
    List<Seeded> seeded = List.of(outbox.seedPending(), outbox.seedPending(), outbox.seedPending());
    for (Seeded s : seeded) {
      outbox.setRetryState(s.outboxId(), "FAILED", 5, null, 5L, "DUPLICATE_KEY", FAILED_AT);
    }

    int requeued = callRequeue(ids(seeded.stream().map(Seeded::outboxId).toList()));

    assertThat(requeued).as("반영 건수").isEqualTo(3);
    for (Seeded s : seeded) {
      RetryRow row = outbox.retryRow(s.outboxId());
      assertThat(row.status()).isEqualTo("PENDING");
      assertThat(row.failedAttempts()).isZero();
      assertThat(row.nextAttemptAt()).isNull();
      assertThat(row.stateVersion()).isEqualTo(6L);
      assertThat(row.lastErrorCode()).as("오류 정보 유지").isEqualTo("DUPLICATE_KEY");
      assertThat(row.lastFailedAt()).as("마지막 실패 시각 유지").isEqualTo(FAILED_AT);
    }

    relayService.relay();
    for (Seeded s : seeded) {
      assertThat(outbox.snapshot(s).status()).isEqualTo("PROCESSED");
      assertThat(outbox.snapshot(s).notificationCount()).isEqualTo(1L);
    }
  }

  // ===================== 입력 검증 =====================

  static Stream<Arguments> rejectedInputs() {
    return Stream.of(
      Arguments.of("NULL", (Function<Long, String>) id -> null),
      Arguments.of("JSON 아님", (Function<Long, String>) id -> "not json"),
      Arguments.of("객체", (Function<Long, String>) id -> "{\"id\":" + id + "}"),
      Arguments.of("빈 배열", (Function<Long, String>) id -> "[]"),
      Arguments.of("소수", (Function<Long, String>) id -> "[" + id + ".0]"),
      Arguments.of("지수 표기 (JSON_TYPE INTEGER)", (Function<Long, String>) id -> "[" + id + ", 1e3]"),
      Arguments.of("문자열", (Function<Long, String>) id -> "[\"" + id + "\"]"),
      Arguments.of("boolean", (Function<Long, String>) id -> "[" + id + ", true]"),
      Arguments.of("null 원소", (Function<Long, String>) id -> "[" + id + ", null]"),
      Arguments.of("0", (Function<Long, String>) id -> "[" + id + ", 0]"),
      Arguments.of("음수", (Function<Long, String>) id -> "[" + id + ", -1]"),
      Arguments.of("BIGINT 최대+1 (JSON_TYPE INTEGER)", (Function<Long, String>) id -> "[" + id + ", 9223372036854775808]"),
      Arguments.of("앞자리 0 (JSON 아님)", (Function<Long, String>) id -> "[0" + id + "]"),
      Arguments.of("중복", (Function<Long, String>) id -> "[" + id + ", " + id + "]"),
      Arguments.of("중첩 배열", (Function<Long, String>) id -> "[[" + id + "]]"),
      Arguments.of("101건", (Function<Long, String>) id ->
        ids(Stream.concat(Stream.of(id), Stream.iterate(MISSING_ID.get(), n -> n + 1).limit(100)).toList())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedInputs")
  @DisplayName("AC-13 입력 위반은 SQLSTATE 45000이고, 유효한 FAILED ID가 함께 있어도 어떤 행도 바꾸지 않는다")
  void ac13_rejectsInvalidInput(String label, Function<Long, String> input) {
    Long id = failedRow();
    RetryRow before = outbox.retryRow(id);

    assertThatThrownBy(() -> callRequeue(input.apply(id)))
      .as(label)
      .isInstanceOf(SQLException.class)
      .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as("%s SQLSTATE", label).isEqualTo("45000"));
    assertThat(outbox.retryRow(id)).as("%s 대상 행 불변", label).isEqualTo(before);
  }

  // ===================== 경계·섞인 입력·반복 =====================

  @Test
  @DisplayName("AC-13 경계: 서로 다른 FAILED 100건은 100, BIGINT 최대(없는 ID)는 0이며 오류 없음")
  void ac13_boundaries() throws SQLException {
    List<Long> hundred = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      hundred.add(failedRow());
    }

    assertThat(callRequeue(ids(hundred))).as("100건").isEqualTo(100);
    assertThat(hundred).allSatisfy(id -> assertThat(outbox.retryRow(id).status()).isEqualTo("PENDING"));
    assertThat(callRequeue("[9223372036854775807]")).as("BIGINT 최대 (없는 ID)").isZero();
  }

  @Test
  @DisplayName("AC-13 섞인 입력: FAILED만 전이, PENDING·PROCESSED·없는 ID는 불변, 반환은 FAILED 건수")
  void ac13_mixedInputOnlyFailedTransitions() throws SQLException {
    Long failed = failedRow();
    Long pending = outbox.insertBare("PENDING", 2, MutableClock.START.plusMinutes(1), 2L, null);
    Long processed = outbox.insertBare("PENDING", 0, null, 0L, null);
    outbox.markProcessed(processed, FAILED_AT, 1L);
    RetryRow pendingBefore = outbox.retryRow(pending);
    RetryRow processedBefore = outbox.retryRow(processed);

    int requeued = callRequeue(ids(List.of(failed, pending, processed, MISSING_ID.incrementAndGet())));

    assertThat(requeued).isEqualTo(1);
    assertThat(outbox.retryRow(failed).status()).isEqualTo("PENDING");
    assertThat(outbox.retryRow(pending)).isEqualTo(pendingBefore);
    assertThat(outbox.retryRow(processed)).isEqualTo(processedBefore);
  }

  @Test
  @DisplayName("AC-13 반복 요청은 0, 그 사이 다시 FAILED가 된 행은 같은 요청으로 다시 전이")
  void ac13_repeatScope() throws SQLException {
    Long id = failedRow();
    String request = ids(List.of(id));

    assertThat(callRequeue(request)).as("첫 요청").isEqualTo(1);
    assertThat(callRequeue(request)).as("반복 요청").isZero();
    assertThat(outbox.retryRow(id).stateVersion()).as("반복 요청 후 버전").isEqualTo(6L);

    outbox.setRetryState(id, "FAILED", 5, null, 11L, "DUPLICATE_KEY", FAILED_AT);
    assertThat(callRequeue(request)).as("다시 FAILED가 된 뒤 같은 요청").isEqualTo(1);
    assertThat(outbox.retryRow(id).stateVersion()).isEqualTo(12L);
  }

  // ===================== 부분 실패 원자성 =====================

  @Test
  @Timeout(60)
  @DisplayName("AC-13 잠금 timeout: 잠긴 대상 행 때문에 재처리가 1205로 실패하면 모든 대상 행이 그대로다 (부분 갱신 여부는 관찰 기록만)")
  void ac13_lockTimeoutLeavesAllRowsUnchanged() throws Exception {
    List<Long> rows = List.of(failedRow(), failedRow(), failedRow());
    Long locked = rows.get(2);
    List<RetryRow> before = rows.stream().map(outbox::retryRow).toList();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicLong waiterThreadId = new AtomicLong(-1);
    AtomicReference<SessionAfterFailure> session = new AtomicReference<>();
    long holderThreadId = -1;
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = directConnection()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      Future<Integer> waiter = null;
      try {
        holderThreadId = LockWaitObserver.connectionId(holder);
        lockRow(holder, locked);

        waiter = executor.submit(() -> {
          try (Connection w = directConnection(); Statement st = w.createStatement()) {
            st.execute("set session innodb_lock_wait_timeout = 3");
            waiterThreadId.set(LockWaitObserver.connectionId(w));
            try {
              return callRequeue(w, ids(rows));
            } catch (SQLException e) {
              // 연결을 닫기 전에 같은 물리 연결의 정리 상태를 모은다 (WORK-30 Finding). 원래 오류는 그대로 다시 던진다
              try {
                session.set(SessionAfterFailure.observe(w));
              } catch (SQLException observeFailure) {
                e.addSuppressed(observeFailure);
              }
              throw e;
            }
          }
        });
        awaitThreadId(waiterThreadId);

        Future<Integer> running = waiter;
        Optional<LockWaitObserver.Wait> wait = LockWaitObserver.await(observer, waiterThreadId.get(), holderThreadId,
          "outbox_event", String.valueOf(locked), running::isDone, WAIT_TIMEOUT);
        if (wait.isEmpty()) {
          throw LockWaitObserver.failForEarlyFinish(waiter, "TASK-033-1 STUB",
            "재처리는 잠긴 대상 행을 기다려야 한다");
        }
        // 증거 범위 기록: 대기 시점에 같은 문장이 이미 다른 대상 행을 바꿨는지 (실행 계획에 따라 달라지며 요구하지 않는다)
        // rows_modified = 0이면 이 사례는 "잠금 timeout 시 반영 없음"만 입증하고 부분 갱신 rollback 증거가 아니다.
        // 부분 갱신 rollback은 ac13_partialUpdateThenFailureRollsBackAll(장애 trigger)이 입증한다
        long modified = LockWaitObserver.rowsModified(observer, wait.get().requestingTrxId());
        System.out.println("AC13-LOCK-OBSERVE rows_modified_while_waiting=" + modified
          + " evidence=" + (modified >= 1 ? "partial-update-then-timeout" : "timeout-before-any-update"));

        Future<Integer> pendingResult = waiter;
        assertThatThrownBy(() -> pendingResult.get(30, TimeUnit.SECONDS))
          .as("잠금 timeout으로 재처리 실패")
          .isInstanceOf(ExecutionException.class)
          .satisfies(e -> {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getErrorCode()).as("lock wait timeout vendor code").isEqualTo(1205);
            System.out.println("AC13-SESSION-OBSERVE call_sqlstate=" + ((SQLException) cause).getSQLState()
              + " call_vendor_code=" + ((SQLException) cause).getErrorCode());
          });
      } finally {
        holder.rollback();
        if (waiter != null) {
          waiter.cancel(true);
        }
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          throw HarnessException.harness("worker did not terminate");
        }
        long open = LockWaitObserver.awaitNoOpenTransactions(observer,
          List.of(holderThreadId, waiterThreadId.get()), Duration.ofSeconds(5));
        if (open > 0) {
          throw HarnessException.harness("cleanup left open transactions: " + open + LockWaitObserver.diagnostics(observer));
        }
      }
    }

    for (int i = 0; i < rows.size(); i++) {
      assertThat(outbox.retryRow(rows.get(i))).as("대상 행 %d 불변", i).isEqualTo(before.get(i));
    }

    // 실패한 CALL 뒤 같은 연결의 세션 정리 (WORK-30 Finding 회귀). 두 결과는 위에서 이미 모았으므로 함께 판정한다
    SessionAfterFailure observed = session.get();
    if (observed == null) {
      throw HarnessException.harness("caller session state was not collected after the failed call");
    }
    System.out.println("AC13-SESSION-OBSERVE same_connection=" + (observed.connectionIdAfter() == waiterThreadId.get())
      + " dynamic_sql_variable_is_null=" + observed.variableIsNull()
      + " first_deallocate_vendor_code=" + observed.firstDeallocateVendorCode());
    if (observed.connectionIdAfter() != waiterThreadId.get()) {
      throw HarnessException.harness("caller connection changed after the failed call");
    }
    SoftAssertions.assertSoftly(soft -> {
      soft.assertThat(observed.variableIsNull())
        .as("실패 후 같은 연결에서 @outbox_requeue_failed_sql IS NULL (잔류하면 false)").isTrue();
      soft.assertThat(observed.firstDeallocateVendorCode())
        .as("실패 후 같은 연결의 첫 DEALLOCATE PREPARE outbox_requeue_failed_stmt vendor code "
          + "(1243 = 남은 statement 없음, 0 = 남아 있던 statement를 해제함)").isEqualTo(1243);
    });
  }

  /**
   * 실패한 CALL 직후 같은 연결의 세션 상태. 동적 SQL 원문과 ID 목록은 담지 않는다
   * - firstDeallocateVendorCode: DEALLOCATE 성공이면 0, 실패면 vendor code. 이 관찰이 statement를 해제하므로 마지막에 한 번만 한다
   */
  private record SessionAfterFailure(long connectionIdAfter, boolean variableIsNull, int firstDeallocateVendorCode) {

    static SessionAfterFailure observe(Connection connection) throws SQLException {
      long connectionId = LockWaitObserver.connectionId(connection);
      boolean variableIsNull;
      try (Statement st = connection.createStatement();
           ResultSet rs = st.executeQuery("select @outbox_requeue_failed_sql is null")) {
        rs.next();
        variableIsNull = rs.getBoolean(1);
      }
      int deallocate;
      try (Statement st = connection.createStatement()) {
        st.execute("deallocate prepare outbox_requeue_failed_stmt");
        deallocate = 0;
      } catch (SQLException e) {
        deallocate = e.getErrorCode();
      }
      return new SessionAfterFailure(connectionId, variableIsNull, deallocate);
    }
  }

  @Test
  @DisplayName("AC-13 부분 실패 원자성: 대상 행 하나가 실제 갱신된 뒤 다음 행 갱신에서 장애가 나면 CALL은 실패하고 모든 대상 행이 원래 값이다")
  void ac13_partialUpdateThenFailureRollsBackAll() {
    List<Long> rows = List.of(failedRow(), failedRow(), failedRow());
    List<RetryRow> before = rows.stream().map(outbox::retryRow).toList();
    outbox.installFaultTriggers(DB);
    try {
      outbox.armFault(DB, OutboxFixture.FaultMode.REQUEUE_SECOND, null);

      SQLException failure = null;
      Integer returned = null;
      try {
        returned = callRequeue(ids(rows));
      } catch (SQLException e) {
        failure = e;
      }
      List<OutboxFixture.FaultLog> log = outbox.faultLog();
      long updated = log.stream().filter(l -> "UPDATED".equals(l.event())).count();
      long signals = log.stream().filter(l -> "SIGNAL".equals(l.event())).count();
      System.out.println("AC13-ATOMICITY-OBSERVE returned=" + returned + " failure="
        + (failure == null ? null : failure.getSQLState() + "/" + failure.getErrorCode()) + " log=" + log);

      if (failure == null) {
        // 대상 행을 하나도 전이하지 않고 정상 종료 (준비 상태 stub 포함) - 동작 결과
        throw new AssertionError("재처리가 FAILED 대상 행을 전이하지 않고 정상 종료: returned=" + returned + ", log=" + log);
      }
      if (updated < 1 || signals != 1) {
        throw HarnessException.precondition("partial-update premise not established: updated=" + updated
          + ", signals=" + signals + ", failure=" + failure.getSQLState());
      }
      assertThat(failure.getSQLState()).as("장애 trigger SQLSTATE").isEqualTo("45000");
      for (int i = 0; i < rows.size(); i++) {
        assertThat(outbox.retryRow(rows.get(i))).as("대상 행 %d 원래 값", i).isEqualTo(before.get(i));
      }
    } finally {
      outbox.dropFaultTriggers(DB);
    }
  }

  // ===================== AC-10b 실제 잠금 경합 =====================

  @Test
  @Timeout(60)
  @DisplayName("AC-10b 다른 트랜잭션이 같은 FAILED 행을 전이·커밋하면, 대기한 재처리는 반환 0이고 버전은 한 번만 증가")
  void ac10b_waitsThenSeesCommittedTransition() throws Exception {
    Long id = failedRow();

    int requeued = requeueWhileBlocked(id, Connection::commit);

    assertThat(requeued).as("대기 후 반영 건수").isZero();
    RetryRow row = outbox.retryRow(id);
    assertThat(row.status()).isEqualTo("PENDING");
    assertThat(row.stateVersion()).as("버전은 차단자 전이 1회분").isEqualTo(6L);
    assertThat(row.failedAttempts()).isZero();
  }

  @Test
  @Timeout(60)
  @DisplayName("AC-10b 차단자가 rollback하면 대기한 재처리가 최신(FAILED) 상태 기준으로 전이해 반환 1")
  void ac10b_waitsThenTransitionsAfterRollback() throws Exception {
    Long id = failedRow();

    int requeued = requeueWhileBlocked(id, Connection::rollback);

    assertThat(requeued).as("대기 후 반영 건수").isEqualTo(1);
    RetryRow row = outbox.retryRow(id);
    assertThat(row.status()).isEqualTo("PENDING");
    assertThat(row.stateVersion()).isEqualTo(6L);
  }

  @FunctionalInterface
  private interface Release {
    void apply(Connection holder) throws SQLException;
  }

  /**
   * 차단자: 프로시저와 같은 조건의 JDBC UPDATE를 미커밋으로 보유. 대기자: autocommit connection에서 CALL
   */
  private int requeueWhileBlocked(Long id, Release release) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicLong waiterThreadId = new AtomicLong(-1);
    long holderThreadId = -1;
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = directConnection()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      Future<Integer> waiter = null;
      try {
        holderThreadId = LockWaitObserver.connectionId(holder);
        try (PreparedStatement ps = holder.prepareStatement("""
          update outbox_event set status = 'PENDING', failed_attempts = 0, next_attempt_at = null,
                                  state_version = state_version + 1
          where id = ? and status = 'FAILED'
          """)) {
          ps.setLong(1, id);
          if (ps.executeUpdate() != 1) {
            throw HarnessException.precondition("blocker transition did not apply");
          }
        }

        waiter = executor.submit(() -> {
          try (Connection w = directConnection()) {
            waiterThreadId.set(LockWaitObserver.connectionId(w));
            return callRequeue(w, ids(List.of(id)));
          }
        });
        awaitThreadId(waiterThreadId);

        // INNODB_LOCKS.lock_data가 NULL로 남는 경우(InnoDB가 페이지를 즉시 얻지 못함)에 대비해 기본키 probe로 대상 행을 입증한다.
        // probe는 holder 해제 전에 worker·연결·트랜잭션까지 정리된다
        Future<Integer> running = waiter;
        Optional<PrimaryKeyLockProbe.Observed> wait = PrimaryKeyLockProbe.await(observer, waiterThreadId.get(),
          holderThreadId, "outbox_event", id, OutboxRequeueProcedureIntegrationTest::directConnection, running::isDone,
          WAIT_TIMEOUT);
        wait.ifPresent(w -> System.out.println("AC10B-OBSERVE path=" + w.path()));
        if (wait.isEmpty()) {
          throw LockWaitObserver.failForEarlyFinish(waiter, "TASK-033-1 STUB",
            "재처리는 같은 FAILED 행을 잠근 트랜잭션을 기다려야 한다");
        }

        release.apply(holder);
        return waiter.get(30, TimeUnit.SECONDS);
      } finally {
        holder.rollback();
        if (waiter != null) {
          waiter.cancel(true);
        }
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          throw HarnessException.harness("worker did not terminate");
        }
        long open = LockWaitObserver.awaitNoOpenTransactions(observer,
          List.of(holderThreadId, waiterThreadId.get()), Duration.ofSeconds(5));
        if (open > 0) {
          throw HarnessException.harness("cleanup left open transactions: " + open + LockWaitObserver.diagnostics(observer));
        }
      }
    }
  }

  private static void awaitThreadId(AtomicLong threadId) throws InterruptedException {
    long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
    while (threadId.get() < 0) {
      if (System.nanoTime() > deadline) {
        throw HarnessException.harness("waiter connection was not ready");
      }
      Thread.sleep(20);
    }
  }

  private static void lockRow(Connection connection, Long id) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("select id from outbox_event where id = ? for update")) {
      ps.setLong(1, id);
      ps.executeQuery().close();
    }
  }

  private static int callRequeue(String ids) throws SQLException {
    try (Connection connection = directConnection()) {
      return callRequeue(connection, ids);
    }
  }

  private static int callRequeue(Connection connection, String ids) throws SQLException {
    try (CallableStatement call = connection.prepareCall("{call outbox_requeue_failed(?, ?)}")) {
      if (ids == null) {
        call.setNull(1, Types.VARCHAR);
      } else {
        call.setString(1, ids);
      }
      call.registerOutParameter(2, Types.INTEGER);
      call.execute();
      return call.getInt(2);
    }
  }

  private static Connection directConnection() throws SQLException {
    return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
  }
}
