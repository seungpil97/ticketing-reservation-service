package com.pil97.ticketing.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenService {

  private static final String REFRESH_KEY_PREFIX = "refresh:";
  private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
  private static final String BLACKLIST_VALUE = "logout";
  private static final long REFRESH_TOKEN_TTL_DAYS = 7;

  private final StringRedisTemplate redisTemplate;

  /**
   * RefreshToken Redis 저장
   * - key: refresh:{memberId}
   * - TTL: 7일
   */
  public void saveRefreshToken(Long memberId, String refreshToken) {
    redisTemplate.opsForValue().set(
      REFRESH_KEY_PREFIX + memberId,
      refreshToken,
      REFRESH_TOKEN_TTL_DAYS,
      TimeUnit.DAYS
    );
  }

  /**
   * Redis에서 RefreshToken 조회
   */
  public Optional<String> getRefreshToken(Long memberId) {
    return Optional.ofNullable(
      redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + memberId)
    );
  }

  /**
   * Redis에서 RefreshToken 삭제
   * - 로그아웃 시 호출
   */
  public void deleteRefreshToken(Long memberId) {
    redisTemplate.delete(REFRESH_KEY_PREFIX + memberId);
  }

  /**
   * AccessToken 블랙리스트 등록
   * - key: blacklist:{accessToken}
   * - TTL: 토큰 잔여 만료 시간 (불필요한 메모리 점유 방지)
   */
  public void addToBlacklist(String accessToken, long remainingMs) {
    if (remainingMs <= 0) {
      return;
    }
    redisTemplate.opsForValue().set(
      BLACKLIST_KEY_PREFIX + accessToken,
      BLACKLIST_VALUE,
      remainingMs,
      TimeUnit.MILLISECONDS
    );
  }

  /**
   * AccessToken 블랙리스트 등록 여부 확인
   */
  public boolean isBlacklisted(String accessToken) {
    return Boolean.TRUE.equals(
      redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + accessToken)
    );
  }
}