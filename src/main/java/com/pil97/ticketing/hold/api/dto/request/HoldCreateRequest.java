package com.pil97.ticketing.hold.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좌석 선점(HOLD) 요청 바디 DTO
 * - seatId: 선점할 좌석 ID
 * - memberId: 선점 요청 회원 ID
 */
@Getter
@NoArgsConstructor
public class HoldCreateRequest {

  @NotNull(message = "seatId is required")
  private Long seatId;

  @NotNull(message = "memberId is required")
  private Long memberId;
}
