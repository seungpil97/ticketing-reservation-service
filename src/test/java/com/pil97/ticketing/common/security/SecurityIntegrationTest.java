package com.pil97.ticketing.common.security;

import com.pil97.ticketing.auth.application.TokenService;
import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.jwt.JwtProvider;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtProvider jwtProvider;

  @MockitoBean
  private MemberRepository memberRepository;

  @MockitoBean
  private TokenService tokenService;

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${jwt.refresh-expiration-ms}")
  private long refreshExpirationMs;

  // ────────────────────────────────────────────────
  // 인증 불필요 API → 토큰 없이 통과
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("GET /health → 인증 없이 200")
  void health_noToken_permitAll() throws Exception {
    mockMvc.perform(get("/health"))
      .andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /auth/login → 인증 없이 접근 가능 (401 아님)")
  void authLogin_noToken_notUnauthorized() throws Exception {
    // 빈 바디 → Validation 400 = Security가 막지 않았다는 증명
    mockMvc.perform(post("/auth/login")
        .contentType("application/json")
        .content("{}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /auth/reissue → 인증 없이 접근 가능 (401 아님)")
  void authReissue_noToken_notUnauthorized() throws Exception {
    // 빈 바디 → Validation 400 = Security가 막지 않았다는 증명
    mockMvc.perform(post("/auth/reissue")
        .contentType("application/json")
        .content("{}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /events → 인증 없이 접근 가능 (401 아님)")
  void events_noToken_notUnauthorized() throws Exception {
    mockMvc.perform(get("/events"))
      .andExpect(status().is(not(401)));
  }

  @Test
  @DisplayName("GET /showtimes/{id}/seats → 인증 없이 접근 가능 (401 아님)")
  void seats_noToken_notUnauthorized() throws Exception {
    mockMvc.perform(get("/showtimes/1/seats"))
      .andExpect(status().is(not(401)));
  }

  // ────────────────────────────────────────────────
  // 인증 필요 API → 토큰 없으면 401 + ApiResponse 포맷 검증
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /showtimes/{id}/hold → 토큰 없으면 401 + 에러 포맷 검증")
  void hold_noToken_returns401() throws Exception {
    mockMvc.perform(post("/showtimes/1/hold"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()))
      .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("POST /holds/{id}/reserve → 토큰 없으면 401")
  void reserve_noToken_returns401() throws Exception {
    mockMvc.perform(post("/holds/1/reserve"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
  }

  @Test
  @DisplayName("DELETE /reservations/{id} → 토큰 없으면 401")
  void cancelReservation_noToken_returns401() throws Exception {
    mockMvc.perform(delete("/reservations/1"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
  }

  // ────────────────────────────────────────────────
  // 유효한 토큰 → 인증 통과 (401 아님)
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /showtimes/{id}/hold → 유효한 토큰이면 인증 통과 (401 아님)")
  void hold_validToken_notUnauthorized() throws Exception {
    // given
    Long memberId = 1L;
    String token = jwtProvider.generateAccessToken(memberId);
    Member member = new Member("a@test.com", "sp", "$2a$encoded");
    when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(tokenService.isBlacklisted(token)).thenReturn(false);

    // when & then
    mockMvc.perform(post("/showtimes/1/hold")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().is(not(401)));
  }

  // ────────────────────────────────────────────────
  // 유효하지 않은 토큰 → 401
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /showtimes/{id}/hold → 만료된 토큰이면 401")
  void hold_expiredToken_returns401() throws Exception {
    // given: 만료 1ms짜리 토큰 생성
    JwtProvider shortLived = new JwtProvider(jwtSecret, 1L, refreshExpirationMs);
    String expiredToken = shortLived.generateAccessToken(1L);
    Thread.sleep(10);

    mockMvc.perform(post("/showtimes/1/hold")
        .header("Authorization", "Bearer " + expiredToken))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
  }

  @Test
  @DisplayName("POST /showtimes/{id}/hold → 잘못된 형식의 토큰이면 401")
  void hold_malformedToken_returns401() throws Exception {
    mockMvc.perform(post("/showtimes/1/hold")
        .header("Authorization", "Bearer this.is.not.valid"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
  }

  @Test
  @DisplayName("POST /showtimes/{id}/hold → Bearer 없이 토큰만 전달하면 401")
  void hold_tokenWithoutBearer_returns401() throws Exception {
    String token = jwtProvider.generateAccessToken(1L);

    mockMvc.perform(post("/showtimes/1/hold")
        .header("Authorization", token))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
  }

  // ────────────────────────────────────────────────
  // 블랙리스트 → 401
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /showtimes/{id}/hold → 블랙리스트에 등록된 토큰이면 401")
  void hold_blacklistedToken_returns401() throws Exception {
    // given
    Long memberId = 1L;
    String token = jwtProvider.generateAccessToken(memberId);
    when(tokenService.isBlacklisted(token)).thenReturn(true);

    // when & then
    mockMvc.perform(post("/showtimes/1/hold")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
  }
}