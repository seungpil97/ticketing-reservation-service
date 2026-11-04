package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.lock.DistributedLockService;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import com.pil97.ticketing.queue.application.QueueService;
import com.pil97.ticketing.seat.error.SeatErrorCode;
import com.pil97.ticketing.showtime.error.ShowtimeErrorCode;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import com.pil97.ticketing.hold.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.hold.api.dto.response.HoldResponse;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.seat.domain.repository.SeatRepository;
import com.pil97.ticketing.showtime.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldService {

  /**
   * HOLD 기본 만료 시간(분)
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
  private final QueueService queueService;

  /**
   * 좌석 선점(HOLD) 진입점
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

    // 입장 토큰 검증 - 대기열을 통해 입장 허용된 유저만 HOLD 가능
    queueService.validateAdmissionToken(request.getMemberId());

    return distributedLockService.executeWithLock(
      "hold:seat:" + showtimeId + ":" + request.getSeatId(),
      3L,
      5L,
      () -> processHold(showtimeId, request)
    );
  }


  /**
   * 좌석 선점(HOLD) 실제 처리
   * - 분산락 획득 후 호출되는 내부 메서드
   * - showtime, seat, showtimeSeat, member 존재 여부를 검증
   * - AVAILABLE 상태의 좌석만 HOLD 가능
   * - 성공 시 HOLD를 생성하고 좌석 상태를 HELD로 변경
   * - HOLD 만료 시간은 현재 시각 기준 5분 뒤로 저장
   * - 좌석 행 비관적 락 (TASK-062): Redis 분산락은 DB commit 전에 풀릴 수 있으므로
   *   트랜잭션의 첫 DB 접근으로 회차별 좌석 unique key(showtime_id, seat_id)를 잠그고 commit/rollback까지 유지한다
   *   - 잠금 읽기로 최신 좌석 상태를 확인한다 (일관 읽기로 read view를 먼저 만들지 않음)
   *   - 회차·좌석 존재 확인은 좌석 잠금 조회가 비었을 때만 수행해 기존 오류 우선순위(회차 → 좌석 → 회차별 좌석)를 유지한다
   *
   * @param showtimeId 공연 회차 ID
   * @param request    선점 요청 정보(seatId, memberId)
   * @return HOLD 생성 결과 응답
   */
  @Transactional
  public HoldResponse processHold(Long showtimeId, HoldCreateRequest request) {

    // 1) 회차별 좌석 잠금 조회 - 2)·3)의 회차·좌석 존재 확인은 연결 정보가 없을 때만 오류 구분용으로 수행
    ShowtimeSeat showtimeSeat = showtimeSeatRepository
      .findByShowtimeIdAndSeatIdWithLock(showtimeId, request.getSeatId())
      .orElseThrow(() -> showtimeSeatNotFound(showtimeId, request.getSeatId()));

    // 4) 회원 존재 여부 확인
    Member member = memberRepository.findById(request.getMemberId())
      .orElseThrow(() -> new BusinessException(SeatErrorCode.NOT_AVAILABLE_FOR_HOLD));

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
      showtimeId,
      request.getSeatId(),
      showtimeSeat.getStatus().name(),
      savedHold.getExpiresAt()
    );
  }

  /**
   * 회차별 좌석이 없을 때 기존 우선순위로 오류 구분
   * - 2) 회차 없음 → 3) 좌석 없음 → 회차에 속하지 않는 좌석
   */
  private BusinessException showtimeSeatNotFound(Long showtimeId, Long seatId) {
    if (!showtimeRepository.existsById(showtimeId)) {
      return new BusinessException(ShowtimeErrorCode.NOT_FOUND);
    }
    if (!seatRepository.existsById(seatId)) {
      return new BusinessException(SeatErrorCode.NOT_FOUND);
    }
    return new BusinessException(ShowtimeSeatErrorCode.NOT_FOUND);
  }

  /**
   * 좌석이 선점 가능한 상태인지 검증
   * - AVAILABLE 상태가 아니면 HOLD 불가
   * - 이미 HELD 또는 RESERVED 상태인 경우 409 예외 처리
   */
  private void validateAvailable(ShowtimeSeat showtimeSeat) {
    if (showtimeSeat.getStatus() != ShowtimeSeatStatus.AVAILABLE) {
      throw new BusinessException(SeatErrorCode.NOT_AVAILABLE_FOR_HOLD);
    }
  }
}