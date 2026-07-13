package com.pil97.ticketing.hold.api;

import com.pil97.ticketing.common.ratelimit.RateLimit;
import com.pil97.ticketing.common.ratelimit.RateLimitApi;
import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.hold.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.hold.api.dto.response.HoldResponse;
import com.pil97.ticketing.hold.application.HoldService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "5. Hold", description = "좌석 선점 API - 회차 좌석 선점(HOLD)")
@RestController
@RequiredArgsConstructor
public class HoldController {

  private final HoldService holdService;

  /**
   * POST /showtimes/{showtimeId}/hold
   *
   * <p>특정 회차의 좌석을 일정 시간 동안 선점(HOLD)한다.
   *
   * <p>Rate Limit 정책: 60초 윈도우 내 최대 5회
   * HOLD는 DB Lock과 Redis 분산락이 중복 선점을 막지만
   * 반복 요청 자체의 부하를 줄이기 위해 제한을 적용한다.
   */
  @RateLimit(api = RateLimitApi.HOLD, limit = 5, windowSeconds = 60)
  @PostMapping("/showtimes/{showtimeId}/hold")
  public ResponseEntity<ApiResponse<HoldResponse>> hold(
    @PathVariable Long showtimeId,
    @Valid @RequestBody HoldCreateRequest request
  ) {
    HoldResponse response = holdService.hold(showtimeId, request);
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.success(response));
  }
}