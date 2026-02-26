package com.pil97.ticketing.member.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ✅ PATCH /members/{id} 요청 바디 DTO
 * - @Valid + @RequestBody 조합으로 들어오면 Bean Validation이 동작
 * - 검증 실패 시 MethodArgumentNotValidException 발생 → GlobalExceptionHandler가 표준 에러 응답 생성
 */
@Getter
@NoArgsConstructor
public class MemberUpdateRequest {

  @Email
  @Size(max = 100)
  private String email;

  @Size(max = 30)
  private String name;
}
