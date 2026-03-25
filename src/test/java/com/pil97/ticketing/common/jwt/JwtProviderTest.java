package com.pil97.ticketing.common.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

  private static final String TEST_SECRET = "test-secret-key-must-be-32bytes!!";
  private static final long EXPIRATION_MS = 3_600_000L;       // 1시간
  private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7일

  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    jwtProvider = new JwtProvider(TEST_SECRET, EXPIRATION_MS, REFRESH_EXPIRATION_MS);
  }

  @Test
  @DisplayName("generateAccessToken: memberId로 토큰 생성 후 getMemberId로 동일 memberId 추출")
  void generateAccessToken_and_getMemberId() {
    // given
    Long memberId = 42L;

    // when
    String token = jwtProvider.generateAccessToken(memberId);
    Long extracted = jwtProvider.getMemberId(token);

    // then
    assertThat(token).isNotBlank();
    assertThat(extracted).isEqualTo(memberId);
  }

  @Test
  @DisplayName("validateToken: 유효한 AccessToken → true 반환")
  void validateToken_validToken_returnsTrue() {
    // given
    String token = jwtProvider.generateAccessToken(1L);

    // when & then
    assertThat(jwtProvider.validateToken(token)).isTrue();
  }

  @Test
  @DisplayName("validateToken: 만료된 토큰 → false 반환")
  void validateToken_expiredToken_returnsFalse() throws InterruptedException {
    // given: 만료 시간 1ms로 설정한 provider
    JwtProvider shortLivedProvider = new JwtProvider(TEST_SECRET, 1L, REFRESH_EXPIRATION_MS);
    String token = shortLivedProvider.generateAccessToken(1L);

    Thread.sleep(10);

    // when & then
    assertThat(shortLivedProvider.validateToken(token)).isFalse();
  }

  @Test
  @DisplayName("validateToken: 잘못된 시그니처 토큰 → false 반환")
  void validateToken_wrongSignature_returnsFalse() {
    // given: 다른 secret으로 서명된 토큰
    JwtProvider otherProvider = new JwtProvider("other-secret-key-must-be-32bytes!!", EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    String tokenByOther = otherProvider.generateAccessToken(1L);

    // when & then
    assertThat(jwtProvider.validateToken(tokenByOther)).isFalse();
  }

  @Test
  @DisplayName("validateToken: 형식이 잘못된 문자열 → false 반환")
  void validateToken_malformedToken_returnsFalse() {
    assertThat(jwtProvider.validateToken("this.is.not.a.jwt")).isFalse();
  }

  @Test
  @DisplayName("validateToken: 빈 문자열 → false 반환")
  void validateToken_emptyString_returnsFalse() {
    assertThat(jwtProvider.validateToken("")).isFalse();
  }

  @Test
  @DisplayName("generateAccessToken: 서로 다른 memberId는 다른 토큰을 생성한다")
  void generateAccessToken_differentMemberId_produceDifferentTokens() {
    // given & when
    String token1 = jwtProvider.generateAccessToken(1L);
    String token2 = jwtProvider.generateAccessToken(2L);

    // then
    assertThat(token1).isNotEqualTo(token2);
  }

  @Test
  @DisplayName("generateRefreshToken: memberId로 RefreshToken 생성 후 getMemberId로 동일 memberId 추출")
  void generateRefreshToken_and_getMemberId() {
    // given
    Long memberId = 42L;

    // when
    String token = jwtProvider.generateRefreshToken(memberId);
    Long extracted = jwtProvider.getMemberId(token);

    // then
    assertThat(token).isNotBlank();
    assertThat(extracted).isEqualTo(memberId);
  }

  @Test
  @DisplayName("getRemainingMs: 유효한 토큰 → 양수 잔여 시간 반환")
  void getRemainingMs_validToken_returnsPositive() {
    // given
    String token = jwtProvider.generateAccessToken(1L);

    // when
    long remaining = jwtProvider.getRemainingMs(token);

    // then
    assertThat(remaining).isPositive();
  }
}