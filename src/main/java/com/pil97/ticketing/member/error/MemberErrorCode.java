package com.pil97.ticketing.member.error;

import com.pil97.ticketing.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 회원 도메인 에러코드
 * <p>
 * 새 항목 추가 시 다음 순번으로 추가할 것 (현재 마지막: MEMBER-002)
 * 이 파일은 회원 도메인의 에러를 정의하는 enum입니다. 회원 조회 실패, 이메일 중복 등 회원 관련 비즈니스 예외를 담당합니다.
 */
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

  // 회원을 찾을 수 없음 - GET /members/{id}, PATCH /members/{id} 등
  NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-001", "Member not found"),

  // 이메일 중복 - 회원 생성/수정 시 이미 사용 중인 이메일
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER-002", "Duplicate email");

  private final HttpStatus status;
  private final String code;
  private final String message;
}