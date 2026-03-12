package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.ticketing.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.ticketing.api.dto.response.HoldResponse;
import com.pil97.ticketing.ticketing.domain.*;
import com.pil97.ticketing.ticketing.domain.repository.HoldRepository;
import com.pil97.ticketing.ticketing.domain.repository.SeatRepository;
import com.pil97.ticketing.ticketing.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.ticketing.domain.repository.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.pil97.ticketing.common.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldService {

  /**
   * ✅ HOLD 기본 만료 시간(분)
   * - 이번 TASK에서는 고정 5분으로 처리
   * - 추후 설정값 분리(application.yml) 또는 정책화 가능
   */
  private static final long HOLD_MINUTES = 5L;

  private final HoldRepository holdRepository;
  private final ShowtimeRepository showtimeRepository;
  private final SeatRepository seatRepository;
  private final ShowtimeSeatRepository showtimeSeatRepository;


  /**
   * ✅ 좌석 선점(HOLD) 처리
   * - showtime, seat, showtimeSeat 존재 여부를 검증
   * - AVAILABLE 상태의 좌석만 HOLD 가능
   * - 성공 시 HOLD를 생성하고 좌석 상태를 HELD로 변경
   * - HOLD 만료 시간은 현재 시각 기준 5분 뒤로 저장
   *
   * @param showtimeId 공연 회차 ID
   * @param request    선점 요청 정보(seatId)
   * @return HOLD 생성 결과 응답
   */
  @Transactional
  public HoldResponse hold(Long showtimeId, HoldCreateRequest request) {

    // 1) 회차 존재 여부 확인
    Showtime showtime = showtimeRepository.findById(showtimeId)
      .orElseThrow(() -> new BusinessException(SHOWTIME_NOT_FOUND));

    // 2) 좌석 존재 여부 확인
    Seat seat = seatRepository.findById(request.getSeatId())
      .orElseThrow(() -> new BusinessException(SEAT_NOT_FOUND));

    // 3) 해당 회차에 속한 좌석인지 확인
    ShowtimeSeat showtimeSeat = showtimeSeatRepository
      .findByShowtimeIdAndSeatId(showtime.getId(), seat.getId())
      .orElseThrow(() -> new BusinessException(SHOWTIME_SEAT_NOT_FOUND));

    // 4) AVAILABLE 상태인지 검증
    validateAvailable(showtimeSeat);

    // 5) HOLD 만료 시간 계산
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(HOLD_MINUTES);

    // 6) HOLD 저장
    Hold hold = Hold.create(showtimeSeat, expiresAt);
    Hold savedHold = holdRepository.save(hold);

    // 7) 좌석 상태를 HELD로 변경
    showtimeSeat.markHeld();

    // 8) 응답 반환
    return new HoldResponse(
      savedHold.getId(),
      showtime.getId(),
      seat.getId(),
      showtimeSeat.getStatus().name(),
      savedHold.getExpiresAt()
    );
  }

  /**
   * ✅ 좌석이 선점 가능한 상태인지 검증
   * - AVAILABLE 상태가 아니면 HOLD 불가
   * - 이미 HELD 또는 RESERVED 상태인 경우 409 예외 처리
   */
  private void validateAvailable(ShowtimeSeat showtimeSeat) {
    if (showtimeSeat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
      throw new BusinessException(SEAT_NOT_AVAILABLE_FOR_HOLD);
    }
  }
}