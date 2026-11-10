package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.outbox.OutboxFixture.Snapshot;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TASK-033 (#108) 독립 검토 후속 - 빈 잔여 범위와 호출당 batch 상한 통합 테스트 (격리 MariaDB 컨테이너)
 *
 * <p>계약: 진행 중 순회의 잔여 범위가 0건이면 같은 호출에서 새 순회를 최대 한 번 시작하고, 호출 1회의 시도는 최대 batch건이다.
 *
 * <p>다른 테스트의 실행 순서에 의존하지 않도록 이 클래스는 자체 격리 DB·자체 context(@DirtiesContext)를 쓰고 테스트는 1개다.
 * relay bean의 순회 상태는 이 테스트가 처음 만든다. relay 스케줄러는 test profile에서 꺼져 있고 HOLD 만료·대기열은 제거한다.
 */
@Testcontainers
@SpringBootTest(properties = "outbox.relay.batch-size=2")
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class})
@DirtiesContext
class OutboxRelaySweepIntegrationTest {

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxRelayService relayService;
  @MockitoSpyBean private OutboxItemProcessor itemProcessor;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationContext context;

  @AfterEach
  void tearDown() {
    outbox.cleanup();
    fixture.cleanup();
  }

  @Test
  @DisplayName("진행 중 순회의 잔여 범위가 비면 같은 호출에서 새 순회를 시작하고, 호출당 최대 batch(2)건만 시도한다")
  void emptyRemainingRange_startsNewSweepInSameCall_withinBatchLimit() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    assertThat(outbox.currentDatabase()).as("격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);
    assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event where status = 'PENDING'", Long.class))
      .as("시작 시 PENDING 없음").isZero();

    // 호출 1: 순회 상한 r3로 r1·r2 처리 → 상한 미도달·batch 가득이라 순회 진행 중 (위치 r2)
    Seeded r1 = outbox.seedPending();
    Seeded r2 = outbox.seedPending();
    Seeded r3 = outbox.seedPending();
    clearInvocations(itemProcessor);
    int first = relayService.relay();
    assertThat(first).as("호출 1 처리 건수").isEqualTo(2);
    verify(itemProcessor, times(2)).process(anyLong());
    verify(itemProcessor, never()).process(r3.outboxId());
    assertProcessed(outbox.snapshot(r1), "호출 1 r1");
    assertProcessed(outbox.snapshot(r2), "호출 1 r2");
    assertPending(outbox.snapshot(r3), "호출 1 r3");

    // 잔여 후보 r3를 다른 경로에서 처리 완료로 만든다 → 진행 중 순회의 잔여 범위 (r2, r3]가 빈다
    assertThat(jdbcTemplate.update(
      "update outbox_event set status = 'PROCESSED', processed_at = now() where id = ? and status = 'PENDING'",
      r3.outboxId())).as("r3 처리 완료 표시").isEqualTo(1);

    // 상한 뒤 새 PENDING 3건
    Seeded r4 = outbox.seedPending();
    Seeded r5 = outbox.seedPending();
    Seeded r6 = outbox.seedPending();
    clearInvocations(itemProcessor);

    // 호출 2: 잔여 범위 0건 → 같은 호출에서 새 순회(상한 r6) 시작 → batch 2건(r4·r5)만 시도
    int second = relayService.relay();

    assertThat(second).as("호출 2 처리 건수 (같은 호출의 새 순회)").isEqualTo(2);
    verify(itemProcessor, times(2)).process(anyLong());
    verify(itemProcessor).process(r4.outboxId());
    verify(itemProcessor).process(r5.outboxId());
    verify(itemProcessor, never()).process(r6.outboxId());
    assertProcessed(outbox.snapshot(r4), "호출 2 r4");
    assertProcessed(outbox.snapshot(r5), "호출 2 r5");
    assertPending(outbox.snapshot(r6), "호출 2 r6 (batch 상한)");
  }

  private static void assertProcessed(Snapshot snapshot, String label) {
    assertThat(snapshot.notificationCount()).as("%s 알림 건수", label).isEqualTo(1L);
    assertThat(snapshot.status()).as("%s Outbox 상태", label).isEqualTo("PROCESSED");
  }

  private static void assertPending(Snapshot snapshot, String label) {
    assertThat(snapshot.notificationCount()).as("%s 알림 건수", label).isZero();
    assertThat(snapshot.status()).as("%s Outbox 상태", label).isEqualTo("PENDING");
  }
}
