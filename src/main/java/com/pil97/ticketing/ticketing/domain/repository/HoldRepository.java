package com.pil97.ticketing.ticketing.domain.repository;


import com.pil97.ticketing.ticketing.domain.Hold;
import com.pil97.ticketing.ticketing.domain.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface HoldRepository extends JpaRepository<Hold, Long> {
  List<Hold> findAllByStatusAndExpiresAtBefore(HoldStatus status, LocalDateTime now);
}
