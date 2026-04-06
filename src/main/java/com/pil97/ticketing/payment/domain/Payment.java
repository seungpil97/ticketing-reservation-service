package com.pil97.ticketing.payment.domain;

import com.pil97.ticketing.reservation.domain.Reservation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 결제 대상 예약 - 하나의 예약에 하나의 결제
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @Column(nullable = false)
  private int amount;

  // 결제 상태: PENDING -> SUCCESS 또는 FAIL
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  // 결제 완료 시각 - 실패 시 null
  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  private Payment(Reservation reservation, int amount) {
    this.reservation = reservation;
    this.amount = amount;
    // 결제 요청 직후 PENDING 상태로 시작
    this.status = PaymentStatus.PENDING;
  }

  public static Payment create(Reservation reservation, int amount) {
    return new Payment(reservation, amount);
  }

  // 결제 성공 처리 - 상태를 SUCCESS로 전환하고 완료 시각 기록
  public void success() {
    this.status = PaymentStatus.SUCCESS;
    this.paidAt = LocalDateTime.now();
  }

  // 결제 실패 처리 - 상태를 FAIL로 전환, paidAt은 null 유지
  public void fail() {
    this.status = PaymentStatus.FAIL;
  }
}