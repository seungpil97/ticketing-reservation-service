package com.pil97.ticketing.queue.application;

import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.queue.application.scheduler.QueueScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 테스트 흐름 설명
 * 1. 100개 스레드가 동시에 동일 이벤트 대기열에 등록 요청
 * 2. Redis Sorted Set ZADD NX로 각 memberId가 중복 없이 등록되는지 검증
 * 3. 등록된 유저 수 == 100, 순번 중복 없음 검증
 * <p>
 * EventRepository mock 이유:
 * 100개 스레드가 동시에 existsById() DB 쿼리를 실행하면 HikariCP 커넥션 풀이
 * 고갈되어 done.await() 타임아웃이 발생할 수 있다.
 * 이 테스트의 목적은 Redis Sorted Set 동시 접근 안정성 검증이므로
 * DB 호출은 mock으로 처리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
class QueueConcurrencyTest {

  // QueueScheduler가 테스트 중 실행되면 대기열에서 멤버를 제거해 순번 검증이 깨지므로 MockitoBean으로 비활성화
  @MockitoBean
  private QueueScheduler queueScheduler;

  // EventRepository mock 이유: 위 클래스 주석 참조
  @MockitoBean
  private EventRepository eventRepository;

  @Autowired
  private QueueService queueService;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long eventId;

  @BeforeEach
  void setUp() {
    // seed 데이터 기준으로 첫 번째 이벤트 사용 - 실제 DB에 존재하는 이벤트이므로 mock 불필요
    eventId = jdbcTemplate.queryForObject(
      "select id from event order by id limit 1", Long.class);

    // 테스트 전 대기열 및 입장 허용 이력 초기화
    redisTemplate.delete("queue:event:" + eventId);
    redisTemplate.delete("queue:admitted:members:" + eventId);
    // seq 카운터 초기화 - 테스트 간 score 독립성 보장
    redisTemplate.delete("queue:seq:" + eventId);
    // queue:active:events에서 해당 eventId 제거 - 테스트 간 잔여 데이터 방지
    redisTemplate.opsForSet().remove("queue:active:events", String.valueOf(eventId));

    given(eventRepository.existsById(anyLong())).willReturn(true);
  }

  @Test
  @DisplayName("100명이 동시에 대기열에 등록하면 순번 중복 없이 100개가 발급된다")
  void enter_concurrency_no_duplicate_rank() throws InterruptedException {
    // given
    int threadCount = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount); // 모든 스레드 준비 완료 신호
    CountDownLatch start = new CountDownLatch(1);           // 동시 출발 신호
    CountDownLatch done = new CountDownLatch(threadCount);  // 모든 스레드 완료 신호

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when
    for (int i = 0; i < threadCount; i++) {
      final long memberId = i + 1L;
      executor.submit(() -> {
        ready.countDown();     // 준비 완료 알림
        try {
          start.await();     // 출발 신호 대기
          queueService.enter(eventId, memberId);
          successCount.incrementAndGet();
        } catch (Exception e) {
          failCount.incrementAndGet();
          System.err.println("예외 발생: " + e.getClass().getName() + " - " + e.getMessage());
        } finally {
          done.countDown();  // 완료 알림
        }
      });
    }

    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    // then - 100명 전원 성공
    assertThat(successCount.get()).isEqualTo(threadCount);
    assertThat(failCount.get()).isEqualTo(0);

    // Redis Sorted Set에 100개 등록 확인
    Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
      redisTemplate.opsForZSet()
        .rangeWithScores("queue:event:" + eventId, 0, -1);

    assertThat(tuples).hasSize(threadCount);

// score(순번) 중복 체크
    Set<Double> scores = new java.util.HashSet<>();
    for (var t : tuples) {
      scores.add(t.getScore());
    }
    assertThat(scores).hasSize(threadCount);
  }

  @Test
  @DisplayName("동일 유저가 동시에 중복 등록 시도하면 1번만 등록된다")
  void enter_duplicateConcurrency_onlyOneRegistered() throws InterruptedException {
    // given
    int threadCount = 10;
    Long memberId = 1L;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    // when
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        ready.countDown();
        try {
          start.await();
          queueService.enter(eventId, memberId);
        } catch (Exception ignored) {
        } finally {
          done.countDown();
        }
      });
    }

    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    // then - 동일 memberId는 Sorted Set에 1개만 존재해야 함
    Long rank = redisTemplate.opsForZSet()
      .rank("queue:event:" + eventId, String.valueOf(memberId));
    assertThat(rank).isNotNull();

    Long totalSize = redisTemplate.opsForZSet()
      .size("queue:event:" + eventId);
    assertThat(totalSize).isEqualTo(1L);
  }
}