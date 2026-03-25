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
  private final long accessExpirationMs;
  private final long refreshExpirationMs;

  /**
   * JwtProvider 생성자
   * - secretKey: JWT 서명에 사용할 비밀키 (application.yml에서 주입)
   * - accessExpirationMs: AccessToken 만료 시간 (밀리초, application.yml에서 주입)
   * - refreshExpirationMs: RefreshToken 만료 시간 (밀리초, application.yml에서 주입)
   */
  public JwtProvider(
    @Value("${jwt.secret}") String secret,
    @Value("${jwt.expiration-ms}") long accessExpirationMs,
    @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs
  ) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  /**
   * AccessToken 생성 (TTL: jwt.expiration-ms)
   */
  public String generateAccessToken(Long memberId) {
    return generateToken(memberId, accessExpirationMs);
  }

  /**
   * RefreshToken 생성 (TTL: jwt.refresh-expiration-ms)
   */
  public String generateRefreshToken(Long memberId) {
    return generateToken(memberId, refreshExpirationMs);
  }

  /**
   * JWT 토큰에서 memberId 추출
   */
  public Long getMemberId(String token) {
    return Long.parseLong(parseClaims(token).getSubject());
  }

  /**
   * JWT 토큰 유효성 검증
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
   * 토큰 잔여 만료 시간(ms) 반환
   * - 블랙리스트 TTL 설정에 사용
   */
  public long getRemainingMs(String token) {
    Date expiration = parseClaims(token).getExpiration();
    return expiration.getTime() - System.currentTimeMillis();
  }

  private String generateToken(Long memberId, long expirationMs) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
      .subject(String.valueOf(memberId))
      .issuedAt(now)
      .expiration(expiry)
      .signWith(secretKey)
      .compact();
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
      .verifyWith(secretKey)
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }
}