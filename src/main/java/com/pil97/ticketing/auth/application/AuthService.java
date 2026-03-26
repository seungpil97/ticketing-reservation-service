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
  private final TokenService tokenService;


  /**
   * 로그인
   * 1) 이메일로 회원 조회
   * 2) 비밀번호 검증 (BCrypt)
   * 3) AccessToken + RefreshToken 발급
   * 4) RefreshToken Redis 저장
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

    // 3) AccessToken + RefreshToken 발급
    String accessToken = jwtProvider.generateAccessToken(member.getId());
    String refreshToken = jwtProvider.generateRefreshToken(member.getId());

    // 4) RefreshToken Redis 저장
    tokenService.saveRefreshToken(member.getId(), refreshToken);

    return new LoginResponse(accessToken, refreshToken);
  }

  /**
   * AccessToken 재발급 (RefreshToken Rotation 적용)
   * 1) RefreshToken 서명/만료 검증
   * 2) Redis에 RefreshToken 존재 여부 확인
   * 3) Redis 저장값과 요청값 일치 여부 확인 (불일치 = 탈취 감지)
   * 4) 새 AccessToken + 새 RefreshToken 발급
   * 5) Redis에 새 RefreshToken으로 교체 저장 (기존 토큰 무효화)
   */
  public ReissueResponse reissue(ReissueRequest request) {
    String refreshToken = request.getRefreshToken();

    // 1) 서명/만료 검증
    if (!jwtProvider.validateToken(refreshToken)) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    Long memberId = jwtProvider.getMemberId(refreshToken);

    // 2) Redis 존재 여부 확인
    String storedToken = tokenService.getRefreshToken(memberId)
      .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND));

    // 3) 저장값과 요청값 불일치 = 탈취 감지 → 즉시 강제 로그아웃
    if (!storedToken.equals(refreshToken)) {
      tokenService.deleteRefreshToken(memberId);
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    // 4) 새 AccessToken + 새 RefreshToken 발급
    String newAccessToken = jwtProvider.generateAccessToken(memberId);
    String newRefreshToken = jwtProvider.generateRefreshToken(memberId);

    // 5) Redis에 새 RefreshToken으로 교체 저장
    tokenService.saveRefreshToken(memberId, newRefreshToken);

    return new ReissueResponse(newAccessToken, newRefreshToken);
  }

  /**
   * 로그아웃
   * 1) AccessToken 블랙리스트 등록 (잔여 만료 시간 TTL)
   * 2) Redis RefreshToken 삭제
   */
  public void logout(String accessToken) {
    Long memberId = jwtProvider.getMemberId(accessToken);
    long remainingMs = jwtProvider.getRemainingMs(accessToken);

    tokenService.addToBlacklist(accessToken, remainingMs);
    tokenService.deleteRefreshToken(memberId);
  }
}