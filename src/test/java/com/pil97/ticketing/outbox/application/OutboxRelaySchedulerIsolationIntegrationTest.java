package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Snapshot;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033 (#108) F-1 - relay가 DB 잠금에서 대기해도 같은 기본 스케줄러의 다른 스케줄 작업이 진행되는지 (격리 MariaDB 컨테이너)
 *
 * <p>스케줄러 풀 크기는 덮어쓰지 않는다 (application-common.yml 값 사용).
 * relay 스케줄러를 켜고(outbox.relay.enabled=true) HOLD 만료·대기열은 제거한 뒤,
 * 같은 기본 스케줄러에서 도는 테스트 전용 카운터 작업(@Scheduled fixedDelay 100ms)의 진행을 관찰한다.
 *
 * <p>relay 대기 만들기: 결제 행을 별도 트랜잭션에서 FOR UPDATE로 잠근 뒤 PENDING Outbox를 커밋한다.
 * relay 항목 트랜잭션은 Outbox 행을 잠그고 알림 INSERT의 FK 검사에서 그 결제 행을 기다린다
 * (잠금 이전에 Outbox가 없으므로 relay가 먼저 처리해 버리는 경합이 없다).
 * 대기 판정은 INNODB_LOCK_WAITS·INNODB_TRX·INNODB_LOCKS로 대기자(알림 INSERT 중인 트랜잭션)·차단자(테스트 잠금 connection)·
 * 대상 행(payment 기본키 = 결제 ID)을 연결해서만 인정한다. 관찰은 컨테이너 root connection으로 한다 (PROCESS 권한).
 *
 * <p>관찰 권한·잠금 대기 제한 시간 확인 실패, relay 대기 관찰 실패는 HarnessException이다 (RED로 집계하지 않는다).
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.enabled=true",
  "outbox.relay.fixed-delay-ms=200"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class,
  OutboxRelaySchedulerIsolationIntegrationTest.CounterConfig.class})
@DirtiesContext
class OutboxRelaySchedulerIsolationIntegrationTest {

  private static final Duration WAITER_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration PROGRESS_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
  // InnoDB의 information_schema 잠금 표는 마지막 읽기 후 0.1초가 지나야 갱신되므로 관찰 간격을 그보다 길게 둔다
  private static final Duration OBSERVE_INTERVAL = Duration.ofMillis(250);
  private static final int MIN_PROGRESS = 3;
  // 관찰 창(최대 PROGRESS_TIMEOUT + 대기자 확인)보다 충분히 길어야 relay 대기가 관찰 중 timeout으로 끝나지 않는다
  private static final int MIN_LOCK_WAIT_TIMEOUT_SECONDS = 30;

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private SchedulingCounter counter;
  @Autowired private ApplicationContext context;

  private final List<Long> paymentIds = new ArrayList<>();

  @TestConfiguration(proxyBeanMethods = false)
  static class CounterConfig {
    @Bean
    SchedulingCounter schedulingCounter() {
      return new SchedulingCounter();
    }
  }

  /**
   * 같은 기본 스케줄러에서 도는 카운터 작업 (HOLD 만료·대기열 같은 다른 스케줄 작업의 대리)
   */
  static class SchedulingCounter {
    private final AtomicInteger ticks = new AtomicInteger();

    @Scheduled(fixedDelay = 100)
    void tick() {
      ticks.incrementAndGet();
    }

    int ticks() {
      return ticks.get();
    }
  }

  private record Blocked(String requestingTrxId, String query) {
  }

  @AfterEach
  void tearDown() {
    // relay가 잠금 해제 뒤 처리 중일 수 있으므로 끝날 때까지 기다린 뒤 정리한다 (실패 판정 아님)
    for (Long paymentId : paymentIds) {
      awaitCondition(() -> pendingOutboxCount(paymentId) == 0, RECOVERY_TIMEOUT);
      jdbcTemplate.update("delete from notification where payment_id = ?", paymentId);
      jdbcTemplate.update("delete from outbox_event where event_type = ? and aggregate_id = ?",
        OutboxFixture.EVENT_TYPE, paymentId);
    }
    paymentIds.clear();
    fixture.cleanup();
  }

