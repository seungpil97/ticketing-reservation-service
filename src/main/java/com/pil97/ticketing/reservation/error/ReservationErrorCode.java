package com.pil97.ticketing.reservation.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 예약 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: RESERVATION-004)
 * 이 파일은 예약(Reservation) 도메인의 에러를 정의하는 enum입니다.
 * 기존 RESERVATION-002(NOT_CONFIRMED) 제거
 */
@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

  // 예약을 찾을 수 없음 - DELETE /reservations/{reservationId} 등
  NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION-001", "Reservation not found"),

  // CONFIRMED 상태 예약 직접 취소 시도 - 환불 경로 사용 안내
  // CONFIRMED 예약은 POST /payments/{paymentId}/refund 를 통해서만 취소 가능
  RESERVATION_CANCEL_REQUIRES_REFUND(HttpStatus.CONFLICT, "RESERVATION-003",
    "Confirmed reservation must be cancelled via refund API"),

  // 취소 불가 상태(FAILED, CANCELLED)에서 취소 시도
  RESERVATION_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "RESERVATION-004",
    "Reservation cannot be cancelled in current status");

  private final HttpStatus status;
  private final String code;
  private final String message;
}