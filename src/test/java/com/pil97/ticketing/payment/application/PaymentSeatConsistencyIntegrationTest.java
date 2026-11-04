package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.assertBusinessError;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.payRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-062 (#104) 결제 성공·forceFailure 조건과 정상 결제·환불 회귀 통합 테스트 (실제 MariaDB·멱등성 Redis)
 *
 * <p>결제 검증 순서 계약 (기존 ErrorCode만 사용):
 * <ol>
 *   <li>Idempotency-Key 누락/재응답/처리 중/본문 불일치 - 기존 IDEMPOTENCY-* 유지</li>
 *   <li>예약 미존재 RESERVATION-001, PENDING 아님 PAYMENT-002 - 기존 유지</li>
 *   <li>(좌석 → 관련 HOLD 잠금·최신 상태 확보 후) 대상 HOLD가 ACTIVE 아님: HOLD-002 (성공·forceFailure 공통)</li>
 *   <li>성공 경로만: 좌석이 HELD 아님 SHOWTIME-SEAT-002</li>
 *   <li>성공 경로만: 같은 좌석에 다른 ACTIVE/CONFIRMED HOLD 존재(선점 주체 확정 불가) SHOWTIME-SEAT-002</li>
 * </ol>
 * forceFailure는 3번 통과 시 FAIL로 커밋하되, 대상 HOLD가 선점자일 때만 좌석을 해제한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConsistencyFixture.class)
class PaymentSeatConsistencyIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private ConsistencyFixture fixture;
  @Autowired private PaymentService paymentService;

  @AfterEach
  void tearDown() {
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

  private void assertNoIdempotencyEntry(String key) {
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
  }

  // ===================== 정상 경로 회귀 (guard) =====================

  @Test
  @DisplayName("회귀: 정상 결제 성공 - Payment SUCCESS, 예약 CONFIRMED, HOLD CONFIRMED, 좌석 RESERVED, 커밋 후 Redis 결과 저장")
  void pay_normal_success() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), false)).getResponse();

    assertThat(response.status()).isEqualTo("SUCCESS");
    Map<String, Object> payment = fixture.paymentRow(response.paymentId());
    assertThat(payment.get("status")).isEqualTo("SUCCESS");
    assertThat(payment.get("paid_at")).isNotNull();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isTrue();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
  }

  @Test
  @DisplayName("회귀: 정상 forceFailure - Payment FAIL, 예약 FAILED, HOLD EXPIRED, 좌석 AVAILABLE, Redis 결과 없음")
  void pay_normal_forceFailure() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), true)).getResponse();

    assertThat(response.status()).isEqualTo("FAIL");
    Map<String, Object> payment = fixture.paymentRow(response.paymentId());
    assertThat(payment.get("status")).isEqualTo("FAIL");
    assertThat(payment.get("paid_at")).isNull();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("회귀: 정상 환불 - Payment REFUNDED, 예약 CANCELLED, HOLD REFUNDED, 좌석 AVAILABLE")
  void refund_normal_regression() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    PaymentResponse paid = paymentService
      .pay(fixture.idempotencyKey(PAYMENT_PREFIX), payRequest(p.reservationId(), false)).getResponse();

    PaymentResponse refunded = paymentService.refund(paid.paymentId(), p.member());

    assertThat(refunded.status()).isEqualTo("REFUNDED");
    Map<String, Object> payment = fixture.paymentRow(paid.paymentId());
    assertThat(payment.get("status")).isEqualTo("REFUNDED");
    assertThat(payment.get("refunded_at")).isNotNull();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("REFUNDED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
  }

  // ===================== 결제 성공 조건 =====================

  @Test
  @DisplayName("결제 성공 조건: 대상 HOLD가 ACTIVE가 아니면 HOLD-002, 좌석 검증보다 먼저 판정하고 부분 변경 없음")
  void pay_holdNotActive_rejectedWithHoldNotActive() {
    // HOLD EXPIRED + 좌석 AVAILABLE: HOLD 검증이 좌석 검증보다 앞서는지 함께 고정
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.EXPIRED);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, HoldErrorCode.NOT_ACTIVE));
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("결제 성공 조건: 대상 HOLD ACTIVE지만 좌석이 HELD가 아니면 SHOWTIME-SEAT-002, 부분 변경 없음")
  void pay_seatNotHeld_rejectedWithNotHeld() {
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.ACTIVE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, ShowtimeSeatErrorCode.NOT_HELD));
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("결제 성공 조건: 같은 좌석에 다른 ACTIVE HOLD가 있으면(선점 주체 확정 불가) SHOWTIME-SEAT-002, 성공 처리하지 않음")
  void pay_otherLiveHoldOnSeat_rejectedWithNotHeld() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    Member other = fixture.member();
    Long otherHold = fixture.hold(p.seat(), other, HoldStatus.ACTIVE, nowSeconds().plusMinutes(5));
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, ShowtimeSeatErrorCode.NOT_HELD));
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.holdStatus(otherHold)).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertNoIdempotencyEntry(key);
  }

  // ===================== AC-20 다른 회원 CONFIRMED HOLD 보호 (결제·forceFailure) =====================
  // HELD 오염 사례: 좌석이 HELD여서 좌석 검사로는 거절되지 않고, CONFIRMED HOLD를 활성 HOLD로 고려해야 통과한다
  // RESERVED 보존 사례: 다른 회원 결제 완료 좌석과 결제·예약·HOLD 상태가 보존되는지 확인한다

  @Test
  @DisplayName("AC-20 결제(HELD 오염): 같은 좌석에 다른 회원 CONFIRMED HOLD가 있으면 SHOWTIME-SEAT-002로 거절, 부분 변경 없음")
  void pay_otherConfirmedHold_seatHeldContaminated_rejectedWithNotHeld() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(p.seat());
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, ShowtimeSeatErrorCode.NOT_HELD));
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    fixture.assertPaidByOtherPreserved(paid);
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-20 결제(RESERVED 보존): 다른 회원 결제 완료 RESERVED 좌석이면 SHOWTIME-SEAT-002로 거절, 타인 결제 상태 보존")
  void pay_otherConfirmedHold_seatReserved_rejectedWithNotHeld() {
    Pending p = pending(ShowtimeSeatStatus.RESERVED, HoldStatus.ACTIVE);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(p.seat());
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), false)))
      .satisfies(e -> assertBusinessError(e, ShowtimeSeatErrorCode.NOT_HELD));
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    fixture.assertPaidByOtherPreserved(paid);
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-20 forceFailure(HELD 오염): 다른 회원 CONFIRMED HOLD가 있으면 FAIL로 커밋하되 좌석을 해제하지 않는다")
  void forceFailure_otherConfirmedHold_seatHeldContaminated_doesNotReleaseSeat() {
    Pending p = pending(ShowtimeSeatStatus.HELD, HoldStatus.ACTIVE);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(p.seat());
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), true)).getResponse();

    assertThat(response.status()).isEqualTo("FAIL");
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).get("status")).isEqualTo("FAIL");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    fixture.assertPaidByOtherPreserved(paid);
    assertNoIdempotencyEntry(key);
  }

  @Test
  @DisplayName("AC-20 forceFailure(RESERVED 보존): 다른 회원 결제 완료 RESERVED 좌석이면 FAIL로 커밋하되 좌석을 해제하지 않는다")
  void forceFailure_otherConfirmedHold_seatReserved_preservesReservedSeat() {
    Pending p = pending(ShowtimeSeatStatus.RESERVED, HoldStatus.ACTIVE);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(p.seat());
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    PaymentResponse response = paymentService.pay(key, payRequest(p.reservationId(), true)).getResponse();

    assertThat(response.status()).isEqualTo("FAIL");
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).get("status")).isEqualTo("FAIL");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    fixture.assertPaidByOtherPreserved(paid);
    assertNoIdempotencyEntry(key);
  }

  // ===================== forceFailure =====================

  @Test
  @DisplayName("forceFailure: 대상 HOLD가 선점자가 아니면(다른 회원 ACTIVE HOLD 존재) FAIL로 커밋하되 좌석은 해제하지 않는다")
  void forceFailure_notOwner_doesNotReleaseOthersSeat() {
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
  }

  @Test
  @DisplayName("forceFailure: 대상 HOLD가 ACTIVE가 아니면 HOLD-002로 거절되고 Payment row를 남기지 않는다")
  void forceFailure_holdNotActive_rejectedWithHoldNotActive() {
    Pending p = pending(ShowtimeSeatStatus.AVAILABLE, HoldStatus.EXPIRED);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    assertThatThrownBy(() -> paymentService.pay(key, payRequest(p.reservationId(), true)))
      .satisfies(e -> assertBusinessError(e, HoldErrorCode.NOT_ACTIVE));
    assertThat(fixture.payments(p.reservationId())).isEmpty();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertNoIdempotencyEntry(key);
  }
}
