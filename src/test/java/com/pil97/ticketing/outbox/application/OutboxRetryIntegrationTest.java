package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.domain.OutboxErrorCode;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.MutableClock;
import com.pil97.ticketing.support.outbox.OutboxFaultInterceptor;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.RetryRow;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TASK-033-1 (#115) 실패 기록·소진·기록 장애 격리 통합 테스트 (격리 MariaDB 컨테이너)
 *
 * <p>계약 (AC-3·AC-7·AC-11):
 * <ul>
 *   <li>처리 실패는 처리 트랜잭션 rollback 뒤 독립 트랜잭션으로 기록한다: 실패 횟수 +1, last_failed_at = floor_s(T_fail),
 *       next_attempt_at = ceil_s(max(T_fail, T_rec) + delay(k)), 오류 분류, state_version +1. 알림은 남지 않는다</li>
 *   <li>기록된 실패가 5회가 되면 FAILED·next_attempt_at NULL이 되고 자동 순회에서 제외된다</li>
 *   <li>한 항목의 실패 기록 장애는 같은 호출의 다른 항목 기록을 막지 않는다. 기록 장애 항목은 PENDING·횟수·버전이 그대로이고
 *       다음 순회에서 다시 대상이 된다 (AC-11: 기록기 진입 전 예외로 coordinator 격리, AC-11b: 기록기 실제 트랜잭션의 갱신 실행 단계 장애로
 *       부분 반영 없음 - 격리 DB의 테스트 전용 trigger)</li>
 *   <li>실패 뒤 성공(AC-3b): PROCESSED·알림 1건, 실패 이력 보존, 성공 전이도 state_version +1, next_attempt_at은 NULL, 이후 재변경 없음</li>
 * </ul>
 * 시각은 MutableClock으로 제어한다. T_fail은 처리 실패 직후, T_rec는 기록기 진입 직전 spy hook에서 진행시킨 시각이다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.batch-size=2",
  "outbox.relay.retry.max-attempts=5",
  "outbox.relay.retry.initial-backoff=30s",
  "outbox.relay.retry.max-backoff=10m",
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.outbox.OutboxFaultInterceptor"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class,
  MutableClock.Config.class})
