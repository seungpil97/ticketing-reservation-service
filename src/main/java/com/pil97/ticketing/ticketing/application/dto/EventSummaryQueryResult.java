package com.pil97.ticketing.ticketing.application.dto;

import com.pil97.ticketing.ticketing.domain.EventStatus;

import java.time.LocalDateTime;

public record EventSummaryQueryResult(
    Long id,
    String name,
    String venueName,
    LocalDateTime startAt,
    EventStatus status
) {
}
