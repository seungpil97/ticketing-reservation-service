package com.pil97.ticketing.hold.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pil97.ticketing.common.persistence.ConcurrentUpdateConflictException;
import com.pil97.ticketing.hold.api.dto.response.HoldResponse;
import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.application.PaymentService;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.reservation.application.ReservationService;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.seat.error.SeatErrorCode;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.LockWaitProbe;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.assertBusinessError;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.failure;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.hasSnapshotConflictCause;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.holdRequest;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.payRequest;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.result;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.shutdown;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.worker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * TASK-062 (#104) 결정적 경합 통합 테스트 (실제 MariaDB·Redis)
 *
 * <p>순서 제어 수단 (모두 test source, production hook 없음)
 * <ul>
 *   <li>OTP: 테스트 스레드의 outer 트랜잭션이 처리 트랜잭션의 "이전 상태 읽기"를 먼저 수행하고, 다른 worker 커밋 후 같은 트랜잭션에서 production 메서드로 재개</li>
 *   <li>TL: 테스트 트랜잭션이 행을 잡아 worker를 대기시킨 뒤 같은 트랜잭션에서 경쟁 변경을 수행하고 커밋</li>
 *   <li>CommitGate: worker 커넥션이 대상 UPDATE를 실행한 뒤 실제 commit 직전에 대기 (ObservingDataSource).
 *       정상 release일 때만 commit하고, abort(pause 도달 전 포함)·timeout·interrupt 이후에는 해당 thread의 commit을 거부한다</li>
 *   <li>LockWaitProbe: 하나의 deadline 안에서 worker의 in-flight 실행을 재선택하며, 선택한 실행의 connection ID 기준으로
 *       PROCESSLIST SQL 대응(+INNODB_TRX LOCK WAIT) 확인. 입증하지 못하면 HarnessException.
 *       대기 관찰 전에 worker의 대상 SQL이 1020으로 끝났고 worker 결과도 1020 원인 실패이면 CONFLICT_ABORT
 *       (RED 쪽 경쟁 지점 도달 증거. SERVER_EXECUTING·LOCK_WAIT과 구분해 기록)</li>
 *   <li>StatementGate: worker가 지정 SQL(X1-P: 예약 잠금 조회)을 정상 반환한 직후 대기. CommitGate와 같은 등록·abort·격리 규칙</li>
 * </ul>
 * 공통 판정 (WORK-4B)
 * <ul>
 *   <li>전제: innodb_snapshot_isolation=ON, REPEATABLE READ. 각 시나리오는 관련 thread가 실제로 사용한 모든 connection의
 *       SESSION 진단을 업무 단언 전에 사전조건으로 확인한다 (누락·불일치는 PRECONDITION, Invalid)</li>
 *   <li>일반 경합(X1-P, X2~X6, F-6): 업무 결과 단언 뒤 관련 worker의 1020 0건, 만료 정상 경합은 요약 failed=0을 단언한다</li>
 *   <li>X1-J(기존 read view를 가진 외부 트랜잭션): 1020 0건 조건을 적용하지 않는다. 결제가 정상 반환하지 않고
 *       원인 1020의 ConcurrentUpdateConflictException으로 끝나며 외부 트랜잭션이 rollback되는지를 본다</li>
 *   <li>SQL 순서 GREEN 조건 (업무 단언 뒤): 결제의 예약 잠금 다음 문장이 좌석 unique key 잠금, 만료 후보 조회 다음 문장이
 *       좌석 id 잠금, HOLD 생성의 첫 문장이 좌석 unique key 잠금 (LAZY proxy ID 접근·항목 트랜잭션 첫 접근 가정의 실행 증거)</li>
 * </ul>
 * 모든 대기는 제한 시간을 가진다. 테스트 본문의 gate는 finally에서 abort하고,
 * @AfterEach는 gate abort → worker 종료 확인 → gate·관찰 기록 정리 → Redisson 해제 확인 → fixture 정리 순서로 수행한다.
 * worker 종료를 확인하지 못하면 SqlObserver 격리 목록에 보존하고 이후 정리·후속 테스트 시작을 HarnessException으로 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, SqlObserverConfig.class})
class HoldSeatRaceIntegrationTest {

  private static final Duration WAIT = Duration.ofSeconds(10);
  private static final String PAYMENT_PREFIX = "idempotency:payment";
  private static final String RESERVATION_PREFIX = "idempotency:reservation";

