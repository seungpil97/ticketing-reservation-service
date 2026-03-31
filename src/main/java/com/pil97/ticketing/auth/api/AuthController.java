package com.pil97.ticketing.auth.api;

import com.pil97.ticketing.auth.api.dto.request.LoginRequest;
import com.pil97.ticketing.auth.api.dto.request.ReissueRequest;
import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
import com.pil97.ticketing.auth.api.dto.response.ReissueResponse;
import com.pil97.ticketing.auth.application.AuthService;
import com.pil97.ticketing.auth.error.AuthErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;


@Tag(name = "1. Auth", description = "인증 API - 로그인 / 토큰 재발급 / 로그아웃")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /**
   * 로그인
   * - 이메일 + 비밀번호로 로그인
   * - 성공 시 AccessToken + RefreshToken 반환
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
    @Valid @RequestBody LoginRequest request
  ) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * AccessToken 재발급
   * - 유효한 RefreshToken으로 새 AccessToken 발급
   */
  @PostMapping("/reissue")
  public ResponseEntity<ApiResponse<ReissueResponse>> reissue(
    @Valid @RequestBody ReissueRequest request
  ) {
    ReissueResponse response = authService.reissue(request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * 로그아웃
   * - AccessToken 블랙리스트 등록
   * - RefreshToken 삭제
   */
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
    @RequestHeader(value = "Authorization", required = false) String bearerToken
  ) {
    if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
    String accessToken = bearerToken.substring(7);
    authService.logout(accessToken);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}