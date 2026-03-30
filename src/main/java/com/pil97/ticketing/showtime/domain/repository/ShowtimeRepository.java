package com.pil97.ticketing.showtime.domain.repository;


import com.pil97.ticketing.showtime.domain.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

  List<Showtime> findAllByEventIdOrderByShowAtAsc(Long eventId);
}
