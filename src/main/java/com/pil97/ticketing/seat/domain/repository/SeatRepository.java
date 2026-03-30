package com.pil97.ticketing.seat.domain.repository;


import com.pil97.ticketing.seat.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
