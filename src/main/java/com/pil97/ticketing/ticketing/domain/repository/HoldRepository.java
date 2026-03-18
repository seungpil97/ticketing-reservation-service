package com.pil97.ticketing.ticketing.domain.repository;


import com.pil97.ticketing.ticketing.domain.Hold;
import com.pil97.ticketing.ticketing.domain.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HoldRepository extends JpaRepository<Hold, Long> {
  List<Hold> findAllByStatusAndExpiresAtBefore(HoldStatus status, LocalDateTime now);

  /**
   * ✅ fetch join을 이용한 만료 HOLD 조회
   * - showtimeSeat를 한 번에 같이 로드해서 N+1 방지
   * - HoldExpirationService에서 루프 돌면서 showtimeSeat에 접근하므로
   * 미리 join해서 가져오지 않으면 Hold 수만큼 추가 쿼리 발생
   */
  @Query("select h from Hold h join fetch h.showtimeSeat where h.status = :status and h.expiresAt < :now")
  List<Hold> findAllByStatusAndExpiresAtBeforeWithSeat(
    @Param("status") HoldStatus status,
    @Param("now") LocalDateTime now
  );
}
