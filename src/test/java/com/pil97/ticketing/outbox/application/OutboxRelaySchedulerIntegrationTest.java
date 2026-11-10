package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.outbox.application.scheduler.OutboxRelayScheduler;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.application.PaymentService;
import com.pil97.ticketing.queue.application.scheduler.QueueScheduler;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Snapshot;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.payRequest;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033 (#108) 독립 검토 후속 - 스케줄러 자동 폴링 경로 통합 테스트 (격리 MariaDB 컨테이너·실제 Redis)
 *
 * <p>test profile은 relay 스케줄러를 끈다 (outbox.relay.enabled=false, 기존 테스트 클래스들의 assertNoScheduledTasks가 계속 검증).
 * 이 클래스만 outbox.relay.enabled=true를 명시해 실제 @Scheduled 등록과 자동 폴링을 확인한다.
 * HOLD 만료·대기열 스케줄러는 SchedulerExclusionConfig로 제거한다.
 *
 * <p>진입점: 결제는 서비스 진입점(PaymentService.pay)으로 커밋한다 (HTTP API 아님).
 * 테스트는 OutboxRelayService.relay()나 OutboxItemProcessor.process()를 직접 호출하지 않고,
 * 스케줄러가 처리한 결과를 제한 시간 안의 조건 대기로 관찰한다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.enabled=true",
  "outbox.relay.fixed-delay-ms=200"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class})
@DirtiesContext
class OutboxRelaySchedulerIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";
  private static final Duration TIMEOUT = Duration.ofSeconds(15);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private PaymentService paymentService;
  @Autowired private ApplicationContext context;

  @AfterEach
  void tearDown() {
    outbox.cleanup();
    fixture.cleanup();
  }

  @Test
  @DisplayName("스케줄러 활성: 서비스 진입점으로 커밋한 결제의 알림 1건과 Outbox PROCESSED를 직접 호출 없이 자동 폴링으로 관찰한다")
  void schedulerEnabled_committedPaymentIsRelayedWithoutDirectCall() {
    // 스케줄러 등록: relay 스케줄러 bean과 그 @Scheduled 작업 1건만 있다 (HOLD 만료·대기열은 제거)
    assertThat(outbox.currentDatabase()).as("격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);
    assertThat(context.getBeanNamesForType(OutboxRelayScheduler.class)).as("relay 스케줄러 bean").hasSize(1);
    assertThat(context.getBeanNamesForType(HoldExpirationScheduler.class)).as("HOLD 만료 스케줄러 bean").isEmpty();
    assertThat(context.getBeanNamesForType(QueueScheduler.class)).as("대기열 스케줄러 bean").isEmpty();
    List<ScheduledTask> tasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
      .flatMap(holder -> holder.getScheduledTasks().stream())
      .toList();
    assertThat(tasks).as("등록된 @Scheduled 작업").hasSize(1);
    // Spring 6.2는 실행 결과 추적용 Runnable로 감싸며, 문자열 표현은 대상 메서드(클래스명.메서드명)다
    assertThat(tasks.get(0).getTask().getRunnable().toString())
      .as("relay 스케줄 작업 대상 메서드")
      .isEqualTo(OutboxRelayScheduler.class.getName() + ".relay");

    // 서비스 진입점으로 정상 결제 커밋
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(1));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    outbox.trackReservation(reservationId);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(reservationId, false)).getResponse();
    assertThat(response.status()).isEqualTo("SUCCESS");
    Long paymentId = response.paymentId();
    List<Map<String, Object>> rows = outbox.outboxRowsOfPayment(paymentId);
    assertThat(rows).as("결제 커밋 후 Outbox").hasSize(1);
    Long outboxId = ((Number) rows.get(0).get("id")).longValue();

    // 직접 호출 없이 스케줄러가 처리할 때까지 조건 대기
    Snapshot processed = awaitSnapshot(() -> outbox.snapshot(outboxId, paymentId),
      s -> "PROCESSED".equals(s.status()) && s.notificationCount() == 1L);

    assertThat(processed.notificationCount()).as("자동 폴링 후 알림 건수").isEqualTo(1L);
    assertThat(processed.status()).as("자동 폴링 후 Outbox 상태").isEqualTo("PROCESSED");
    assertThat(processed.processedAt()).isNotNull();
    Map<String, Object> notification = outbox.notificationsOfPayment(paymentId).get(0);
    assertThat(notification.get("member_id")).isEqualTo(member.getId());
    assertThat(notification.get("outbox_event_id")).isEqualTo(outboxId);
  }

  /**
   * 조건이 참이 될 때까지 짧은 간격으로 다시 읽는다 - 제한 시간을 넘기면 마지막 관찰값과 함께 실패
   */
  private static Snapshot awaitSnapshot(Supplier<Snapshot> read, java.util.function.Predicate<Snapshot> done) {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    Snapshot last = read.get();
    while (!done.test(last)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("자동 폴링 결과를 " + TIMEOUT.toSeconds() + "초 안에 관찰하지 못했다. 마지막 관찰: " + last);
      }
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("조건 대기 중 interrupt", e);
      }
      last = read.get();
    }
    return last;
  }
}
