package com.pil97.ticketing.queue.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대기열 등록 요청 바디 DTO
 * - eventId: 대기열에 등록할 이벤트 ID
 */
@Getter
@NoArgsConstructor
public class EnterQueueRequest {

  @NotNull(message = "eventId is required")
  private Long eventId;
}