  // 시나리오별 대기 SQL (경쟁 트랜잭션이 보유한 행에 대한 쓰기 또는 잠금 조회)
  // - 같은 worker의 선행 SQL이 정상 반환된 뒤 다음 실행이 대기 지점이 될 수 있어, probe는 in-flight 실행을 재선택한다
  // - RED(현행)와 GREEN(개선 후)의 대기 SQL이 달라도 같은 행에 대한 대기이면 같은 패턴에 포함한다
  // 좌석 행: 현행 UPDATE showtime_seat / 개선 후 좌석 FOR UPDATE
  private static final Pattern SEAT_WAIT = Pattern.compile(
    "(update showtime_seat |from showtime_seat \\S+ where .* for update)");
  // HOLD 행: 현행 UPDATE holds / 개선 후 HOLD FOR UPDATE (예약 생성의 findByIdWithLock 포함)
  private static final Pattern HOLD_WAIT = Pattern.compile(
    "(update holds |from holds \\S+ where .* for update)");
  // 좌석·HOLD 행 모두 경쟁 트랜잭션이 보유한 경우
  private static final Pattern ANY_SEAT_OR_HOLD_WAIT = Pattern.compile(
    "(update showtime_seat |from showtime_seat \\S+ where .* for update|update holds |from holds \\S+ where .* for update)");
  // F-6: 현행 INSERT holds(FK 공유 락 대기) / 개선 후 좌석 FOR UPDATE
  private static final Pattern F6_WAIT = Pattern.compile(
    "(insert into holds |from showtime_seat \\S+ where .* for update)");

  // X1-P StatementGate 대상: 결제의 예약 잠금 조회
  private static final Pattern RESERVATION_LOCK = Pattern.compile("from reservations \\S+ where .* for update");
  // SQL 순서 GREEN 조건
  // 좌석 unique key(showtime_id, seat_id) 잠금 - 결제·취소(예약 FK 사용), HOLD 생성
  private static final Pattern SEAT_LOCK_BY_KEY = Pattern.compile(
    "from showtime_seat \\S+ where \\S+\\.showtime_id=\\? and \\S+\\.seat_id=\\? for update");
  // 좌석 id 잠금 - 만료 항목 트랜잭션
  private static final Pattern SEAT_LOCK_BY_ID = Pattern.compile(
    "from showtime_seat \\S+ where \\S+\\.id=\\? for update");
  // 만료 후보 조회 (현행 fetch join 조회와 개선 후 projection 조회 모두)
  private static final Pattern EXPIRE_CANDIDATES = Pattern.compile(
    "from holds .*where .*status=\\? and .*expires_at<\\?");
  // 만료 요약 로그 logger 범위
  private static final String EXPIRE_LOGGER = "com.pil97.ticketing.hold.application";

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private ConsistencyFixture fixture;
  @Autowired private HoldExpirationService holdExpirationService;
  @Autowired private HoldService holdService;
  @Autowired private ReservationService reservationService;
  @Autowired private PaymentService paymentService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private HoldRepository holdRepository;
  @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext
  private EntityManager entityManager;

  private final List<ExecutorService> executors = new ArrayList<>();
  private final List<SeatRef> lockCheckedSeats = new ArrayList<>();

  private Logger expireLogger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> summaryAppender;

  @BeforeEach
  void setUp() {
    // 종료 미확인 worker가 격리 중이면 clear()가 아무것도 지우지 않고 HarnessException을 던져 시나리오를 시작하지 않는다
    SqlObserver.clear();
    // 만료 요약 로그(INFO) 수집 - tearDown 첫 단계에서 제거·level 복원
    expireLogger = (Logger) LoggerFactory.getLogger(EXPIRE_LOGGER);
    originalLevel = expireLogger.getLevel();
    expireLogger.setLevel(Level.INFO);
    summaryAppender = new ListAppender<>();
    summaryAppender.start();
    expireLogger.addAppender(summaryAppender);
  }

  @AfterEach
  void tearDown() {
    // 정리 순서
    // (1) gate abort - 등록은 유지: pause 중이거나 아직 도달하지 않은 worker의 commit 모두 거부
    // (2) 이 테스트의 모든 worker에 종료 요청 후 종료 확인. 미확인 worker는 SqlObserver 격리 목록으로 옮겨지고 HARNESS 예외
    //     → 이후 단계(관찰 정리, 락 확인, fixture 정리)를 수행하지 않는다
    // (3) gate 등록·관찰 기록 정리 - 이전 테스트의 격리가 남아 있으면 HARNESS 예외로 중단 (보호 상태 유지)
    // (4) Redisson 해제 확인 (5) fixture 정리 - (2)(3)을 통과해 살아 있는 worker가 없을 때만
    // (0) 요약 로그 appender 제거 (관찰 상태·worker와 무관하므로 가장 먼저)
    detachSummaryAppender();
    SqlObserver.abortGate();
    List<ExecutorService> mine = new ArrayList<>(executors);
    // 참조는 terminateWorkers가 미확인 시 격리 목록에 보존하므로 인스턴스 목록은 비워도 된다
    executors.clear();
    shutdown(mine.toArray(ExecutorService[]::new));
    SqlObserver.clear();
    try {
      // Redisson 락은 직접 삭제하지 않고, worker 종료 후 정상 해제 여부만 확인한다
      for (SeatRef seat : lockCheckedSeats) {
        assertThat(fixture.redissonLocked(seat)).as("redisson lock released for %s", seat).isFalse();
      }
    } finally {
      lockCheckedSeats.clear();
      fixture.cleanup();
    }
  }

