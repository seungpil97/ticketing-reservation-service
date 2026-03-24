package com.pil97.ticketing.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

  private final SecretKey secretKey;
  private final long expirationMs;

  /**
   * JwtProvider 생성자
   * - secretKey: JWT 서명에 사용할 비밀키 (application.yml에서 주입)
   * - expirationMs: 토큰 만료 시간 (밀리초, application.yml에서 주입)
   */
  public JwtProvider(
    @Value("${jwt.secret}") String secret,
    @Value("${jwt.expiration-ms}") long expirationMs
  ) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
  }

  /**
   * JWT 토큰 생성
   * - subject: 토큰 주체 (memberId)
   * - 발급 시각과 만료 시각을 포함
   * - HMAC-SHA256 알고리즘으로 서명
   */
  public String generateToken(Long memberId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
      .subject(String.valueOf(memberId))
      .issuedAt(now)
      .expiration(expiry)
      .signWith(secretKey)
      .compact();
  }

  /**
   * JWT 토큰에서 memberId 추출
   */
  public Long getMemberId(String token) {
    Claims claims = parseClaims(token);
    return Long.parseLong(claims.getSubject());
  }

  /**
   * JWT 토큰 유효성 검증
   * - 서명 검증
   * - 만료 시간 검증
   * - 형식 검증
   */
  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("JWT expired: {}", e.getMessage());
    } catch (MalformedJwtException e) {
      log.warn("JWT malformed: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      log.warn("JWT unsupported: {}", e.getMessage());
    } catch (SignatureException e) {
      log.warn("JWT signature invalid: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      log.warn("JWT illegal argument: {}", e.getMessage());
    }
    return false;
  }

  /**
   * JWT 토큰 파싱
   * - 서명 검증 후 Claims 반환
   */
  private Claims parseClaims(String token) {
    return Jwts.parser()
      .verifyWith(secretKey)
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }
}