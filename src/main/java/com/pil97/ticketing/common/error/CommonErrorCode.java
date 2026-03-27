package com.pil97.ticketing.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: COMMON-007)
 * 이 파일은 특정 도메인에 속하지 않는 공통 에러(입력값 검증, 404, 500 등)를 정의하는 enum입니다.
 * ErrorCode 인터페이스를 구현해 GlobalExceptionHandler가 동일하게 처리할 수 있습니다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

  // 입력값 검증 실패 - @NotBlank, @Size 등 Bean Validation 위반
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON-001", "Validation failed"),

  // RequestBody 파싱 실패 - JSON 문법 오류, 타입 미스매치 등
  INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON-002", "Invalid request body"),

  // 비즈니스적으로 유효하지 않은 요청 (공통 400)
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-003", "Invalid request"),

  // 리소스를 찾을 수 없음 (공통 404)
  NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-004", "Resource not found"),

  // HTTP 메서드 불일치 - POST만 열려있는데 GET으로 호출한 경우
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-005", "Method not allowed"),

  // DB 제약조건 위반 - unique, fk, not null 등 무결성 제약 위반
  CONFLICT(HttpStatus.CONFLICT, "COMMON-006", "Data integrity violation"),

  // 예상하지 못한 서버 오류 (공통 500)
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-007", "Internal server error");

  private final HttpStatus status;
  private final String code;
  private final String message;
}