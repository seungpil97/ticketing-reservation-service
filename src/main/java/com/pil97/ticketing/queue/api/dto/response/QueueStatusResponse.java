package com.pil97.ticketing.queue.api.dto.response;

/**
 * 대기 상태 조회 응답 DTO
 * <p>
 * 대기 중 JSON 예시:
 * {
 * "rank": 3,
 * "estimatedWaitSeconds": 90,
 * "admitted": false
 * }
 * <p>
 * 입장 가능 JSON 예시:
 * {
 * "rank": 0,
 * "estimatedWaitSeconds": 0,
 * "admitted": true
 * }
 */
public record QueueStatusResponse(
  long rank,
  long estimatedWaitSeconds,
  boolean admitted
) {

  // 입장 가능 상태 생성 팩토리 메서드
  public static QueueStatusResponse ofAdmitted() {
    return new QueueStatusResponse(0, 0, true);
  }

  // 대기 중 상태 생성 팩토리 메서드
  public static QueueStatusResponse ofWaiting(long rank, long estimatedWaitSeconds) {
    return new QueueStatusResponse(rank, estimatedWaitSeconds, false);
  }
}