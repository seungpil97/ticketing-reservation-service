package com.pil97.ticketing.ticketing.api.dto.response;

import java.time.LocalDateTime;

/**
 * 좌석 선점(HOLD) 성공 시 클라이언트에게 내려줄 응답 DTO
 * <p>
 * JSON 예시:
 * {
 * "holdId": 1,
 * "showtimeId": 1,
 * "seatId": 3,
 * "status": "HELD",
 * "expiresAt": "2026-03-12T10:30:00"
 * }
 */
public record HoldResponse(
  Long holdId,
  Long showtimeId,
  Long seatId,
  String status,
  LocalDateTime expiresAt
) {
}
