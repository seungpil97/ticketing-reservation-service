package com.pil97.ticketing.payment.domain.repository;

import com.pil97.ticketing.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  // 예약 ID로 결제 조회 - TASK-036 멱등성 구현 시 중복 결제 여부 확인에 활용
  Optional<Payment> findByReservationId(Long reservationId);
}