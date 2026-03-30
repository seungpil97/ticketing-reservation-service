package com.pil97.ticketing.showtime.api.dto.response;

import com.pil97.ticketing.showtimeseat.application.dto.ShowtimeSeatQueryResult;

/**
 * 특정 회차의 좌석 목록 조회 시 클라이언트에게 내려줄 응답 DTO
 * <p>
 * JSON 예시:
 * {
 * "seatNumber": "A1",
 * "grade": "VIP",
 * "price": 150000,
 * "status": "AVAILABLE"
 * }
 */
public record ShowtimeSeatResponse(
  String seatNumber,
  String grade,
  int price,
  String status
) {
  public static ShowtimeSeatResponse from(ShowtimeSeatQueryResult result) {
    return new ShowtimeSeatResponse(
      result.seatNumber(),
      result.grade().name(),
      result.price(),
      result.status().name()
    );
  }
}
