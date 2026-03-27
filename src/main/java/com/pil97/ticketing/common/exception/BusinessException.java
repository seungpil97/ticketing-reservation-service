package com.pil97.ticketing.common.exception;

import com.pil97.ticketing.common.error.CommonErrorCode;
import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;


/**
 * 비즈니스 예외용 공통 런타임 예외
 * <p>
 * - 서비스/도메인에서 에러 상황을 만나면 이 예외를 던진다.
 * - ErrorCode 인터페이스를 구현한 모든 도메인 에러코드를 수용한다.
 * - GlobalExceptionHandler가 잡아서 ErrorCode 기준으로 표준 에러 응답을 내려준다.
 * <p>
 * 사용 예시:
 * throw new BusinessException(MemberErrorCode.NOT_FOUND);
 * throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
 */
@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {

    // RuntimeException 메시지에도 같이 넣어두면 로그 볼 때 편함
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

}
