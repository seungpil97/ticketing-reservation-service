package com.pil97.ticketing.queue.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 대기열 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: QUEUE-004)
 * 이 파일은 대기열 도메인의 에러를 정의하는 enum입니다.
 * 대기열 미등록, 입장 토큰 없음/만료, 이벤트 없음 등 대기열 관련 비즈니스 예외를 담당합니다.
 */
@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements ErrorCode {

  // 대기열에 등록되지 않은 유저 - GET /queue/status 호출 시 대기열에 없는 경우
  NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "QUEUE-001", "User is not in queue"),

  // 입장 토큰 없음 - HOLD API 호출 시 입장 토큰이 Redis에 존재하지 않는 경우
  ADMISSION_TOKEN_NOT_FOUND(HttpStatus.FORBIDDEN, "QUEUE-002", "Admission token not found"),

  // 입장 토큰 만료 - 입장 토큰 TTL 30분 초과
  ADMISSION_TOKEN_EXPIRED(HttpStatus.FORBIDDEN, "QUEUE-003", "Admission token has expired"),

  // 대상 이벤트 없음 - 존재하지 않는 eventId로 대기열 등록 시도
  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "QUEUE-004", "Event not found");

  private final HttpStatus status;
  private final String code;
  private final String message;
}