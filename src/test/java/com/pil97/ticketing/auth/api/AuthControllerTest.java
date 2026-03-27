package com.pil97.ticketing.auth.api;

import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
import com.pil97.ticketing.auth.api.dto.response.ReissueResponse;
import com.pil97.ticketing.auth.application.AuthService;
import com.pil97.ticketing.auth.error.AuthErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
  value = AuthController.class,
  excludeAutoConfiguration = SecurityAutoConfiguration.class
)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService authService;

  // ────────────────────────────────────────────────
  // POST /auth/login
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /auth/login: 정상 로그인 → 200 + accessToken 반환")
  void login_success() throws Exception {
    // given
    when(authService.login(any()))
      .thenReturn(new LoginResponse("mocked.access.token", "mocked.refresh.token"));

    // when & then
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email": "a@test.com", "password": "rawPass1!"}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.accessToken").value("mocked.access.token"))
      .andExpect(jsonPath("$.data.refreshToken").value("mocked.refresh.token"));
  }

  @Test
  @DisplayName("POST /auth/login: 이메일/비밀번호 불일치 → 401")
  void login_invalidCredentials_returns401() throws Exception {
    // given
    when(authService.login(any()))
      .thenThrow(new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

    // when & then
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email": "a@test.com", "password": "wrongPass!"}
          """))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(AuthErrorCode.INVALID_CREDENTIALS.getCode()));
  }

  @Test
  @DisplayName("POST /auth/login: email 누락 → 400")
  void login_missingEmail_returns400() throws Exception {
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"password": "rawPass1!"}
          """))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /auth/login: password 누락 → 400")
  void login_missingPassword_returns400() throws Exception {
    mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"email": "a@test.com"}
          """))
      .andExpect(status().isBadRequest());
  }

  // ────────────────────────────────────────────────
  // POST /auth/reissue
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /auth/reissue: 유효한 RefreshToken → 200 + 새 accessToken + 새 refreshToken 반환")
  void reissue_success() throws Exception {
    // given
    when(authService.reissue(any()))
      .thenReturn(new ReissueResponse("new.access.token", "new.refresh.token"));

    // when & then
    mockMvc.perform(post("/auth/reissue")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"refreshToken": "valid.refresh.token"}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.accessToken").value("new.access.token"))
      .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"));
  }

  @Test
  @DisplayName("POST /auth/reissue: 만료/위조된 RefreshToken → 401")
  void reissue_invalidToken_returns401() throws Exception {
    // given
    when(authService.reissue(any()))
      .thenThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

    // when & then
    mockMvc.perform(post("/auth/reissue")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"refreshToken": "invalid.refresh.token"}
          """))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(AuthErrorCode.REFRESH_TOKEN_INVALID.getCode()));
  }

  @Test
  @DisplayName("POST /auth/reissue: Redis에 없는 RefreshToken → 401")
  void reissue_tokenNotFound_returns401() throws Exception {
    // given
    when(authService.reissue(any()))
      .thenThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

    // when & then
    mockMvc.perform(post("/auth/reissue")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"refreshToken": "logged.out.refresh.token"}
          """))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND.getCode()));
  }

  // ────────────────────────────────────────────────
  // POST /auth/logout
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /auth/logout: 유효한 AccessToken → 200")
  void logout_success() throws Exception {

    // when & then
    mockMvc.perform(post("/auth/logout")
        .header("Authorization", "Bearer valid.access.token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("POST /auth/logout: Authorization 헤더 누락 → 401")
  void logout_missingHeader_returns401() throws Exception {
    mockMvc.perform(post("/auth/logout"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.error.code").value(AuthErrorCode.INVALID_TOKEN.getCode()));
  }

  @Test
  @DisplayName("POST /auth/logout: Bearer 형식 아닌 헤더 → 401")
  void logout_invalidBearerFormat_returns401() throws Exception {
    mockMvc.perform(post("/auth/logout")
        .header("Authorization", "InvalidToken"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(AuthErrorCode.INVALID_TOKEN.getCode()));
  }
}