  @Test
  @DisplayName("F-1 relay가 결제 행 잠금에서 대기하는 동안에도 같은 기본 스케줄러의 다른 작업이 진행되고, 잠금 해제 후 알림 1건·PROCESSED")
  void otherScheduledTaskProgressesWhileRelayWaitsOnLock() throws Exception {
    assertThat(outbox.currentDatabase()).as("격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);

    try (Connection observer = rootConnection()) {
      // 관찰 권한·잠금 대기 제한 시간 사전 확인
      int lockWaitTimeout = queryInt(observer, "select @@global.innodb_lock_wait_timeout");
      if (lockWaitTimeout < MIN_LOCK_WAIT_TIMEOUT_SECONDS) {
        throw HarnessException.precondition("innodb_lock_wait_timeout too short: " + lockWaitTimeout);
      }
      queryInt(observer, "select count(*) from information_schema.innodb_trx");
      queryInt(observer, "select count(*) from information_schema.innodb_lock_waits");

      // 결제 완료 상태 (Outbox 없음)
      Paid paid = seedPaid();
      Long outboxId;

      Blocked blocked;
      try (Connection locker = rootConnection()) {
        locker.setAutoCommit(false);
        try {
          long lockerThreadId = queryLong(locker, "select connection_id()");
          try (PreparedStatement ps = locker.prepareStatement("select id from payment where id = ? for update")) {
            ps.setLong(1, paid.paymentId());
            ps.executeQuery().close();
          }

          // 잠금 뒤 PENDING Outbox 커밋 → 스케줄러 relay가 집어 알림 INSERT의 FK 검사에서 대기
          outboxId = insertPendingOutbox(paid);
          blocked = awaitRelayWaitingOn(observer, lockerThreadId, paid.paymentId());

          // relay 대기 중 같은 기본 스케줄러의 카운터 작업 진행
          int before = counter.ticks();
          boolean progressed = awaitCondition(() -> counter.ticks() >= before + MIN_PROGRESS, PROGRESS_TIMEOUT);
          int after = counter.ticks();
          Blocked stillBlocked = findRelayWaiter(observer, lockerThreadId, paid.paymentId());

          assertThat(progressed)
            .as("relay 대기 중 카운터 작업 진행 (%s초 안에 %d회 이상, 실제 %d → %d)",
              PROGRESS_TIMEOUT.toSeconds(), MIN_PROGRESS, before, after)
            .isTrue();
          assertThat(stillBlocked).as("관찰 직후에도 relay가 같은 결제 행 잠금에서 대기").isNotNull();
          assertThat(stillBlocked.requestingTrxId()).isEqualTo(blocked.requestingTrxId());
        } finally {
          locker.rollback();
        }
      }

      // 잠금 해제 후 relay가 처리를 마친다
      awaitCondition(() -> {
        Snapshot snapshot = outbox.snapshot(outboxId, paid.paymentId());
        return "PROCESSED".equals(snapshot.status()) && snapshot.notificationCount() == 1L;
      }, RECOVERY_TIMEOUT);
      Snapshot snapshot = outbox.snapshot(outboxId, paid.paymentId());
      assertThat(snapshot.notificationCount()).as("잠금 해제 후 알림 건수").isEqualTo(1L);
      assertThat(snapshot.status()).as("잠금 해제 후 Outbox 상태").isEqualTo("PROCESSED");
    }

    // 자동 설정 taskScheduler의 실제 풀 크기 (application-common.yml)
    ThreadPoolTaskScheduler taskScheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
    assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).as("기본 스케줄러 풀 크기").isEqualTo(3);
  }

  private record Paid(Long paymentId, Long reservationId, Long memberId) {
  }

