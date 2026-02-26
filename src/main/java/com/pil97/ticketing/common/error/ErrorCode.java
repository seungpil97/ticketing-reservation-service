package com.pil97.ticketing.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ✅ 시스템에서 사용할 "표준 에러 목록"을 모아둔 enum
 * <p>
 * 표준 에러 목록이란?
 * - 어떤 에러가 발생하더라도 HTTP 상태코드 + 내부 에러코드 + 메시지를 "항상 동일한 규칙"으로 내려주기 위한 목록
 * <p>
 * enum을 쓰는 이유
 * 1) 오타 방지: 문자열을 코드 곳곳에 직접 쓰면 오타/불일치가 생김
 * 2) 일관성: status/code/message를 한 곳에서 관리해서 응답 규칙이 통일됨
 * 3) 변경 용이: 메시지/코드 규칙이 바뀌면 enum만 바꾸면 전체가 같이 반영됨
 */
@Getter
public enum ErrorCode {

  /**
   * ✅ 입력값 검증(Validation) 실패
   * - @NotBlank, @Size 같은 Bean Validation에 걸렸을 때 내려줄 대표 에러
   * - HttpStatus.BAD_REQUEST(400): 클라이언트가 잘못 보낸 요청이라는 의미
   * - code: COMMON-001 -> 내부 규칙으로 분기 처리(프론트가 이 코드로 화면 처리하기 쉬움)
   * - message: 사람이 읽을 수 있는 기본 메시지
   */
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON-001", "Validation failed"),

  /**
   * ✅ RequestBody 자체가 파싱/형식 문제로 실패한 경우
   * - 예: JSON 문법 오류, 타입 미스매치(문자인데 숫자 들어옴) 등
   * - 보통 HttpMessageNotReadableException 같은 케이스를 여기에 매핑할 수 있음
   */
  INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON-002", "Invalid request body"),

  /**
   * ✅ 유효하지 않은 요청(공통 400)
   * - 요청은 들어왔지만 "비즈니스적으로 의미 없는/유효하지 않은" 요청을 표현할 때 사용
   * - 예: PATCH 요청에서 변경할 값(name/email)이 하나도 없는 경우
   */
  COMMON_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-003", "Invalid request"),

  /**
   * ✅ 공통 404
   * - 특정 도메인과 상관 없이 "리소스를 찾을 수 없음"을 표현할 때
   * - 예: 없는 엔드포인트, 없는 리소스 접근 등
   */
  COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "Resource not found"),

  /**
   * ✅ 405 Method Not Allowed
   * - 예: POST만 열려있는데 GET으로 호출한 경우
   * - 스프링에서 HttpRequestMethodNotSupportedException으로 잡히는 대표 케이스
   */
  COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405", "Method not allowed"),

  /**
   * ✅ 500 Internal Server Error
   * - 예상하지 못한 모든 예외(NullPointerException 등)
   * - 보통 자세한 내부 내용은 숨기고 이 코드로 통일
   */
  COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "Internal server error"),

  /**
   * ✅ 회원을 찾을 수 없음(도메인 전용 404)
   * - 공통 404와 다르게 "회원 도메인에서 없음"이라는 의미가 분명해짐
   * - 예: PATCH/DELETE/GET /members/{id} 에서 없는 id 접근
   */
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-404", "Member not found"),

  /**
   * ✅ 이메일 중복(도메인 전용 409)
   * - email에 unique 제약이 걸린 상태에서 중복 email로 생성/수정 시 발생
   * - 보통 DataIntegrityViolationException(또는 유사 DB 예외)을 전역 예외에서 캐치해 이 코드로 매핑
   */
  MEMBER_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER-409", "Duplicate email");

  /**
   * ✅ 이 에러가 반환되어야 하는 HTTP 상태 코드 (ResponseEntity 상태코드로 사용)
   */
  private final HttpStatus status;

  /**
   * ✅ 내부 에러 코드(문자열) - 클라이언트가 이 값으로 분기 처리 가능
   */
  private final String code;

  /**
   * ✅ 기본 메시지 - 사람이 읽기 좋은 메시지(필요 시 상세 메시지로 확장 가능)
   */
  private final String message;

  /**
   * ✅ enum 상수마다 status/code/message 값을 고정 세트로 보관
   */
  ErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }
}