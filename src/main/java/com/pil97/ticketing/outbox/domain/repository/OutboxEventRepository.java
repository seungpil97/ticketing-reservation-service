package com.pil97.ticketing.outbox.domain.repository;

import com.pil97.ticketing.outbox.domain.OutboxEvent;
import com.pil97.ticketing.outbox.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * 순회 상한 - 지정 상태의 최대 ID (없으면 null)
   */
  @Query("select max(o.id) from OutboxEvent o where o.status = :status")
  Long findMaxIdByStatus(@Param("status") OutboxStatus status);

  /**
   * 순회 범위 (after, upper]의 지정 상태 ID를 오름차순으로 조회 - 잠금 없음, 항목 트랜잭션에서 잠근 뒤 재확인한다
   */
  @Query("""
    select o.id from OutboxEvent o
    where o.status = :status and o.id > :after and o.id <= :upper
    order by o.id
    """)
  List<Long> findIdsInRange(@Param("status") OutboxStatus status, @Param("after") Long after,
                            @Param("upper") Long upper, Limit limit);

  /**
   * Outbox 행 잠금 (SELECT ... FOR UPDATE)
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OutboxEvent o where o.id = :id")
  Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
