package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.application.OutboxBacklogQuery.Snapshot;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.MutableClock;
import com.pil97.ticketing.support.outbox.OutboxFaultInterceptor;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033-1 (#115) AC-15 처리 지연 관측 통합 테스트 (격리 MariaDB 컨테이너, MutableClock, 출력 캡처)
 *
 * <p>계약:
 * <ul>
 *   <li>snapshot(now): dueCount(PENDING이고 next NULL 또는 ≤ now), backoffCount(PENDING이고 next > now), failedCount,
 *       oldestDueAgeSeconds(대상 PENDING 중 가장 이른 created_at 기준 now까지 초, 없으면 null),
 *       oldestPendingAgeSeconds(PENDING 전체 기준, 없으면 null)</li>
 *   <li>relay 호출은 세 건수 중 하나라도 0보다 크면 "Outbox backlog." 로그를 남기고, 직전 출력 후
 *       outbox.relay.backlog-log-interval(60s) 이상 지나기 전에는 다시 남기지 않는다. 모두 0이면 남기지 않는다</li>
 *   <li>로그 형식: Outbox backlog. due={} backoff={} failed={} oldestDueAgeSeconds={} oldestPendingAgeSeconds={}</li>
 * </ul>
 * 격리 DB에는 이 클래스가 만든 행만 있고 각 테스트는 비어 있는 상태에서 시작한다. created_at은 JDBC로 지정한다.
 * 로그 주기 상태는 relay singleton이 가지므로, 로그를 검증하는 메서드는 실행 전에 새 context를 만든다
 * (@DirtiesContext BEFORE_METHOD - 메서드 실행 순서·내부 필드 초기화에 의존하지 않는다). 컨테이너는 클래스 단위로 공유한다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.batch-size=10",
  "outbox.relay.backlog-log-interval=60s",
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.outbox.OutboxFaultInterceptor"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, MutableClock.Config.class})
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class OutboxBacklogIntegrationTest {

  private static final String BACKLOG_LOG = "Outbox backlog.";

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxBacklogQuery backlogQuery;
  @Autowired private OutboxRelayService relayService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    outbox.assertIsolated(DB);
    OutboxFaultInterceptor.reset();
    clock.set(MutableClock.START);
    assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event", Long.class)).as("시작 시 Outbox 없음").isZero();
  }

  @AfterEach
  void tearDown() {
    OutboxFaultInterceptor.reset();
    outbox.cleanup();
    fixture.cleanup();
  }

  private static LocalDateTime at(int hour, int minute) {
    return LocalDateTime.of(2026, 1, 1, hour, minute, 0);
  }

  private static long occurrences(String text, String token) {
    long count = 0;
    for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) {
      count++;
    }
    return count;
  }

  @Test
  @DisplayName("AC-15 snapshot: 대상·백오프·FAILED 건수와 가장 오래된 대상·PENDING 경과 시간")
  void ac15_snapshotValues() {
    outbox.insertBare("PENDING", 0, null, 0L, at(9, 58));
    outbox.insertBare("PENDING", 1, at(9, 59), 1L, at(9, 55));
    outbox.insertBare("PENDING", 2, at(10, 1), 2L, at(9, 50));
    Long failed = outbox.insertBare("PENDING", 0, null, 0L, at(9, 40));
    outbox.setRetryState(failed, "FAILED", 5, null, 5L, "UNKNOWN", at(9, 45));

    Snapshot snapshot = backlogQuery.snapshot(at(10, 0));

    assertThat(snapshot.dueCount()).as("대상 PENDING").isEqualTo(2L);
    assertThat(snapshot.backoffCount()).as("백오프 PENDING").isEqualTo(1L);
    assertThat(snapshot.failedCount()).as("FAILED").isEqualTo(1L);
    assertThat(snapshot.oldestDueAgeSeconds()).as("가장 오래된 대상 경과 (09:55)").isEqualTo(300L);
    assertThat(snapshot.oldestPendingAgeSeconds()).as("가장 오래된 PENDING 경과 (09:50)").isEqualTo(600L);
  }

  @Test
  @DisplayName("AC-15 snapshot: 행이 없으면 건수 0·경과 시간 null")
  void ac15_emptySnapshot() {
    Snapshot snapshot = backlogQuery.snapshot(at(10, 0));

    assertThat(snapshot).isEqualTo(new Snapshot(0L, 0L, 0L, null, null));
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  @DisplayName("AC-15 대상 0·FAILED만 있어도 backlog 로그 1회 (순회 시작 여부와 무관)")
  void ac15_failedOnlyIsLogged(CapturedOutput output) {
    Long failed = outbox.insertBare("PENDING", 0, null, 0L, at(9, 40));
    outbox.setRetryState(failed, "FAILED", 5, null, 5L, "UNKNOWN", at(9, 45));

    relayService.relay();

    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("backlog 로그 횟수").isEqualTo(1L);
    assertThat(output.getAll()).contains("due=0").contains("backoff=0").contains("failed=1");
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  @DisplayName("AC-15 주기: 60초 안에는 다시 남기지 않고, 60초가 지나면 다시 남긴다 (모두 0이면 남기지 않음과 함께 판정)")
  void ac15_logInterval(CapturedOutput output) {
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("모두 0").isZero();

    Long failed = outbox.insertBare("PENDING", 0, null, 0L, at(9, 40));
    outbox.setRetryState(failed, "FAILED", 5, null, 5L, "UNKNOWN", at(9, 45));
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("첫 출력").isEqualTo(1L);

    clock.advance(Duration.ofSeconds(59));
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("59초 뒤").isEqualTo(1L);

    clock.advance(Duration.ofSeconds(1));
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("60초 뒤").isEqualTo(2L);
  }

  /** 처리 대상 행을 실제로 처리하는 도중(알림 INSERT 직전) 시각을 진행해, 폴링 시작 시각과 처리 후 시각을 벌린다 */
  private Seeded seedProcessedWhileClockAdvances(Duration processing) {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.onNotificationInsert(s.outboxId(), () -> clock.advance(processing));
    return s;
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  @DisplayName("AC-15 주기 (회귀 보완): 항목 처리에 90초가 걸려도 최소 간격은 처리 후 첫 출력 시각부터 센다 (5초·59초 뒤 없음, 60초에 출력)")
  void ac15_intervalCountsFromActualOutputAfterLongProcessing(CapturedOutput output) {
    Long failed = outbox.insertBare("PENDING", 0, null, 0L, at(9, 40));
    outbox.setRetryState(failed, "FAILED", 5, null, 5L, "UNKNOWN", at(9, 45));
    Seeded s = seedProcessedWhileClockAdvances(Duration.ofSeconds(90));

    relayService.relay();
    assertThat(clock.instant()).as("처리 도중 시각 진행").isEqualTo(MutableClock.START.plusSeconds(90)
      .atZone(MutableClock.ZONE).toInstant());
    assertThat(outbox.snapshot(s).status()).as("처리 대상 행 처리 완료").isEqualTo("PROCESSED");
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("첫 출력 (처리 후, FAILED 1건)").isEqualTo(1L);

    clock.advance(Duration.ofSeconds(5));
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("첫 출력 5초 뒤").isEqualTo(1L);

    clock.advance(Duration.ofSeconds(54));
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("첫 출력 59초 뒤").isEqualTo(1L);

    clock.advance(Duration.ofSeconds(1));
    relayService.relay();
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("첫 출력 60초 뒤").isEqualTo(2L);
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  @DisplayName("AC-15 관측 시각 (회귀 보완): 처리 후 backlog는 처리 후 시각 기준 - 폴링 때 백오프였고 처리 중 대상이 된 행은 후보가 아니었고 due로 집계된다")
  void ac15_backlogObservedAtPostProcessingTime(CapturedOutput output) {
    Long late = outbox.insertBare("PENDING", 0, at(10, 0).plusSeconds(30), 0L, at(9, 50));
    Seeded s = seedProcessedWhileClockAdvances(Duration.ofSeconds(90));

    relayService.relay();

    assertThat(outbox.snapshot(s).status()).as("처리 대상 행 처리 완료").isEqualTo("PROCESSED");
    assertThat(output.getAll()).as("최초 후보는 처리 대상 1건뿐").contains("candidates=1 processed=1 skipped=0 failed=0");
    assertThat(jdbcTemplate.queryForMap(
      "select status, failed_attempts, state_version from outbox_event where id = ?", late))
      .as("폴링 때 백오프였던 행은 이번 호출에서 처리·기록되지 않음")
      .containsEntry("status", "PENDING").containsEntry("failed_attempts", 0).containsEntry("state_version", 0L);
    assertThat(occurrences(output.getAll(), BACKLOG_LOG)).as("backlog 로그 횟수").isEqualTo(1L);
    // 처리 후 시각 10:01:30 기준: 그 행은 대상(next 10:00:30 ≤ now), 경과 09:50 → 10:01:30 = 690초
    assertThat(output.getAll()).as("처리 후 시각 기준 관측")
      .contains("Outbox backlog. due=1 backoff=0 failed=0 oldestDueAgeSeconds=690 oldestPendingAgeSeconds=690");
  }
}
