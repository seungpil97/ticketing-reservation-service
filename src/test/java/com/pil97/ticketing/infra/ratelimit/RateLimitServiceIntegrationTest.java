package com.pil97.ticketing.infra.ratelimit;

import com.pil97.ticketing.common.error.RateLimitErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.ratelimit.RateLimitApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RateLimitService Redis 연동 테스트
 *
 * <p>Mockito로는 실제 Lua Script 원자성과 슬라이딩 윈도우 동작을 보장할 수 없다.
 * 실제 Redis를 사용해 윈도우 내 카운트, 초과 차단, 윈도우 만료, 동시 요청 처리를 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
class RateLimitServiceIntegrationTest {

  @Autowired
  private RateLimitService rateLimitService;

  @Autowired
  private StringRedisTemplate redisTemplate;

  private static final Long MEMBER_ID = 9999L;
  private static final Long OTHER_MEMBER_ID = 8888L;
  private static final RateLimitApi API = RateLimitApi.HOLD;

  @AfterEach
  void tearDown() {
    // 테스트 후 잔여 key 정리
    redisTemplate.delete("rate:limit:" + MEMBER_ID + ":" + API.name());
    redisTemplate.delete("rate:limit:" + OTHER_MEMBER_ID + ":" + API.name());
  }

  @Test
  @DisplayName("허용 횟수 이하 요청은 모두 통과한다")
  void check_withinLimit_allPass() {
    // when & then - limit 3, 3번 요청 모두 통과
    for (int i = 0; i < 3; i++) {
      rateLimitService.check(MEMBER_ID, API, 3, 60);
    }
  }

  @Test
  @DisplayName("허용 횟수 초과 요청은 RATE_LIMIT_EXCEEDED 예외를 던진다")
  void check_exceedsLimit_throwsException() {
    // given - limit 3, 3번 통과
    for (int i = 0; i < 3; i++) {
      rateLimitService.check(MEMBER_ID, API, 3, 60);
    }

    // when & then - 4번째 요청 차단
    assertThatThrownBy(() ->
      rateLimitService.check(MEMBER_ID, API, 3, 60)
    )
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(RateLimitErrorCode.RATE_LIMIT_EXCEEDED);
      });
  }

  @Test
  @DisplayName("다른 memberId는 독립적으로 카운트를 관리한다")
  void check_differentMembers_independentCount() {
    // given - MEMBER_ID는 이미 한도 초과
    for (int i = 0; i < 3; i++) {
      rateLimitService.check(MEMBER_ID, API, 3, 60);
    }

    // when & then - OTHER_MEMBER_ID는 영향 없이 통과
    rateLimitService.check(OTHER_MEMBER_ID, API, 3, 60);
  }

  @Test
  @DisplayName("윈도우(1초) 경과 후 요청은 카운트가 초기화되어 통과한다")
  void check_afterWindowExpires_countResets() throws InterruptedException {
    // given - windowSeconds=1, limit=2로 한도 채움
    rateLimitService.check(MEMBER_ID, API, 2, 1);
    rateLimitService.check(MEMBER_ID, API, 2, 1);

    // when - 윈도우(1초) 대기
    Thread.sleep(1100);

    // then - 윈도우 만료 후 통과
    rateLimitService.check(MEMBER_ID, API, 2, 1);
  }

  @Test
  @DisplayName("동시 요청 시 Lua Script 원자성으로 정확하게 카운트된다")
  void check_concurrentRequests_atomicCount() throws InterruptedException {
    int threadCount = 10;
    int limit = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger passCount = new AtomicInteger(0);
    AtomicInteger rejectCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          rateLimitService.check(MEMBER_ID, API, limit, 60);
          passCount.incrementAndGet();
        } catch (BusinessException e) {
          rejectCount.incrementAndGet();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();

    assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();

    // limit=5, 10개 요청 → 정확히 5개 통과, 5개 차단
    assertThat(passCount.get()).isEqualTo(limit);
    assertThat(rejectCount.get()).isEqualTo(threadCount - limit);
  }
}