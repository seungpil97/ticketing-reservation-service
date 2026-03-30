package com.pil97.ticketing.reservation.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

  private final HoldRepository holdRepository;
  private final ReservationRepository reservationRepository;

  /**
   * 예약 확정(RESERVE) 처리
   * - holdId로 HOLD를 조회한다
   * - HOLD가 존재하는지 검증한다
   * - HOLD가 ACTIVE 상태인지 검증한다
   * - HOLD가 만료되지 않았는지 검증한다
   * - 연결된 좌석 상태가 HELD인지 검증한다
   * - 예약 정보를 저장한다
   * - 좌석 상태를 HELD -> RESERVED 로 변경한다
   * - HOLD 상태를 ACTIVE -> CONFIRMED 로 변경한다
   *
   * @param holdId 예약 확정 대상 HOLD ID
   * @return 예약 확정 결과 응답
   */
  @Transactional
  public ReservationResponse reserve(Long holdId) {
    // 1) HOLD 존재 여부 확인
    Hold hold = holdRepository.findById(holdId)
      .orElseThrow(() -> new BusinessException(HoldErrorCode.NOT_FOUND));

    // 2) 예약 가능한 HOLD인지 검증
    validateReservableHold(hold);

    // 3) 연결된 회차별 좌석 조회
    ShowtimeSeat showtimeSeat = hold.getShowtimeSeat();

    // 4) 예약 생성 및 저장
    Reservation reservation = Reservation.create(
      hold,
      showtimeSeat.getShowtime(),
      showtimeSeat.getSeat(),
      hold.getMember()
    );

    Reservation savedReservation = reservationRepository.save(reservation);

    // 5) 좌석 상태를 RESERVED로 변경
    showtimeSeat.markReserved();

    // 6) HOLD 상태를 CONFIRMED로 변경
    hold.confirm();

    // 7) 응답 반환
    return new ReservationResponse(
      savedReservation.getId(),
      hold.getId(),
      showtimeSeat.getShowtime().getId(),
      showtimeSeat.getSeat().getId(),
      hold.getMember().getId(),
      showtimeSeat.getStatus().name(),
      hold.getStatus().name()
    );
  }

  /**
   * 예약 가능한 HOLD인지 검증
   * - ACTIVE 상태여야 한다
   * - 만료 시간이 현재 시각보다 이후여야 한다
   * - 연결된 좌석 상태가 HELD 여야 한다
   */
  private void validateReservableHold(Hold hold) {
    validateActiveHold(hold);
    validateNotExpired(hold);
    validateHeldSeat(hold);
  }

  /**
   * HOLD 상태가 ACTIVE인지 검증
   * - ACTIVE가 아니면 이미 만료되었거나 처리된 HOLD로 판단한다
   */
  private void validateActiveHold(Hold hold) {
    if (hold.getStatus() != HoldStatus.ACTIVE) {
      throw new BusinessException(HoldErrorCode.NOT_ACTIVE);
    }
  }

  /**
   * HOLD가 만료되지 않았는지 검증
   * - expiresAt이 현재 시각보다 이전이거나 같으면 예약 불가
   */
  private void validateNotExpired(Hold hold) {
    LocalDateTime now = LocalDateTime.now();

    if (!hold.getExpiresAt().isAfter(now)) {
      throw new BusinessException(HoldErrorCode.EXPIRED);
    }
  }

  /**
   * 예약 취소 처리
   * - reservationId로 예약을 조회한다
   * - 예약이 존재하는지 검증한다
   * - 예약 상태가 CONFIRMED인지 검증한다
   * - 좌석 상태를 RESERVED -> AVAILABLE 로 변경한다
   * - 예약 상태를 CONFIRMED -> CANCELLED 로 변경한다
   *
   * @param reservationId 취소 대상 예약 ID
   */
  @Transactional
  public void cancel(Long reservationId) {
    // 1) 예약 존재 여부 확인
    Reservation reservation = reservationRepository.findById(reservationId)
      .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));

    // 2) 취소 가능한 예약인지 검증
    validateCancellable(reservation);

    // 3) 좌석 상태를 AVAILABLE로 복구
    reservation.getHold().getShowtimeSeat().markAvailable();

    // 4) 예약 상태를 CANCELLED로 변경
    reservation.cancel();
  }


  /**
   * 연결된 회차별 좌석 상태가 HELD인지 검증
   * - HELD 상태가 아니면 예약 확정할 수 없다
   */
  private void validateHeldSeat(Hold hold) {
    if (hold.getShowtimeSeat().getStatus() != ShowtimeSeatStatus.HELD) {
      throw new BusinessException(ShowtimeSeatErrorCode.NOT_HELD);
    }
  }

  /**
   * 취소 가능한 예약인지 검증
   * - CONFIRMED 상태여야 한다
   */
  private void validateCancellable(Reservation reservation) {
    if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
      throw new BusinessException(ReservationErrorCode.NOT_CONFIRMED);
    }
  }
}