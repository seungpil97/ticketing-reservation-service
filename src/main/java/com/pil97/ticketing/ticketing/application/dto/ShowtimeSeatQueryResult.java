package com.pil97.ticketing.ticketing.application.dto;

import com.pil97.ticketing.ticketing.domain.SeatGrade;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeatStatus;

public record ShowtimeSeatQueryResult(
    String seatNumber,
    SeatGrade grade,
    int price,
    ShowtimeSeatStatus status
) {
}
