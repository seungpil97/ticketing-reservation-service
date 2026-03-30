package com.pil97.ticketing.showtimeseat.application.dto;

import com.pil97.ticketing.seat.domain.SeatGrade;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;

public record ShowtimeSeatQueryResult(
    String seatNumber,
    SeatGrade grade,
    int price,
    ShowtimeSeatStatus status
) {
}
