package com.pil97.ticketing.infra.queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueueRedisRepository Redis 연동 테스트
 * 실제 Redis에 key가 생성/삭제되는지 검증한다.
 * Mockito verify로는 실제 Redis 동작을 보장할 수 없으므로 별도 연동 테스트로 분리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
class QueueRedisRepositoryTest {

  @Autowired
  private QueueRedisRepository queueRedisRepository;

  @Autowired
  private StringRedisTemplate redisTemplate;

  private static final Long EVENT_ID = 999L;

  @AfterEach
  void tearDown() {
    // 테스트 후 잔여 key 정리
    redisTemplate.delete("queue:seq:" + EVENT_ID);
  }

  @Test
  @DisplayName("deleteSeq: queue:seq:{eventId} key가 실제로 삭제된다")
  void deleteSeq_removesKeyFromRedis() {
    // given - nextScore() 호출로 queue:seq:{eventId} key 생성
    queueRedisRepository.nextScore(EVENT_ID);
    assertThat(redisTemplate.hasKey("queue:seq:" + EVENT_ID)).isTrue();

    // when
    queueRedisRepository.deleteSeq(EVENT_ID);

    // then
    assertThat(redisTemplate.hasKey("queue:seq:" + EVENT_ID)).isFalse();
  }
}