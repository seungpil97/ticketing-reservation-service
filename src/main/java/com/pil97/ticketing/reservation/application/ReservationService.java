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
   * 예약 생성 처리 (결제 대기 상태)
   * - holdId로 HOLD를 조회한다
   * - HOLD가 존재하는지 검증한다
   * - HOLD가 ACTIVE 상태인지 검증한다
   * - HOLD가 만료되지 않았는지 검증한다
   * - 연결된 좌석 상태가 HELD인지 검증한다
   * - 예약을 PENDING 상태로 저장한다
   * - 좌석/HOLD 상태 변경은 결제 완료(PaymentService) 시점에 처리한다
   *
   * @param holdId 예약 대상 HOLD ID
   * @return 예약 생성 결과 응답
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

    // 4) 예약 PENDING 상태로 생성 및 저장
    // 좌석/HOLD 상태 변경은 결제 완료 시점(PaymentService)에 처리
    Reservation reservation = Reservation.create(
      hold,
      showtimeSeat.getShowtime(),
      showtimeSeat.getSeat(),
      hold.getMember()
    );

    Reservation savedReservation = reservationRepository.save(reservation);

    // 5) 응답 반환 - 좌석/HOLD 상태는 아직 변경되지 않음
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
   * 예약 취소 처리
   * - PENDING: 결제 전 취소 - 좌석 HELD -> AVAILABLE 복구
   * - CONFIRMED: 결제 후 취소 - 좌석 RESERVED -> AVAILABLE 복구
   *
   * @param reservationId 취소 대상 예약 ID
   */
  @Transactional
  public void cancel(Long reservationId) {
    // 1) 예약 존재 여부 확인
    Reservation reservation = reservationRepository.findById(reservationId)
      .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));

    // 2) 취소 가능한 예약인지 검증 (PENDING, CONFIRMED만 허용)
    validateCancellable(reservation);

    // 3) 좌석 상태를 AVAILABLE로 복구
    reservation.getHold().getShowtimeSeat().markAvailable();

    // 4) 예약 상태를 CANCELLED로 변경
    reservation.cancel();
  }

  /**
   * 예약 가능한 HOLD인지 검증
   */
  private void validateReservableHold(Hold hold) {
    validateActiveHold(hold);
    validateNotExpired(hold);
    validateHeldSeat(hold);
  }

  /**
   * HOLD 상태가 ACTIVE인지 검증
   */
  private void validateActiveHold(Hold hold) {
    if (hold.getStatus() != HoldStatus.ACTIVE) {
      throw new BusinessException(HoldErrorCode.NOT_ACTIVE);
    }
  }

  /**
   * HOLD가 만료되지 않았는지 검증
   */
  private void validateNotExpired(Hold hold) {
    if (!hold.getExpiresAt().isAfter(LocalDateTime.now())) {
      throw new BusinessException(HoldErrorCode.EXPIRED);
    }
  }

  /**
   * 연결된 회차별 좌석 상태가 HELD인지 검증
   */
  private void validateHeldSeat(Hold hold) {
    if (hold.getShowtimeSeat().getStatus() != ShowtimeSeatStatus.HELD) {
      throw new BusinessException(ShowtimeSeatErrorCode.NOT_HELD);
    }
  }

  /**
   * 취소 가능한 예약인지 검증
   * - PENDING: 결제 전 취소 허용
   * - CONFIRMED: 결제 후 취소 허용
   * - FAILED, CANCELLED: 취소 불가
   */
  private void validateCancellable(Reservation reservation) {
    if (reservation.getStatus() != ReservationStatus.PENDING
      && reservation.getStatus() != ReservationStatus.CONFIRMED) {
      throw new BusinessException(ReservationErrorCode.NOT_CONFIRMED);
    }
  }
}