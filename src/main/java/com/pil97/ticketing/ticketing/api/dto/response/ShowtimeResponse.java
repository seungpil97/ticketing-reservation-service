package com.pil97.ticketing.ticketing.api.dto.response;

import com.pil97.ticketing.ticketing.domain.Showtime;

import java.time.LocalDateTime;

/**
 * 특정 공연의 회차 목록 조회 시 클라이언트에게 내려줄 응답 DTO
 * <p>
 * JSON 예시:
 * {
 * "id": 1,
 * "showAt": "2026-03-15T19:00:00"
 * }
 */
public record ShowtimeResponse(
  Long id,
  LocalDateTime showAt
) {

  public static ShowtimeResponse from(
    Showtime showtime
  ) {

    return new ShowtimeResponse(
      showtime.getId(),
      showtime.getShowAt()
    );
  }
}
