package com.pil97.ticketing.common.exception;

import com.pil97.ticketing.common.error.ErrorCode;

/**
 * ✅ "비즈니스 예외"용 공통 런타임 예외
 * <p>
 * - 서비스/도메인에서 에러 상황을 만나면 이 예외를 던진다.
 * - GlobalExceptionHandler가 잡아서 ErrorCode 기준으로 표준 에러 응답을 내려준다.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {

    // RuntimeException 메시지에도 같이 넣어두면 로그 볼 때 편함
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
