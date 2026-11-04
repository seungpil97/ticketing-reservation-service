package com.pil97.ticketing.support.consistency;

/**
 * 테스트 harness·환경 사전조건 실패 전용 예외
 * - 메시지는 "HARNESS:" 또는 "PRECONDITION:"으로 시작한다
 * - 이 예외로 끝난 테스트는 계약 위반(AssertionError)이 아니므로 Valid RED로 인정하지 않는다
 */
public class HarnessException extends RuntimeException {

  public HarnessException(String message) {
    super(message);
  }

  public HarnessException(String message, Throwable cause) {
    super(message, cause);
  }

  public static HarnessException harness(String message) {
    return new HarnessException("HARNESS: " + message);
  }

  public static HarnessException harness(String message, Throwable cause) {
    return new HarnessException("HARNESS: " + message, cause);
  }

  public static HarnessException precondition(String message) {
    return new HarnessException("PRECONDITION: " + message);
  }
}
