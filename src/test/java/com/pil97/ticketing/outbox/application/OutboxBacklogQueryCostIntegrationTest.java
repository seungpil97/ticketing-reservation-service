package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.application.OutboxBacklogQuery.Snapshot;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.MutableClock;
import com.pil97.ticketing.support.outbox.OutboxFixture;
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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033-1 (#115) backlog snapshot 조회 비용 (격리 MariaDB 컨테이너, SqlObserver로 snapshot 호출 범위의 실행 SQL 측정)
 * - PENDING·FAILED가 없으면 존재 조회 1개만 실행하고 (0, 0, 0, null, null)을 반환한다
 * - 처리 완료(PROCESSED) 행만 있어도 같다
 * - 대상 행이 있으면 기존 집계 의미(AC-15)를 유지한다
 * 측정 범위: SqlObserver.clear() 직후부터 snapshot 반환까지 현재 thread의 실행 시작 이벤트. DB·repository는 실제 구현이다
 */
@Testcontainers
@SpringBootTest(properties = "outbox.relay.batch-size=10")
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class,
  MutableClock.Config.class})
@DirtiesContext
class OutboxBacklogQueryCostIntegrationTest {

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxBacklogQuery backlogQuery;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    outbox.assertIsolated(DB);
    SqlObserver.clear();
    assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event", Long.class)).as("시작 시 Outbox 없음").isZero();
  }

  @AfterEach
  void tearDown() {
    SqlObserver.clear();
    outbox.cleanup();
    fixture.cleanup();
  }

  private static LocalDateTime at(int hour, int minute) {
    return LocalDateTime.of(2026, 1, 1, hour, minute, 0);
  }

  private record Measured(Snapshot snapshot, List<String> sql) {
  }

  /** snapshot 호출 범위에서 현재 thread가 실행한 SQL (바인딩 값 없는 문장 형태) */
  private Measured measure(LocalDateTime now) {
    String thread = Thread.currentThread().getName();
    SqlObserver.clear();
    Snapshot snapshot = backlogQuery.snapshot(now);
    List<String> sql = SqlObserver.executions(thread).stream().map(SqlObserver.Event::sql).toList();
    SqlObserver.executions(thread).stream().map(SqlObserver.Event::connectionId).distinct()
      .forEach(id -> SqlObserver.sessionDiagnostic(id).ifPresent(d -> System.out.println(
        "BACKLOG-COST session transaction_isolation=" + d.transactionIsolation())));
    return new Measured(snapshot, sql);
  }

  private static void print(String label, Measured m) {
    System.out.println("BACKLOG-COST " + label + " statements=" + m.sql().size() + " snapshot=" + m.snapshot());
    for (String s : m.sql()) {
      System.out.println("BACKLOG-COST " + label + " sql=" + s);
    }
  }

  @Test
  @DisplayName("빈 상태: PENDING·FAILED가 없으면 SQL 1개(존재 조회)로 (0, 0, 0, null, null)")
  void emptyUsesSingleExistenceQuery() {
    Measured m = measure(at(10, 0));
    print("empty", m);

    assertThat(m.snapshot()).isEqualTo(new Snapshot(0L, 0L, 0L, null, null));
    assertThat(m.sql()).as("빈 snapshot 실행 SQL 수").hasSize(1);
    assertThat(m.sql().get(0)).as("outbox_event 존재 조회").containsIgnoringCase("outbox_event");
  }

  @Test
  @DisplayName("처리 완료 행만 있으면 빈 상태와 같다: SQL 1개, (0, 0, 0, null, null)")
  void processedOnlyIsEmpty() {
    outbox.insertBare("PROCESSED", 0, null, 1L, at(9, 30));
    outbox.insertBare("PROCESSED", 2, null, 3L, at(9, 40));

    Measured m = measure(at(10, 0));
    print("processed_only", m);

    assertThat(m.snapshot()).isEqualTo(new Snapshot(0L, 0L, 0L, null, null));
    assertThat(m.sql()).as("처리 완료만 있는 snapshot 실행 SQL 수").hasSize(1);
  }

  @Test
  @DisplayName("대상 행이 있으면 기존 집계 의미 그대로 (AC-15 snapshot과 같은 데이터·기대값), 처리 완료 행은 집계에서 빠진다")
  void nonEmptyKeepsAggregates() {
    outbox.insertBare("PENDING", 0, null, 0L, at(9, 58));
    outbox.insertBare("PENDING", 1, at(9, 59), 1L, at(9, 55));
    outbox.insertBare("PENDING", 2, at(10, 1), 2L, at(9, 50));
    Long failed = outbox.insertBare("PENDING", 0, null, 0L, at(9, 40));
    outbox.setRetryState(failed, "FAILED", 5, null, 5L, "UNKNOWN", at(9, 45));
    outbox.insertBare("PROCESSED", 0, null, 1L, at(9, 0));

    Measured m = measure(at(10, 0));
    print("non_empty", m);

    assertThat(m.snapshot()).isEqualTo(new Snapshot(2L, 1L, 1L, 300L, 600L));
  }

  @Test
  @DisplayName("FAILED만 있어도 빈 상태로 줄이지 않는다 (failed=1)")
  void failedOnlyIsNotEmpty() {
    Long failed = outbox.insertBare("PENDING", 0, null, 0L, at(9, 40));
    outbox.setRetryState(failed, "FAILED", 5, null, 5L, "UNKNOWN", at(9, 45));

    Measured m = measure(at(10, 0));
    print("failed_only", m);

    assertThat(m.snapshot()).isEqualTo(new Snapshot(0L, 0L, 1L, null, null));
  }

  @Test
  @DisplayName("관찰 기록: 존재 조회와 집계 조회의 실행 계획 (EXPLAIN, 판정 없음)")
  void explainPlans() {
    for (Map.Entry<String, String> q : Map.of(
      "exists_pending_or_failed", "select id from outbox_event where status in ('PENDING', 'FAILED') limit 1",
      "count_by_status_failed", "select count(*) from outbox_event where status = 'FAILED'",
      "count_due_pending", "select count(*) from outbox_event where status = 'PENDING' "
        + "and (next_attempt_at is null or next_attempt_at <= '2026-01-01 10:00:00')",
      "min_created_pending", "select min(created_at) from outbox_event where status = 'PENDING'").entrySet()) {
      for (Map<String, Object> row : jdbcTemplate.queryForList("explain " + q.getValue())) {
        System.out.println("BACKLOG-COST explain " + q.getKey() + " type=" + row.get("type") + " key=" + row.get("key")
          + " rows=" + row.get("rows") + " extra=" + row.get("Extra"));
      }
    }
  }
}
