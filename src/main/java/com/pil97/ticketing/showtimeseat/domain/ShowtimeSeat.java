package com.pil97.ticketing.showtimeseat.domain;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "showtime_seat")
public class ShowtimeSeat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "showtime_id", nullable = false)
  private Showtime showtime;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seat_id", nullable = false)
  private Seat seat;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ShowtimeSeatStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public void markHeld() {
    this.status = ShowtimeSeatStatus.HELD;
  }

  /**
   * 좌석 AVAILABLE 복구
   * - HELD(결제 전 취소) 또는 RESERVED(환불) 상태에서만 AVAILABLE 전환 허용
   */
  public void markAvailable() {
    if (this.status != ShowtimeSeatStatus.HELD
      && this.status != ShowtimeSeatStatus.RESERVED) {
      throw new BusinessException(ShowtimeSeatErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = ShowtimeSeatStatus.AVAILABLE;
  }

  /**
   * 좌석 예약 확정
   * - HELD 상태에서만 RESERVED 전환 허용
   * - AVAILABLE 또는 RESERVED 상태에서 호출 시 예외 발생
   */
  public void markReserved() {
    if (this.status != ShowtimeSeatStatus.HELD) {
      throw new BusinessException(ShowtimeSeatErrorCode.INVALID_STATUS_TRANSITION);
    }
    this.status = ShowtimeSeatStatus.RESERVED;
  }
}