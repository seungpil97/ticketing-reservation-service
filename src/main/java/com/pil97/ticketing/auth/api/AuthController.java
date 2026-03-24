package com.pil97.ticketing.auth.api;

import com.pil97.ticketing.auth.api.dto.request.LoginRequest;
import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
import com.pil97.ticketing.auth.application.AuthService;
import com.pil97.ticketing.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /**
   * 로그인
   * - 이메일 + 비밀번호로 로그인
   * - 성공 시 JWT AccessToken 반환
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
    @Valid @RequestBody LoginRequest request
  ) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}