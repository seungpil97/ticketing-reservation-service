package com.pil97.ticketing.queue.api.dto.response;

/**
 * 대기 상태 조회 응답 DTO
 * <p>
 * 대기 중 JSON 예시:
 * {
 * "rank": 3,
 * "estimatedWaitSeconds": 90,
 * "admitted": false,
 * "reEnterRequired": false
 * }
 * <p>
 * 입장 가능 JSON 예시:
 * {
 * "rank": 0,
 * "estimatedWaitSeconds": 0,
 * "admitted": true,
 * "reEnterRequired": false
 * }
 * <p>
 * 재등록 필요 JSON 예시:
 * {
 * "rank": 0,
 * "estimatedWaitSeconds": 0,
 * "admitted": false,
 * "reEnterRequired": true
 * }
 */
public record QueueStatusResponse(
  long rank,
  long estimatedWaitSeconds,
  boolean admitted,
  // 입장 토큰 만료 + 대기열 미등록 상태 - 클라이언트에서 재등록 유도 UI 표시에 사용
  boolean reEnterRequired
) {

  // 대기 중 상태 생성 팩토리 메서드
  public static QueueStatusResponse ofWaiting(long rank, long estimatedWaitSeconds) {
    return new QueueStatusResponse(rank, estimatedWaitSeconds, false, false);
  }

  // 입장 가능 상태 생성 팩토리 메서드
  public static QueueStatusResponse ofAdmitted() {
    return new QueueStatusResponse(0, 0, true, false);
  }

  // 재등록 필요 상태 생성 팩토리 메서드
  // 입장 토큰 만료 후 대기열에도 없는 경우 반환
  public static QueueStatusResponse ofReEnterRequired() {
    return new QueueStatusResponse(0, 0, false, true);
  }
}