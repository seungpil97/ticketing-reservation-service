package com.pil97.ticketing.ticketing.domain.repository;


import com.pil97.ticketing.ticketing.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
