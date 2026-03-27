package com.pil97.ticketing.auth.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증/인가 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: AUTH-005)
 * 이 파일은 인증/인가 도메인의 에러를 정의하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

  // 이메일 또는 비밀번호 불일치 - 보안상 어떤 정보가 틀렸는지 노출하지 않음
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-001", "Invalid email or password"),

  // 유효하지 않은 AccessToken - 만료되었거나 서명이 잘못된 JWT
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-002", "Invalid or expired token"),

  // 인증되지 않은 요청 - 토큰 없이 인증이 필요한 API에 접근
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-003", "Unauthorized"),

  // 유효하지 않은 RefreshToken - 만료, 서명 불일치, Redis 저장값과 불일치
  REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-004", "Invalid or expired refresh token"),

  // RefreshToken Redis 미존재 - 로그아웃 이후 재발급 요청 또는 미발급 상태에서 요청
  REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH-005", "Refresh token not found");

  private final HttpStatus status;
  private final String code;
  private final String message;
}