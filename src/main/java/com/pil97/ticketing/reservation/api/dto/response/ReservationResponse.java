package com.pil97.ticketing.reservation.api.dto.response;


/**
 * 예약 확정(RESERVE) 성공 시 클라이언트에게 내려줄 응답 DTO
 * <p>
 * JSON 예시:
 * {
 * "reservationId": 1,
 * "holdId": 1,
 * "showtimeId": 1,
 * "seatId": 3,
 * "memberId": 1,
 * "seatStatus": "RESERVED",
 * "holdStatus": "CONFIRMED"
 * }
 */
public record ReservationResponse(
  Long reservationId,
  Long holdId,
  Long showtimeId,
  Long seatId,
  Long memberId,
  String seatStatus,
  String holdStatus
) {
}