  private ExecutorService newWorker(String role) {
    ExecutorService executor = worker("t062-" + role + "-" + System.nanoTime());
    executors.add(executor);
    return executor;
  }

  private static String threadNameOf(ExecutorService executor) throws Exception {
    return executor.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
  }

  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId, LocalDateTime expiresAt) {
    LocalDateTime afterExpiry() {
      return expiresAt.plusSeconds(1);
    }
  }

  /**
   * 좌석 HELD, HOLD ACTIVE(expiresAt = 실제 시각 + 1분), 예약 PENDING
   * - 예약 생성 검증(만료 전)과 고정 시각 만료 호출(T + 1초)을 모두 만족시키기 위한 시각 배치
   */
  private Pending pending() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, expiresAt);
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    lockCheckedSeats.add(seat);
    return new Pending(seat, member, holdId, reservationId, expiresAt);
  }

  /**
   * worker가 대상 SQL을 서버에서 실행 중이며 반환되지 않은 상태(경쟁 지점)에 도달했는지 connection ID 기준으로 확인
   * - 입증하지 못하면 HarnessException (Invalid RED)
   */
  private void awaitBlocked(String threadName, Pattern waitSql, String contentionHolder, Future<?> future) {
    LockWaitProbe.awaitBlocked(jdbcTemplate, threadName, waitSql, contentionHolder, future, WAIT);
  }

  private static long countErrors(String threadName, int errorCode) {
    return SqlObserver.failedExecutions(threadName).stream().filter(e -> e.errorCode() == errorCode).count();
  }

  private void detachSummaryAppender() {
    if (summaryAppender != null) {
      expireLogger.detachAppender(summaryAppender);
      summaryAppender.stop();
      expireLogger.setLevel(originalLevel);
      summaryAppender = null;
    }
  }

  /**
   * 일반 경합 GREEN 조건: 관련 worker thread의 실행 중 1020(ER_CHECKREAD)으로 끝난 것이 없어야 한다
   */
  private static void assertNoSnapshotConflict(String... threadNames) {
    for (String threadName : threadNames) {
      assertThat(SqlObserver.snapshotConflicts(threadName)).as("no 1020 on %s", threadName).isEmpty();
    }
  }

  /**
   * 만료 정상 경합 GREEN 조건: 해당 만료 worker의 요약 로그 한 이벤트에 expired=와 failed=0
   */
  private void assertExpireSummaryFailedZero(String expireThread) {
    List<ILoggingEvent> events = List.copyOf(summaryAppender.list);
    assertThat(events)
      .as("expire summary of %s with failed=0", expireThread)
      .anySatisfy(e -> {
        assertThat(e.getThreadName()).isEqualTo(expireThread);
        assertThat(e.getFormattedMessage())
          .containsPattern("expired=\\d+")
          .containsPattern("failed=0(\\D|$)");
      });
  }

  /**
   * SQL 순서 GREEN 조건: thread의 첫 after 실행 바로 다음 실행이 expectedNext와 맞아야 한다
   */
  private static void assertNextStatement(String threadName, Pattern after, Pattern expectedNext, String description) {
    List<SqlObserver.Event> executions = SqlObserver.executions(threadName);
    int index = -1;
    for (int i = 0; i < executions.size(); i++) {
      if (after.matcher(executions.get(i).sql()).find()) {
        index = i;
        break;
      }
    }
    assertThat(index).as("%s: '%s' executed on %s", description, after, threadName).isGreaterThanOrEqualTo(0);
    assertThat(index + 1).as("%s: statement after '%s' exists", description, after).isLessThan(executions.size());
    assertThat(executions.get(index + 1).sql()).as(description).containsPattern(expectedNext);
  }

  /**
   * SQL 순서 GREEN 조건: thread의 첫 실행이 expected와 맞아야 한다
   */
  private static void assertFirstStatement(String threadName, Pattern expected, String description) {
    List<SqlObserver.Event> executions = SqlObserver.executions(threadName);
    assertThat(executions).as("%s: %s executed SQL", description, threadName).isNotEmpty();
    assertThat(executions.get(0).sql()).as(description).containsPattern(expected);
  }

  private static void registerCompletion(AtomicInteger completion) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        completion.set(status);
      }
    });
  }

  private static <T> T unchecked(Future<T> future) {
    try {
      return result(future);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ===================== X1-J 외부 트랜잭션(기존 read view) 결제 stale 대 만료·재선점·재결제 =====================

  @Test
  @DisplayName("X1-J(AC-12): 기존 read view를 가진 외부 트랜잭션이 이전 상태를 읽은 뒤 만료·재선점·재결제가 커밋되면, 같은 트랜잭션의 결제는 정상 반환하지 않고 원인 1020의 전용 충돌 예외로 끝나며 외부 트랜잭션은 rollback, 이중 판매 없음")
  void x1j_staleReadPaymentInOuterTx_failsWithConcurrentUpdateConflict() throws Exception {
    Pending p = pending();
    Member other = fixture.member();
    fixture.admit(other);
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    String staleKey = fixture.idempotencyKey(PAYMENT_PREFIX);
    String otherReserveKey = fixture.idempotencyKey(RESERVATION_PREFIX);
    String otherPayKey = fixture.idempotencyKey(PAYMENT_PREFIX);
    ExecutorService competitor = newWorker("x1j-competitor");
    String competitorThread = threadNameOf(competitor);
    String outerThread = Thread.currentThread().getName();

    AtomicReference<Throwable> payError = new AtomicReference<>();
    AtomicBoolean payReturned = new AtomicBoolean(false);
    AtomicInteger outerCompletion = new AtomicInteger(-1);
    AtomicReference<Long> otherReservation = new AtomicReference<>();
    AtomicReference<Long> otherHold = new AtomicReference<>();

    // pay 호출 결과와 외부 트랜잭션 종료 결과를 따로 포착한다 (commit 예외가 핵심 단언 도달을 막지 않게)
    Throwable outerEnd = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
      registerCompletion(outerCompletion);
      // Tx-P: 예약 락과 HOLD·좌석의 이전 상태(ACTIVE/HELD) 적재 - 일관 읽기로 read view 생성
      reservationRepository.findByIdWithLock(p.reservationId()).orElseThrow();
      holdRepository.findById(p.holdId()).orElseThrow();
      showtimeSeatRepository.findById(p.seat().showtimeSeatId()).orElseThrow();

      // 다른 트랜잭션들: 만료 → 재선점 → 예약 → 결제 커밋
      Long reserved = unchecked(competitor.submit(() -> {
        holdExpirationService.expireHolds(p.afterExpiry());
        HoldResponse rehold = holdService.hold(p.seat().showtimeId(), holdRequest(p.seat(), other));
        otherHold.set(rehold.holdId());
        ReservationResponse r = reservationService.reserve(otherReserveKey, rehold.holdId()).getResponse();
        paymentService.pay(otherPayKey, payRequest(r.reservationId(), false));
        return r.reservationId();
      }));
      otherReservation.set(reserved);

      // Tx-P 재개: 같은 트랜잭션에서 production 결제 호출
      try {
        paymentService.pay(staleKey, payRequest(p.reservationId(), false));
        payReturned.set(true);
      } catch (RuntimeException e) {
        payError.set(e);
      }
      // 실패한 트랜잭션을 더 사용하지 않는다 (예외를 삼켜 계속 진행하지 않음). rollback 여부는 시스템이 정한 결과로 관찰한다
    }));
    System.out.println("[X1-J] outerEnd=" + outerEnd + ", outerCompletion=" + outerCompletion.get());
    SqlObserver.requireSessionDiagnostics(outerThread, competitorThread);

    assertThat(payReturned.get()).as("pay must not return normally inside stale outer tx").isFalse();
    assertThat(payError.get()).isInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(hasSnapshotConflictCause(payError.get())).as("conflict cause is 1020").isTrue();
    assertThat(outerCompletion.get()).as("outer tx rolled back").isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    List<Map<String, Object>> otherPayments = fixture.payments(otherReservation.get());
    assertThat(otherPayments).hasSize(1);
    assertThat(otherPayments.get(0).get("status")).isEqualTo("SUCCESS");
    assertThat(fixture.reservationStatus(otherReservation.get())).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(otherHold.get())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + staleKey)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + staleKey)).isFalse();
  }

  // ===================== X1-P 일반 public 결제(예약 잠금 직후 pause) 대 만료·재선점·재결제 =====================

  @Test
  @DisplayName("X1-P(AC-12): 일반 public 결제가 예약 잠금 직후 멈춘 동안 만료·재선점·재결제가 커밋되면, 재개된 결제는 HOLD-002로 실패하고 이중 판매·1020이 없다")
  void x1p_paymentPausedAfterReservationLock_failsWithHoldNotActive() throws Exception {
    Pending p = pending();
    Member other = fixture.member();
    fixture.admit(other);
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    String staleKey = fixture.idempotencyKey(PAYMENT_PREFIX);
    String otherReserveKey = fixture.idempotencyKey(RESERVATION_PREFIX);
    String otherPayKey = fixture.idempotencyKey(PAYMENT_PREFIX);
    ExecutorService payWorker = newWorker("x1p-pay");
    ExecutorService competitor = newWorker("x1p-competitor");
    String payThread = threadNameOf(payWorker);
    String competitorThread = threadNameOf(competitor);
    AtomicReference<Long> otherHold = new AtomicReference<>();

    // 제어 위치: 결제 worker의 예약 FOR UPDATE 실행이 정상 반환된 직후 (좌석·HOLD 확인 전)
    SqlObserver.CommitGate gate = SqlObserver.armStatementGate(payThread, RESERVATION_LOCK);
    Future<?> pay;
    Long otherReservation;
    try {
      pay = payWorker.submit(() -> paymentService.pay(staleKey, payRequest(p.reservationId(), false)));
      gate.requirePaused(WAIT);

      // 다른 트랜잭션들: 만료 → 재선점 → 예약 → 결제 커밋 (결제 worker가 잡은 예약 행에는 접근하지 않음)
      otherReservation = result(competitor.submit(() -> {
        holdExpirationService.expireHolds(p.afterExpiry());
        HoldResponse rehold = holdService.hold(p.seat().showtimeId(), holdRequest(p.seat(), other));
        otherHold.set(rehold.holdId());
        ReservationResponse r = reservationService.reserve(otherReserveKey, rehold.holdId()).getResponse();
        paymentService.pay(otherPayKey, payRequest(r.reservationId(), false));
        return r.reservationId();
      }));
      gate.release();
    } finally {
      // 실패 경로에서는 결제 worker를 진행시키지 않고 해제 (이미 release된 경우 no-op)
      gate.abort();
    }
    SqlObserver.requireSessionDiagnostics(payThread, competitorThread);

    assertBusinessError(failure(pay), HoldErrorCode.NOT_ACTIVE);
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    List<Map<String, Object>> otherPayments = fixture.payments(otherReservation);
    assertThat(otherPayments).hasSize(1);
    assertThat(otherPayments.get(0).get("status")).isEqualTo("SUCCESS");
    assertThat(fixture.reservationStatus(otherReservation)).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(otherHold.get())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + staleKey)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + staleKey)).isFalse();
    assertNoSnapshotConflict(payThread, competitorThread);
    // GREEN 조건: 예약 잠금 다음 문장이 좌석 unique key 잠금 (예약 FK의 LAZY proxy ID 접근이 SELECT를 내지 않음)
    assertNextStatement(payThread, RESERVATION_LOCK, SEAT_LOCK_BY_KEY, "pay: seat lock right after reservation lock");
  }

  // ===================== X2 결제 먼저(커밋 전) 대 만료 =====================

  @Test
  @DisplayName("X2(AC-13): 결제가 쓰기 후 커밋 전일 때 만료가 대기하고, 결제 커밋 후 만료는 결제 완료 상태를 바꾸지 않는다")
  void x2_paymentFirstUncommitted_expireWaitsAndKeepsPaidState() throws Exception {
    Pending p = pending();
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    ExecutorService expireWorker = newWorker("x2-expire");
    String expireThread = threadNameOf(expireWorker);
    AtomicReference<Future<?>> expire = new AtomicReference<>();

    transactionTemplate.executeWithoutResult(status -> {
      paymentService.pay(key, payRequest(p.reservationId(), false));
      entityManager.flush();
      expire.set(expireWorker.submit(() -> holdExpirationService.expireHolds(p.afterExpiry())));
      awaitBlocked(expireThread, ANY_SEAT_OR_HOLD_WAIT, "test tx: pay(R1) flushed (R1·H1·seat row locks)", expire.get());
    });
    SqlObserver.requireSessionDiagnostics(expireThread);

    assertThat(failure(expire.get())).isNull();
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CONFIRMED");
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).get("status")).isEqualTo("SUCCESS");
    assertNoSnapshotConflict(expireThread);
    assertExpireSummaryFailedZero(expireThread);
    // GREEN 조건: 만료 항목 트랜잭션의 첫 대상 테이블 접근이 좌석 id 잠금
    assertNextStatement(expireThread, EXPIRE_CANDIDATES, SEAT_LOCK_BY_ID, "expire: item tx starts with seat lock");
  }

  // ===================== X3 만료 먼저(커밋 직전) 대 결제 =====================

  @Test
  @DisplayName("X3(AC-14): 만료가 대상 UPDATE 후 커밋 직전일 때 결제가 대기하고, 만료 커밋 후 결제는 HOLD-002로 실패한다")
  void x3_expireFirstBeforeCommit_paymentWaitsThenFails() throws Exception {
    Pending p = pending();
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    ExecutorService expireWorker = newWorker("x3-expire");
    ExecutorService payWorker = newWorker("x3-pay");
    String expireThread = threadNameOf(expireWorker);
    String payThread = threadNameOf(payWorker);

    // RED/GREEN 모두 별도 worker에서 public expireHolds(now)를 호출한다
    // 제어 위치: 대상 HOLD UPDATE 실행 이후, 실제 commit 직전
    SqlObserver.CommitGate gate = SqlObserver.armCommitGate(expireThread, Pattern.compile("update holds"));
    Future<?> expire;
    Future<?> pay;
    try {
      expire = expireWorker.submit(() -> holdExpirationService.expireHolds(p.afterExpiry()));
      gate.requirePaused(WAIT);

      pay = payWorker.submit(() -> paymentService.pay(key, payRequest(p.reservationId(), false)));
      awaitBlocked(payThread, ANY_SEAT_OR_HOLD_WAIT, "expire worker paused before commit (H1·seat row locks)", pay);
      gate.release();
    } finally {
      // 실패 경로에서는 commit하지 않고 해제 (이미 release된 경우 no-op)
      gate.abort();
    }
    SqlObserver.requireSessionDiagnostics(expireThread, payThread);

    assertThat(failure(expire)).isNull();
    assertBusinessError(failure(pay), HoldErrorCode.NOT_ACTIVE);
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
    assertNoSnapshotConflict(expireThread, payThread);
    assertExpireSummaryFailedZero(expireThread);
    // GREEN 조건: 결제의 예약 잠금 다음 문장이 좌석 unique key 잠금
    assertNextStatement(payThread, RESERVATION_LOCK, SEAT_LOCK_BY_KEY, "pay: seat lock right after reservation lock");
  }

  // ===================== X4 만료 대 취소·재선점 =====================

  @Test
  @DisplayName("X4(AC-15): 만료가 후보를 읽고 좌석에서 대기하는 동안 취소·재선점이 커밋되면, 만료는 재선점 좌석을 덮어쓰지 않는다")
  void x4_expireWaiting_cancelAndReholdCommitted_reheldSeatPreserved() throws Exception {
    Pending p = pending();
    Member other = fixture.member();
    fixture.admit(other);
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    ExecutorService expireWorker = newWorker("x4-expire");
    String expireThread = threadNameOf(expireWorker);
    AtomicReference<Future<?>> expire = new AtomicReference<>();
    AtomicReference<Long> rehold = new AtomicReference<>();

    transactionTemplate.executeWithoutResult(status -> {
      jdbcTemplate.queryForObject(
        "select id from showtime_seat where id = ? for update", Long.class, p.seat().showtimeSeatId());
      expire.set(expireWorker.submit(() -> holdExpirationService.expireHolds(p.afterExpiry())));
      awaitBlocked(expireThread, SEAT_WAIT, "test tx: showtime_seat FOR UPDATE", expire.get());
      reservationService.cancel(p.reservationId());
      rehold.set(holdService.hold(p.seat().showtimeId(), holdRequest(p.seat(), other)).holdId());
    });
    SqlObserver.requireSessionDiagnostics(expireThread);

    assertThat(failure(expire.get())).isNull();
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertThat(fixture.holdStatus(rehold.get())).isEqualTo("ACTIVE");
    assertThat(fixture.holdIdsBySeatAndStatus(p.seat(), HoldStatus.ACTIVE)).containsExactly(rehold.get());
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertNoSnapshotConflict(expireThread);
    assertExpireSummaryFailedZero(expireThread);
    assertNextStatement(expireThread, EXPIRE_CANDIDATES, SEAT_LOCK_BY_ID, "expire: item tx starts with seat lock");
  }

  // ===================== X5 취소 먼저(커밋 전) 대 만료 =====================

  @Test
  @DisplayName("X5(AC-15): 취소가 커밋 전일 때 만료가 대기하고, 취소 커밋 후 만료는 예외 없이 CANCELLED HOLD를 바꾸지 않는다")
  void x5_cancelFirstUncommitted_expireWaitsAndKeepsCancelled() throws Exception {
    Pending p = pending();
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    ExecutorService expireWorker = newWorker("x5-expire");
    String expireThread = threadNameOf(expireWorker);
    AtomicReference<Future<?>> expire = new AtomicReference<>();

    transactionTemplate.executeWithoutResult(status -> {
      reservationService.cancel(p.reservationId());
      entityManager.flush();
      expire.set(expireWorker.submit(() -> holdExpirationService.expireHolds(p.afterExpiry())));
      awaitBlocked(expireThread, SEAT_WAIT, "test tx: cancel(R1) flushed (seat row lock)", expire.get());
    });
    SqlObserver.requireSessionDiagnostics(expireThread);

    assertThat(failure(expire.get())).isNull();
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertNoSnapshotConflict(expireThread);
    assertExpireSummaryFailedZero(expireThread);
    assertNextStatement(expireThread, EXPIRE_CANDIDATES, SEAT_LOCK_BY_ID, "expire: item tx starts with seat lock");
  }

  // ===================== X6 예약 생성 대 만료 (AC-16) =====================

  @Test
  @DisplayName("X6a(AC-16, guard): 만료가 커밋 직전일 때 예약 생성이 대기하고, 만료 커밋 후 예약 생성은 HOLD-002로 거절된다")
  void x6a_expireFirstBeforeCommit_reserveRejected() throws Exception {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member u1 = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long holdId = fixture.hold(seat, u1, HoldStatus.ACTIVE, expiresAt);
    lockCheckedSeats.add(seat);
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(holdId));
    String key = fixture.idempotencyKey(RESERVATION_PREFIX);
    ExecutorService expireWorker = newWorker("x6a-expire");
    ExecutorService reserveWorker = newWorker("x6a-reserve");
    String expireThread = threadNameOf(expireWorker);
    String reserveThread = threadNameOf(reserveWorker);

    SqlObserver.CommitGate gate = SqlObserver.armCommitGate(expireThread, Pattern.compile("update holds"));
    Future<?> expire;
    Future<?> reserve;
    try {
      expire = expireWorker.submit(() -> holdExpirationService.expireHolds(now));
      gate.requirePaused(WAIT);
      reserve = reserveWorker.submit(() -> reservationService.reserve(key, holdId));
      awaitBlocked(reserveThread, HOLD_WAIT, "expire worker paused before commit (H1 row lock)", reserve);
      gate.release();
    } finally {
      gate.abort();
    }
    SqlObserver.requireSessionDiagnostics(expireThread, reserveThread);

    assertThat(failure(expire)).isNull();
    assertBusinessError(failure(reserve), HoldErrorCode.NOT_ACTIVE);
    assertThat(fixture.reservationIdByHold(holdId)).isNull();
    assertThat(fixture.holdStatus(holdId)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("AVAILABLE");
    // guard 유지: 요약 로그 단언은 넣지 않는다 (현행 로그 형식과 무관한 보존 계약)
    assertNoSnapshotConflict(expireThread, reserveThread);
  }

  @Test
  @DisplayName("X6b(AC-16): 예약 생성이 커밋 전일 때 만료가 대기하고, 이후 예약 PENDING·HOLD EXPIRED 상태의 결제는 HOLD-002로 거절된다")
  void x6b_reserveFirstUncommitted_expireWaits_thenPaymentRejected() throws Exception {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member u1 = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long holdId = fixture.hold(seat, u1, HoldStatus.ACTIVE, expiresAt);
    lockCheckedSeats.add(seat);
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(holdId));
    String reserveKey = fixture.idempotencyKey(RESERVATION_PREFIX);
    String payKey = fixture.idempotencyKey(PAYMENT_PREFIX);
    ExecutorService expireWorker = newWorker("x6b-expire");
    String expireThread = threadNameOf(expireWorker);
    AtomicReference<Future<?>> expire = new AtomicReference<>();

    transactionTemplate.executeWithoutResult(status -> {
      reservationService.reserve(reserveKey, holdId);
      expire.set(expireWorker.submit(() -> holdExpirationService.expireHolds(now)));
      awaitBlocked(expireThread, HOLD_WAIT, "test tx: reserve(H1) holds H1 FOR UPDATE", expire.get());
    });
    SqlObserver.requireSessionDiagnostics(expireThread);

    assertThat(failure(expire.get())).isNull();
    Long reservationId = fixture.reservationIdByHold(holdId);
    assertThat(reservationId).isNotNull();
    assertThat(fixture.reservationStatus(reservationId)).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(holdId)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("AVAILABLE");

    Throwable payError = null;
    try {
      paymentService.pay(payKey, payRequest(reservationId, false));
    } catch (RuntimeException e) {
      payError = e;
    }
    assertBusinessError(payError, HoldErrorCode.NOT_ACTIVE);
    assertThat(fixture.payments(reservationId)).isEmpty();
    assertThat(fixture.reservationStatus(reservationId)).isEqualTo("PENDING");
    assertNoSnapshotConflict(expireThread);
    assertExpireSummaryFailedZero(expireThread);
  }

  // ===================== F-6 HOLD 생성 좌석 행 락 (AC-11) =====================

  @Test
  @DisplayName("F-6(AC-11): 경쟁자가 좌석 행 락을 쥔 채 HELD로 커밋하면, 대기한 HOLD 요청은 SEAT-002로 거절되고 활성 HOLD는 1개다")
  void f6_competitorHoldsSeatRowLock_waitingHoldRejected_singleActiveHold() throws Exception {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.AVAILABLE);
    lockCheckedSeats.add(seat);
    Member competitor = fixture.member();
    Member requester = fixture.member();
    fixture.admit(requester);
    ExecutorService holdWorker = newWorker("f6-hold");
    String holdThread = threadNameOf(holdWorker);
    AtomicReference<Future<?>> hold = new AtomicReference<>();
    AtomicReference<Long> competitorHold = new AtomicReference<>();

    transactionTemplate.executeWithoutResult(status -> {
      jdbcTemplate.queryForObject(
        "select id from showtime_seat where id = ? for update", Long.class, seat.showtimeSeatId());
      jdbcTemplate.update("update showtime_seat set status = 'HELD' where id = ?", seat.showtimeSeatId());
      GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
      jdbcTemplate.update(con -> {
        PreparedStatement ps = con.prepareStatement(
          "insert into holds (showtime_seat_id, member_id, status, expires_at) values (?, ?, 'ACTIVE', ?)",
          Statement.RETURN_GENERATED_KEYS);
        ps.setLong(1, seat.showtimeSeatId());
        ps.setLong(2, competitor.getId());
        ps.setTimestamp(3, Timestamp.valueOf(nowSeconds().plusMinutes(5)));
        return ps;
      }, keyHolder);
      competitorHold.set(keyHolder.getKey().longValue());

      hold.set(holdWorker.submit(() -> holdService.hold(seat.showtimeId(), holdRequest(seat, requester))));
      awaitBlocked(holdThread, F6_WAIT, "test tx: seat FOR UPDATE + HELD + competitor hold", hold.get());
    });
    SqlObserver.requireSessionDiagnostics(holdThread);

    assertBusinessError(failure(hold.get()), SeatErrorCode.NOT_AVAILABLE_FOR_HOLD);
    assertThat(fixture.holdIdsBySeatAndStatus(seat, HoldStatus.ACTIVE)).containsExactly(competitorHold.get());
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");
    assertNoSnapshotConflict(holdThread);
    // GREEN 조건: HOLD 생성의 첫 DB 문장이 좌석 unique key 잠금 (일관 읽기로 read view를 먼저 만들지 않음)
    assertFirstStatement(holdThread, SEAT_LOCK_BY_KEY, "hold: first statement is seat lock");
  }

  // ===================== 보조 stress guard (결정적 테스트를 대체하지 않음) =====================

  @Test
  @DisplayName("보조 guard: 취소와 만료 동시 시작 20회 - 예외 없음, 좌석 AVAILABLE, 예약 CANCELLED, HOLD 종료 상태")
  void stress_cancelVsExpire_20iterations() throws Exception {
    ExecutorService cancelWorker = newWorker("stress-cancel");
    ExecutorService expireWorker = newWorker("stress-expire");
    String cancelThread = threadNameOf(cancelWorker);
    String expireThread = threadNameOf(expireWorker);

    for (int i = 0; i < 20; i++) {
      Pending p = pending();
      fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
      CyclicBarrier barrier = new CyclicBarrier(2);

      Future<?> cancel = cancelWorker.submit(() -> {
        barrier.await(WAIT.toSeconds(), TimeUnit.SECONDS);
        reservationService.cancel(p.reservationId());
        return null;
      });
      Future<?> expire = expireWorker.submit(() -> {
        barrier.await(WAIT.toSeconds(), TimeUnit.SECONDS);
        holdExpirationService.expireHolds(p.afterExpiry());
        return null;
      });

      Throwable cancelError = failure(cancel);
      Throwable expireError = failure(expire);
      // 보조 보고 (RED 근거 아님): 누적 1020·1213 건수
      System.out.println("[stress] iteration " + i
        + " cancel(1020=" + countErrors(cancelThread, 1020) + ", 1213=" + countErrors(cancelThread, 1213) + ")"
        + " expire(1020=" + countErrors(expireThread, 1020) + ", 1213=" + countErrors(expireThread, 1213) + ")");
      assertThat(cancelError).as("iteration %d cancel", i).isNull();
      assertThat(expireError).as("iteration %d expire", i).isNull();
      assertThat(fixture.seatStatus(p.seat())).as("iteration %d seat", i).isEqualTo("AVAILABLE");
      assertThat(fixture.reservationStatus(p.reservationId())).as("iteration %d reservation", i).isEqualTo("CANCELLED");
      assertThat(fixture.holdStatus(p.holdId())).as("iteration %d hold", i).isIn("CANCELLED", "EXPIRED");

      fixture.cleanup();
      lockCheckedSeats.clear();
    }
  }
}
