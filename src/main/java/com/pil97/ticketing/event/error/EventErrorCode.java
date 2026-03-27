package com.pil97.ticketing.event.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공연 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: EVENT-001)
 * 이 파일은 공연(Event) 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements ErrorCode {

  // 공연을 찾을 수 없음 - GET /events/{eventId}/showtimes 등
  NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT-001", "Event not found");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
