package com.pil97.ticketing.common.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.auth.application.TokenService;
import com.pil97.ticketing.auth.error.AuthErrorCode;
import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.common.response.ErrorResponse;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
  private final TokenService tokenService;
  private final ObjectMapper objectMapper;

  /**
   * JWT 토큰 검증 필터
   * <p>
   * 동작 흐름:
   * 1) Authorization 헤더에서 Bearer 토큰 추출
   * 2) 토큰이 존재하지만 유효하지 않으면 → AUTH-002 즉시 응답 (필터 체인 중단)
   * 3) 토큰이 블랙리스트에 등록되어 있으면 → AUTH-002 즉시 응답 (필터 체인 중단)
   * 4) 유효한 토큰이면 memberId로 회원 조회 후 SecurityContext에 인증 정보 저장
   * 5) 토큰이 없으면 SecurityContext에 저장하지 않고 통과
   * → 인증이 필요한 API는 SecurityConfig AuthenticationEntryPoint에서 AUTH-003 처리
   */
  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {

    String token = extractToken(request);

    // 토큰이 없으면 통과 - 인증 필요 API는 SecurityConfig에서 AUTH-003 처리
    if (!StringUtils.hasText(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 토큰이 있는데 유효하지 않으면 AUTH-002 즉시 응답
    if (!jwtProvider.validateToken(token)) {
      writeErrorResponse(response, request.getRequestURI(), AuthErrorCode.INVALID_TOKEN);
      return;
    }

    // 블랙리스트에 등록된 토큰이면 AUTH-002 즉시 응답 (로그아웃된 토큰)
    if (tokenService.isBlacklisted(token)) {
      writeErrorResponse(response, request.getRequestURI(), AuthErrorCode.INVALID_TOKEN);
      return;
    }


    // 유효한 토큰 - SecurityContext에 인증 정보 저장
    Long memberId = jwtProvider.getMemberId(token);
    memberRepository.findById(memberId).ifPresent(member -> {
      UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(member, null, List.of());
      authentication.setDetails(
        new WebAuthenticationDetailsSource().buildDetails(request)
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);
    });

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


  /**
   * 필터 레벨에서 표준 에러 응답 직접 작성
   * - GlobalExceptionHandler를 거치지 않으므로 직접 응답을 직렬화
   */
  private void writeErrorResponse(
    HttpServletResponse response,
    String path,
    AuthErrorCode errorCode
  ) throws IOException {
    ErrorResponse errorResponse = ErrorResponse.of(
      errorCode.getCode(),
      errorCode.getMessage(),
      path
    );
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(
      objectMapper.writeValueAsString(ApiResponse.error(errorResponse))
    );
  }
}