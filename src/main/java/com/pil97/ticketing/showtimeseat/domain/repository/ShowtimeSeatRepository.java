package com.pil97.ticketing.showtimeseat.domain.repository;


import com.pil97.ticketing.showtimeseat.application.dto.ShowtimeSeatQueryResult;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, Long> {

  @Query("""
    select new com.pil97.ticketing.showtimeseat.application.dto.ShowtimeSeatQueryResult(
        seat.seatNumber,
        seat.grade,
        sgp.price,
        ss.status
    )
    from ShowtimeSeat ss
    join ss.seat seat
    join ss.showtime st
    join SeatGradePrice sgp
        on sgp.event = st.event
       and sgp.grade = seat.grade
    where ss.showtime.id = :showtimeId
    order by seat.rowLabel asc, seat.seatNo asc
    """)
  List<ShowtimeSeatQueryResult> findSeatSummariesByShowtimeId(Long showtimeId);

  Optional<ShowtimeSeat> findByShowtimeIdAndSeatId(Long showtimeId, Long seatId);

  /**
   * ✅ 비관적 락(Pessimistic Write)을 이용한 좌석 조회 - 회차별 좌석 unique key(showtime_id, seat_id) 기준
   * - HOLD 생성: 트랜잭션의 첫 DB 접근으로 사용하고, 이미 HELD 상태면 validateAvailable에서 예외 처리
   * - 예약 취소·결제 (TASK-062): 잠근 예약 행의 FK(showtime_id, seat_id)로 좌석을 잠근다
   * - 동시 요청 시 하나의 트랜잭션만 락을 획득하고 나머지는 대기
   * - 락을 획득한 트랜잭션이 커밋/롤백되면 다음 대기 트랜잭션이 락 획득
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select ss from ShowtimeSeat ss where ss.showtime.id = :showtimeId and ss.seat.id = :seatId")
  Optional<ShowtimeSeat> findByShowtimeIdAndSeatIdWithLock(@Param("showtimeId") Long showtimeId,
                                                           @Param("seatId") Long seatId);

  /**
   * 회차별 좌석 ID 기준 비관적 락 조회 (TASK-062)
   * - HOLD 만료 항목 트랜잭션의 첫 대상 행 접근 (후보 projection의 좌석 ID 사용)
   * - 좌석 행은 HOLD 생성·취소·결제·만료의 공통 직렬화 지점이다
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select ss from ShowtimeSeat ss where ss.id = :id")
  Optional<ShowtimeSeat> findByIdForUpdate(@Param("id") Long id);
}
