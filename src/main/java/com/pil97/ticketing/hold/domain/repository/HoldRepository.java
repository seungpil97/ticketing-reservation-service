package com.pil97.ticketing.hold.domain.repository;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {

  List<Hold> findAllByStatusAndExpiresAtBefore(HoldStatus status, LocalDateTime now);

  /**
   * fetch join을 이용한 만료 HOLD 조회
   * - showtimeSeat를 한 번에 같이 로드해서 N+1 방지
   * - HoldExpirationService에서 루프 돌면서 showtimeSeat에 접근하므로
   * 미리 join해서 가져오지 않으면 Hold 수만큼 추가 쿼리 발생
   */
  @Query("select h from Hold h join fetch h.showtimeSeat where h.status = :status and h.expiresAt < :now")
  List<Hold> findAllByStatusAndExpiresAtBeforeWithSeat(
    @Param("status") HoldStatus status,
    @Param("now") LocalDateTime now
  );

  /**
   * 비관적 락을 이용한 HOLD 단건 조회
   * - 예약 생성 시 동일 HOLD에 대한 동시 요청을 DB 레벨에서 직렬화
   * - 서로 다른 Idempotency-Key로 동시에 들어온 예약 생성 요청이
   * 같은 HOLD를 대상으로 할 때 중복 예약 생성을 방지
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from Hold h where h.id = :id")
  Optional<Hold> findByIdWithLock(@Param("id") Long id);
}