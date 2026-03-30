package com.pil97.ticketing.event.domain.repository;


import com.pil97.ticketing.event.application.dto.EventSummaryQueryResult;
import com.pil97.ticketing.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

}
