package com.pil97.ticketing.auth.application;

import com.pil97.ticketing.auth.api.dto.request.LoginRequest;
import com.pil97.ticketing.auth.api.dto.response.LoginResponse;
import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.jwt.JwtProvider;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;

  /**
   * 로그인
   * 1) 이메일로 회원 조회
   * 2) 비밀번호 검증 (BCrypt)
   * 3) JWT 토큰 발급
   * <p>
   * 보안: 이메일 미존재 / 비밀번호 불일치 모두 동일한 에러 반환
   * → 어떤 정보가 틀렸는지 노출하지 않기 위해
   */
  public LoginResponse login(LoginRequest request) {

    // 1) 이메일로 회원 조회
    Member member = memberRepository.findByEmail(request.getEmail())
      .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    // 2) 비밀번호 검증
    if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    // 3) JWT 토큰 발급
    String token = jwtProvider.generateToken(member.getId());

    return new LoginResponse(token);
  }
}