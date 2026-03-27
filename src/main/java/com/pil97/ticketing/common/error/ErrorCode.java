package com.pil97.ticketing.common.error;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인별 에러코드 enum이 구현해야 할 공통 인터페이스
 * <p>
 * 사용 방법:
 * - 새 도메인 추가 시 XxxErrorCode enum을 만들고 이 인터페이스를 implement할 것
 * - BusinessException / GlobalExceptionHandler는 이 인터페이스만 바라보므로 수정 불필요
 * 이 파일은 모든 도메인별 에러코드 enum이 구현해야 할 공통 계약을 정의하는 인터페이스입니다.
 * BusinessException과 GlobalExceptionHandler가 도메인 enum에 상관없이 동일하게 동작할 수 있는 핵심 추상화입니다.
 */
public interface ErrorCode {

  // HTTP 상태코드 반환
  HttpStatus getStatus();

  // 응답 body의 code 필드 - 클라이언트 분기 처리용 식별자
  String getCode();

  // 응답 body의 message 필드 - 사람이 읽을 수 있는 에러 설명
  String getMessage();
}
