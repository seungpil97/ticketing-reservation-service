package com.pil97.ticketing.ticketing.domain.repository;

import com.pil97.ticketing.ticketing.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
