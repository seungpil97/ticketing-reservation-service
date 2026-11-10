package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.infra.idempotency.IdempotencyResult;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.error.PaymentErrorCode;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.event.PaymentEventProbe;
import com.pil97.ticketing.support.event.PaymentEventProbe.Captured;
import com.pil97.ticketing.support.event.PaymentEventProbe.InjectedListenerFailure;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.assertBusinessError;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.payRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-033 (#108) 결제 성공 Outbox 저장 통합 테스트 (공유 테스트 DB·실제 Redis·실제 Spring 이벤트)
 *
 * <p>Outbox 저장 계약:
 * <ul>
 *   <li>결제 성공이 커밋되면 해당 결제의 Outbox(PAYMENT_SUCCEEDED) 1건이 PENDING으로 남는다</li>
 *   <li>Outbox 저장 이후 같은 결제 트랜잭션이 롤백되면 Outbox·결제가 남지 않는다</li>
 *   <li>결제 실패·검증 거절에서는 Outbox·알림이 생성되지 않고, 멱등 재요청은 추가로 생성하지 않는다</li>
 * </ul>
 *
 * <p>relay는 이 클래스에서 실행하지 않는다 (알림 생성·폴링은 격리 DB의 OutboxRelayIntegrationTest).
 * 장애 주입은 동결된 PaymentEventProbe(@Order LOWEST_PRECEDENCE)를 수정 없이 사용하므로
 * Outbox 저장은 결제 성공 이벤트 처리 중 probe보다 먼저 실행돼야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, PaymentEventProbe.class, OutboxFixture.class, SchedulerExclusionConfig.class})
class PaymentOutboxIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private PaymentEventProbe probe;
  @Autowired private PaymentService paymentService;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    probe.reset();
  }

  @AfterEach
  void tearDown() {
    probe.reset();
    outbox.cleanup();
    fixture.cleanup();
  }

  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId) {
  }

  private Pending pending() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(1));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    outbox.trackReservation(reservationId);
    return new Pending(seat, member, holdId, reservationId);
  }

  // ===================== AC-1 성공 커밋 시 저장 =====================

  @Test
  @DisplayName("AC-1 결제 성공 커밋: 해당 결제의 Outbox 1건이 PENDING으로 남고 알림은 없다, 기존 상태·Redis 결과 유지")
  void ac1_paySuccess_savesOnePendingOutbox() {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    IdempotencyResult<PaymentResponse> result = paymentService.pay(key, payRequest(p.reservationId(), false));
    Long paymentId = result.getResponse().paymentId();

    List<Map<String, Object>> rows = outbox.outboxRowsOfPayment(paymentId);
    assertThat(rows).as("결제 커밋 후 해당 결제의 Outbox 건수").hasSize(1);
    Map<String, Object> row = rows.get(0);
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(row.get("processed_at")).isNull();
    assertThat((String) row.get("payload"))
      .contains("\"paymentId\":" + paymentId)
      .contains("\"reservationId\":" + p.reservationId())
      .contains("\"memberId\":" + p.member().getId());
    assertThat(outbox.notificationsOfPayment(paymentId)).as("relay 전 알림").isEmpty();

    // 기존 결제 경로 계약 유지
    assertThat(result.isReplayed()).isFalse();
    assertThat(result.getResponse().status()).isEqualTo("SUCCESS");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isTrue();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
  }

  // ===================== AC-2 저장 이후 롤백 =====================

  @Test
  @DisplayName("AC-2 Outbox 저장 이후 같은 결제 트랜잭션에서 장애: Outbox·결제가 남지 않고 상태·Redis 결과도 남지 않는다")
  void ac2_failureAfterOutboxSaved_rollsBackOutboxAndPayment() {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    probe.observeWith(() -> {
      if (!TransactionSynchronizationManager.hasResource(dataSource)) {
        throw HarnessException.harness("JDBC connection is not bound to the payment transaction");
      }
      entityManager.flush();
      return Map.of("outboxInTransaction", outbox.outboxCountOfReservation(p.reservationId()));
    });
    probe.failOn(PaymentEventProbe.SUCCEEDED);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = e; t != null && !chain.contains(t); t = t.getCause()) {
          chain.add(t);
        }
        assertThat(chain).as("주입한 리스너 예외가 호출자에게 전파되어야 한다")
          .anyMatch(InjectedListenerFailure.class::isInstance);
      });

    // 주입 직전 같은 트랜잭션에서 Outbox가 이미 기록돼 있었다 (저장 이후 장애)
    assertThat(probe.injectedCount()).as("장애 주입 횟수").isEqualTo(1);
    Captured captured = probe.events(PaymentEventProbe.SUCCEEDED).get(0);
    assertThat(captured.observed().get("outboxInTransaction"))
      .as("장애 주입 직전 같은 트랜잭션의 Outbox 건수").isEqualTo(1L);
    assertThat(captured.outcome().completionStatus())
      .as("결제 트랜잭션 롤백, %s", captured.outcome()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);

    // 트랜잭션 종료 후 별도 조회
    assertThat(outbox.outboxCountOfReservation(p.reservationId())).as("롤백 후 Outbox").isZero();
    assertThat(fixture.payments(p.reservationId())).as("롤백 후 결제").isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
  }

  // ===================== AC-3 성공 이벤트 없음 =====================

  @Test
  @DisplayName("AC-3① 업무상 결제 실패(forceFailure): FAIL로 커밋되지만 Outbox·알림은 없다")
  void ac3_forceFailure_createsNoOutbox() {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), true)).getResponse();

    assertThat(response.status()).isEqualTo("FAIL");
    assertThat(fixture.payments(p.reservationId())).hasSize(1);
    assertThat(outbox.outboxCountOfReservation(p.reservationId())).as("결제 실패 Outbox").isZero();
    assertThat(outbox.notificationCountOfReservation(p.reservationId())).as("결제 실패 알림").isZero();
  }

  @Test
  @DisplayName("AC-3② 검증 거절(예약이 PENDING 아님): 결제·Outbox·알림이 없다")
  void ac3_rejected_reservationNotPending_createsNoOutbox() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(1));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.FAILED);
    outbox.trackReservation(reservationId);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(reservationId, false)))
      .satisfies(e -> assertBusinessError(e, PaymentErrorCode.PAYMENT_ALREADY_PROCESSED));

    assertThat(fixture.payments(reservationId)).isEmpty();
    assertThat(outbox.outboxCountOfReservation(reservationId)).as("검증 거절 Outbox").isZero();
    assertThat(outbox.notificationCountOfReservation(reservationId)).as("검증 거절 알림").isZero();
  }

  @Test
  @DisplayName("AC-3② 검증 거절(멱등 키 누락): 결제·Outbox·알림이 없다")
  void ac3_rejected_missingKey_createsNoOutbox() {
    Pending p = pending();

    assertThatThrownBy(() -> paymentService.pay(null, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING));

    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(outbox.outboxCountOfReservation(p.reservationId())).as("검증 거절 Outbox").isZero();
    assertThat(outbox.notificationCountOfReservation(p.reservationId())).as("검증 거절 알림").isZero();
  }

  @Test
  @DisplayName("AC-3③ 멱등 재요청: 최초 결제 대비 Outbox·알림을 추가로 생성하지 않는다")
  void ac3_replay_createsNoAdditionalOutbox() {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    PaymentResponse first = paymentService.pay(key, payRequest(p.reservationId(), false)).getResponse();
    long outboxAfterFirst = outbox.outboxCountOfReservation(p.reservationId());
    long notificationAfterFirst = outbox.notificationCountOfReservation(p.reservationId());
    assertThat(outboxAfterFirst).as("전제: 최초 결제 후 Outbox 1건").isEqualTo(1L);

    IdempotencyResult<PaymentResponse> replay = paymentService.pay(key, payRequest(p.reservationId(), false));

    assertThat(replay.isReplayed()).isTrue();
    assertThat(replay.getResponse().paymentId()).isEqualTo(first.paymentId());
    assertThat(outbox.outboxCountOfReservation(p.reservationId())).as("재요청 후 Outbox").isEqualTo(outboxAfterFirst);
    assertThat(outbox.notificationCountOfReservation(p.reservationId())).as("재요청 후 알림")
      .isEqualTo(notificationAfterFirst);
  }
}
