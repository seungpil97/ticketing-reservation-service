package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.TicketingReservationApplication;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.outbox.OutboxFixture.Snapshot;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033 (#108) 새 애플리케이션 context에서의 Outbox 복구 통합 테스트 (격리 MariaDB 컨테이너·실제 Redis)
 *
 * <p>context A가 PENDING Outbox를 커밋하고 처리하지 않은 채 종료한 뒤,
 * 같은 격리 DB를 쓰는 독립 context B가 폴링으로 처리한다.
 * B는 A의 메모리 상태를 공유하지 않으므로 처리 근거는 DB에 커밋된 행뿐이다.
 *
 * <p>A·B는 @SpringBootTest가 아닌 직접 띄운 context다 (test profile, web 없음, 작은 connection pool).
 * HOLD 만료·대기열 스케줄러는 SchedulerExclusionConfig로 제거하고, relay 스케줄러는 test profile property로 끈다.
 * 격리 DB는 이 클래스의 컨테이너가 폐기하므로 행 정리를 하지 않는다 (시드는 Redis key를 만들지 않는다).
 */
@Testcontainers
class OutboxRecoveryIntegrationTest {

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  private static ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(TicketingReservationApplication.class, SchedulerExclusionConfig.class,
      ConsistencyFixture.class, OutboxFixture.class)
      .web(WebApplicationType.NONE)
      .run(IsolatedMariaDb.args(DB,
        "--spring.profiles.active=test",
        "--spring.datasource.hikari.minimum-idle=1",
        "--spring.datasource.hikari.maximum-pool-size=2"));
  }

  @Test
  @DisplayName("AC-7 context A가 커밋한 PENDING Outbox를 A 종료 후 새로 시작한 context B가 처리한다")
  void ac7_newContextProcessesPendingCommittedByClosedContext() throws Exception {
    // context A: 결제 완료 상태와 PENDING Outbox 커밋, 처리하지 않고 종료
    ConfigurableApplicationContext a = start();
    Seeded seeded;
    HikariDataSource aPool;
    try {
      SchedulerExclusionConfig.assertNoScheduledTasks(a);
      OutboxFixture aOutbox = a.getBean(OutboxFixture.class);
      assertThat(aOutbox.currentDatabase()).as("A 격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);
      seeded = aOutbox.seedPending();
      aPool = a.getBean(DataSource.class).unwrap(HikariDataSource.class);
    } finally {
      a.close();
    }
    long aClosedAt = System.currentTimeMillis();
    assertThat(a.isActive()).as("A 종료").isFalse();
    assertThat(aPool.isClosed()).as("A connection pool 종료").isTrue();

    // context B: 같은 격리 DB로 새로 시작해 폴링
    ConfigurableApplicationContext b = start();
    try {
      assertThat(b).isNotSameAs(a);
      assertThat(b.isActive()).as("B 활성").isTrue();
      assertThat(b.getStartupDate()).as("B는 A 종료 후 시작").isGreaterThanOrEqualTo(aClosedAt);
      SchedulerExclusionConfig.assertNoScheduledTasks(b);
      OutboxFixture bOutbox = b.getBean(OutboxFixture.class);
      assertThat(bOutbox.currentDatabase()).as("B 격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);
      Snapshot before = bOutbox.snapshot(seeded);
      assertThat(before.status()).as("B 시작 직후 Outbox 상태").isEqualTo("PENDING");
      assertThat(before.notificationCount()).as("B 시작 직후 알림 건수").isZero();

      b.getBean(OutboxRelayService.class).relay();

      Snapshot after = bOutbox.snapshot(seeded);
      assertThat(after.notificationCount()).as("B 폴링 후 알림 건수").isEqualTo(1L);
      assertThat(after.status()).as("B 폴링 후 Outbox 상태").isEqualTo("PROCESSED");
      assertThat(after.processedAt()).isNotNull();
    } finally {
      b.close();
    }
  }
}