  private Paid seedPaid() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.CONFIRMED, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.CONFIRMED);
    Long paymentId = fixture.payment(reservationId, "SUCCESS", nowSeconds());
    paymentIds.add(paymentId);
    return new Paid(paymentId, reservationId, member.getId());
  }

  /**
   * OutboxFixture와 같은 계약 값으로 PENDING Outbox 저장 (autocommit)
   *
   * @return Outbox ID
   */
  private Long insertPendingOutbox(Paid paid) {
    String payload = "{\"paymentId\":" + paid.paymentId() + ",\"reservationId\":" + paid.reservationId()
      + ",\"memberId\":" + paid.memberId() + "}";
    jdbcTemplate.update(
      "insert into outbox_event (event_type, aggregate_id, payload, status) values (?, ?, ?, 'PENDING')",
      OutboxFixture.EVENT_TYPE, paid.paymentId(), payload);
    return jdbcTemplate.queryForObject(
      "select id from outbox_event where event_type = ? and aggregate_id = ?",
      Long.class, OutboxFixture.EVENT_TYPE, paid.paymentId());
  }

  private long pendingOutboxCount(Long paymentId) {
    return jdbcTemplate.queryForObject(
      "select count(*) from outbox_event where event_type = ? and aggregate_id = ? and status = 'PENDING'",
      Long.class, OutboxFixture.EVENT_TYPE, paymentId);
  }

  /**
   * 대기자·차단자·대상 행을 연결해 relay 대기를 확인 - 제한 시간 안에 관찰하지 못하면 HarnessException
   */
  private Blocked awaitRelayWaitingOn(Connection observer, long lockerThreadId, Long paymentId) {
    Blocked[] found = new Blocked[1];
    String[] early = new String[1];
    long earlyAt = System.nanoTime() + Duration.ofSeconds(1).toNanos();
    boolean observed = awaitCondition(() -> {
      found[0] = findRelayWaiter(observer, lockerThreadId, paymentId);
      if (found[0] == null && early[0] == null && System.nanoTime() > earlyAt) {
        early[0] = diagnostics(observer);
      }
      return found[0] != null;
    }, WAITER_TIMEOUT, OBSERVE_INTERVAL);
    if (!observed) {
      throw HarnessException.harness("relay lock wait on payment " + paymentId + " was not observed within "
        + WAITER_TIMEOUT.toSeconds() + "s. lockerThreadId=" + lockerThreadId
        + ", diagnostics@1s=" + early[0] + ", diagnostics@end=" + diagnostics(observer));
    }
    return found[0];
  }

  /**
   * 관찰 실패 진단용 - 현재 트랜잭션·잠금 대기·잠금 목록
   */
  private static String diagnostics(Connection observer) {
    StringBuilder out = new StringBuilder();
    for (String sql : List.of(
      "select id, command, time, state, info from information_schema.processlist where command <> 'Sleep'",
      "select trx_id, trx_state, trx_mysql_thread_id, trx_query from information_schema.innodb_trx",
      "select requesting_trx_id, requested_lock_id, blocking_trx_id, blocking_lock_id from information_schema.innodb_lock_waits",
      "select lock_id, lock_trx_id, lock_mode, lock_type, lock_table, lock_index, lock_data from information_schema.innodb_locks")) {
      out.append("\n[").append(sql).append("]");
      try (Statement st = observer.createStatement(); ResultSet rs = st.executeQuery(sql)) {
        int columns = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          out.append("\n  ");
          for (int i = 1; i <= columns; i++) {
            out.append(rs.getMetaData().getColumnLabel(i)).append('=').append(rs.getString(i)).append(' ');
          }
        }
      } catch (SQLException e) {
        out.append("\n  error: ").append(e.getMessage());
      }
    }
    return out.toString();
  }

  /**
   * 차단자가 테스트 잠금 connection이고, 대기 잠금이 payment 기본키 = 결제 ID이며, 대기자가 알림 INSERT 중인 트랜잭션
   */
  private Blocked findRelayWaiter(Connection observer, long lockerThreadId, Long paymentId) {
    String sql = """
      select r.trx_id, r.trx_query, l.lock_table, l.lock_index, l.lock_data
      from information_schema.innodb_lock_waits w
      join information_schema.innodb_trx r on r.trx_id = w.requesting_trx_id
      join information_schema.innodb_trx b on b.trx_id = w.blocking_trx_id
      join information_schema.innodb_locks l on l.lock_id = w.requested_lock_id
      where b.trx_mysql_thread_id = ? and r.trx_state = 'LOCK WAIT'
      """;
    try (PreparedStatement ps = observer.prepareStatement(sql)) {
      ps.setLong(1, lockerThreadId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String table = rs.getString("lock_table");
          String index = rs.getString("lock_index");
          String data = rs.getString("lock_data");
          String query = rs.getString("trx_query");
          boolean paymentRow = table != null && table.endsWith("`payment`") && "PRIMARY".equals(index)
            && String.valueOf(paymentId).equals(data);
          boolean notificationInsert = query != null
            && query.toLowerCase().replaceAll("\\s+", " ").contains("insert into notification");
          if (paymentRow && notificationInsert) {
            return new Blocked(rs.getString("trx_id"), query);
          }
        }
      }
      return null;
    } catch (SQLException e) {
      throw HarnessException.harness("lock wait observation failed", e);
    }
  }

  private static Connection rootConnection() throws SQLException {
    try {
      return DriverManager.getConnection(DB.getJdbcUrl(), "root", DB.getPassword());
    } catch (SQLException e) {
      throw HarnessException.harness("observer connection failed", e);
    }
  }

  private static int queryInt(Connection connection, String sql) {
    return (int) queryLong(connection, sql);
  }

  private static long queryLong(Connection connection, String sql) {
    try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException e) {
      throw HarnessException.harness("observation query failed: " + sql, e);
    }
  }

  /**
   * 조건이 참이 될 때까지 짧은 간격으로 확인 - 제한 시간 안에 참이 되면 true
   */
  private static boolean awaitCondition(BooleanSupplier condition, Duration timeout) {
    return awaitCondition(condition, timeout, POLL_INTERVAL);
  }

  private static boolean awaitCondition(BooleanSupplier condition, Duration timeout, Duration interval) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      try {
        Thread.sleep(interval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }
}
