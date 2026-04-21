package com.pil97.ticketing.hold.domain;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "holds")
public class Hold {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "showtime_seat_id", nullable = false)
  private ShowtimeSeat showtimeSeat;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private HoldStatus status;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  private Hold(ShowtimeSeat showtimeSeat, Member member, LocalDateTime expiresAt, HoldStatus status) {
    this.showtimeSeat = showtimeSeat;
    this.member = member;
    this.expiresAt = expiresAt;
    this.status = status;
  }

  public static Hold create(ShowtimeSeat showtimeSeat, Member member, LocalDateTime expiresAt) {
    return new Hold(showtimeSeat, member, expiresAt, HoldStatus.ACTIVE);
  }

  /**
   * HOLD 만료 처리 - 시간 초과 또는 결제 실패/사용자 직접 취소 경로
   * - ACTIVE 상태에서만 EXPIRED 전환 허용
   */
  public void expire() {
    if (this.status != HoldStatus.ACTIVE) {
      throw new BusinessException(HoldErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = HoldStatus.EXPIRED;
  }

  /**
   * HOLD 확정 처리 - 결제 성공 경로
   * - ACTIVE 상태에서만 CONFIRMED 전환 허용
   */
  public void confirm() {
    if (this.status != HoldStatus.ACTIVE) {
      throw new BusinessException(HoldErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = HoldStatus.CONFIRMED;
  }

  /**
   * HOLD 환불 처리 - 환불 경로 전용
   * - CONFIRMED 상태에서만 REFUNDED 전환 허용
   * - 시간 만료(EXPIRED)와 구분하여 환불로 종료된 선점임을 명시
   */
  public void refund() {
    if (this.status != HoldStatus.CONFIRMED) {
      throw new BusinessException(HoldErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = HoldStatus.REFUNDED;
  }
}