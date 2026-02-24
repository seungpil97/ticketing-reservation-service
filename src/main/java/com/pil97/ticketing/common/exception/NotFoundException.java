package com.pil97.ticketing.common.exception;

import com.pil97.ticketing.common.error.ErrorCode;

/**
 * ✅ 404(Not Found) 계열 예외를 "의미 있게" 분리하기 위한 전용 예외 클래스(옵션)
 * <p>
 * 왜 굳이 NotFoundException을 따로 만들까?
 * 1) 코드 읽기 쉬움:
 * - throw new BusinessException(MEMBER_NOT_FOUND) 보다
 * - throw new NotFoundException(MEMBER_NOT_FOUND)가 "아, 이건 404구나"가 바로 보임
 * <p>
 * 2) 전역 예외 처리에서 404만 따로 다루고 싶을 때 유리:
 * - 지금은 BusinessException 하나로 처리해도 되지만
 * - 나중에 404만 로그 레벨/메시지/응답 details 등을 다르게 하고 싶을 수 있음
 * <p>
 * 3) 도메인별 NotFound 예외들을 만들기 쉬움:
 * - MemberNotFoundException 같은 걸 만들기 전에 공통 404 뼈대로 쓰기 좋음
 * <p>
 * Day2 기준 결론:
 * - "있으면 더 깔끔"하지만
 * - 없어도 BusinessException만으로 충분히 목표 달성 가능(선택)
 */
public class NotFoundException extends BusinessException {

  /**
   * ✅ NotFoundException 생성자
   * <p>
   * ErrorCode를 받는 이유:
   * - 404라도 종류가 여러 개일 수 있음 (MEMBER_NOT_FOUND, POST_NOT_FOUND 등)
   * - 어떤 404인지 "코드/메시지"를 ErrorCode로 통일해서 관리하려고
   * <p>
   * super(errorCode)를 호출하는 이유:
   * - BusinessException이 errorCode를 보관하고 있고
   * - GlobalExceptionHandler가 BusinessException을 잡아서
   * errorCode의 status/code/message로 표준 응답을 만들기 때문
   */
  public NotFoundException(ErrorCode errorCode) {
    super(errorCode);
  }
}
