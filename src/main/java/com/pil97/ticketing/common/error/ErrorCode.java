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
   * ================================
   * Common
   * ================================
   */

  /**
   * ✅ 입력값 검증(Validation) 실패
   * - @NotBlank, @Size 같은 Bean Validation에 걸렸을 때 내려줄 대표 에러
   * - HttpStatus.BAD_REQUEST(400): 클라이언트가 잘못 보낸 요청이라는 의미
   * - code: COMMON-001 -> 내부 규칙으로 분기 처리 가능
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
   * - 요청은 들어왔지만 비즈니스적으로 의미 없는/유효하지 않은 요청을 표현할 때 사용
   */
  COMMON_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-003", "Invalid request"),

  /**
   * ✅ 공통 404
   * - 특정 도메인과 상관 없이 "리소스를 찾을 수 없음"을 표현할 때 사용
   */
  COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "Resource not found"),

  /**
   * ✅ 405 Method Not Allowed
   * - 예: POST만 열려있는데 GET으로 호출한 경우
   */
  COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-405", "Method not allowed"),

  /**
   * ✅ DB 제약조건 위반(공통 409)
   * - unique, fk, not null 등 DB 무결성 제약조건을 위반했을 때 사용
   */
  COMMON_CONFLICT(HttpStatus.CONFLICT, "COMMON-409", "Data integrity violation"),

  /**
   * ✅ 500 Internal Server Error
   * - 예상하지 못한 모든 예외를 처리할 때 사용
   */
  COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "Internal server error"),

  /**
   * ================================
   * Member
   * ================================
   */

  /**
   * ✅ 회원을 찾을 수 없음(도메인 전용 404)
   * - 예: GET /members/{id}, PATCH /members/{id}, DELETE /members/{id}
   */
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-404", "Member not found"),

  /**
   * ✅ 이메일 중복(도메인 전용 409)
   * - 예: 회원 생성/수정 시 중복 email 사용
   */
  MEMBER_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER-409", "Duplicate email"),

  /**
   * ================================
   * Event
   * ================================
   */

  /**
   * ✅ 공연을 찾을 수 없음(도메인 전용 404)
   * - 예: GET /events/{eventId}/showtimes 에서 없는 eventId 접근
   */
  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EVENT-404", "Event not found"),

  /**
   * ================================
   * Showtime
   * ================================
   */

  /**
   * ✅ 회차를 찾을 수 없음(도메인 전용 404)
   * - 예: GET /showtimes/{showtimeId}/seats 에서 없는 showtimeId 접근
   */
  SHOWTIME_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOWTIME-404", "Showtime not found"),

  /**
   * ================================
   * ShowtimeSeat
   * ================================
   */

  /**
   * ✅ 회차별 좌석을 찾을 수 없음(도메인 전용 404)
   * - 회차-좌석 연결 정보가 없는 경우
   * - 예: POST /showtimes/{showtimeId}/hold 에서 해당 회차에 속하지 않는 seatId 요청
   */
  SHOWTIME_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOWTIME-SEAT-404", "Showtime seat not found"),

  /**
   * ✅ 예약 가능한 좌석 상태가 아님(도메인 전용 409)
   * - 예약 확정은 HELD 상태의 회차별 좌석에 대해서만 가능함
   * - 예: POST /holds/{holdId}/reserve 에서 연결된 showtimeSeat 상태가 HELD가 아닌 경우
   */
  SHOWTIME_SEAT_NOT_HELD(HttpStatus.CONFLICT, "SHOWTIME-SEAT-409", "Showtime seat is not held"),

  /**
   * ================================
   * Seat
   * ================================
   */

  /**
   * ✅ 좌석을 찾을 수 없음(도메인 전용 404)
   * - 예: POST /showtimes/{showtimeId}/hold 에서 없는 seatId 접근
   */
  SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "SEAT-404", "Seat not found"),

  /**
   * ✅ 선점할 수 없는 좌석 상태일 때 발생(도메인 전용 409)
   * - 좌석 상태 충돌로 HOLD 불가
   * - 예: POST /showtimes/{showtimeId}/hold 에서 이미 HELD 또는 RESERVED 상태인 좌석 요청
   */
  SEAT_NOT_AVAILABLE_FOR_HOLD(HttpStatus.CONFLICT, "SEAT-409", "Seat is not available for hold"),

  /**
   * ================================
   * Hold
   * ================================
   */

  /**
   * ✅ HOLD를 찾을 수 없음(도메인 전용 404)
   * - 예: POST /holds/{holdId}/reserve 에서 존재하지 않는 holdId 접근
   */
  HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "HOLD-404", "Hold not found"),

  /**
   * ✅ 예약 가능한 HOLD 상태가 아님(도메인 전용 409)
   * - ACTIVE 상태가 아닌 HOLD는 예약 확정할 수 없음
   * - 예: 이미 EXPIRED 되었거나 CONFIRMED 된 HOLD에 대해 예약 확정 요청
   */
  HOLD_NOT_ACTIVE(HttpStatus.CONFLICT, "HOLD-409", "Hold is not active"),

  /**
   * ✅ 만료된 HOLD(도메인 전용 409)
   * - expiresAt 기준으로 이미 만료된 HOLD는 예약 확정할 수 없음
   * - 예: HOLD 만료 시간 이후에 POST /holds/{holdId}/reserve 호출
   */
  HOLD_EXPIRED(HttpStatus.CONFLICT, "HOLD-409", "Hold is expired"),

  /**
   * ================================
   * Reservation
   * ================================
   */

  /**
   * ✅ 예약을 찾을 수 없음(도메인 전용 404)
   * - 예: DELETE /reservations/{reservationId} 에서 존재하지 않는 reservationId 접근
   */
  RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION-404", "Reservation not found"),

  /**
   * ✅ 예약 취소 가능한 상태가 아님(도메인 전용 409)
   * - CONFIRMED 상태가 아닌 예약은 취소할 수 없음
   * - 예: 이미 CANCELLED된 예약에 대해 취소 요청
   */
  RESERVATION_NOT_CONFIRMED(HttpStatus.CONFLICT, "RESERVATION-409", "Reservation is not confirmed");

  private final HttpStatus status;
  private final String code;
  private final String message;


  ErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }
}