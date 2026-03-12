package com.pil97.ticketing.ticketing.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /showtimes/{showtimeId}/hold 요청 바디 DTO
 */
@Getter
@NoArgsConstructor
public class HoldCreateRequest {

  @NotNull(message = "seatId is required")
  private Long seatId;
}
