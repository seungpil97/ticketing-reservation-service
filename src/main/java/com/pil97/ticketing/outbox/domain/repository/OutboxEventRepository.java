package com.pil97.ticketing.outbox.domain.repository;

import com.pil97.ticketing.outbox.domain.OutboxEvent;
import com.pil97.ticketing.outbox.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * 순회 상한 - 지금 처리 대상(다음 시도 시각이 없거나 now 이하)인 지정 상태의 최대 ID (없으면 null)
   */
  @Query("""
    select max(o.id) from OutboxEvent o
    where o.status = :status and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)
    """)
  Long findMaxDueId(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now);

  /**
   * 순회 범위 (after, upper]의 처리 대상 [ID, 상태 버전]을 ID 오름차순으로 조회 - 잠금 없음, 항목 트랜잭션에서 잠근 뒤 재확인한다
   * (TASK-033-1) 상태 버전은 실패 기록의 회차 토큰으로 쓴다
   */
  @Query("""
    select o.id, o.stateVersion from OutboxEvent o
    where o.status = :status and o.id > :after and o.id <= :upper
      and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)
    order by o.id
    """)
  List<Object[]> findDueCandidatesInRange(@Param("status") OutboxStatus status, @Param("after") Long after,
                                          @Param("upper") Long upper, @Param("now") LocalDateTime now, Limit limit);

  /**
   * Outbox 행 잠금 (SELECT ... FOR UPDATE)
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OutboxEvent o where o.id = :id")
  Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

  // ===================== 처리 지연 관측 (TASK-033-1) =====================

  boolean existsByStatusIn(List<OutboxStatus> statuses);

  @Query("""
    select count(o) from OutboxEvent o
    where o.status = :status and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)
    """)
  long countDue(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now);

  @Query("select count(o) from OutboxEvent o where o.status = :status and o.nextAttemptAt > :now")
  long countBackoff(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now);

  long countByStatus(OutboxStatus status);

  @Query("""
    select min(o.createdAt) from OutboxEvent o
    where o.status = :status and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)
    """)
  LocalDateTime findOldestDueCreatedAt(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now);

  @Query("select min(o.createdAt) from OutboxEvent o where o.status = :status")
  LocalDateTime findOldestCreatedAt(@Param("status") OutboxStatus status);
}
