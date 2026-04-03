package com.pil97.ticketing.queue.api.dto.response;

/**
 * 대기 상태 조회 응답 DTO
 * <p>
 * 대기 중 JSON 예시:
 * {
 * "rank": 3,
 * "estimatedWaitSeconds": 90,
 * "admitted": false,
 * "reEnterType": null
 * }
 * <p>
 * 입장 가능 JSON 예시:
 * {
 * "rank": 0,
 * "estimatedWaitSeconds": 0,
 * "admitted": true,
 * "reEnterType": null
 * }
 * <p>
 * 최초 미진입 JSON 예시:
 * {
 * "rank": 0,
 * "estimatedWaitSeconds": 0,
 * "admitted": false,
 * "reEnterType": "NONE"
 * }
 * <p>
 * 토큰 만료 재진입 JSON 예시:
 * {
 * "rank": 0,
 * "estimatedWaitSeconds": 0,
 * "admitted": false,
 * "reEnterType": "EXPIRED"
 * }
 */
public record QueueStatusResponse(
  long rank,
  long estimatedWaitSeconds,
  boolean admitted,
  // 재진입 타입 - null이면 정상 대기 중 또는 입장 허용 상태
  ReEnterType reEnterType
) {

  /**
   * 재진입 타입 열거형
   * NONE: 한 번도 입장 허용된 적 없는 최초 미진입 상태
   * EXPIRED: 입장 토큰이 발급됐으나 TTL 만료된 상태
   */
  public enum ReEnterType {
    NONE,
    EXPIRED
  }

  // 대기 중 상태 생성 팩토리 메서드
  public static QueueStatusResponse ofWaiting(long rank, long estimatedWaitSeconds) {
    return new QueueStatusResponse(rank, estimatedWaitSeconds, false, null);
  }

  // 입장 가능 상태 생성 팩토리 메서드
  public static QueueStatusResponse ofAdmitted() {
    return new QueueStatusResponse(0, 0, true, null);
  }

  // 재진입 필요 상태 생성 팩토리 메서드
  // reEnterType으로 최초 미진입(NONE)과 토큰 만료(EXPIRED)를 구분한다.
  public static QueueStatusResponse ofReEnterRequired(ReEnterType reEnterType) {
    return new QueueStatusResponse(0, 0, false, reEnterType);
  }
}