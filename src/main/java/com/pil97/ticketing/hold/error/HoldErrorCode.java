package com.pil97.ticketing.hold.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 선점 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: HOLD-003)
 * 이 파일은 선점(Hold) 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum HoldErrorCode implements ErrorCode {

  // HOLD를 찾을 수 없음 - POST /holds/{holdId}/reserve 에서 없는 holdId 접근
  NOT_FOUND(HttpStatus.NOT_FOUND, "HOLD-001", "Hold not found"),

  // 예약 확정 불가 상태 - ACTIVE가 아닌 HOLD (EXPIRED, CONFIRMED 등)
  NOT_ACTIVE(HttpStatus.CONFLICT, "HOLD-002", "Hold is not active"),

  // HOLD 만료 - expiresAt 초과 후 예약 확정 요청
  EXPIRED(HttpStatus.CONFLICT, "HOLD-003", "Hold is expired");

  private final HttpStatus status;
  private final String code;
  private final String message;
}