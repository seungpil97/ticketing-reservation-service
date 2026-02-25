package com.pil97.ticketing.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ✅ 시스템에서 사용할 "표준 에러 목록"을 모아둔 enum
 * <p>
 * 여기서 말하는 표준 에러 목록이란?
 * - 어떤 에러가 나면 HTTP 상태코드 + 내부 에러코드 + 메시지를 "항상 동일한 규칙"으로 내려주기 위한 목록
 * <p>
 * 왜 enum을 쓰냐?
 * 1) 오타 방지: "COMMON-404" 같은 문자열을 코드 곳곳에 직접 쓰면 오타/불일치가 생김
 * 2) 일관성: status/code/message를 한 군데에서 관리해서 응답 포맷이 통일됨
 * 3) 변경 용이: 메시지/코드 규칙이 바뀌면 enum만 바꾸면 전체가 같이 바뀜
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
   * - 예: JSON 문법 오류, 타입이 안 맞음(문자인데 숫자 들어옴) 등
   * - 보통 HttpMessageNotReadableException 같은 케이스를 여기에 매핑할 수 있음
   */
  INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "COMMON-002", "Invalid request body"),

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
   * - "일단 서버가 터졌다"를 의미하므로 자세한 내부 내용은 보통 숨기고 이 코드로 통일
   */
  COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "Internal server error"),

  /**
   * ✅ 회원을 찾을 수 없음(도메인 전용 404)
   * - 공통 404와 다르게 "회원 도메인에서 없음"이라는 의미가 분명해짐
   * - 예: GET /members/{id} 에서 없는 id 조회
   */
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-404", "Member not found");

  /**
   * ✅ 이 에러가 반환되어야 하는 HTTP 상태 코드
   * - 예: 400, 404, 500 등
   * - GlobalExceptionHandler에서 이 status를 꺼내 ResponseEntity 상태코드로 사용
   * -- GETTER --
   *  ✅ getter: GlobalExceptionHandler(또는 서비스)에서 상태코드가 필요할 때 사용

   */
  private final HttpStatus status;

  /**
   * ✅ 내부 에러 코드(문자열)
   * - 프론트/클라이언트가 이 값을 보고 분기 처리할 수 있음
   * - 예: "COMMON-001"이면 validation 화면 처리
   * -- GETTER --
   *  ✅ getter: 표준 에러 코드가 필요할 때 사용

   */
  private final String code;

  /**
   * ✅ 기본 메시지
   * - 사람이 읽기 좋은 메시지
   * - 필요하면 나중에 다국어/상세 메시지로 확장 가능
   * -- GETTER --
   *  ✅ getter: 기본 메시지가 필요할 때 사용

   */
  private final String message;

  /**
   * ✅ 각 enum 상수(예: VALIDATION_FAILED)를 만들 때 호출되는 생성자
   * - enum 상수마다 status/code/message 값을 다르게 넣어 "고정 세트"로 보관한다
   */
  ErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

}
