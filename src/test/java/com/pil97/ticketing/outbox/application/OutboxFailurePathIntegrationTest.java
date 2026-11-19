package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.MutableClock;
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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TASK-033-1 (#115) AC-12 실제 실패 경로 통합 테스트 - 장애 주입기 없이 실제 DB 오류·payload 오류 (격리 MariaDB 컨테이너)
 *
 * <p>계약:
 * <ul>
 *   <li>[회귀 성격] 실패 항목만 rollback되고 PENDING으로 남으며, 같은 호출의 다음 항목은 처리된다 (TASK-033 F-4 후속 검증)</li>
 *   <li>[신규] 실패 항목에 실패 1회·오류 분류가 기록된다: UNIQUE 위반 DUPLICATE_KEY, FK 위반 FOREIGN_KEY, 깨진 payload PAYLOAD_INVALID</li>
 *   <li>SQL 오류의 기대 SQLState·vendor code·예외 타입은 classifier와 무관한 직접 JDBC probe(H-7)의 실제 관찰값이다.
 *       production이 저장한 값을 정답으로 삼지 않는다</li>
 * </ul>
 * 배치는 2건(실패 항목 → 정상 항목 순)이다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.batch-size=2",
  "outbox.relay.retry.max-attempts=5",
  "outbox.relay.retry.initial-backoff=30s",
  "outbox.relay.retry.max-backoff=10m"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, MutableClock.Config.class})
@DirtiesContext
class OutboxFailurePathIntegrationTest {

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
    clock.set(MutableClock.START);
  }

  @AfterEach
  void tearDown() {
    outbox.cleanup();
    fixture.cleanup();
  }

  private void relayAndAssertIsolation(Seeded failing, Seeded normal, long expectedFailingNotifications) {
    assertThatCode(() -> relayService.relay()).doesNotThrowAnyException();
    // 회귀 성격: 실패 항목만 rollback, 다음 항목 처리
    assertThat(outbox.snapshot(failing).status()).as("실패 항목 상태").isEqualTo("PENDING");
    assertThat(outbox.snapshot(failing).notificationCount()).as("실패 항목 알림").isEqualTo(expectedFailingNotifications);
    assertThat(outbox.snapshot(normal).status()).as("다음 항목 상태").isEqualTo("PROCESSED");
    assertThat(outbox.snapshot(normal).notificationCount()).as("다음 항목 알림").isEqualTo(1L);
  }

  /**
   * 실패 기록 단정 - SQL 오류 사례의 기대값은 classifier와 무관한 직접 JDBC 관찰(H-7: UNIQUE 23000/1062, FK 23000/1452,
   * 예외 클래스 SQLIntegrityConstraintViolationException)과 분류 규칙(가장 깊은 허용 목록 예외의 단순명)에서 정한다
   */
  private static void assertRecorded(RetryRow row, String code, String type, String sqlState, Integer vendorCode,
                                     String label) {
    System.out.println("AC12-OBSERVE " + label + " code=" + row.lastErrorCode() + " type=" + row.lastErrorType()
      + " sqlState=" + row.lastSqlState() + " vendor=" + row.lastVendorCode());
    assertThat(row.failedAttempts()).as("%s 기록된 실패", label).isEqualTo(1);
    assertThat(row.lastErrorCode()).as("%s 오류 분류", label).isEqualTo(code);
    if (type == null) {
      assertThat(row.lastErrorType()).as("%s 오류 타입", label).isNotNull();
    } else {
      assertThat(row.lastErrorType()).as("%s 오류 타입", label).isEqualTo(type);
    }
    assertThat(row.lastSqlState()).as("%s SQLState", label).isEqualTo(sqlState);
    assertThat(row.lastVendorCode()).as("%s vendor code", label).isEqualTo(vendorCode);
  }

  @Test
  @DisplayName("AC-12 UNIQUE 위반(알림 선존재): 해당 항목만 rollback·PENDING·알림 1 유지, DUPLICATE_KEY 기록, 다음 항목 처리")
  void ac12_uniqueViolation() {
    Seeded failing = outbox.seedPending();
    outbox.insertNotification(failing);
    Seeded normal = outbox.seedPending();

    relayAndAssertIsolation(failing, normal, 1L);
    assertRecorded(outbox.retryRow(failing.outboxId()), "DUPLICATE_KEY", "SQLIntegrityConstraintViolationException",
      "23000", 1062, "unique");
  }

  @Test
  @DisplayName("AC-12 FK 위반(알림 없는 결제 삭제): 해당 항목만 rollback·PENDING·알림 0, FOREIGN_KEY 기록, 다음 항목 처리")
  void ac12_foreignKeyViolation() {
    Seeded failing = outbox.seedPending();
    outbox.deletePayment(failing.paymentId());
    Seeded normal = outbox.seedPending();

    relayAndAssertIsolation(failing, normal, 0L);
    assertRecorded(outbox.retryRow(failing.outboxId()), "FOREIGN_KEY", "SQLIntegrityConstraintViolationException",
      "23000", 1452, "fk");
  }

  @Test
  @DisplayName("AC-12 깨진 payload: 해당 항목만 rollback·PENDING·알림 0, PAYLOAD_INVALID 기록(SQL 정보 없음), 다음 항목 처리")
  void ac12_brokenPayload() {
    Seeded failing = outbox.seedPendingWithPayload("{\"paymentId\": not-json");
    Seeded normal = outbox.seedPending();

    relayAndAssertIsolation(failing, normal, 0L);
    // payload 사례의 Jackson 예외 클래스는 실제 관찰 전이라 타입은 존재만 단정한다
    assertRecorded(outbox.retryRow(failing.outboxId()), "PAYLOAD_INVALID", null, null, null, "payload");
  }
}
