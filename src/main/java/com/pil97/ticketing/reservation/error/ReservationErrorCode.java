package com.pil97.ticketing.reservation.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 예약 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: RESERVATION-002)
 * 이 파일은 예약(Reservation) 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

  // 예약을 찾을 수 없음 - DELETE /reservations/{reservationId} 등
  NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION-001", "Reservation not found"),

  // 취소 불가 상태 - CONFIRMED가 아닌 예약 (이미 CANCELLED된 예약 등)
  NOT_CONFIRMED(HttpStatus.CONFLICT, "RESERVATION-002", "Reservation is not confirmed");

  private final HttpStatus status;
  private final String code;
  private final String message;
}