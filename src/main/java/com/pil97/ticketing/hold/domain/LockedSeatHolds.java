package com.pil97.ticketing.hold.domain;

import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 좌석 행 잠금 후 잠금 조회한 대상 HOLD와 같은 좌석의 live HOLD 묶음 (TASK-062)
 * - 취소·결제·만료가 좌석 해제 권한과 다른 live HOLD 존재 여부를 같은 기준으로 판정하기 위한 내부 타입
 * - live 상태: ACTIVE, CONFIRMED (결제 완료 HOLD도 좌석의 현재 주체로 본다)
 */
public final class LockedSeatHolds {

  // 같은 좌석의 현재 주체로 보는 HOLD 상태
  public static final Set<HoldStatus> LIVE_STATUSES = EnumSet.of(HoldStatus.ACTIVE, HoldStatus.CONFIRMED);

  private final Hold target;
  private final boolean otherLiveHold;

  private LockedSeatHolds(Hold target, boolean otherLiveHold) {
    this.target = target;
    this.otherLiveHold = otherLiveHold;
  }

  /**
   * 잠금 조회 결과에서 대상 HOLD를 id로 찾는다
   * - 대상이 없으면 empty (좌석과 HOLD의 연결이 맞지 않거나 대상이 삭제된 경우)
   */
  public static Optional<LockedSeatHolds> of(Collection<Hold> lockedHolds, Long targetHoldId) {
    Hold target = null;
    boolean otherLive = false;
    for (Hold hold : lockedHolds) {
      if (targetHoldId.equals(hold.getId())) {
        target = hold;
      } else if (LIVE_STATUSES.contains(hold.getStatus())) {
        otherLive = true;
      }
    }
    return target == null ? Optional.empty() : Optional.of(new LockedSeatHolds(target, otherLive));
  }

  public Hold target() {
    return target;
  }

  /**
   * 같은 좌석에 대상 외 ACTIVE/CONFIRMED HOLD가 있는지
   */
  public boolean hasOtherLiveHold() {
    return otherLiveHold;
  }

  /**
   * 좌석 해제 권한 - 대상 HOLD를 종료하기 전에 계산한다
   * - 대상 HOLD가 ACTIVE이고, 좌석이 HELD이며, 같은 좌석에 다른 live HOLD가 없을 때만 true
   * - 종료 후의 HOLD 상태로 다시 판정하지 않는다
   */
  public boolean ownsSeat(ShowtimeSeat lockedSeat) {
    return target.getStatus() == HoldStatus.ACTIVE
      && lockedSeat.getStatus() == ShowtimeSeatStatus.HELD
      && !otherLiveHold;
  }

  /**
   * 좌석 → 관련 HOLD 잠금 조회 결과를 그대로 받는 편의 생성 (대상 필수)
   */
  public static LockedSeatHolds require(List<Hold> lockedHolds, Long targetHoldId) {
    return of(lockedHolds, targetHoldId).orElseThrow(() -> new IllegalStateException(
      "target hold is not linked to the locked seat: holdId=" + targetHoldId));
  }
}
