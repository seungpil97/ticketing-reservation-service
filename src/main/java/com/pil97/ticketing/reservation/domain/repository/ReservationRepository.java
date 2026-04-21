package com.pil97.ticketing.reservation.domain.repository;

import com.pil97.ticketing.reservation.domain.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  /**
   * 비관적 락을 사용한 예약 조회
   * - 동시에 동일 예약에 결제 요청이 들어올 경우 SELECT ... FOR UPDATE로 직렬화
   * - 락 획득 후 PENDING 상태 재검증(validatePayable)으로 중복 결제 차단
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Reservation r WHERE r.id = :id")
  Optional<Reservation> findByIdWithLock(@Param("id") Long id);
}