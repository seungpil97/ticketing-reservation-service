package com.pil97.ticketing.common.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    // Keys.hmacShaKeyFor()는 HMAC-SHA256 기준 최소 32바이트 요구
    private static final String TEST_SECRET = "test-secret-key-must-be-32bytes!!";
    private static final long EXPIRATION_MS = 3_600_000L; // 1시간

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken: memberId로 토큰 생성 후 getMemberId로 동일 memberId 추출")
    void generateToken_and_getMemberId() {
        // given
        Long memberId = 42L;

        // when
        String token = jwtProvider.generateToken(memberId);
        Long extracted = jwtProvider.getMemberId(token);

        // then
        assertThat(token).isNotBlank();
        assertThat(extracted).isEqualTo(memberId);
    }

    @Test
    @DisplayName("validateToken: 유효한 토큰 → true 반환")
    void validateToken_validToken_returnsTrue() {
        // given
        String token = jwtProvider.generateToken(1L);

        // when & then
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken: 만료된 토큰 → false 반환")
    void validateToken_expiredToken_returnsFalse() throws InterruptedException {
        // given: 만료 시간 1ms로 설정한 provider
        JwtProvider shortLivedProvider = new JwtProvider(TEST_SECRET, 1L);
        String token = shortLivedProvider.generateToken(1L);

        Thread.sleep(10); // 만료 대기

        // when & then
        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken: 잘못된 시그니처 토큰 → false 반환")
    void validateToken_wrongSignature_returnsFalse() {
        // given: 다른 secret으로 서명된 토큰
        JwtProvider otherProvider = new JwtProvider("other-secret-key-must-be-32bytes!!", EXPIRATION_MS);
        String tokenByOther = otherProvider.generateToken(1L);

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
    @DisplayName("generateToken: 서로 다른 memberId는 다른 토큰을 생성한다")
    void generateToken_differentMemberId_produceDifferentTokens() {
        // given & when
        String token1 = jwtProvider.generateToken(1L);
        String token2 = jwtProvider.generateToken(2L);

        // then
        assertThat(token1).isNotEqualTo(token2);
    }
}