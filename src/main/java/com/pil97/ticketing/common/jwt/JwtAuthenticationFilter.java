package com.pil97.ticketing.common.jwt;

import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final MemberRepository memberRepository;

  /**
   * JWT 토큰 검증 필터
   * <p>
   * 동작 흐름:
   * 1) Authorization 헤더에서 Bearer 토큰 추출
   * 2) 토큰 유효성 검증
   * 3) 토큰에서 memberId 추출 후 DB에서 회원 조회
   * 4) SecurityContext에 인증 정보 저장
   * 5) 다음 필터로 전달
   * <p>
   * 토큰이 없거나 유효하지 않으면 SecurityContext에 저장하지 않고 통과
   * → 인증이 필요한 API는 SecurityConfig에서 막힘
   */
  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    String token = extractToken(request);

    if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
      Long memberId = jwtProvider.getMemberId(token);

      memberRepository.findById(memberId).ifPresent(member -> {
        UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
            member,
            null,
            List.of()
          );
        authentication.setDetails(
          new WebAuthenticationDetailsSource().buildDetails(request)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
      });
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Authorization 헤더에서 Bearer 토큰 추출
   * - Authorization: Bearer {token} 형식
   */
  private String extractToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}