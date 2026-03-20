package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.lock.DistributedLockService;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
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
  private final MemberRepository memberRepository;
  private final DistributedLockService distributedLockService;

  /**
   * ✅ 좌석 선점(HOLD) 진입점
   * - Redis 분산락을 획득한 뒤 실제 선점 로직을 실행한다
   * - 락 키: "hold:seat:{showtimeId}:{seatId}"
   * → 같은 회차의 같은 좌석에 대한 동시 요청만 직렬화되도록 설계
   * - waitTime(3초): 락 획득을 최대 3초 대기
   * - leaseTime(5초): 락 획득 후 최대 5초 유지
   * → 5초 안에 처리가 완료되지 않으면 락 자동 해제
   *
   * @param showtimeId 공연 회차 ID
   * @param request    선점 요청 정보(seatId, memberId)
   * @return HOLD 생성 결과 응답
   */
  @Transactional
  public HoldResponse hold(Long showtimeId, HoldCreateRequest request) {
    return distributedLockService.executeWithLock(
      "hold:seat:" + showtimeId + ":" + request.getSeatId(),
      3L,
      5L,
      () -> processHold(showtimeId, request)
    );
  }


  /**
   * ✅ 좌석 선점(HOLD) 실제 처리
   * - 분산락 획득 후 호출되는 내부 메서드
   * - showtime, seat, showtimeSeat, member 존재 여부를 검증
   * - AVAILABLE 상태의 좌석만 HOLD 가능
   * - 성공 시 HOLD를 생성하고 좌석 상태를 HELD로 변경
   * - HOLD 만료 시간은 현재 시각 기준 5분 뒤로 저장
   * - 비관적 락 없이 분산락 범위 안에서만 실행되므로 동시성 안전
   *
   * @param showtimeId 공연 회차 ID
   * @param request    선점 요청 정보(seatId, memberId)
   * @return HOLD 생성 결과 응답
   */
  @Transactional
  public HoldResponse processHold(Long showtimeId, HoldCreateRequest request) {

    // 1) 회차 존재 여부 확인
    Showtime showtime = showtimeRepository.findById(showtimeId)
      .orElseThrow(() -> new BusinessException(SHOWTIME_NOT_FOUND));

    // 2) 좌석 존재 여부 확인
    Seat seat = seatRepository.findById(request.getSeatId())
      .orElseThrow(() -> new BusinessException(SEAT_NOT_FOUND));

    // 3) 해당 회차에 속한 좌석인지 확인
    //    비관적 락 제거: Redis 분산락이 동시성을 보장하므로 DB 락 불필요
    ShowtimeSeat showtimeSeat = showtimeSeatRepository
      .findByShowtimeIdAndSeatId(showtime.getId(), seat.getId())
      .orElseThrow(() -> new BusinessException(SHOWTIME_SEAT_NOT_FOUND));

    // 4) 회원 존재 여부 확인
    Member member = memberRepository.findById(request.getMemberId())
      .orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));

    // 5) AVAILABLE 상태인지 검증
    validateAvailable(showtimeSeat);

    // 6) HOLD 만료 시간 계산
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiresAt = now.plusMinutes(HOLD_MINUTES);

    // 7) HOLD 저장
    Hold hold = Hold.create(showtimeSeat, member, expiresAt);
    Hold savedHold = holdRepository.save(hold);

    // 8) 좌석 상태를 HELD로 변경
    showtimeSeat.markHeld();

    // 9) 응답 반환
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