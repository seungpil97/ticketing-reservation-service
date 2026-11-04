package com.pil97.ticketing.hold.domain.repository;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {

  List<Hold> findAllByStatusAndExpiresAtBefore(HoldStatus status, LocalDateTime now);

  /**
   * 비관적 락을 이용한 HOLD 단건 조회
   * - 예약 생성 시 동일 HOLD에 대한 동시 요청을 DB 레벨에서 직렬화
   * - 서로 다른 Idempotency-Key로 동시에 들어온 예약 생성 요청이
   * 같은 HOLD를 대상으로 할 때 중복 예약 생성을 방지
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from Hold h where h.id = :id")
  Optional<Hold> findByIdWithLock(@Param("id") Long id);

  /**
   * HOLD가 가리키는 회차별 좌석 ID 조회 (TASK-062)
   * - HOLD 엔티티를 적재하지 않고 좌석 ID만 조회한다
   * - 일관 읽기이므로 취소·결제·만료의 잠금 경로에서는 사용하지 않는다
   *   (취소·결제는 예약 FK, 만료는 후보 projection의 좌석 ID로 좌석을 잠근다)
   */
  @Query("select h.showtimeSeat.id from Hold h where h.id = :holdId")
  Optional<Long> findShowtimeSeatIdById(@Param("holdId") Long holdId);

  /**
   * 대상 HOLD와 같은 좌석의 활성 HOLD를 id 오름차순으로 비관적 락 조회 (TASK-062)
   * - 대상 HOLD는 종료 상태여도 포함한다
   * - 좌석 행 락 획득 이후에 호출해 락 순서(좌석 → HOLD)를 유지한다
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
    select h from Hold h
    where h.showtimeSeat.id = :showtimeSeatId
      and (h.id = :holdId or h.status in :liveStatuses)
    order by h.id asc
    """)
  List<Hold> findTargetAndLiveHoldsForUpdate(
    @Param("showtimeSeatId") Long showtimeSeatId,
    @Param("holdId") Long holdId,
    @Param("liveStatuses") Collection<HoldStatus> liveStatuses
  );

  /**
   * 만료 후보 (HOLD ID, 좌석 ID)를 HOLD id 오름차순으로 조회 (TASK-062)
   * - 잠금 없는 후보 조회이며, 실제 처리 전에 항목별로 잠금 후 재검증한다
   * - 좌석 ID를 함께 가져와 항목 트랜잭션 안에서 좌석 ID 조회(일관 읽기)를 하지 않게 한다
   * - 자체 읽기 전용 트랜잭션으로 실행한다: 트랜잭션 밖(NEVER)의 coordinator에서 호출해도
   *   EntityManager가 coordinator 범위에 묶여 항목 트랜잭션과 공유되지 않게 하기 위함
   */
  @Transactional(readOnly = true)
  @Query("""
    select new com.pil97.ticketing.hold.domain.repository.ExpirationCandidate(h.id, h.showtimeSeat.id)
    from Hold h
    where h.status = :status and h.expiresAt < :now
    order by h.id asc
    """)
  List<ExpirationCandidate> findExpirationCandidates(
    @Param("status") HoldStatus status,
    @Param("now") LocalDateTime now
  );
}