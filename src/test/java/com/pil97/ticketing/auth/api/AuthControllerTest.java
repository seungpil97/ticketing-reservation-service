package com.pil97.ticketing.auth.api;

import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
import com.pil97.ticketing.auth.application.AuthService;
import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /auth/login: 정상 로그인 → 200 + accessToken 반환")
    void login_success() throws Exception {
        // given
        when(authService.login(any())).thenReturn(new LoginResponse("mocked.jwt.token"));

        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "a@test.com", "password": "rawPass1!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("mocked.jwt.token"))
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/login: 이메일/비밀번호 불일치 → 401")
    void login_invalidCredentials_returns401() throws Exception {
        // given
        when(authService.login(any()))
            .thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // when & then
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "a@test.com", "password": "wrongPass!"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_INVALID_CREDENTIALS.getCode()));
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
}