package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.infra.idempotency.IdempotencyResult;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.error.PaymentErrorCode;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.PaidByOther;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.event.PaymentEventProbe;
import com.pil97.ticketing.support.event.PaymentEventProbe.Captured;
import com.pil97.ticketing.support.event.PaymentEventProbe.InjectedListenerFailure;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.assertBusinessError;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.payRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-032 (#106) 결제·환불 후속 상태 변경 Spring Events 분리 통합 테스트 (실제 Spring 이벤트 전달·MariaDB·Redis)
 *
 * <p>이벤트 계약 (PaymentEventProbe가 단순 클래스명으로 식별):
 * <ul>
 *   <li>결제 성공: PaymentSucceededEvent, 업무상 결제 실패(forceFailure): PaymentFailedEvent, 환불 성공: PaymentRefundedEvent</li>
 *   <li>요청 thread에서 동기 전달되고, 트랜잭션이 활성인 상태에서 수신된다 (트랜잭션 이름은 진단 메시지로만 남긴다)</li>
 *   <li>요청 1건당 해당 이벤트 1회, 다른 결제 이벤트 0회</li>
 *   <li>상태 변경 업무 리스너는 @Order를 명시하고 값이 Ordered.LOWEST_PRECEDENCE보다 작아야 한다 (PaymentEventProbe 참고)</li>
 * </ul>
 *
 * <p>최종 상태는 ConsistencyFixture의 JDBC autocommit 조회로 트랜잭션 종료 후 별도로 읽는다.
 * AC-4·AC-5의 "새 이벤트 0회" 단언은 각 테스트에서 독립적으로 판정한다.
 * 관찰 경로가 실제로 이벤트를 받는지는 별도 대조 테스트(ac5_control_*)와 AC-1·AC-3이 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, PaymentEventProbe.class})
class PaymentEventIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private ConsistencyFixture fixture;
  @Autowired private PaymentEventProbe probe;
  @Autowired private PaymentService paymentService;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;
  @Autowired private StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    probe.reset();
  }

  @AfterEach
  void tearDown() {
    probe.reset();
    fixture.cleanup();
  }

  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId) {
  }

  private Pending pending(ShowtimeSeatStatus seatStatus, HoldStatus holdStatus) {
    SeatRef seat = fixture.seat(seatStatus);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, holdStatus, nowSeconds().plusMinutes(1));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    return new Pending(seat, member, holdId, reservationId);
  }

  /**
   * 요청 1건에서 지정 이벤트만 정확히 1회, 요청 thread·활성 트랜잭션 안에서 수신됐는지 단언
   * - 트랜잭션 이름은 단언하지 않고 실패 메시지의 진단 정보로만 남긴다
   */
  private Captured assertSingleEventInTransaction(String eventName) {
    assertThat(probe.all())
      .as("결제 이벤트 수신 목록")
      .extracting(Captured::name)
      .containsExactly(eventName);
    Captured captured = probe.events(eventName).get(0);
    String diagnostic = "transactionName=" + captured.transactionName();
    assertThat(captured.threadName()).as("동기 전달 (요청 thread), %s", diagnostic)
      .isEqualTo(Thread.currentThread().getName());
    assertThat(captured.txActive()).as("수신 시점 트랜잭션 활성, %s", diagnostic).isTrue();
    return captured;
  }

  private void assertNoPaymentEvent() {
    assertThat(probe.all()).as("결제 이벤트 수신 목록").isEmpty();
  }

  private void assertNoIdempotencyEntry(String key) {
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
  }

  /**
   * 이벤트 수신 시점(같은 트랜잭션)에서 flush 후 같은 connection으로 상태를 읽는 observer
   * - production 리스너의 상태 변경이 시작됐는지 장애 주입 직전에 기록한다
   */
  private Supplier<Map<String, Object>> inTransactionStateObserver(SeatRef seat, Long holdId, Long reservationId) {
    return () -> {
      if (!TransactionSynchronizationManager.hasResource(dataSource)) {
        throw HarnessException.harness("JDBC connection is not bound to the payment transaction");
      }
      entityManager.flush();
      Map<String, Object> state = new LinkedHashMap<>();
      state.put("reservation", fixture.reservationStatus(reservationId));
      state.put("hold", fixture.holdStatus(holdId));
      if (seat != null) {
        state.put("seat", fixture.seatStatus(seat));
      }
      state.put("payments", fixture.payments(reservationId).stream().map(row -> row.get("status")).toList());
      return state;
    };
  }

  /**
   * AC-6: 예외가 상태 변경 업무 리스너 뒤에 1회 주입됐고, 수신 트랜잭션이 롤백으로 끝났는지 단언
   * - 수신 시점 상태가 기대와 다르면 업무 리스너 순서 조건(@Order < LOWEST_PRECEDENCE) 위반을 우선 의심한다
   */
  private void assertInjectedAfterStateChange(String eventName, Map<String, Object> expectedInTransaction) {
    assertThat(probe.injectedCount()).as("리스너 예외 주입 횟수").isEqualTo(1);
    Captured captured = probe.events(eventName).get(0);
    assertThat(captured.observed())
      .as("예외 주입 직전 같은 트랜잭션의 상태 (업무 리스너가 probe보다 먼저 실행돼야 한다: @Order < LOWEST_PRECEDENCE), "
        + "transactionName=%s", captured.transactionName())
      .containsExactlyInAnyOrderEntriesOf(expectedInTransaction);
    assertThat(captured.outcome().completionStatus())
      .as("수신 트랜잭션 롤백, %s", captured.outcome()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
  }

  private static void assertInjectedListenerFailure(Throwable error) {
    List<Throwable> chain = new ArrayList<>();
    Set<Throwable> seen = new HashSet<>();
    for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
      chain.add(t);
    }
    assertThat(chain)
      .as("주입한 리스너 예외가 호출자에게 전파되어야 한다")
      .anyMatch(InjectedListenerFailure.class::isInstance);
  }

  // ===================== AC-1 결제 성공 =====================

  @Test
  @DisplayName("AC-1 결제 성공: PaymentSucceededEvent 1회(동기·같은 트랜잭션), 기존 최종 상태·응답 유지, 커밋 직전 Redis 결과 없음·커밋 후 저장")
  void ac1_paySuccess_publishesSucceededEventOnce() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    probe.observeWith(() -> Map.of("cachedAtEvent", fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)));
    probe.observeBeforeCommitWith(() -> Map.of("cachedBeforeCommit", fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)));

    IdempotencyResult<PaymentResponse> result = paymentService.pay(key, payRequest(p.reservationId(), false));

    // 기존 응답·최종 상태 유지
    PaymentResponse response = result.getResponse();
    assertThat(result.isReplayed()).isFalse();
    assertThat(response.status()).isEqualTo("SUCCESS");
    assertThat(response.paidAt()).isNotNull();
    assertThat(response.refundedAt()).isNull();
    Map<String, Object> payment = fixture.paymentRow(response.paymentId());
    assertThat(payment.get("status")).isEqualTo("SUCCESS");
    assertThat(payment.get("paid_at")).isNotNull();
    assertThat(fixture.payments(p.reservationId())).hasSize(1);
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isTrue();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();

    // 이벤트 계약
    Captured captured = assertSingleEventInTransaction(PaymentEventProbe.SUCCEEDED);
    assertThat(captured.observed().get("cachedAtEvent")).as("이벤트 수신 시점에는 Redis 결과가 없어야 한다").isEqualTo(false);

    // 커밋 직전 Redis 결과 없음 → 커밋 성공 → 커밋 후 Redis 결과 있음 (위 redisKeyExists 단언)
    PaymentEventProbe.TxOutcome outcome = captured.outcome();
    assertThat(outcome.registered()).as("수신 트랜잭션 synchronization 등록, %s", outcome).isTrue();
    assertThat(outcome.beforeCommit()).as("커밋 직전 관찰, %s", outcome).isNotNull();
    assertThat(outcome.beforeCommit().get("cachedBeforeCommit"))
      .as("커밋 직전에는 Redis 성공 캐시가 없어야 한다, %s", outcome).isEqualTo(false);
    assertThat(outcome.completionStatus())
      .as("수신 트랜잭션 커밋 성공, %s", outcome).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
  }

  // ===================== AC-2 업무상 결제 실패 =====================

  @Test
  @DisplayName("AC-2 업무상 실패(선점자): PaymentFailedEvent 1회, FAIL 커밋, 예약 FAILED·HOLD EXPIRED, 좌석 AVAILABLE 해제, Redis 결과 없음")
  void ac2_forceFailure_owner_publishesFailedEventOnceAndReleasesSeat() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    IdempotencyResult<PaymentResponse> result = paymentService.pay(key, payRequest(p.reservationId(), true));

    PaymentResponse response = result.getResponse();
    assertThat(result.isReplayed()).isFalse();
    assertThat(response.status()).isEqualTo("FAIL");
    assertThat(response.paidAt()).isNull();
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).get("status")).isEqualTo("FAIL");
    assertThat(payments.get(0).get("paid_at")).isNull();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(key);

    assertSingleEventInTransaction(PaymentEventProbe.FAILED);
  }

  @Test
  @DisplayName("AC-2 업무상 실패(비선점자): 같은 좌석에 다른 ACTIVE HOLD가 있으면 PaymentFailedEvent 1회, FAIL 커밋, 좌석 HELD·타인 HOLD 보존")
  void ac2_forceFailure_notOwner_publishesFailedEventOnceAndKeepsSeat() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    Member other = fixture.member();
    Long otherHold = fixture.hold(p.seat(), other, HoldStatus.ACTIVE, nowSeconds().plusMinutes(5));
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), true)).getResponse();

    assertThat(response.status()).isEqualTo("FAIL");
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).get("status")).isEqualTo("FAIL");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertThat(fixture.holdStatus(otherHold)).isEqualTo("ACTIVE");
    assertNoIdempotencyEntry(key);

    assertSingleEventInTransaction(PaymentEventProbe.FAILED);
  }

  @Test
  @DisplayName("AC-2 업무상 실패(타인 결제 완료 좌석): PaymentFailedEvent 1회, FAIL 커밋, RESERVED 좌석과 타인 결제 상태 보존")
  void ac2_forceFailure_otherPaidSeat_publishesFailedEventOnceAndPreservesReservedSeat() {
    Pending p = pending(ShowtimeSeatStatus.RESERVED, HoldStatus.ACTIVE);
    PaidByOther paidByOther = fixture.paidByOther(p.seat());
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), true)).getResponse();

    assertThat(response.status()).isEqualTo("FAIL");
    assertThat(fixture.payments(p.reservationId())).hasSize(1);
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    fixture.assertPaidByOtherPreserved(paidByOther);
    assertNoIdempotencyEntry(key);

    assertSingleEventInTransaction(PaymentEventProbe.FAILED);
  }

  // ===================== AC-3 정상 환불 =====================

  @Test
  @DisplayName("AC-3 정상 환불: PaymentRefundedEvent 1회(동기·같은 트랜잭션), Payment REFUNDED·예약 CANCELLED·HOLD REFUNDED·좌석 AVAILABLE")
  void ac3_refund_publishesRefundedEventOnce() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    PaidByOther paid = fixture.paidByOther(seat);

    PaymentResponse response = paymentService.refund(paid.paymentId(), paid.member());

    assertThat(response.paymentId()).isEqualTo(paid.paymentId());
    assertThat(response.status()).isEqualTo("REFUNDED");
    assertThat(response.refundedAt()).isNotNull();
    Map<String, Object> payment = fixture.paymentRow(paid.paymentId());
    assertThat(payment.get("status")).isEqualTo("REFUNDED");
    assertThat(payment.get("refunded_at")).isNotNull();
    assertThat(fixture.reservationStatus(paid.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.holdStatus(paid.holdId())).isEqualTo("REFUNDED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("AVAILABLE");

    assertSingleEventInTransaction(PaymentEventProbe.REFUNDED);
  }

  // ===================== AC-4 기존 응답 재사용 =====================

  @Test
  @DisplayName("AC-4 응답 재사용: 같은 key·같은 본문 재요청은 기존 응답을 반환하고 새 이벤트 0회, DB 변경 없음")
  void ac4_replay_publishesNoNewEvent() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    PaymentResponse first = paymentService.pay(key, payRequest(p.reservationId(), false)).getResponse();
    // 최초 결제로 Redis 성공 캐시를 만든 뒤 관찰 기록을 비운다 (최초 요청의 이벤트 수는 AC-1이 검증)
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).as("최초 결제 성공 캐시").isTrue();
    probe.reset();

    IdempotencyResult<PaymentResponse> replay = paymentService.pay(key, payRequest(p.reservationId(), false));

    assertThat(replay.isReplayed()).isTrue();
    assertThat(replay.getResponse().paymentId()).isEqualTo(first.paymentId());
    assertThat(replay.getResponse().status()).isEqualTo("SUCCESS");
    assertNoPaymentEvent();
    assertThat(fixture.payments(p.reservationId())).hasSize(1);
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
  }

  // ===================== AC-5 검증 거절 =====================
  // 각 거절 테스트는 거절 오류코드·이벤트 0회·상태 보존만 독립적으로 판정한다
  // 관찰 경로가 결제 이벤트를 실제로 받는지는 대조 테스트가 따로 확인한다 (환불 경로는 AC-3)

  @Test
  @DisplayName("AC-5 대조: 정상 결제 1건은 PaymentSucceededEvent를 정확히 1회 발생시킨다 (거절 테스트의 0회 판정이 관찰 경로 부재 때문이 아님을 확인)")
  void ac5_control_validPayment_publishesSucceededEventOnce() {
    Pending control = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);

    paymentService.pay(fixture.idempotencyKey(PAYMENT_PREFIX), payRequest(control.reservationId(), false));

    assertSingleEventInTransaction(PaymentEventProbe.SUCCEEDED);
  }

  @Test
  @DisplayName("AC-5 결제 거절(Idempotency-Key 누락): IDEMPOTENCY-003, 새 이벤트 0회")
  void ac5_pay_missingKey_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);

    assertThatThrownBy(() -> paymentService.pay(" ", payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING));
    assertNoPaymentEvent();
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("AC-5 결제 거절(같은 key·다른 본문): IDEMPOTENCY-002, 새 이벤트 0회")
  void ac5_pay_payloadMismatch_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    Pending another = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    paymentService.pay(key, payRequest(p.reservationId(), false));
    probe.reset();

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(another.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, IdempotencyErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH));
    assertNoPaymentEvent();
    assertThat(fixture.payments(another.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(another.reservationId())).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("AC-5 결제 거절(같은 key 처리 중): IDEMPOTENCY-001, 새 이벤트 0회")
  void ac5_pay_inProgress_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    stringRedisTemplate.opsForValue().set(PAYMENT_PREFIX + ":lock:" + key, "processing");

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS));
    assertNoPaymentEvent();
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("AC-5 결제 거절(예약 미존재): RESERVATION-001, 새 이벤트 0회")
  void ac5_pay_reservationNotFound_publishesNoEvent() {
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(-1L, false)))
      .satisfies(e -> assertBusinessError(e, ReservationErrorCode.NOT_FOUND));
    assertNoPaymentEvent();
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-5 결제 거절(예약 PENDING 아님): PAYMENT-002, 새 이벤트 0회")
  void ac5_pay_reservationNotPending_publishesNoEvent() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(1));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.FAILED);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(reservationId, false)))
      .satisfies(e -> assertBusinessError(e, PaymentErrorCode.PAYMENT_ALREADY_PROCESSED));
    assertNoPaymentEvent();
    assertThat(fixture.payments(reservationId)).isEmpty();
    assertThat(fixture.reservationStatus(reservationId)).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(holdId)).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-5 결제 거절(대상 HOLD ACTIVE 아님, 성공·forceFailure): HOLD-002, 새 이벤트 0회")
  void ac5_pay_holdNotActive_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.EXPIRED);
    String successKey = fixture.idempotencyKey(PAYMENT_PREFIX);
    String failureKey = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(successKey, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, HoldErrorCode.NOT_ACTIVE));
    assertThatThrownBy(() -> paymentService.pay(failureKey, payRequest(p.reservationId(), true)))
      .satisfies(e -> assertBusinessError(e, HoldErrorCode.NOT_ACTIVE));
    assertNoPaymentEvent();
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(successKey);
    assertNoIdempotencyEntry(failureKey);
  }

  @Test
  @DisplayName("AC-5 결제 거절(좌석 HELD 아님): SHOWTIME-SEAT-002, 새 이벤트 0회")
  void ac5_pay_seatNotHeld_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, ShowtimeSeatErrorCode.NOT_HELD));
    assertNoPaymentEvent();
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-5 환불 거절(결제 미존재): PAYMENT-001, 새 이벤트 0회")
  void ac5_refund_paymentNotFound_publishesNoEvent() {
    Member member = fixture.member();

    assertThatThrownBy(() -> paymentService.refund(-1L, member))
      .satisfies(e -> assertBusinessError(e, PaymentErrorCode.PAYMENT_NOT_FOUND));
    assertNoPaymentEvent();
  }

  @Test
  @DisplayName("AC-5 환불 거절(타인 결제): PAYMENT-006, 결제·예약·HOLD·좌석 보존, 새 이벤트 0회")
  void ac5_refund_forbidden_publishesNoEvent() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    PaidByOther paid = fixture.paidByOther(seat);
    Member stranger = fixture.member();

    assertThatThrownBy(() -> paymentService.refund(paid.paymentId(), stranger))
      .satisfies(e -> assertBusinessError(e, PaymentErrorCode.REFUND_FORBIDDEN));
    assertNoPaymentEvent();
    fixture.assertPaidByOtherPreserved(paid);
    assertThat(fixture.seatStatus(seat)).isEqualTo("RESERVED");
  }

  @Test
  @DisplayName("AC-5 환불 거절(타인의 SUCCESS 아닌 결제): 소유권 검증이 먼저 - PAYMENT-006, 새 이벤트 0회")
  void ac5_refund_forbiddenBeforeStatus_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.EXPIRED);
    Long paymentId = fixture.payment(p.reservationId(), "FAIL", null);
    Member stranger = fixture.member();

    assertThatThrownBy(() -> paymentService.refund(paymentId, stranger))
      .satisfies(e -> assertBusinessError(e, PaymentErrorCode.REFUND_FORBIDDEN));
    assertNoPaymentEvent();
    assertThat(fixture.paymentRow(paymentId).get("status")).isEqualTo("FAIL");
  }

  @Test
  @DisplayName("AC-5 환불 거절(본인의 SUCCESS 아닌 결제): PAYMENT-005, 새 이벤트 0회")
  void ac5_refund_notAllowed_publishesNoEvent() {
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.EXPIRED);
    Long paymentId = fixture.payment(p.reservationId(), "FAIL", null);

    assertThatThrownBy(() -> paymentService.refund(paymentId, p.member()))
      .satisfies(e -> assertBusinessError(e, PaymentErrorCode.REFUND_NOT_ALLOWED));
    assertNoPaymentEvent();
    assertThat(fixture.paymentRow(paymentId).get("status")).isEqualTo("FAIL");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
  }

  // ===================== AC-6 리스너 예외 =====================
  // 순서 보장: probe는 @Order(LOWEST_PRECEDENCE), 상태 변경 업무 리스너는 그보다 작은 @Order를 명시해야 한다
  // probe가 flush 후 같은 트랜잭션에서 상태 변경 시작을 기록하고 동기 리스너 예외를 주입한다
  // 트랜잭션 종료 후 JDBC autocommit 조회로 롤백을 확인한다

  @Test
  @DisplayName("AC-6 결제 성공 리스너 예외: 예외 전파, Payment 미생성·예약 PENDING·HOLD ACTIVE·좌석 HELD 롤백, Redis 성공 응답·lock 없음")
  void ac6_paySuccess_listenerFailure_rollsBackAndCachesNothing() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    probe.observeWith(inTransactionStateObserver(p.seat(), p.holdId(), p.reservationId()));
    probe.failOn(PaymentEventProbe.SUCCEEDED);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(PaymentEventIntegrationTest::assertInjectedListenerFailure);

    // 예외 주입 시점: 같은 트랜잭션 안에서 상태 변경이 시작돼 있었다
    assertInjectedAfterStateChange(PaymentEventProbe.SUCCEEDED, Map.of(
      "payments", List.of("SUCCESS"), "reservation", "CONFIRMED", "hold", "CONFIRMED", "seat", "RESERVED"));

    // 트랜잭션 종료 후 별도 조회: 롤백
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-6 업무상 실패 리스너 예외: 예외 전파, FAIL Payment 미생성·예약 PENDING·HOLD ACTIVE·좌석 HELD 롤백, Redis 결과·lock 없음")
  void ac6_forceFailure_listenerFailure_rollsBack() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    probe.observeWith(inTransactionStateObserver(p.seat(), p.holdId(), p.reservationId()));
    probe.failOn(PaymentEventProbe.FAILED);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), true)))
      .satisfies(PaymentEventIntegrationTest::assertInjectedListenerFailure);

    assertInjectedAfterStateChange(PaymentEventProbe.FAILED, Map.of(
      "payments", List.of("FAIL"), "reservation", "FAILED", "hold", "EXPIRED", "seat", "AVAILABLE"));

    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-6 환불 리스너 예외: 예외 전파, Payment SUCCESS·예약 CONFIRMED·HOLD CONFIRMED·좌석 RESERVED 롤백")
  void ac6_refund_listenerFailure_rollsBack() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    PaidByOther paid = fixture.paidByOther(seat);
    probe.observeWith(inTransactionStateObserver(seat, paid.holdId(), paid.reservationId()));
    probe.failOn(PaymentEventProbe.REFUNDED);

    assertThatThrownBy(() -> paymentService.refund(paid.paymentId(), paid.member()))
      .satisfies(PaymentEventIntegrationTest::assertInjectedListenerFailure);

    assertInjectedAfterStateChange(PaymentEventProbe.REFUNDED, Map.of(
      "payments", List.of("REFUNDED"), "reservation", "CANCELLED", "hold", "REFUNDED", "seat", "AVAILABLE"));

    fixture.assertPaidByOtherPreserved(paid);
    assertThat(fixture.paymentRow(paid.paymentId()).get("refunded_at")).isNull();
    assertThat(fixture.seatStatus(seat)).isEqualTo("RESERVED");
  }
}
