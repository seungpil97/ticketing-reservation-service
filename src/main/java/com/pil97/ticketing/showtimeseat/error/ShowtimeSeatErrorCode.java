package com.pil97.ticketing.showtimeseat.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 회차별 좌석 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: SHOWTIME-SEAT-002)
 * 이 파일은 회차별 좌석(ShowtimeSeat) 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ShowtimeSeatErrorCode implements ErrorCode {

  // 회차별 좌석 연결 정보 없음 - 해당 회차에 속하지 않는 seatId 요청
  NOT_FOUND(HttpStatus.NOT_FOUND, "SHOWTIME-SEAT-001", "Showtime seat not found"),

  // 예약 가능 상태 아님 - 예약 확정은 HELD 상태의 좌석에 대해서만 가능
  NOT_HELD(HttpStatus.CONFLICT, "SHOWTIME-SEAT-002", "Showtime seat is not held");

  private final HttpStatus status;
  private final String code;
  private final String message;
}