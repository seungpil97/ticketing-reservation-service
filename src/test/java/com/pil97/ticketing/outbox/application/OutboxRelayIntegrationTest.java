package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.OutboxFaultInterceptor;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.outbox.OutboxFixture.Snapshot;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-033 (#108) Outbox relay·소비 통합 테스트 (격리 MariaDB 컨테이너·실제 Redis)
 *
 * <p>소비 계약:
 * <ul>
 *   <li>커밋된 PENDING Outbox를 처리하면 결제 건당 알림 1건과 처리 완료(PROCESSED)가 함께 관찰된다</li>
 *   <li>알림 기록과 처리 완료 표시는 함께 반영되거나 함께 반영되지 않는다 (실패 시 PENDING 유지, 다음 처리에서 재시도)</li>
 *   <li>같은 이벤트가 소비 경로(OutboxItemProcessor.process)에 다시 전달되어도 알림은 1건이다</li>
 *   <li>폴러는 처리 완료 행에 대해 소비 경로를 호출하지 않는다</li>
 * </ul>
 *
 * <p>폴링 순회 계약 (batch = outbox.relay.batch-size, 이 클래스는 2):
 * <ul>
 *   <li>순회 시작 시 그 시점의 최대 PENDING ID를 순회 상한으로 고정하고, 상한 이하 PENDING을 ID 오름차순으로 순회한다</li>
 *   <li>폴링 호출 1회는 최대 batch건을 시도한다. 순회 범위에서 직전 위치 뒤의 PENDING을 고르고,
 *       성공·실패와 관계없이 위치는 전진한다</li>
 *   <li>시도한 마지막 ID가 상한에 도달하거나 batch보다 적게 선택되면 순회가 끝나고, 다음 호출이 새 순회를 시작한다</li>
 *   <li>진행 중 순회의 남은 범위가 비어 있으면(선택 0건) 같은 호출에서 새 순회를 최대 한 번 시작한다</li>
 *   <li>상한 뒤에 추가된 PENDING은 다음 순회에서 다뤄지므로, 실패한 앞쪽 행은 큰 ID가 계속 추가되어도 다음 순회에서 다시 시도된다</li>
 * </ul>
 *
 * <p>시작 상태는 결제 서비스를 거치지 않고 만든다 (결제 완료 행 + JDBC로 넣은 PENDING Outbox).
 * 격리 DB에는 이 클래스가 만든 행만 있고, 각 테스트는 자기 행을 정리해 다음 테스트 시작 시 PENDING이 없다.
 * 최종 상태는 JDBC autocommit 조회로 트랜잭션 종료 후 별도로 읽는다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.batch-size=2",
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.outbox.OutboxFaultInterceptor"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class})
@DirtiesContext
class OutboxRelayIntegrationTest {

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
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    assertThat(outbox.currentDatabase()).as("격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);
    SqlObserver.clear();
    OutboxFaultInterceptor.reset();
  }

  @AfterEach
  void tearDown() {
    OutboxFaultInterceptor.reset();
    outbox.cleanup();
    fixture.cleanup();
    SqlObserver.clear();
  }

  private static void assertProcessed(Snapshot snapshot, String label) {
    assertThat(snapshot.notificationCount()).as("%s 알림 건수", label).isEqualTo(1L);
    assertThat(snapshot.status()).as("%s Outbox 상태", label).isEqualTo("PROCESSED");
    assertThat(snapshot.processedAt()).as("%s 처리 시각", label).isNotNull();
  }

  private static void assertPending(Snapshot snapshot, String label) {
    assertThat(snapshot.notificationCount()).as("%s 알림 건수", label).isZero();
    assertThat(snapshot.status()).as("%s Outbox 상태", label).isEqualTo("PENDING");
    assertThat(snapshot.processedAt()).as("%s 처리 시각", label).isNull();
  }

  // ===================== AC-4 커밋된 PENDING 처리 =====================

  @Test
  @DisplayName("AC-4 커밋된 PENDING Outbox 폴링 1회: 결제 건당 알림 1건과 처리 완료가 함께 관찰된다")
  void ac4_relay_processesPendingIntoOneNotification() {
    Seeded s = outbox.seedPending();

    int processed = relayService.relay();

    Snapshot snapshot = outbox.snapshot(s);
    assertProcessed(snapshot, "폴링 후");
    Map<String, Object> notification = outbox.notificationsOfPayment(s.paymentId()).get(0);
    assertThat(notification.get("member_id")).isEqualTo(s.memberId());
    assertThat(notification.get("outbox_event_id")).isEqualTo(s.outboxId());
    assertThat(notification.get("type")).isEqualTo(OutboxFixture.NOTIFICATION_TYPE);
    assertThat(processed).as("처리 완료 건수").isEqualTo(1);
  }

  // ===================== AC-5 소비 경로 재전달 =====================

  @Test
  @DisplayName("AC-5 처리 완료된 이벤트가 소비 경로에 다시 전달되어도 알림은 1건, 처리 시각 유지, 예외 없음")
  void ac5_redeliveredToConsumer_keepsSingleNotification() {
    Seeded s = outbox.seedPending();
    relayService.relay();
    Snapshot first = outbox.snapshot(s);
    assertProcessed(first, "전제: 첫 폴링 후");

    boolean again = itemProcessor.process(s.outboxId());

    assertThat(again).as("재전달 처리 결과 (건너뜀)").isFalse();
    Snapshot after = outbox.snapshot(s);
    assertProcessed(after, "재전달 후");
    assertThat(after.processedAt()).as("처리 시각 유지").isEqualTo(first.processedAt());
  }

  // ===================== AC-5S 폴러 선택 규칙 =====================

  @Test
  @DisplayName("AC-5S 처리 완료 행은 폴링 후보가 아니다: 소비 경로 호출 0회, 알림 1건 유지")
  void ac5s_poller_doesNotInvokeConsumerForProcessedRow() {
    Seeded s = outbox.seedPending();
    relayService.relay();
    Snapshot first = outbox.snapshot(s);
    assertProcessed(first, "전제: 첫 폴링 후");
    clearInvocations(itemProcessor);

    relayService.relay();

    verify(itemProcessor, never()).process(s.outboxId());
    Snapshot after = outbox.snapshot(s);
    assertProcessed(after, "두 번째 폴링 후");
    assertThat(after.processedAt()).isEqualTo(first.processedAt());
  }

  // ===================== AC-6 처리 완료 표시 단계 실패 =====================

  @Test
  @DisplayName("AC-6 알림 기록 이후 처리 완료 표시 단계 장애: 알림 롤백·PENDING 유지, 다음 폴링에서 알림 1건과 처리 완료")
  void ac6_failureAfterNotificationInsert_rollsBackAndRetries() {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());

    assertThatCode(() -> relayService.relay()).as("항목 장애는 폴링 밖으로 전파되지 않는다").doesNotThrowAnyException();

    // 장애가 알림 INSERT 이후 처리 완료 표시 단계에서 주입됐다
    assertThat(OutboxFaultInterceptor.injectedCount(s.outboxId())).as("장애 주입 횟수").isEqualTo(1);
    assertThat(OutboxFaultInterceptor.injections(s.outboxId()).get(0).notificationInsertedBefore())
      .as("주입 직전 같은 처리에서 알림 INSERT 실행").isTrue();
    // 처리 트랜잭션 종료 후 별도 조회: 부분 반영 없음
    assertPending(outbox.snapshot(s), "실패 시도 후");
    assertThat(fixture.paymentRow(s.paymentId()).get("status")).isEqualTo("SUCCESS");
    assertThat(fixture.reservationStatus(s.reservationId())).isEqualTo("CONFIRMED");

    relayService.relay();
    Snapshot retried = outbox.snapshot(s);
    assertProcessed(retried, "재처리 후");

    relayService.relay();
    assertThat(outbox.snapshot(s).notificationCount()).as("추가 폴링 후 알림 건수").isEqualTo(1L);
    assertThat(OutboxFaultInterceptor.injectedCount(s.outboxId())).isEqualTo(1);
  }

  // ===================== AC-6B 폴링 진행 보장 =====================

  @Test
  @DisplayName("AC-6B-1 같은 배치 안의 실패 건은 뒤 정상 건을 막지 않는다 (폴링 1회)")
  void ac6b1_failureIsolatedWithinBatch() {
    Seeded r1 = outbox.seedPending();
    Seeded r2 = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(r1.outboxId());

    relayService.relay();

    assertThat(OutboxFaultInterceptor.injectedCount(r1.outboxId())).as("r1 시도(장애) 횟수").isEqualTo(1);
    assertPending(outbox.snapshot(r1), "r1");
    assertProcessed(outbox.snapshot(r2), "r2");
  }

  @Test
  @DisplayName("AC-6B-2 배치 크기만큼 앞쪽 실패가 있어도 배치 뒤 정상 건은 다음 폴링에서 처리되고, 실패 건은 다음 순회에서 재시도된다")
  void ac6b2_rowBeyondBatchProcessedAndFailuresRetriedNextSweep() {
    Seeded r1 = outbox.seedPending();
    Seeded r2 = outbox.seedPending();
    Seeded r3 = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(r1.outboxId());
    OutboxFaultInterceptor.failAlways(r2.outboxId());

    // 폴링 1: r1·r2 시도(장애)
    relayService.relay();
    assertThat(OutboxFaultInterceptor.injectedCount(r1.outboxId())).as("폴링 1 r1 시도").isEqualTo(1);
    assertThat(OutboxFaultInterceptor.injectedCount(r2.outboxId())).as("폴링 1 r2 시도").isEqualTo(1);
    assertPending(outbox.snapshot(r1), "폴링 1 r1");
    assertPending(outbox.snapshot(r2), "폴링 1 r2");
    assertPending(outbox.snapshot(r3), "폴링 1 r3");

    // 폴링 2: 배치 경계 뒤의 r3 처리, r1·r2는 이 폴링에서 다시 시도하지 않는다
    relayService.relay();
    assertProcessed(outbox.snapshot(r3), "폴링 2 r3");
    assertThat(OutboxFaultInterceptor.injectedCount(r1.outboxId())).as("폴링 2 r1 시도 누계").isEqualTo(1);
    assertThat(OutboxFaultInterceptor.injectedCount(r2.outboxId())).as("폴링 2 r2 시도 누계").isEqualTo(1);
    assertPending(outbox.snapshot(r1), "폴링 2 r1");
    assertPending(outbox.snapshot(r2), "폴링 2 r2");

    // 폴링 3 (장애 해제): 새 순회에서 r1·r2 재시도 성공
    OutboxFaultInterceptor.disarm();
    relayService.relay();
    assertProcessed(outbox.snapshot(r1), "폴링 3 r1");
    assertProcessed(outbox.snapshot(r2), "폴링 3 r2");
    assertProcessed(outbox.snapshot(r3), "폴링 3 r3");
  }

  @Test
  @DisplayName("AC-6B-3 순회 중 더 큰 ID가 계속 추가되어도 앞쪽 실패 건은 다음 순회에서 다시 선택된다")
  void ac6b3_frontFailureReselectedWhileNewerRowsKeepArriving() {
    Seeded r1 = outbox.seedPending();
    Seeded r2 = outbox.seedPending();
    Seeded r3 = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(r1.outboxId());

    // 폴링 1 (순회 상한 r3): r1 시도(장애), r2 처리
    relayService.relay();
    assertThat(OutboxFaultInterceptor.injectedCount(r1.outboxId())).as("폴링 1 r1 시도").isEqualTo(1);
    assertPending(outbox.snapshot(r1), "폴링 1 r1");
    assertProcessed(outbox.snapshot(r2), "폴링 1 r2");

    // 폴링 2 전 r4 추가: 상한(r3)까지 처리하고 순회 종료, r4는 이번 순회 대상이 아니다
    Seeded r4 = outbox.seedPending();
    relayService.relay();
    assertProcessed(outbox.snapshot(r3), "폴링 2 r3");
    assertPending(outbox.snapshot(r4), "폴링 2 r4");
    assertThat(OutboxFaultInterceptor.injectedCount(r1.outboxId())).as("폴링 2 r1 시도 누계").isEqualTo(1);

    // 폴링 3 전 r5 추가: 새 순회(상한 r5)가 앞쪽 실패 건 r1부터 다시 선택한다
    Seeded r5 = outbox.seedPending();
    relayService.relay();
    assertThat(OutboxFaultInterceptor.injectedCount(r1.outboxId())).as("폴링 3 r1 재시도 누계").isEqualTo(2);
    assertPending(outbox.snapshot(r1), "폴링 3 r1");
    assertProcessed(outbox.snapshot(r4), "폴링 3 r4");
    assertPending(outbox.snapshot(r5), "폴링 3 r5");
  }
}
