package com.pil97.ticketing.event.api.dto;

import com.pil97.ticketing.event.application.dto.EventSummaryQueryResult;

import java.time.LocalDateTime;

import static com.pil97.ticketing.event.domain.EventStatus.ON_SALE;

/**
 * 공연 목록 조회 시 클라이언트에게 내려줄 응답 DTO
 * <p>
 * record를 사용하는 이유:
 * 1) 불변 객체로 응답 데이터를 안전하게 전달할 수 있다
 * 2) 생성자, getter, equals, hashCode, toString 등을 자동 생성해 코드가 간결해진다
 * 3) 조회 전용 데이터 묶음이라는 의도가 명확하다
 * <p>
 * JSON 예시:
 * {
 * "id": 1,
 * "name": "뮤지컬 레미제라블",
 * "venueName": "블루스퀘어",
 * "startAt": "2026-03-15T19:00:00",
 * "bookingOpen": true
 * }
 */
public record EventSummaryResponse(
  Long id,
  String name,
  String venueName,
  LocalDateTime startAt,
  boolean bookingOpen
) {

  public static EventSummaryResponse from(EventSummaryQueryResult result) {
    return new EventSummaryResponse(
      result.id(),
      result.name(),
      result.venueName(),
      result.startAt(),
      result.status() == ON_SALE
    );
  }
}
