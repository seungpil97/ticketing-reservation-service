package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.LockedSeatHolds;
import com.pil97.ticketing.hold.domain.repository.ExpirationCandidate;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * HOLD 만료 항목 처리 - 항목 하나를 독립 트랜잭션으로 처리한다 (TASK-062)
 * - HoldExpirationService(coordinator)와 다른 bean이어야 트랜잭션 프록시가 적용된다 (self-invocation 금지)
 * - commit flush까지 이 트랜잭션 경계에 포함되므로, 항목 장애는 이 항목만 rollback한 뒤 호출자에게 전파된다
 * - 첫 대상 행 접근은 후보의 좌석 ID로 잡는 좌석 행 잠금이다 (HOLD·좌석 ID를 일관 읽기로 먼저 조회하지 않음)
 */
@Component
@RequiredArgsConstructor
public class HoldExpirationItemProcessor {

  private final HoldRepository holdRepository;
  private final ShowtimeSeatRepository showtimeSeatRepository;

  /**
   * 잠금 순서: 좌석(id) → 대상 및 같은 좌석의 live HOLD (id 오름차순)
   * - 잠금 후 대상이 ACTIVE이고 expiresAt < now일 때만 만료한다 (아니면 건너뜀)
   * - 좌석 해제 권한은 HOLD 종료 전에 계산한다 (대상 ACTIVE + 좌석 HELD + 다른 live HOLD 없음)
   *
   * @return 만료 처리했으면 true, 만료 대상이 아니어서 건너뛰었으면 false
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean expire(ExpirationCandidate candidate, LocalDateTime now) {
    Optional<ShowtimeSeat> lockedSeat = showtimeSeatRepository.findByIdForUpdate(candidate.showtimeSeatId());
    if (lockedSeat.isEmpty()) {
      return false;
    }
    ShowtimeSeat showtimeSeat = lockedSeat.get();

    Optional<LockedSeatHolds> lockedHolds = LockedSeatHolds.of(
      holdRepository.findTargetAndLiveHoldsForUpdate(
        showtimeSeat.getId(), candidate.holdId(), LockedSeatHolds.LIVE_STATUSES),
      candidate.holdId());
    if (lockedHolds.isEmpty()) {
      return false;
    }
    LockedSeatHolds holds = lockedHolds.get();
    Hold hold = holds.target();

    // 잠금 후 재검증 - 후보 조회 이후 결제·취소·다른 만료가 커밋했을 수 있다
    if (hold.getStatus() != HoldStatus.ACTIVE || !hold.getExpiresAt().isBefore(now)) {
      return false;
    }

    // HOLD 종료 전 해제 권한 계산
    boolean releaseSeat = holds.ownsSeat(showtimeSeat);
    hold.expire();
    if (releaseSeat) {
      showtimeSeat.markAvailable();
    }
    return true;
  }
}
