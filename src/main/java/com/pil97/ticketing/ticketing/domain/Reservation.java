package com.pil97.ticketing.ticketing.domain;

import com.pil97.ticketing.member.domain.Member;
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

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  private Reservation(Hold hold, Showtime showtime, Seat seat, Member member) {
    this.hold = hold;
    this.showtime = showtime;
    this.seat = seat;
    this.member = member;
  }

  public static Reservation create(Hold hold, Showtime showtime, Seat seat, Member member) {
    return new Reservation(hold, showtime, seat, member);
  }
}