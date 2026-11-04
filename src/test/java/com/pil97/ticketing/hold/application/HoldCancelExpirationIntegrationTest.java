package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.api.dto.response.HoldResponse;
import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.application.PaymentService;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.reservation.application.ReservationService;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.assertBusinessError;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.holdRequest;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.payRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-062 (#104) 취소·만료·재선점 순차 시나리오 통합 테스트 (실제 MariaDB·Redis)
 * - 실제 스케줄러는 @MockitoBean으로 격리하고, 만료는 고정 시각으로 public expireHolds(now)를 직접 호출한다
 * - fixture HOLD의 expiresAt은 실제 시계보다 미래(T = now + 1분)로 두고, 만료 호출 시각은 T + 1초를 사용한다
 * - 최종 상태는 JDBC autocommit 조회(새 트랜잭션)로 확인한다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConsistencyFixture.class)
class HoldCancelExpirationIntegrationTest {

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private ConsistencyFixture fixture;
  @Autowired private ReservationService reservationService;
  @Autowired private HoldExpirationService holdExpirationService;
  @Autowired private HoldService holdService;
  @Autowired private PaymentService paymentService;
  @Autowired private TransactionTemplate transactionTemplate;

  @AfterEach
  void tearDown() {
    fixture.cleanup();
  }