@DirtiesContext
class OutboxRetryIntegrationTest {

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
  @MockitoSpyBean private OutboxItemProcessor itemProcessor;
  @MockitoSpyBean private OutboxFailureRecorder recorder;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    outbox.assertIsolated(DB);
    OutboxFaultInterceptor.reset();
    SqlObserver.clear();
    clock.set(MutableClock.START);
  }

  @AfterEach
  void tearDown() {
    OutboxFaultInterceptor.reset();
    outbox.cleanup();
    fixture.cleanup();
    SqlObserver.clear();
  }

  private static LocalDateTime at(int hour, int minute, int second, int millis) {
    return LocalDateTime.of(2026, 1, 1, hour, minute, second, millis * 1_000_000);
  }

  /**
   * 기록기 진입 직전에 시각을 진행 (T_rec 제어). 실제 기록 동작은 그대로 호출한다
   */
  private void advanceBeforeRecord(Long outboxId, Duration duration) {
    doAnswer(invocation -> {
      clock.advance(duration);
      return invocation.callRealMethod();
    }).when(recorder).record(eq(outboxId), anyLong(), any(), any());
  }

  // ===================== AC-3 실패 기록 =====================

  @Test
  @DisplayName("AC-3 commit 시점 장애: 처리 rollback 뒤 독립 트랜잭션으로 실패 1회·시각·오류 분류·버전 증가 기록, 알림 0")
  void ac3_commitFailureRecordedInSeparateTransaction() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    clock.set(at(10, 0, 0, 400));
    advanceBeforeRecord(s.outboxId(), Duration.ofMillis(100));

    assertThatCode(() -> relayService.relay()).as("항목 장애는 폴링 밖으로 전파되지 않는다").doesNotThrowAnyException();

    assertThat(OutboxFaultInterceptor.injectedCount(s.outboxId())).as("장애 주입 횟수").isEqualTo(1);
    assertThat(outbox.snapshot(s).notificationCount()).as("알림 rollback").isZero();
    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.status()).isEqualTo("PENDING");
    assertThat(row.failedAttempts()).as("기록된 실패 횟수").isEqualTo(1);
    assertThat(row.lastFailedAt()).as("last_failed_at = floor_s(T_fail)").isEqualTo(at(10, 0, 0, 0));
    assertThat(row.nextAttemptAt()).as("next = ceil_s(max(T_fail, T_rec) + 30s)").isEqualTo(at(10, 0, 31, 0));
    assertThat(row.lastErrorCode()).as("오류 분류 코드").isIn(Arrays.stream(OutboxErrorCode.values()).map(Enum::name).toList());
    assertThat(row.lastErrorType()).as("오류 타입 (허용 목록 또는 OTHER)").isNotNull();
    assertThat(row.stateVersion()).as("상태 버전 증가").isEqualTo(1L);
    assertThat(row.processedAt()).isNull();
  }

  @Test
  @DisplayName("AC-3b 실패 뒤 성공: PROCESSED·알림 1, 실패 이력(횟수·last_error_*·last_failed_at) 보존, 버전 +1, next NULL, 이후 폴링에서 재변경 없음")
  void ac3b_successAfterFailurePreservesHistory() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    relayService.relay();
    RetryRow failed = outbox.retryRow(s.outboxId());
    assertThat(failed.failedAttempts()).as("실패 기록").isEqualTo(1);
    assertThat(failed.nextAttemptAt()).as("다음 시도 시각").isNotNull();

    LocalDateTime successAt = failed.nextAttemptAt();
    clock.set(successAt);
    relayService.relay();

    RetryRow done = outbox.retryRow(s.outboxId());
    assertThat(done.status()).isEqualTo("PROCESSED");
    assertThat(outbox.snapshot(s).notificationCount()).as("알림").isEqualTo(1L);
    assertThat(done.processedAt()).as("처리 완료 시각").isEqualTo(successAt);
    assertThat(done.failedAttempts()).as("실패 횟수 보존").isEqualTo(failed.failedAttempts());
    assertThat(done.lastFailedAt()).as("마지막 실패 시각 보존").isEqualTo(failed.lastFailedAt());
    assertThat(done.lastErrorCode()).as("오류 분류 보존").isEqualTo(failed.lastErrorCode());
    assertThat(done.lastErrorType()).as("오류 타입 보존").isEqualTo(failed.lastErrorType());
    assertThat(done.lastSqlState()).isEqualTo(failed.lastSqlState());
    assertThat(done.lastVendorCode()).isEqualTo(failed.lastVendorCode());
    assertThat(done.stateVersion()).as("성공 전이 버전 증가").isEqualTo(failed.stateVersion() + 1);
    assertThat(done.nextAttemptAt()).as("성공 후 다음 시도 시각 정책: NULL").isNull();

    clock.advance(Duration.ofDays(1));
    clearInvocations(itemProcessor);
    relayService.relay();
    relayService.relay();
    verify(itemProcessor, never()).process(s.outboxId());
    assertThat(outbox.retryRow(s.outboxId())).as("처리 완료 뒤 추가 폴링에서 재변경 없음").isEqualTo(done);
    assertThat(outbox.snapshot(s).notificationCount()).isEqualTo(1L);
  }

  // ===================== AC-7 소진 =====================

  @Test
  @DisplayName("AC-7 기록된 실패 4회는 PENDING, 5회째 FAILED·next NULL, 이후 시각을 크게 진행해도 소비 경로 미호출·알림 0")
  void ac7_fifthRecordedFailureExhausts() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(s.outboxId());

    for (int attempt = 1; attempt <= 5; attempt++) {
      relayService.relay();
      RetryRow row = outbox.retryRow(s.outboxId());
      assertThat(row.failedAttempts()).as("%d회째 시도 후 기록된 실패", attempt).isEqualTo(attempt);
      if (attempt < 5) {
        assertThat(row.status()).as("%d회째 시도 후 상태", attempt).isEqualTo("PENDING");
        assertThat(row.nextAttemptAt()).as("%d회째 시도 후 다음 시도 시각", attempt).isNotNull();
        clock.set(row.nextAttemptAt());
      } else {
        assertThat(row.status()).as("5회째 시도 후 상태").isEqualTo("FAILED");
        assertThat(row.nextAttemptAt()).as("FAILED의 다음 시도 시각").isNull();
      }
    }
    assertThat(OutboxFaultInterceptor.injectedCount(s.outboxId())).as("실제 시도 횟수").isEqualTo(5);

    clock.advance(Duration.ofDays(1));
    clearInvocations(itemProcessor);
    relayService.relay();
    relayService.relay();
    verify(itemProcessor, never()).process(s.outboxId());
    assertThat(outbox.snapshot(s).notificationCount()).isZero();
    assertThat(outbox.retryRow(s.outboxId()).status()).isEqualTo("FAILED");
  }

  // ===================== AC-11 기록 장애 격리 =====================

  @Test
  @DisplayName("AC-11 한 항목의 실패 기록 장애는 같은 호출의 다른 항목 기록을 막지 않고, 장애 항목은 그대로 다음 순회에서 다시 대상")
  void ac11_recordFailureIsolated() {
    Seeded a = outbox.seedPending();
    Seeded b = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(a.outboxId());
    OutboxFaultInterceptor.failAlways(b.outboxId());
    doAnswer(invocation -> {
      throw new IllegalStateException("injected recorder failure");
    }).when(recorder).record(eq(a.outboxId()), anyLong(), any(), any());

    assertThatCode(() -> relayService.relay()).as("기록 장애도 폴링 밖으로 전파되지 않는다").doesNotThrowAnyException();

    // 신규 동작: 다른 항목은 기록된다
    verify(recorder, times(1)).record(eq(b.outboxId()), anyLong(), any(), any());
    RetryRow rowB = outbox.retryRow(b.outboxId());
    assertThat(rowB.failedAttempts()).as("B 기록된 실패").isEqualTo(1);
    assertThat(rowB.nextAttemptAt()).as("B 다음 시도 시각").isNotNull();

    // 기록 장애 항목: 기록 시도는 있었고, 상태는 그대로
    verify(recorder, times(1)).record(eq(a.outboxId()), anyLong(), any(), any());
    RetryRow rowA = outbox.retryRow(a.outboxId());
    assertThat(rowA.status()).isEqualTo("PENDING");
    assertThat(rowA.failedAttempts()).as("A 기록된 실패 (기록 장애로 미증가)").isZero();
    assertThat(rowA.stateVersion()).as("A 상태 버전").isZero();
    assertThat(rowA.nextAttemptAt()).as("A 다음 시도 시각").isNull();

    // 다음 순회: A는 즉시 대상, B는 백오프 중
    clearInvocations(itemProcessor);
    relayService.relay();
    verify(itemProcessor, times(1)).process(a.outboxId());
    verify(itemProcessor, never()).process(b.outboxId());
  }

  @Test
  @DisplayName("AC-11b 실패 기록 트랜잭션이 갱신 실행 단계에서 실패하면 A의 상태·횟수·버전·시각·오류 컬럼에 부분 반영이 없고, 같은 호출의 B는 기록된다")
  void ac11b_recordTransactionFailureLeavesNoPartialWrite() {
    Seeded a = outbox.seedPending();
    Seeded b = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(a.outboxId());
    OutboxFaultInterceptor.failAlways(b.outboxId());
    RetryRow aBefore = outbox.retryRow(a.outboxId());
    outbox.installFaultTriggers(DB);
    try {
      // 기록기 실제 트랜잭션 안에서 A의 failed_attempts를 바꾸는 UPDATE가 실행되는 순간 DB가 거부한다 (구현 방식과 무관)
      outbox.armFault(DB, OutboxFixture.FaultMode.RECORD, a.outboxId());

      assertThatCode(() -> relayService.relay()).as("기록 트랜잭션 장애도 폴링 밖으로 전파되지 않는다").doesNotThrowAnyException();

      // 신규 동작: 같은 호출의 다른 항목은 기록된다
      RetryRow rowB = outbox.retryRow(b.outboxId());
      assertThat(rowB.failedAttempts()).as("B 기록된 실패").isEqualTo(1);

      // 장치 전제: A의 기록 갱신이 실제로 실행돼 거부됐다
      long aSignals = outbox.faultLog().stream()
        .filter(l -> "SIGNAL".equals(l.event()) && a.outboxId().equals(l.outboxId())).count();
      if (aSignals < 1) {
        throw HarnessException.precondition("record transaction fault was not triggered for A: " + outbox.faultLog());
      }
      assertThat(outbox.retryRow(a.outboxId())).as("A 부분 반영 없음 (상태·횟수·버전·시각·오류 컬럼)").isEqualTo(aBefore);
    } finally {
      outbox.dropFaultTriggers(DB);
    }
  }
}
