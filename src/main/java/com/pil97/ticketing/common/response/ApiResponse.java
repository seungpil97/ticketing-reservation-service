package com.pil97.ticketing.common.response;


import java.time.LocalDateTime;

/**
 * ✅ 모든 API 응답을 "하나의 형태"로 통일하기 위한 래퍼(Wrapper) DTO
 * <p>
 * 성공/실패 상관없이 항상 ApiResponse 형태로 내려준다.
 * <p>
 * 성공 예)
 * {
 * "success": true,
 * "data": {...},
 * "error": null,
 * "timestamp": "2026-02-24T22:10:00"
 * }
 * <p>
 * 실패 예)
 * {
 * "success": false,
 * "data": null,
 * "error": {...},
 * "timestamp": "2026-02-24T22:10:00"
 * }
 * <p>
 * T는 "성공했을 때 data의 타입"이 된다.
 * 예) ApiResponse<MemberDto>, ApiResponse<Void>
 */
public class ApiResponse<T> {

  /**
   * ✅ 성공 여부
   * - true: 정상 응답
   * - false: 에러 응답
   */
  private final boolean success;

  /**
   * ✅ 성공했을 때의 실제 데이터
   * - 성공이면 data에 값이 들어가고 error는 null
   * - 실패면 data는 null
   */
  private final T data;

  /**
   * ✅ 실패했을 때의 에러 정보
   * - 실패면 error에 값이 들어가고 data는 null
   * - 성공이면 error는 null
   */
  private final ErrorResponse error;

  /**
   * ✅ 응답 생성 시각
   * - 서버에서 응답을 만든 시점을 찍는다.
   */
  private final LocalDateTime timestamp;

  /**
   * ✅ 생성자를 private으로 막아두는 이유
   * - 외부에서 new ApiResponse(...) 마구 만들지 말고
   * - 아래의 success(), error() 팩토리 메서드를 통해 "일관된 형태"로만 생성하게 하려고
   */
  private ApiResponse(boolean success, T data, ErrorResponse error) {
    this.success = success;
    this.data = data;
    this.error = error;
    this.timestamp = LocalDateTime.now();
  }

  /**
   * ✅ 성공 응답을 만드는 정적 팩토리 메서드
   * - 사용 예: return ApiResponse.success(resultDto);
   */
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  /**
   * ✅ 실패(에러) 응답을 만드는 정적 팩토리 메서드
   * - 사용 예: return ApiResponse.error(errorResponse);
   */
  public static <T> ApiResponse<T> error(ErrorResponse error) {
    return new ApiResponse<>(false, null, error);
  }

  // ✅ Jackson(JSON 변환)이 읽을 수 있도록 getter 제공
  // (Lombok @Getter 써도 됨)

  public boolean isSuccess() {
    return success;
  }

  public T getData() {
    return data;
  }

  public ErrorResponse getError() {
    return error;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }
}