  /**
   * 회원 U1의 PENDING 예약 fixture: 좌석 HELD, HOLD ACTIVE(expiresAt = T), 예약 PENDING
   */
  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId, LocalDateTime expiresAt) {
    LocalDateTime afterExpiry() {
      return expiresAt.plusSeconds(1);
    }
  }

  private Pending pending() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, expiresAt);
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    return new Pending(seat, member, holdId, reservationId, expiresAt);
  }

  // ===================== AC-1 정상 PENDING 취소 =====================

  @Test
  @DisplayName("AC-1: PENDING 취소 후 예약 CANCELLED, HOLD CANCELLED, 좌석 AVAILABLE")
  void cancel_pending_cancelsHoldAndReleasesSeat() {
    Pending p = pending();

    reservationService.cancel(p.reservationId());

    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
  }

  // ===================== AC-2 시나리오 A =====================

  @Test
  @DisplayName("AC-2 시나리오 A: 취소된 HOLD를 만료 처리해도 예외·재변경이 없다")
  void scenarioA_expireAfterCancel_noExceptionAndNoChange() {
    Pending p = pending();
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));

    reservationService.cancel(p.reservationId());

    assertThatCode(() -> holdExpirationService.expireHolds(p.afterExpiry())).doesNotThrowAnyException();
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
  }

  // ===================== AC-3 시나리오 B =====================

  @Test
  @DisplayName("AC-3 시나리오 B: 취소 → 다른 회원 재선점 → 이전 HOLD 만료 시 재선점 HELD 좌석 보존")
  void scenarioB_cancelThenRehold_expireDoesNotReleaseReheldSeat() {
    Pending p = pending();
    Member other = fixture.member();
    fixture.admit(other);
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));

    reservationService.cancel(p.reservationId());
    HoldResponse rehold = holdService.hold(p.seat().showtimeId(), holdRequest(p.seat(), other));

    assertThatCode(() -> holdExpirationService.expireHolds(p.afterExpiry())).doesNotThrowAnyException();
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertThat(fixture.holdStatus(rehold.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.redissonLocked(p.seat())).isFalse();
  }

  // ===================== AC-4 시나리오 C =====================

  @Test
  @DisplayName("AC-4 시나리오 C: 취소 → 재선점·결제 완료 → 이전 HOLD 만료 시 결제 완료 RESERVED 좌석 보존")
  void scenarioC_cancelThenReholdAndPay_expireDoesNotReleaseReservedSeat() {
    Pending p = pending();
    Member other = fixture.member();
    fixture.admit(other);
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));

    reservationService.cancel(p.reservationId());
    HoldResponse rehold = holdService.hold(p.seat().showtimeId(), holdRequest(p.seat(), other));
    ReservationResponse reserved = reservationService
      .reserve(fixture.idempotencyKey("idempotency:reservation"), rehold.holdId()).getResponse();
    paymentService.pay(fixture.idempotencyKey("idempotency:payment"), payRequest(reserved.reservationId(), false));

    assertThatCode(() -> holdExpirationService.expireHolds(p.afterExpiry())).doesNotThrowAnyException();
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    assertThat(fixture.holdStatus(rehold.holdId())).isEqualTo("CONFIRMED");
    assertThat(fixture.reservationStatus(reserved.reservationId())).isEqualTo("CONFIRMED");
    List<Map<String, Object>> payments = fixture.payments(reserved.reservationId());
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).get("status")).isEqualTo("SUCCESS");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
  }

  // ===================== AC-6 시나리오 F =====================

  @Test
  @DisplayName("AC-6 시나리오 F: 자연 만료 → 다른 회원 재선점 → 이전 PENDING 예약 취소 시 재선점 좌석 보존")
  void scenarioF_naturalExpiryThenReholdThenCancel_doesNotReleaseReheldSeat() {
    Pending p = pending();
    Member other = fixture.member();
    fixture.admit(other);
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));

    holdExpirationService.expireHolds(p.afterExpiry());
    HoldResponse rehold = holdService.hold(p.seat().showtimeId(), holdRequest(p.seat(), other));

    assertThatCode(() -> reservationService.cancel(p.reservationId())).doesNotThrowAnyException();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    assertThat(fixture.holdStatus(rehold.holdId())).isEqualTo("ACTIVE");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName("Before/After: 이미 만료된 HOLD의 PENDING 취소는 좌석 AVAILABLE 상태에서도 성공하고 좌석을 바꾸지 않는다")
  void cancel_afterExpiry_seatAvailable_succeedsWithoutSeatChange() {
    Pending p = pending();
    fixture.requireExpirationCandidates(p.afterExpiry(), Set.of(p.holdId()));
    holdExpirationService.expireHolds(p.afterExpiry());

    assertThatCode(() -> reservationService.cancel(p.reservationId())).doesNotThrowAnyException();
    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("AVAILABLE");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("EXPIRED");
  }

  // ===================== AC-7·AC-8 오염 데이터와 배치 =====================

  @Test
  @DisplayName("AC-8 시나리오 E: 오염 HOLD(좌석 이미 AVAILABLE)와 정상 만료 HOLD가 같은 배치여도 정상 HOLD는 만료된다")
  void scenarioE_contaminatedAndNormalInSameBatch_normalStillExpires() {
    // 오염: 기존 취소 로직이 남긴 상태 (예약 CANCELLED, HOLD ACTIVE, 좌석 AVAILABLE)
    SeatRef contaminatedSeat = fixture.seat(ShowtimeSeatStatus.AVAILABLE);
    Member u1 = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long contaminatedHold = fixture.hold(contaminatedSeat, u1, HoldStatus.ACTIVE, expiresAt);
    fixture.reservation(contaminatedHold, ReservationStatus.CANCELLED);
    // 정상 만료 대상
    SeatRef normalSeat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member u9 = fixture.member();
    Long normalHold = fixture.hold(normalSeat, u9, HoldStatus.ACTIVE, expiresAt);
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(contaminatedHold, normalHold));

    assertThatCode(() -> holdExpirationService.expireHolds(now)).doesNotThrowAnyException();
    assertThat(fixture.holdStatus(normalHold)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(normalSeat)).isEqualTo("AVAILABLE");
    assertThat(fixture.holdStatus(contaminatedHold)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(contaminatedSeat)).isEqualTo("AVAILABLE");
  }

  @Test
  @DisplayName("AC-7: 오염 데이터(같은 좌석에 다른 회원 ACTIVE HOLD)에서 이전 HOLD 만료는 좌석을 해제하지 않는다")
  void contaminated_otherLiveHoldOnSeat_expireDoesNotReleaseSeat() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member u1 = fixture.member();
    Member u2 = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long oldHold = fixture.hold(seat, u1, HoldStatus.ACTIVE, expiresAt);
    fixture.reservation(oldHold, ReservationStatus.CANCELLED);
    Long liveHold = fixture.hold(seat, u2, HoldStatus.ACTIVE, expiresAt.plusMinutes(10));
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(oldHold));

    assertThatCode(() -> holdExpirationService.expireHolds(now)).doesNotThrowAnyException();
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");
    assertThat(fixture.holdStatus(liveHold)).isEqualTo("ACTIVE");
    assertThat(fixture.holdStatus(oldHold)).isEqualTo("EXPIRED");
  }

  // ===================== AC-20 다른 회원 CONFIRMED HOLD 보호 (취소·만료) =====================
  // HELD 오염 사례: 좌석 상태 검사만으로는 막을 수 없고 CONFIRMED HOLD를 활성 HOLD로 고려해야 통과한다
  // RESERVED 보존 사례: 결제 완료 좌석이 이전 HOLD 처리로 해제되지 않는지 확인한다

  @Test
  @DisplayName("AC-20 취소(HELD 오염): 같은 좌석에 다른 회원 CONFIRMED HOLD가 있으면 HOLD만 CANCELLED, 좌석·타인 결제 상태 보존")
  void cancel_otherConfirmedHold_seatHeldContaminated_doesNotReleaseSeat() {
    Pending p = pending();
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(p.seat());

    reservationService.cancel(p.reservationId());

    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("HELD");
    fixture.assertPaidByOtherPreserved(paid);
  }

  @Test
  @DisplayName("AC-20 취소(RESERVED 보존): 다른 회원 결제 완료 RESERVED 좌석이면 이전 예약 취소가 좌석을 해제하지 않는다")
  void cancel_otherConfirmedHold_seatReserved_preservesReservedSeat() {
    Pending p = pending();
    fixture.setSeatStatus(p.seat(), ShowtimeSeatStatus.RESERVED);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(p.seat());

    reservationService.cancel(p.reservationId());

    assertThat(fixture.reservationStatus(p.reservationId())).isEqualTo("CANCELLED");
    assertThat(fixture.holdStatus(p.holdId())).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(p.seat())).isEqualTo("RESERVED");
    fixture.assertPaidByOtherPreserved(paid);
  }

  @Test
  @DisplayName("AC-20 만료(HELD 오염): 이전 ACTIVE HOLD와 다른 회원 CONFIRMED HOLD가 함께 있으면 이전 HOLD만 EXPIRED, 좌석·타인 결제 상태 보존")
  void expire_oldActiveHold_otherConfirmedHold_seatHeldContaminated_doesNotReleaseSeat() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member u1 = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long oldHold = fixture.hold(seat, u1, HoldStatus.ACTIVE, expiresAt);
    fixture.reservation(oldHold, ReservationStatus.CANCELLED);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(seat);
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(oldHold));

    assertThatCode(() -> holdExpirationService.expireHolds(now)).doesNotThrowAnyException();
    assertThat(fixture.holdStatus(oldHold)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");
    fixture.assertPaidByOtherPreserved(paid);
  }

  @Test
  @DisplayName("AC-20 만료(RESERVED 보존): 이전 ACTIVE HOLD와 다른 회원의 결제 완료 RESERVED 좌석이 남은 오염 상태에서 좌석을 해제하지 않는다")
  void expire_oldActiveHold_otherPaidReservedSeat_preserved() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    Member u1 = fixture.member();
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Long oldHold = fixture.hold(seat, u1, HoldStatus.ACTIVE, expiresAt);
    fixture.reservation(oldHold, ReservationStatus.CANCELLED);
    ConsistencyFixture.PaidByOther paid = fixture.paidByOther(seat);
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(oldHold));

    assertThatCode(() -> holdExpirationService.expireHolds(now)).doesNotThrowAnyException();
    assertThat(fixture.holdStatus(oldHold)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("RESERVED");
    fixture.assertPaidByOtherPreserved(paid);
  }

  // ===================== AC-9 취소 거절 회귀 (guard) =====================

  @Test
  @DisplayName("회귀: CONFIRMED 예약 직접 취소는 RESERVATION-003으로 거절되고 부분 변경이 없다")
  void cancel_confirmed_rejectedWithoutPartialChange() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    Member u1 = fixture.member();
    Long holdId = fixture.hold(seat, u1, HoldStatus.CONFIRMED, nowSeconds().plusMinutes(1));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.CONFIRMED);
    fixture.payment(reservationId, "SUCCESS", nowSeconds());

    assertThatThrownBy(() -> reservationService.cancel(reservationId))
      .satisfies(e -> assertBusinessError(e, ReservationErrorCode.RESERVATION_CANCEL_REQUIRES_REFUND));
    assertThat(fixture.reservationStatus(reservationId)).isEqualTo("CONFIRMED");
    assertThat(fixture.holdStatus(holdId)).isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("RESERVED");
  }

  @Test
  @DisplayName("회귀: FAILED·CANCELLED 예약 취소는 RESERVATION-004로 거절되고 부분 변경이 없다")
  void cancel_failedOrCancelled_rejectedWithoutPartialChange() {
    SeatRef failedSeat = fixture.seat(ShowtimeSeatStatus.AVAILABLE);
    Member u1 = fixture.member();
    Long failedHold = fixture.hold(failedSeat, u1, HoldStatus.EXPIRED, nowSeconds().plusMinutes(1));
    Long failedReservation = fixture.reservation(failedHold, ReservationStatus.FAILED);

    SeatRef cancelledSeat = fixture.seat(ShowtimeSeatStatus.AVAILABLE);
    Long cancelledHold = fixture.hold(cancelledSeat, u1, HoldStatus.CANCELLED, nowSeconds().plusMinutes(1));
    Long cancelledReservation = fixture.reservation(cancelledHold, ReservationStatus.CANCELLED);

    assertThatThrownBy(() -> reservationService.cancel(failedReservation))
      .satisfies(e -> assertBusinessError(e, ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED));
    assertThatThrownBy(() -> reservationService.cancel(cancelledReservation))
      .satisfies(e -> assertBusinessError(e, ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED));

    assertThat(fixture.reservationStatus(failedReservation)).isEqualTo("FAILED");
    assertThat(fixture.holdStatus(failedHold)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(failedSeat)).isEqualTo("AVAILABLE");
    assertThat(fixture.reservationStatus(cancelledReservation)).isEqualTo("CANCELLED");
    assertThat(fixture.holdStatus(cancelledHold)).isEqualTo("CANCELLED");
    assertThat(fixture.seatStatus(cancelledSeat)).isEqualTo("AVAILABLE");
  }

  @Test
  @DisplayName("회귀: 존재하지 않는 예약 취소는 RESERVATION NOT_FOUND")
  void cancel_notFound() {
    assertThatThrownBy(() -> reservationService.cancel(Long.MAX_VALUE))
      .isInstanceOf(BusinessException.class)
      .satisfies(e -> assertBusinessError(e, ReservationErrorCode.NOT_FOUND));
  }

  // ===================== AC-17 호출 계약 =====================

  @Test
  @DisplayName("AC-17: 기존 트랜잭션 안에서 expireHolds 호출 시 IllegalTransactionStateException")
  void expireHolds_insideExistingTransaction_rejected() {
    LocalDateTime farPast = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    fixture.requireExpirationCandidates(farPast, Set.of());

    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
      status -> holdExpirationService.expireHolds(farPast)))
      .isInstanceOf(IllegalTransactionStateException.class);
  }

  // ===================== AC-19 시간 경계 (guard) =====================

  @Test
  @DisplayName("AC-19: expiresAt=T 기준 now=T-1초·T는 대상 아님, T+1초는 대상 (초 단위 정규화)")
  void expiration_timeBoundary() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member u1 = fixture.member();
    LocalDateTime t = nowSeconds().plusMinutes(10);
    Long holdId = fixture.hold(seat, u1, HoldStatus.ACTIVE, t);

    fixture.requireExpirationCandidates(t.minusSeconds(1), Set.of());
    holdExpirationService.expireHolds(t.minusSeconds(1));
    assertThat(fixture.holdStatus(holdId)).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");

    fixture.requireExpirationCandidates(t, Set.of());
    holdExpirationService.expireHolds(t);
    assertThat(fixture.holdStatus(holdId)).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");

    fixture.requireExpirationCandidates(t.plusSeconds(1), Set.of(holdId));
    holdExpirationService.expireHolds(t.plusSeconds(1));
    assertThat(fixture.holdStatus(holdId)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seat)).isEqualTo("AVAILABLE");
  }
}
