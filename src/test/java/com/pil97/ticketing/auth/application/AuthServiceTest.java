package com.pil97.ticketing.auth.application;

import com.pil97.ticketing.auth.api.dto.request.LoginRequest;
import com.pil97.ticketing.auth.api.dto.request.ReissueRequest;
import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
import com.pil97.ticketing.auth.api.dto.response.ReissueResponse;
import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.jwt.JwtProvider;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private TokenService tokenService;

  @InjectMocks
  private AuthService authService;

  // ────────────────────────────────────────────────
  // login
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("login: 이메일/비밀번호 정상 → accessToken + refreshToken 반환")
  void login_success() {
    // given
    LoginRequest request = mock(LoginRequest.class);
    when(request.getEmail()).thenReturn("a@test.com");
    when(request.getPassword()).thenReturn("rawPass1!");

    Member member = new Member("a@test.com", "sp", "$2a$encoded");
    when(memberRepository.findByEmail("a@test.com")).thenReturn(Optional.of(member));
    when(passwordEncoder.matches("rawPass1!", "$2a$encoded")).thenReturn(true);
    when(jwtProvider.generateAccessToken(member.getId())).thenReturn("mocked.access.token");
    when(jwtProvider.generateRefreshToken(member.getId())).thenReturn("mocked.refresh.token");

    // when
    LoginResponse response = authService.login(request);

    // then
    assertThat(response.accessToken()).isEqualTo("mocked.access.token");
    assertThat(response.refreshToken()).isEqualTo("mocked.refresh.token");
    verify(jwtProvider).generateAccessToken(member.getId());
    verify(jwtProvider).generateRefreshToken(member.getId());
    verify(tokenService).saveRefreshToken(member.getId(), "mocked.refresh.token");
  }

  @Test
  @DisplayName("login: 존재하지 않는 이메일 → AUTH_INVALID_CREDENTIALS")
  void login_emailNotFound_throwsException() {
    // given
    LoginRequest request = mock(LoginRequest.class);
    when(request.getEmail()).thenReturn("none@test.com");
    when(memberRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.login(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
      });

    verify(jwtProvider, never()).generateAccessToken(any());
    verify(tokenService, never()).saveRefreshToken(any(), any());
  }

  @Test
  @DisplayName("login: 비밀번호 불일치 → AUTH_INVALID_CREDENTIALS")
  void login_wrongPassword_throwsException() {
    // given
    LoginRequest request = mock(LoginRequest.class);
    when(request.getEmail()).thenReturn("a@test.com");
    when(request.getPassword()).thenReturn("wrongPass!");

    Member member = new Member("a@test.com", "sp", "$2a$encoded");
    when(memberRepository.findByEmail("a@test.com")).thenReturn(Optional.of(member));
    when(passwordEncoder.matches("wrongPass!", "$2a$encoded")).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> authService.login(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
      });

    verify(jwtProvider, never()).generateAccessToken(any());
    verify(tokenService, never()).saveRefreshToken(any(), any());
  }

  @Test
  @DisplayName("login: 이메일 미존재/비밀번호 불일치 → 동일한 에러코드 반환 (정보 노출 방지)")
  void login_sameErrorCode_forEmailAndPassword() {
    // given
    LoginRequest emailMissing = mock(LoginRequest.class);
    when(emailMissing.getEmail()).thenReturn("none@test.com");
    when(memberRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

    LoginRequest wrongPw = mock(LoginRequest.class);
    when(wrongPw.getEmail()).thenReturn("a@test.com");
    when(wrongPw.getPassword()).thenReturn("wrongPass!");

    Member member = new Member("a@test.com", "sp", "$2a$encoded");
    when(memberRepository.findByEmail("a@test.com")).thenReturn(Optional.of(member));
    when(passwordEncoder.matches("wrongPass!", "$2a$encoded")).thenReturn(false);

    // when
    ErrorCode code1 = catchThrowableOfType(BusinessException.class,
      () -> authService.login(emailMissing)).getErrorCode();
    ErrorCode code2 = catchThrowableOfType(BusinessException.class,
      () -> authService.login(wrongPw)).getErrorCode();

    // then
    assertThat(code1).isEqualTo(code2).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }

  // ────────────────────────────────────────────────
  // reissue
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("reissue: 유효한 RefreshToken → 새 AccessToken 반환")
  void reissue_success() {
    // given
    ReissueRequest request = mock(ReissueRequest.class);
    when(request.getRefreshToken()).thenReturn("valid.refresh.token");
    when(jwtProvider.validateToken("valid.refresh.token")).thenReturn(true);
    when(jwtProvider.getMemberId("valid.refresh.token")).thenReturn(1L);
    when(tokenService.getRefreshToken(1L)).thenReturn(Optional.of("valid.refresh.token"));
    when(jwtProvider.generateAccessToken(1L)).thenReturn("new.access.token");

    // when
    ReissueResponse response = authService.reissue(request);

    // then
    assertThat(response.accessToken()).isEqualTo("new.access.token");
    verify(jwtProvider).generateAccessToken(1L);
  }

  @Test
  @DisplayName("reissue: 만료/위조된 RefreshToken → AUTH_REFRESH_TOKEN_INVALID")
  void reissue_invalidToken_throwsException() {
    // given
    ReissueRequest request = mock(ReissueRequest.class);
    when(request.getRefreshToken()).thenReturn("invalid.refresh.token");
    when(jwtProvider.validateToken("invalid.refresh.token")).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> authService.reissue(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
      });

    verify(jwtProvider, never()).generateAccessToken(any());
  }

  @Test
  @DisplayName("reissue: Redis에 없는 RefreshToken → AUTH_REFRESH_TOKEN_NOT_FOUND")
  void reissue_tokenNotFound_throwsException() {
    // given
    ReissueRequest request = mock(ReissueRequest.class);
    when(request.getRefreshToken()).thenReturn("logged.out.token");
    when(jwtProvider.validateToken("logged.out.token")).thenReturn(true);
    when(jwtProvider.getMemberId("logged.out.token")).thenReturn(1L);
    when(tokenService.getRefreshToken(1L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.reissue(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
      });

    verify(jwtProvider, never()).generateAccessToken(any());
  }

  @Test
  @DisplayName("reissue: Redis 저장값과 요청값 불일치 → AUTH_REFRESH_TOKEN_INVALID")
  void reissue_tokenMismatch_throwsException() {
    // given
    ReissueRequest request = mock(ReissueRequest.class);
    when(request.getRefreshToken()).thenReturn("tampered.token");
    when(jwtProvider.validateToken("tampered.token")).thenReturn(true);
    when(jwtProvider.getMemberId("tampered.token")).thenReturn(1L);
    when(tokenService.getRefreshToken(1L)).thenReturn(Optional.of("original.token"));

    // when & then
    assertThatThrownBy(() -> authService.reissue(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
      });

    verify(jwtProvider, never()).generateAccessToken(any());
  }

  // ────────────────────────────────────────────────
  // logout
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("logout: 유효한 AccessToken → 블랙리스트 등록 + RefreshToken 삭제")
  void logout_success() {
    // given
    when(jwtProvider.getMemberId("valid.access.token")).thenReturn(1L);
    when(jwtProvider.getRemainingMs("valid.access.token")).thenReturn(60000L);

    // when
    authService.logout("valid.access.token");

    // then
    verify(tokenService).addToBlacklist("valid.access.token", 60000L);
    verify(tokenService).deleteRefreshToken(1L);
  }
}