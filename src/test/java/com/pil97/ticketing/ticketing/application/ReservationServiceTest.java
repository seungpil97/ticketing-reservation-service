package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.ticketing.api.dto.response.ReservationResponse;
import com.pil97.ticketing.ticketing.domain.Hold;
import com.pil97.ticketing.ticketing.domain.HoldStatus;
import com.pil97.ticketing.ticketing.domain.Reservation;
import com.pil97.ticketing.ticketing.domain.Seat;
import com.pil97.ticketing.ticketing.domain.Showtime;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeat;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.ticketing.domain.repository.HoldRepository;
import com.pil97.ticketing.ticketing.domain.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.pil97.ticketing.common.error.ErrorCode.HOLD_EXPIRED;
import static com.pil97.ticketing.common.error.ErrorCode.HOLD_NOT_ACTIVE;
import static com.pil97.ticketing.common.error.ErrorCode.HOLD_NOT_FOUND;
import static com.pil97.ticketing.common.error.ErrorCode.SHOWTIME_SEAT_NOT_HELD;
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
        .isEqualTo(HOLD_NOT_FOUND);

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
        .isEqualTo(HOLD_NOT_ACTIVE);

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
        .isEqualTo(HOLD_EXPIRED);

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
        .isEqualTo(SHOWTIME_SEAT_NOT_HELD);

      verify(reservationRepository, never()).save(any());
      verify(showtimeSeat, never()).markReserved();
      verify(hold, never()).confirm();
    }
  }
}