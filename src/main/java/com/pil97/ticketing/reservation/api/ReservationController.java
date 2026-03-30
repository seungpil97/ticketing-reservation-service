package com.pil97.ticketing.reservation.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.reservation.application.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. Reservation", description = "예약 API - 예약 확정 / 예약 취소")
@RestController
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  /**
   * POST /holds/{holdId}/reserve
   * <p>
   * 이 API의 목적:
   * - 유효한 HOLD를 최종 예약 확정(RESERVE)한다.
   * - 성공 시 좌석 상태는 RESERVED로 변경되고, HOLD 상태는 CONFIRMED로 변경된다.
   * <p>
   * 상태코드 정책:
   * - 예약 확정 성공 시 201 Created
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @PostMapping("/holds/{holdId}/reserve")
  public ResponseEntity<ApiResponse<ReservationResponse>> reserve(@PathVariable Long holdId) {

    // 서비스 호출: 예약 확정 처리
    ReservationResponse response = reservationService.reserve(holdId);

    // 201 Created + 표준 응답
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.success(response));
  }


  /**
   * DELETE /reservations/{reservationId}
   * - 예약 확정된 좌석을 취소한다
   * - 성공 시 좌석 상태 AVAILABLE로 복구, 예약 상태 CANCELLED로 변경
   * - 204 No Content
   */
  @DeleteMapping("/reservations/{reservationId}")
  public ResponseEntity<Void> cancel(@PathVariable Long reservationId) {
    reservationService.cancel(reservationId);
    return ResponseEntity.noContent().build();
  }
}
