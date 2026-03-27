package com.pil97.ticketing.seat.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 좌석 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: SEAT-002)
 * 이 파일은 좌석(Seat) 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum SeatErrorCode implements ErrorCode {

  // 좌석을 찾을 수 없음 - POST /showtimes/{showtimeId}/hold 에서 없는 seatId 접근
  NOT_FOUND(HttpStatus.NOT_FOUND, "SEAT-001", "Seat not found"),

  // 선점 불가 상태 - 이미 HELD 또는 RESERVED 상태인 좌석 요청
  NOT_AVAILABLE_FOR_HOLD(HttpStatus.CONFLICT, "SEAT-002", "Seat is not available for hold");

  private final HttpStatus status;
  private final String code;
  private final String message;
}