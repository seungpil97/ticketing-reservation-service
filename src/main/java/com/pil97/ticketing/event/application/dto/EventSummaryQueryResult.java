package com.pil97.ticketing.event.application.dto;

import com.pil97.ticketing.event.domain.EventStatus;

import java.time.LocalDateTime;

public record EventSummaryQueryResult(
    Long id,
    String name,
    String venueName,
    LocalDateTime startAt,
    EventStatus status
) {
}
