package com.pil97.ticketing.common.exception;

import com.pil97.ticketing.common.error.CommonErrorCode;
import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.common.response.ErrorResponse;
import com.pil97.ticketing.common.response.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 * <p>
 * 컨트롤러마다 try-catch 하지 않고,
 * 여기서 예외를 잡아서 표준 에러 응답(ApiResponse.error) 형태로 내려준다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 1) Validation 실패 처리 (@Valid 걸린 DTO 검증 실패)
   * - MethodArgumentNotValidException은 스프링이 자동으로 던져줌
   * - 여기서 field error를 details로 내려주면 클라이언트가 처리하기 편함
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(
    MethodArgumentNotValidException e,
    HttpServletRequest request
  ) {

    // field 에러들을 {field, message} 형태로 변환
    List<FieldErrorDetail> details = e.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
      .collect(Collectors.toList());

    CommonErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;

    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      request.getRequestURI(),
      details
    );

    return ResponseEntity
      .status(errorCode.getStatus())
      .body(ApiResponse.error(errorResponse));
  }

  /**
   * 2) 비즈니스 예외 처리 (의도적으로 던진 예외)
   * - 서비스/도메인에서 BusinessException을 던지면
   * - errorCode 기준으로 상태/코드/메시지를 표준 응답으로 내려줌
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(
    BusinessException e,
    HttpServletRequest request
  ) {
    // ErrorCode 인터페이스로 받아 모든 도메인 에러코드 처리 가능
    ErrorCode errorCode = e.getErrorCode();

    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      request.getRequestURI()
    );

    return ResponseEntity
      .status(errorCode.getStatus())
      .body(ApiResponse.error(errorResponse));
  }

  /**
   * 3) 잘못된 HTTP 메서드 처리 (405)
   * - 예: POST만 가능한데 GET으로 호출
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
    HttpRequestMethodNotSupportedException e,
    HttpServletRequest request
  ) {

    CommonErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;

    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      request.getRequestURI()
    );

    return ResponseEntity
      .status(errorCode.getStatus())
      .body(ApiResponse.error(errorResponse));
  }

  /**
   * 3-1) DB 제약조건 위반 처리 (409)
   * - unique, fk, not null 등 DB 무결성 제약조건 위반 시 발생
   * - 현재는 특정 도메인으로 단정하지 않고 공통 409로 처리
   * - 프로젝트가 커지면 예외 원인을 분석해 도메인별 코드로 세분화 가능
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
    DataIntegrityViolationException e,
    HttpServletRequest request
  ) {

    CommonErrorCode errorCode = CommonErrorCode.CONFLICT;

    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      request.getRequestURI()
    );

    return ResponseEntity
      .status(errorCode.getStatus())
      .body(ApiResponse.error(errorResponse));
  }

  /**
   * 4) 그 외 모든 예외 (500)
   * - 예상치 못한 에러는 여기로 떨어짐
   * - 실제 운영에서는 여기서 로그를 꼭 남김
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(
    Exception e,
    HttpServletRequest request
  ) {

    // 예상치 못한 예외는 반드시 로그를 남긴다
    log.error("Unhandled exception: path={} message={}", request.getRequestURI(), e.getMessage(), e);

    CommonErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;

    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      request.getRequestURI()
    );

    return ResponseEntity
      .status(errorCode.getStatus())
      .body(ApiResponse.error(errorResponse));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
    HttpMessageNotReadableException e,
    HttpServletRequest request
  ) {
    // 클라이언트 요청 오류이므로 WARN 레벨
    log.warn("Request body missing or malformed: path={}", request.getRequestURI());

    CommonErrorCode errorCode = CommonErrorCode.VALIDATION_FAILED;
    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      request.getRequestURI()
    );
    return ResponseEntity
      .status(errorCode.getStatus())
      .body(ApiResponse.error(errorResponse));
  }
}
