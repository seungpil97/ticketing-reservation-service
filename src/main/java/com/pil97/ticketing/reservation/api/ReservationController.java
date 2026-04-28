package com.pil97.ticketing.reservation.api;

import com.pil97.ticketing.common.ratelimit.RateLimit;
import com.pil97.ticketing.common.ratelimit.RateLimitApi;
import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.infra.idempotency.IdempotencyResult;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.reservation.application.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "6. Reservation", description = "예약 API - 예약 확정 / 예약 취소")
@RestController
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  /**
   * POST /holds/{holdId}/reserve
   *
   * <p>유효한 HOLD를 기반으로 결제 대기 상태의 예약(PENDING)을 생성한다.
   *
   * <p>Rate Limit 정책: 60초 윈도우 내 최대 3회
   * 예약 생성은 DB 상태 전이를 동반하므로 HOLD보다 엄격하게 제한한다.
   *
   * <p>멱등성 정책:
   * Idempotency-Key 헤더 필수.
   * 동일 key + 동일 본문 재요청 시 기존 응답 반환 (HTTP 200).
   * 동일 key + 다른 본문 재요청 시 409 반환.
   */
  @RateLimit(api = RateLimitApi.RESERVATION, limit = 3, windowSeconds = 60)
  @PostMapping("/holds/{holdId}/reserve")
  public ResponseEntity<ApiResponse<ReservationResponse>> reserve(
    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
    @PathVariable Long holdId) {

    IdempotencyResult<ReservationResponse> result = reservationService.reserve(idempotencyKey, holdId);
    HttpStatus status = result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;

    return ResponseEntity
      .status(status)
      .body(ApiResponse.success(result.getResponse()));
  }

  /**
   * DELETE /reservations/{reservationId}
   *
   * <p>예약 확정된 좌석을 취소한다.
   * 성공 시 좌석 상태 AVAILABLE로 복구, 예약 상태 CANCELLED로 변경.
   */
  @DeleteMapping("/reservations/{reservationId}")
  public ResponseEntity<Void> cancel(@PathVariable Long reservationId) {
    reservationService.cancel(reservationId);
    return ResponseEntity.noContent().build();
  }
}