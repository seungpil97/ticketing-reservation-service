package com.pil97.ticketing.queue.api.dto.response;

/**
 * 대기열 등록 응답 DTO
 * <p>
 * JSON 예시:
 * {
 * "rank": 5,
 * "estimatedWaitSeconds": 150
 * }
 */
public record QueueEnterResponse(
  long rank,
  long estimatedWaitSeconds
) {
}