package com.pil97.ticketing.showtime.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 회차 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: SHOWTIME-001)
 * 이 파일은 회차(Showtime) 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum ShowtimeErrorCode implements ErrorCode {

  // 회차를 찾을 수 없음 - GET /showtimes/{showtimeId}/seats 등
  NOT_FOUND(HttpStatus.NOT_FOUND, "SHOWTIME-001", "Showtime not found");

  private final HttpStatus status;
  private final String code;
  private final String message;
}