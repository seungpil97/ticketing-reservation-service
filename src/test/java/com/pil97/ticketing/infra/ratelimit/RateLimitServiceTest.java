package com.pil97.ticketing.infra.ratelimit;

import com.pil97.ticketing.common.error.RateLimitErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.ratelimit.RateLimitApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

  @Mock
  private StringRedisTemplate stringRedisTemplate;

  @InjectMocks
  private RateLimitService rateLimitService;

  private static final Long MEMBER_ID = 1L;
  private static final RateLimitApi API = RateLimitApi.HOLD;
  private static final int LIMIT = 5;
  private static final int WINDOW_SECONDS = 60;

  @Test
  @DisplayName("api가 null이면 IllegalArgumentException을 던진다")
  void check_nullApi_throwsIllegalArgument() {
    assertThatThrownBy(() ->
      rateLimitService.check(MEMBER_ID, null, LIMIT, WINDOW_SECONDS)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("windowSeconds가 0 이하이면 IllegalArgumentException을 던진다")
  void check_invalidWindowSeconds_throwsIllegalArgument() {
    assertThatThrownBy(() ->
      rateLimitService.check(MEMBER_ID, API, LIMIT, 0)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("윈도우 내 요청 수가 한도 이하이면 예외 없이 통과한다")
  void check_withinLimit_doesNotThrow() {
    // given - 현재 요청 수 3 (limit 5 이하)
    given(stringRedisTemplate.execute(
      ArgumentMatchers.<DefaultRedisScript<Long>>any(), anyList(), any(Object[].class))
    ).willReturn(3L);

    // when & then
    assertThatCode(() ->
      rateLimitService.check(MEMBER_ID, API, LIMIT, WINDOW_SECONDS)
    ).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("윈도우 내 요청 수가 한도를 초과하면 RATE_LIMIT_EXCEEDED 예외를 던진다")
  void check_exceedsLimit_throwsRateLimitExceeded() {
    // given - 현재 요청 수 6 (limit 5 초과)
    given(stringRedisTemplate.execute(
      ArgumentMatchers.<DefaultRedisScript<Long>>any(), anyList(), any(Object[].class))
    ).willReturn(6L);

    // when & then
    assertThatThrownBy(() ->
      rateLimitService.check(MEMBER_ID, API, LIMIT, WINDOW_SECONDS)
    )
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(RateLimitErrorCode.RATE_LIMIT_EXCEEDED);
      });
  }

  @Test
  @DisplayName("Redis 장애 시 fail-open 정책으로 예외 없이 통과한다")
  void check_redisFailure_failOpen() {
    // given - Redis 장애 시뮬레이션
    given(stringRedisTemplate.execute(
      ArgumentMatchers.<DefaultRedisScript<Long>>any(), anyList(), any(Object[].class))
    ).willThrow(new RuntimeException("Redis connection refused"));

    // when & then - fail-open: 요청 통과
    assertThatCode(() ->
      rateLimitService.check(MEMBER_ID, API, LIMIT, WINDOW_SECONDS)
    ).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("memberId가 null이면 IllegalArgumentException을 던진다")
  void check_nullMemberId_throwsIllegalArgument() {
    assertThatThrownBy(() ->
      rateLimitService.check(null, API, LIMIT, WINDOW_SECONDS)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("limit이 0 이하이면 IllegalArgumentException을 던진다")
  void check_invalidLimit_throwsIllegalArgument() {
    assertThatThrownBy(() ->
      rateLimitService.check(MEMBER_ID, API, 0, WINDOW_SECONDS)
    ).isInstanceOf(IllegalArgumentException.class);
  }
}