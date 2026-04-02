package com.pil97.ticketing.event.domain.repository;


import com.pil97.ticketing.event.application.dto.EventSummaryQueryResult;
import com.pil97.ticketing.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

  @Query("""
    select new com.pil97.ticketing.event.application.dto.EventSummaryQueryResult(
        e.id,
        e.name,
        e.venue,
        min(s.showAt),
        e.status
    )
    from Event e
    join Showtime s on s.event = e
    group by e.id, e.name, e.venue, e.status
    order by min(s.showAt) asc
    """)
  List<EventSummaryQueryResult> findEventSummaries();

  // 종료 시각이 현재보다 이전인 이벤트 목록 조회
  // 스케줄러에서 대기열 자동 삭제 대상 이벤트를 판단할 때 사용
  @Query("select e from Event e where e.endTime is not null and e.endTime < :now")
  List<Event> findEndedEvents(@Param("now") LocalDateTime now);
}
