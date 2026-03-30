package com.pil97.ticketing.showtime.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.showtime.api.dto.response.ShowtimeSeatResponse;
import com.pil97.ticketing.showtime.application.ShowtimeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "4. Showtime", description = "회차 API - 회차 좌석 목록 조회")
@RestController
@RequestMapping("/showtimes")
@RequiredArgsConstructor
public class ShowtimeController {

  private final ShowtimeService showtimeService;

  /**
   * GET /showtimes/{showtimeId}/seats
   * <p>
   * 이 API의 목적:
   * - 특정 회차의 좌석 목록을 조회한다.
   * <p>
   * 상태코드 정책:
   * - 조회 성공 시 200 OK
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @GetMapping("/{showtimeId}/seats")
  public ResponseEntity<ApiResponse<List<ShowtimeSeatResponse>>> getSeats(
    @PathVariable Long showtimeId
  ) {

    // 서비스 호출: 특정 회차의 좌석 목록 조회
    List<ShowtimeSeatResponse> responses = showtimeService.getSeats(showtimeId);

    // 200 OK + 표준 응답
    return ResponseEntity.ok(ApiResponse.success(responses));
  }
}
