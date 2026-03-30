package com.pil97.ticketing.hold.domain;


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

  public void expire() {
    this.status = HoldStatus.EXPIRED;
  }

  public void confirm() {
    this.status = HoldStatus.CONFIRMED;
  }
}
