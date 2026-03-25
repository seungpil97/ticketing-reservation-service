package com.pil97.ticketing.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.auth.application.TokenService;
import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.jwt.JwtAuthenticationFilter;
import com.pil97.ticketing.common.jwt.JwtProvider;
import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.common.response.ErrorResponse;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtProvider jwtProvider;
  private final MemberRepository memberRepository;
  private final TokenService tokenService;
  private final ObjectMapper objectMapper;  // 401 응답을 ApiResponse 포맷으로 직렬화하기 위해 주입

  /**
   * Spring Security 필터 체인 설정
   * <p>
   * CSRF 비활성화
   * - REST API는 세션을 사용하지 않으므로 CSRF 토큰 불필요
   * <p>
   * SessionCreationPolicy.STATELESS
   * - JWT 방식은 서버가 세션 상태를 저장하지 않음
   * <p>
   * AuthenticationEntryPoint 커스텀
   * - 인증 실패 시 Spring Security 기본 응답 대신 프로젝트 표준 ApiResponse 포맷으로 401 반환
   * - ErrorCode.AUTH_UNAUTHORIZED 사용
   * <p>
   * 인증 불필요 API (permitAll)
   * - GET  /health/**              헬스체크
   * - POST /members               회원 가입
   * - POST /auth/login            로그인
   * - POST /auth/reissue          AccessToken 재발급
   * - GET  /events/**             공연/회차 조회
   * - GET  /showtimes/{id}/seats  좌석 조회
   * <p>
   * 인증 필요 API (authenticated)
   * - POST   /showtimes/{id}/hold     좌석 선점
   * - POST   /holds/{id}/reserve      예약 확정
   * - DELETE /reservations/{id}       예약 취소
   * - 그 외 모든 요청
   * <p>
   * JWT 필터
   * - UsernamePasswordAuthenticationFilter 앞에 JwtAuthenticationFilter 등록
   * - 토큰이 없거나 유효하지 않거나 블랙리스트에 있으면 SecurityContext에 저장하지 않고 통과
   * → 인증이 필요한 API는 이후 SecurityConfig에서 401 처리
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .exceptionHandling(exception -> exception
        .authenticationEntryPoint((request, response, authException) -> {
          ErrorResponse errorResponse = ErrorResponse.of(
            ErrorCode.AUTH_UNAUTHORIZED.getCode(),
            ErrorCode.AUTH_UNAUTHORIZED.getMessage(),
            request.getRequestURI()
          );
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          response.setCharacterEncoding("UTF-8");
          response.getWriter().write(
            objectMapper.writeValueAsString(ApiResponse.error(errorResponse))
          );
        })
      )
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/health/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/members").permitAll()
        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
        .requestMatchers(HttpMethod.POST, "/auth/reissue").permitAll()
        .requestMatchers(HttpMethod.GET, "/events/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/showtimes/*/seats").permitAll()
        .anyRequest().authenticated()
      )
      .addFilterBefore(
        new JwtAuthenticationFilter(jwtProvider, memberRepository, tokenService),
        UsernamePasswordAuthenticationFilter.class
      );

    return http.build();
  }
}