package com.pil97.ticketing.member.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ✅ POST /members 요청 바디 DTO
 * - @Valid + @RequestBody 조합으로 들어오면 Bean Validation이 동작
 * - 검증 실패 시 MethodArgumentNotValidException 발생 → GlobalExceptionHandler가 표준 에러 응답 생성
 */
@Getter
@NoArgsConstructor // ✅ Jackson이 JSON → 객체 변환할 때 기본 생성자가 필요해서 Lombok으로 생성
public class MemberCreateRequest {

  @NotBlank(message = "email is required")
  private String email;

  @NotBlank(message = "name is required")
  private String name;
}
