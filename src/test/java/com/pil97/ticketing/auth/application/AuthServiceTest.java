package com.pil97.ticketing.auth.application;

import com.pil97.ticketing.auth.api.dto.request.LoginRequest;
import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
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

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("login: 이메일/비밀번호 정상 → JWT 토큰 반환")
    void login_success() {
        // given
        LoginRequest request = mock(LoginRequest.class);
        when(request.getEmail()).thenReturn("a@test.com");
        when(request.getPassword()).thenReturn("rawPass1!");

        Member member = new Member("a@test.com", "sp", "$2a$encoded");
        when(memberRepository.findByEmail("a@test.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("rawPass1!", "$2a$encoded")).thenReturn(true);
        when(jwtProvider.generateToken(member.getId())).thenReturn("mocked.jwt.token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");
        verify(jwtProvider).generateToken(member.getId());
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

        verify(jwtProvider, never()).generateToken(any());
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

        verify(jwtProvider, never()).generateToken(any());
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
}