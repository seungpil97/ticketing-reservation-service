package com.pil97.ticketing.auth.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private TokenService tokenService;

  // ────────────────────────────────────────────────
  // RefreshToken
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("saveRefreshToken: refresh:{memberId} 키로 7일 TTL 저장")
  void saveRefreshToken_savesWithCorrectKeyAndTtl() {
    // given
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // when
    tokenService.saveRefreshToken(1L, "refresh.token");

    // then
    verify(valueOperations).set("refresh:1", "refresh.token", 7L, TimeUnit.DAYS);
  }

  @Test
  @DisplayName("getRefreshToken: 저장된 토큰이 있으면 Optional.of로 반환")
  void getRefreshToken_exists_returnsOptionalOf() {
    // given
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("refresh:1")).thenReturn("refresh.token");

    // when
    Optional<String> result = tokenService.getRefreshToken(1L);

    // then
    assertThat(result).isPresent().hasValue("refresh.token");
  }

  @Test
  @DisplayName("getRefreshToken: 저장된 토큰이 없으면 Optional.empty 반환")
  void getRefreshToken_notExists_returnsOptionalEmpty() {
    // given
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("refresh:1")).thenReturn(null);

    // when
    Optional<String> result = tokenService.getRefreshToken(1L);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("deleteRefreshToken: refresh:{memberId} 키 삭제")
  void deleteRefreshToken_deletesCorrectKey() {
    // when
    tokenService.deleteRefreshToken(1L);

    // then
    verify(redisTemplate).delete("refresh:1");
  }

  // ────────────────────────────────────────────────
  // 블랙리스트
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("addToBlacklist: blacklist:{token} 키로 잔여 만료 시간 TTL 저장")
  void addToBlacklist_savesWithCorrectKeyAndTtl() {
    // given
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // when
    tokenService.addToBlacklist("access.token", 60000L);

    // then
    verify(valueOperations).set("blacklist:access.token", "logout", 60000L, TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("addToBlacklist: remainingMs가 0 이하면 저장하지 않음")
  void addToBlacklist_zeroOrNegativeRemainingMs_doesNotSave() {
    // when
    tokenService.addToBlacklist("access.token", 0L);
    tokenService.addToBlacklist("access.token", -1L);

    // then
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  @DisplayName("isBlacklisted: 블랙리스트에 존재하면 true 반환")
  void isBlacklisted_exists_returnsTrue() {
    // given
    when(redisTemplate.hasKey("blacklist:access.token")).thenReturn(true);

    // when & then
    assertThat(tokenService.isBlacklisted("access.token")).isTrue();
  }

  @Test
  @DisplayName("isBlacklisted: 블랙리스트에 없으면 false 반환")
  void isBlacklisted_notExists_returnsFalse() {
    // given
    when(redisTemplate.hasKey("blacklist:access.token")).thenReturn(false);

    // when & then
    assertThat(tokenService.isBlacklisted("access.token")).isFalse();
  }
}