package com.pil97.ticketing.ticketing.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.ticketing.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.ticketing.api.dto.response.HoldResponse;
import com.pil97.ticketing.ticketing.application.HoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class HoldController {

  private final HoldService holdService;

  /**
   * ✅ POST /showtimes/{showtimeId}/hold
   * <p>
   * 이 API의 목적:
   * - 특정 회차의 좌석을 일정 시간 동안 선점(HOLD)한다.
   * <p>
   * 상태코드 정책:
   * - 선점 성공 시 201 Created
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @PostMapping("/showtimes/{showtimeId}/hold")
  public ResponseEntity<ApiResponse<HoldResponse>> hold(
    @PathVariable Long showtimeId,
    @Valid @RequestBody HoldCreateRequest request
  ) {

    // ✅ 서비스 호출: 좌석 선점 처리
    HoldResponse response = holdService.hold(showtimeId, request);

    // ✅ 201 Created + 표준 응답
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.success(response));
  }
}