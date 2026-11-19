package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.support.consistency.ConsistencyFixture;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TASK-033-1 (#115) 시각 계약 통합 테스트 (격리 MariaDB 컨테이너, MutableClock)
 *
 * <p>계약 (AC-4·AC-5·AC-6):
 * <ul>
 *   <li>T_poll(호출 시작)은 후보 조회에만, T_lock(처리기 잠금 직후)은 잠금 후 due 재확인에, T_done(markProcessed 직전)은 processed_at에,
 *       T_fail(처리 실패 감지 직후)은 last_failed_at에, max(T_fail, T_rec(기록기 잠금 직후))는 다음 시도 시각 계산에 쓴다</li>
 *   <li>next_attempt_at = ceil_s(max(T_fail, T_rec) + delay), due 판정은 next_attempt_at ≤ now (초 단위 저장 때문에 최소 대기보다 일찍 due가 되지 않는다)</li>
 *   <li>후보 조회 뒤 잠금 전에 다음 시도 시각이 미래로 바뀐 행은 처리기가 건너뛴다</li>
 * </ul>
 * 처리 도중 시각 진행은 장애 주입기 hook(onNotificationInsert·onProcessedFlush), 기록 지연은 기록기 spy hook으로 만든다.
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
class OutboxRetryTimingIntegrationTest {

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxRelayService relayService;
  @Autowired private JdbcTemplate jdbcTemplate;
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

  private void advanceBeforeRecord(Long outboxId, Duration duration) {
    doAnswer(invocation -> {
      clock.advance(duration);
      return invocation.callRealMethod();
    }).when(recorder).record(eq(outboxId), anyLong(), any(), any());
  }

  /**
   * 지정 시각에 폴링했을 때 소비 경로가 해당 행을 호출하지 않는다
   */
  private void assertNotSelectedAt(Seeded s, LocalDateTime now) {
    clock.set(now);
    clearInvocations(itemProcessor);
    relayService.relay();
    verify(itemProcessor, never()).process(s.outboxId());
    assertThat(outbox.retryRow(s.outboxId()).status()).as("%s 폴링 후 상태", now).isEqualTo("PENDING");
  }

  /**
   * 지정 시각에 폴링하면 해당 행을 1회 처리해 알림 1건·PROCESSED
   */
  private void assertProcessedAt(Seeded s, LocalDateTime now) {
    clock.set(now);
    clearInvocations(itemProcessor);
    relayService.relay();
    verify(itemProcessor, times(1)).process(s.outboxId());
    assertThat(outbox.snapshot(s).notificationCount()).as("%s 폴링 후 알림", now).isEqualTo(1L);
    assertThat(outbox.retryRow(s.outboxId()).status()).as("%s 폴링 후 상태", now).isEqualTo("PROCESSED");
  }

  // ===================== AC-4 경계 =====================

  @Test
  @DisplayName("AC-4 T1 소수 초 기록 시각: max(T_fail,T_rec)=10:00:00.500 → next 10:00:31, 10:00:30.999 미선택·10:00:31.000 선택")
  void ac4_t1_fractionalRecordTimeRoundsUp() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    clock.set(at(10, 0, 0, 400));
    advanceBeforeRecord(s.outboxId(), Duration.ofMillis(100));

    relayService.relay();

    assertThat(outbox.retryRow(s.outboxId()).nextAttemptAt()).isEqualTo(at(10, 0, 31, 0));
    assertNotSelectedAt(s, at(10, 0, 30, 999));
    assertProcessedAt(s, at(10, 0, 31, 0));
  }

  @Test
  @DisplayName("AC-4 T2 정수 초 기록 시각: max(T_fail,T_rec)=10:00:00.000 → next 10:00:30, 10:00:29.999 미선택·10:00:30.000 선택")
  void ac4_t2_wholeSecondRecordTime() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    clock.set(at(10, 0, 0, 0));

    relayService.relay();

    assertThat(outbox.retryRow(s.outboxId()).nextAttemptAt()).isEqualTo(at(10, 0, 30, 0));
    assertNotSelectedAt(s, at(10, 0, 29, 999));
    assertProcessedAt(s, at(10, 0, 30, 0));
  }

  // ===================== AC-5 긴 처리 대기·기록 지연 =====================

  @Test
  @DisplayName("AC-5 T3 폴링 시작 후 처리 중 50초 대기 뒤 실패: 다음 시도 시각은 폴링 시작이 아니라 실패 시각 기준 10:01:20")
  void ac5_t3_longProcessingWaitUsesFailureTime() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    OutboxFaultInterceptor.onProcessedFlush(s.outboxId(), () -> clock.advance(Duration.ofSeconds(50)));
    clock.set(at(10, 0, 0, 0));

    relayService.relay();

    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.lastFailedAt()).as("last_failed_at = floor_s(T_fail)").isEqualTo(at(10, 0, 50, 0));
    assertThat(row.nextAttemptAt()).as("next = ceil_s(max(10:00:50, T_rec) + 30s)").isEqualTo(at(10, 1, 20, 0));
    assertNotSelectedAt(s, at(10, 1, 19, 999));
    assertProcessedAt(s, at(10, 1, 20, 0));
  }

  @Test
  @DisplayName("AC-5 T4 기록 지연: T_fail 10:00:50, T_rec 10:01:30 → next 10:02:00, last_failed_at은 실패 시각 10:00:50")
  void ac5_t4_delayedRecordUsesLaterOfFailureAndRecordTime() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    OutboxFaultInterceptor.onProcessedFlush(s.outboxId(), () -> clock.advance(Duration.ofSeconds(50)));
    advanceBeforeRecord(s.outboxId(), Duration.ofSeconds(40));
    clock.set(at(10, 0, 0, 0));

    relayService.relay();

    verify(recorder, times(1)).record(eq(s.outboxId()), anyLong(), any(), any());
    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.lastFailedAt()).as("last_failed_at = floor_s(T_fail)").isEqualTo(at(10, 0, 50, 0));
    assertThat(row.nextAttemptAt()).as("next = ceil_s(max(10:00:50, 10:01:30) + 30s)").isEqualTo(at(10, 2, 0, 0));
    assertNotSelectedAt(s, at(10, 1, 59, 999));
    assertProcessedAt(s, at(10, 2, 0, 0));
  }

  // ===================== AC-6 잠금 후 재확인·처리 완료 시각 =====================

  @Test
  @DisplayName("AC-6 T5 후보 조회 뒤 잠금 전에 다음 시도 시각이 미래로 바뀐 행은 처리기가 건너뛴다: 알림 0·기록 없음")
  void ac6_t5_lockTimeRecheckSkipsRowNoLongerDue() {
    Seeded s = outbox.seedPending();
    LocalDateTime future = at(10, 5, 0, 0);
    doAnswer(invocation -> {
      // 후보 조회 이후, 처리기 트랜잭션·잠금 이전에 다른 경로가 행을 바꾼다 (autocommit)
      jdbcTemplate.update("update outbox_event set next_attempt_at = ?, state_version = state_version + 1 where id = ?",
        future, s.outboxId());
      return invocation.callRealMethod();
    }).when(itemProcessor).process(s.outboxId());

    int processed = relayService.relay();

    assertThat(processed).as("처리 건수").isZero();
    assertThat(outbox.snapshot(s).notificationCount()).as("알림").isZero();
    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.status()).isEqualTo("PENDING");
    assertThat(row.failedAttempts()).as("실패 기록 없음").isZero();
    assertThat(row.nextAttemptAt()).as("다른 경로가 쓴 값 유지").isEqualTo(future);
    assertThat(row.stateVersion()).as("다른 경로가 쓴 버전 유지").isEqualTo(1L);
    verify(recorder, never()).record(anyLong(), anyLong(), any(), any());
  }

  @Test
  @DisplayName("AC-6 T6 processed_at은 처리 완료 표시 시점의 Clock (잠금 후 처리 중 진행된 시각)")
  void ac6_t6_processedAtFromDoneTime() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.onNotificationInsert(s.outboxId(), () -> clock.advance(Duration.ofSeconds(5)));
    clock.set(at(10, 0, 0, 700));

    relayService.relay();

    RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.status()).isEqualTo("PROCESSED");
    assertThat(row.processedAt()).as("processed_at = floor_s(T_done)").isEqualTo(at(10, 0, 5, 0));
  }
}
