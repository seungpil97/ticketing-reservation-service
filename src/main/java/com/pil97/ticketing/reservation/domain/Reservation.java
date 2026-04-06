package com.pil97.ticketing.reservation.domain;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.showtime.domain.Showtime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservations")
public class Reservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hold_id", nullable = false)
  private Hold hold;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "showtime_id", nullable = false)
  private Showtime showtime;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_id", nullable = false)
  private Seat seat;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  /**
   * ✅ 예약 상태
   * - CONFIRMED: 예약 확정 상태
   * - CANCELLED: 취소 상태
   * - 예약 생성 시 기본값 CONFIRMED
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationStatus status;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  private Reservation(Hold hold, Showtime showtime, Seat seat, Member member) {
    this.hold = hold;
    this.showtime = showtime;
    this.seat = seat;
    this.member = member;
    // 결제 완료 전까지 PENDING 상태로 생성
    this.status = ReservationStatus.PENDING;
  }

  public static Reservation create(Hold hold, Showtime showtime, Seat seat, Member member) {
    return new Reservation(hold, showtime, seat, member);
  }

  // 결제 성공 시 CONFIRMED로 전환
  public void confirm() {
    this.status = ReservationStatus.CONFIRMED;
  }

  // 결제 실패 시 FAILED로 전환
  public void fail() {
    this.status = ReservationStatus.FAILED;
  }

  // 예약 취소 - CONFIRMED 상태에서만 가능
  public void cancel() {
    this.status = ReservationStatus.CANCELLED;
  }
}