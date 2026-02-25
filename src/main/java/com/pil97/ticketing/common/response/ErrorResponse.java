package com.pil97.ticketing.common.response;

import java.time.LocalDateTime;

/**
 * ✅ 실패했을 때 내려줄 에러 정보 DTO
 * <p>
 * - code: 에러 코드 (예: COMMON-001)
 * - message: 사람 읽기 좋은 메시지
 * - path: 어떤 API 경로에서 에러가 났는지
 * - timestamp: 에러 응답 생성 시각
 * - details: (선택) validation 같은 경우 필드별 에러를 넣어주면 좋음
 */
public class ErrorResponse {

  /**
   * ✅ 시스템에서 정의한 에러 코드
   * - 프론트/클라가 "문자열 코드"로 분기 처리하기 좋음
   */
  private final String code;

  /**
   * ✅ 사용자/개발자가 읽을 수 있는 메시지
   */
  private final String message;

  /**
   * ✅ 요청 경로
   * 예: /members
   * - 어디서 터졌는지 로그/디버깅에 유리
   */
  private final String path;

  /**
   * ✅ 에러 응답 생성 시각
   */
  private final LocalDateTime timestamp;

  /**
   * ✅ (선택) 추가 상세 정보
   * - Validation 에러일 때: 필드별 에러 리스트
   * - 그 외에도 필요하면 확장 가능
   * <p>
   * Day2는 "일단 적용됨"이 중요하니까 타입을 Object로 두고,
   * 나중에 List<FieldErrorDetail> 같은 타입으로 고도화해도 됨.
   */
  private final Object details;

  /**
   * ✅ 생성자를 private으로 막아두는 이유
   * - 밖에서 new로 만들기보다 of(...) 팩토리로 통일하려고
   */
  private ErrorResponse(String code, String message, String path, Object details) {
    this.code = code;
    this.message = message;
    this.path = path;
    this.details = details;
    this.timestamp = LocalDateTime.now();
  }

  /**
   * ✅ details 없는 기본 에러 응답 생성
   */
  public static ErrorResponse of(String code, String message, String path) {
    return new ErrorResponse(code, message, path, null);
  }

  /**
   * ✅ details 포함 에러 응답 생성
   * - validation errors 같은 거 넣을 때 사용
   */
  public static ErrorResponse of(String code, String message, String path, Object details) {
    return new ErrorResponse(code, message, path, details);
  }

  // ✅ JSON 직렬화를 위한 getter

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getPath() {
    return path;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public Object getDetails() {
    return details;
  }
}
