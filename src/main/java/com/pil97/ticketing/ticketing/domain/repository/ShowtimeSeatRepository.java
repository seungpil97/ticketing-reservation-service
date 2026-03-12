package com.pil97.ticketing.ticketing.domain.repository;


import com.pil97.ticketing.ticketing.application.dto.ShowtimeSeatQueryResult;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, Long> {

  @Query("""
    select new com.pil97.ticketing.ticketing.application.dto.ShowtimeSeatQueryResult(
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
}
