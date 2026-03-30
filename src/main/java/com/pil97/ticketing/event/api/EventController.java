package com.pil97.ticketing.event.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.event.api.dto.EventSummaryResponse;
import com.pil97.ticketing.showtime.api.dto.response.ShowtimeResponse;
import com.pil97.ticketing.event.application.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "3. Event", description = "공연 API - 공연 목록 조회 / 회차 목록 조회")
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;

  /**
   * GET /events
   * <p>
   * 이 API의 목적:
   * - 공연 목록을 조회한다.
   * <p>
   * 상태코드 정책:
   * - 조회 성공 시 200 OK
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<EventSummaryResponse>>> getAllEvents() {

    // 서비스 호출: 공연 목록 조회
    List<EventSummaryResponse> responses = eventService.getAllEvents();

    // 200 OK + 표준 응답
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  /**
   * GET /events/{eventId}/showtimes
   * <p>
   * 이 API의 목적:
   * - 특정 공연의 회차 목록을 조회한다.
   * <p>
   * 상태코드 정책:
   * - 조회 성공 시 200 OK
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @GetMapping("/{eventId}/showtimes")
  public ResponseEntity<ApiResponse<List<ShowtimeResponse>>> getShowtimes(
    @PathVariable Long eventId
  ) {

    // 서비스 호출: 특정 공연의 회차 목록 조회
    List<ShowtimeResponse> responses = eventService.getShowtimes(eventId);

    // 200 OK + 표준 응답
    return ResponseEntity.ok(ApiResponse.success(responses));
  }
}
