package com.pil97.ticketing.reservation.domain;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.showtime.domain.Showtime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReservationTest {

  private Reservation reservation;

  @BeforeEach
  void setUp() {
    // 실제 Reservation 객체 생성 - PENDING 상태로 시작
    reservation = Reservation.create(
      mock(Hold.class),
      mock(Showtime.class),
      mock(Seat.class),
      mock(Member.class)
    );
  }

  // ===================== cancel() - 사용자 직접 취소 =====================

  @Test
  @DisplayName("cancel: PENDING 상태 예약은 취소 성공 후 CANCELLED로 전환된다")
  void cancel_pending_success() {
    // when
    reservation.cancel();

    // then
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
  }

  @Test
  @DisplayName("cancel: CONFIRMED 상태 예약 직접 취소 시도 시 RESERVATION_CANCEL_REQUIRES_REFUND 예외를 던진다")
  void cancel_confirmed_throwsRequiresRefund() {
    // given - CONFIRMED 상태로 전환
    reservation.confirm();

    // when & then
    assertThatThrownBy(() -> reservation.cancel())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_REQUIRES_REFUND);
      });
  }

  @Test
  @DisplayName("cancel: FAILED 상태 예약 취소 시도 시 RESERVATION_CANCEL_NOT_ALLOWED 예외를 던진다")
  void cancel_failed_throwsNotAllowed() {
    // given - FAILED 상태로 전환
    reservation.fail();

    // when & then
    assertThatThrownBy(() -> reservation.cancel())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
      });
  }

  @Test
  @DisplayName("cancel: CANCELLED 상태 예약 재취소 시도 시 RESERVATION_CANCEL_NOT_ALLOWED 예외를 던진다")
  void cancel_alreadyCancelled_throwsNotAllowed() {
    // given - 이미 취소된 상태
    reservation.cancel();

    // when & then
    assertThatThrownBy(() -> reservation.cancel())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
      });
  }

  // ===================== cancelByRefund() - 환불 경로 취소 =====================

  @Test
  @DisplayName("cancelByRefund: CONFIRMED 상태 예약은 환불 취소 성공 후 CANCELLED로 전환된다")
  void cancelByRefund_confirmed_success() {
    // given - CONFIRMED 상태로 전환
    reservation.confirm();

    // when
    reservation.cancelByRefund();

    // then
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
  }

  @Test
  @DisplayName("cancelByRefund: PENDING 상태 예약에 환불 취소 시도 시 RESERVATION_CANCEL_NOT_ALLOWED 예외를 던진다")
  void cancelByRefund_pending_throwsNotAllowed() {
    // when & then
    assertThatThrownBy(() -> reservation.cancelByRefund())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
      });
  }

  @Test
  @DisplayName("cancelByRefund: FAILED 상태 예약에 환불 취소 시도 시 RESERVATION_CANCEL_NOT_ALLOWED 예외를 던진다")
  void cancelByRefund_failed_throwsNotAllowed() {
    // given
    reservation.fail();

    // when & then
    assertThatThrownBy(() -> reservation.cancelByRefund())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
      });
  }
}