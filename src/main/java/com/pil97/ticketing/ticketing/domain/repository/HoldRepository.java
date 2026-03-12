package com.pil97.ticketing.ticketing.domain.repository;


import com.pil97.ticketing.ticketing.domain.Hold;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRepository extends JpaRepository<Hold, Long> {
}
