package com.pil97.ticketing.ticketing.domain.repository;


import com.pil97.ticketing.ticketing.domain.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

  List<Showtime> findAllByEventIdOrderByShowAtAsc(Long eventId);
}
