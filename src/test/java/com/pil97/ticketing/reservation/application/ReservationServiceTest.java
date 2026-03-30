package com.pil97.ticketing.reservation.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.application.ReservationService;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock
  private HoldRepository holdRepository;

  @Mock
  private ReservationRepository reservationRepository;

  @InjectMocks
  private ReservationService reservationService;

  @Nested
  @DisplayName("reserve")
  class Reserve {

    @Test
    @DisplayName("유효한 HOLD면 예약 확정에 성공한다")
    void reserve_success() {
      // given
      Long holdId = 1L;
      Long reservationId = 1L;
      Long showtimeId = 10L;
      Long seatId = 20L;
      Long memberId = 30L;

      Hold hold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
      Showtime showtime = mock(Showtime.class);
      Seat seat = mock(Seat.class);
      Member member = mock(Member.class);
      Reservation savedReservation = mock(Reservation.class);

      given(holdRepository.findById(holdId)).willReturn(Optional.of(hold));
      given(hold.getId()).willReturn(holdId);

      // 검증 시 ACTIVE, 응답 생성 시 CONFIRMED
      given(hold.getStatus()).willReturn(HoldStatus.ACTIVE, HoldStatus.CONFIRMED);
      given(hold.getExpiresAt()).willReturn(LocalDateTime.now().plusMinutes(5));
      given(hold.getShowtimeSeat()).willReturn(showtimeSeat);
      given(hold.getMember()).willReturn(member);

      // 검증 시 HELD, 응답 생성 시 RESERVED
      given(showtimeSeat.getStatus()).willReturn(ShowtimeSeatStatus.HELD, ShowtimeSeatStatus.RESERVED);
      given(showtimeSeat.getShowtime()).willReturn(showtime);
      given(showtimeSeat.getSeat()).willReturn(seat);

      given(showtime.getId()).willReturn(showtimeId);
      given(seat.getId()).willReturn(seatId);
      given(member.getId()).willReturn(memberId);

      given(savedReservation.getId()).willReturn(reservationId);
      given(reservationRepository.save(any(Reservation.class))).willReturn(savedReservation);

      // when
      ReservationResponse response = reservationService.reserve(holdId);

      // then
      assertThat(response.reservationId()).isEqualTo(reservationId);
      assertThat(response.holdId()).isEqualTo(holdId);
      assertThat(response.showtimeId()).isEqualTo(showtimeId);
      assertThat(response.seatId()).isEqualTo(seatId);
      assertThat(response.memberId()).isEqualTo(memberId);
      assertThat(response.seatStatus()).isEqualTo("RESERVED");
      assertThat(response.holdStatus()).isEqualTo("CONFIRMED");

      verify(reservationRepository).save(any(Reservation.class));
      verify(showtimeSeat).markReserved();
      verify(hold).confirm();
    }

    @Test
    @DisplayName("존재하지 않는 HOLD면 예외가 발생한다")
    void reserve_fail_when_hold_not_found() {
      // given
      Long holdId = 1L;
      given(holdRepository.findById(holdId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(holdId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(HoldErrorCode.NOT_FOUND);

      verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("HOLD 상태가 ACTIVE가 아니면 예외가 발생한다")
    void reserve_fail_when_hold_not_active() {
      // given
      Long holdId = 1L;
      Hold hold = mock(Hold.class);

      given(holdRepository.findById(holdId)).willReturn(Optional.of(hold));
      given(hold.getStatus()).willReturn(HoldStatus.CONFIRMED);

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(holdId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(HoldErrorCode.NOT_ACTIVE);

      verify(reservationRepository, never()).save(any());
      verify(hold, never()).confirm();
    }

    @Test
    @DisplayName("HOLD가 만료되었으면 예외가 발생한다")
    void reserve_fail_when_hold_expired() {
      // given
      Long holdId = 1L;
      Hold hold = mock(Hold.class);

      given(holdRepository.findById(holdId)).willReturn(Optional.of(hold));
      given(hold.getStatus()).willReturn(HoldStatus.ACTIVE);
      given(hold.getExpiresAt()).willReturn(LocalDateTime.now().minusMinutes(1));

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(holdId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(HoldErrorCode.EXPIRED);

      verify(reservationRepository, never()).save(any());
      verify(hold, never()).confirm();
    }

    @Test
    @DisplayName("연결된 좌석 상태가 HELD가 아니면 예외가 발생한다")
    void reserve_fail_when_showtime_seat_not_held() {
      // given
      Long holdId = 1L;
      Hold hold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

      given(holdRepository.findById(holdId)).willReturn(Optional.of(hold));
      given(hold.getStatus()).willReturn(HoldStatus.ACTIVE);
      given(hold.getExpiresAt()).willReturn(LocalDateTime.now().plusMinutes(5));
      given(hold.getShowtimeSeat()).willReturn(showtimeSeat);
      given(showtimeSeat.getStatus()).willReturn(ShowtimeSeatStatus.RESERVED);

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(holdId))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ShowtimeSeatErrorCode.NOT_HELD);

      verify(reservationRepository, never()).save(any());
      verify(showtimeSeat, never()).markReserved();
      verify(hold, never()).confirm();
    }
  }

  @Test
  @DisplayName("CONFIRMED 상태의 예약이면 취소에 성공한다")
  void cancel_success() {
    // given
    Long reservationId = 1L;

    Reservation reservation = mock(Reservation.class);
    Hold hold = mock(Hold.class);
    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
    given(reservation.getStatus()).willReturn(ReservationStatus.CONFIRMED);
    given(reservation.getHold()).willReturn(hold);
    given(hold.getShowtimeSeat()).willReturn(showtimeSeat);

    // when
    reservationService.cancel(reservationId);

    // then
    verify(showtimeSeat).markAvailable();
    verify(reservation).cancel();
  }

  @Test
  @DisplayName("존재하지 않는 예약이면 예외가 발생한다")
  void cancel_fail_when_reservation_not_found() {
    // given
    Long reservationId = 1L;
    given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> reservationService.cancel(reservationId))
      .isInstanceOf(BusinessException.class)
      .extracting("errorCode")
      .isEqualTo(ReservationErrorCode.NOT_FOUND);

    verify(reservationRepository).findById(reservationId);
  }

  @Test
  @DisplayName("이미 취소된 예약이면 예외가 발생한다")
  void cancel_fail_when_reservation_already_cancelled() {
    // given
    Long reservationId = 1L;
    Reservation reservation = mock(Reservation.class);

    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
    given(reservation.getStatus()).willReturn(ReservationStatus.CANCELLED);

    // when & then
    assertThatThrownBy(() -> reservationService.cancel(reservationId))
      .isInstanceOf(BusinessException.class)
      .extracting("errorCode")
      .isEqualTo(ReservationErrorCode.NOT_CONFIRMED);

    verify(reservation, never()).cancel();
  }
}