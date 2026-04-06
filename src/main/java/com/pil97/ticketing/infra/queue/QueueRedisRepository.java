package com.pil97.ticketing.infra.queue;

import com.pil97.ticketing.queue.domain.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * QueueRepository의 Redis 구현체
 * <p>
 * Redis Key 규칙:
 * - 대기열: queue:event:{eventId} (Sorted Set, score = 진입 timestamp)
 * - 입장 토큰: token:user:{memberId} (String, TTL 30분)
 * - 활성 대기열 이벤트 목록: queue:active:events (Set)
 * - 입장 허용 이력: queue:admitted:members:{eventId} (Set)
 */
@Repository
@RequiredArgsConstructor
public class QueueRedisRepository implements QueueRepository {

  private static final String QUEUE_KEY_PREFIX = "queue:event:";
  private static final String TOKEN_KEY_PREFIX = "token:user:";
  private static final String ACTIVE_EVENTS_KEY = "queue:active:events";
  private static final String ADMITTED_MEMBERS_KEY_PREFIX = "queue:admitted:members:";
  private static final Duration ADMISSION_TOKEN_TTL = Duration.ofMinutes(30);
  private static final String QUEUE_SEQ_KEY_PREFIX = "queue:seq:";

  private final StringRedisTemplate redisTemplate;

  /**
   * 대기열 등록 - ZADD NX 옵션 적용
   * 이미 등록된 memberId는 score 갱신 없이 무시한다.
   */
  @Override
  public boolean addIfAbsent(Long eventId, Long memberId, double score) {
    Boolean result = redisTemplate.opsForZSet()
      .addIfAbsent(queueKey(eventId), String.valueOf(memberId), score);
    return Boolean.TRUE.equals(result);
  }

  /**
   * 대기열 강제 등록 - ZREM 후 ZADD
   * 기존 순번을 초기화하고 맨 뒤로 재등록한다.
   * 재진입 시 사용한다.
   */
  @Override
  public void addOrReplace(Long eventId, Long memberId, double score) {
    // 기존 순번 제거 후 새 score로 재등록
    redisTemplate.opsForZSet().remove(queueKey(eventId), String.valueOf(memberId));
    redisTemplate.opsForZSet().add(queueKey(eventId), String.valueOf(memberId), score);
  }

  /**
   * 대기열 순번 조회 (0-based)
   * ZRANK 명령 사용 - score 오름차순 기준 순번 반환
   */
  @Override
  public Long getRank(Long eventId, Long memberId) {
    return redisTemplate.opsForZSet()
      .rank(queueKey(eventId), String.valueOf(memberId));
  }

  /**
   * 상위 N명 memberId 조회
   * ZRANGE 0 N-1 명령으로 score 오름차순 상위 N명 반환
   */
  @Override
  public Set<String> getTopMembers(Long eventId, long count) {
    Set<String> members = redisTemplate.opsForZSet()
      .range(queueKey(eventId), 0, count - 1);
    return members != null ? members : Collections.emptySet();
  }

  /**
   * 대기열에서 특정 유저 제거
   * ZREM 명령 사용
   */
  @Override
  public void remove(Long eventId, Long memberId) {
    redisTemplate.opsForZSet()
      .remove(queueKey(eventId), String.valueOf(memberId));
  }

  /**
   * 입장 토큰 저장 - TTL 30분
   * SET token:user:{memberId} {token} EX 1800
   */
  @Override
  public void saveAdmissionToken(Long memberId, String token) {
    redisTemplate.opsForValue()
      .set(tokenKey(memberId), token, ADMISSION_TOKEN_TTL);
  }

  /**
   * 입장 토큰 존재 여부 확인
   * Redis key 존재 여부로 토큰 유효성 판단 (TTL 만료 시 자동 삭제됨)
   */
  @Override
  public boolean hasAdmissionToken(Long memberId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(memberId)));
  }

  /**
   * 활성 대기열 이벤트 등록
   * SADD queue:active:events {eventId}
   */
  @Override
  public void addActiveEvent(Long eventId) {
    redisTemplate.opsForSet()
      .add(ACTIVE_EVENTS_KEY, String.valueOf(eventId));
  }

  /**
   * 활성 대기열 이벤트 제거
   * SREM queue:active:events {eventId}
   */
  @Override
  public void removeActiveEvent(Long eventId) {
    redisTemplate.opsForSet()
      .remove(ACTIVE_EVENTS_KEY, String.valueOf(eventId));
  }

  /**
   * 활성 대기열 이벤트 ID 목록 조회
   * SMEMBERS queue:active:events
   */
  @Override
  public Set<String> getActiveEventIds() {
    Set<String> members = redisTemplate.opsForSet()
      .members(ACTIVE_EVENTS_KEY);
    return members != null ? members : Collections.emptySet();
  }

  /**
   * 대기열 key 삭제
   * DEL queue:event:{eventId}
   */
  @Override
  public void deleteQueue(Long eventId) {
    redisTemplate.delete(queueKey(eventId));
  }

  /**
   * 입장 허용 이력 저장
   * SADD queue:admitted:members:{eventId} {memberId}
   */
  @Override
  public void saveAdmittedHistory(Long eventId, Long memberId) {
    redisTemplate.opsForSet()
      .add(admittedMembersKey(eventId), String.valueOf(memberId));
  }

  /**
   * 입장 허용 이력 존재 여부 확인
   * SISMEMBER queue:admitted:members:{eventId} {memberId}
   */
  @Override
  public boolean hasAdmittedHistory(Long eventId, Long memberId) {
    return Boolean.TRUE.equals(redisTemplate.opsForSet()
      .isMember(admittedMembersKey(eventId), String.valueOf(memberId)));
  }

  /**
   * 입장 허용 이력 Set 전체 조회
   * SMEMBERS queue:admitted:members:{eventId}
   */
  @Override
  public Set<String> getAdmittedMembers(Long eventId) {
    Set<String> members = redisTemplate.opsForSet()
      .members(admittedMembersKey(eventId));
    return members != null ? members : Collections.emptySet();
  }

  /**
   * 입장 허용 이력 key 삭제
   * DEL queue:admitted:members:{eventId}
   */
  @Override
  public void deleteAdmittedHistory(Long eventId) {
    redisTemplate.delete(admittedMembersKey(eventId));
  }

  // queue:event:{eventId}
  private String queueKey(Long eventId) {
    return QUEUE_KEY_PREFIX + eventId;
  }

  // token:user:{memberId}
  private String tokenKey(Long memberId) {
    return TOKEN_KEY_PREFIX + memberId;
  }

  /**
   * 대기열 순번용 전역 카운터 증가
   * INCR queue:seq:{eventId} - Redis 단일 스레드 보장으로 동시성 안전
   */
  @Override
  public long nextScore(Long eventId) {
    Long seq = redisTemplate.opsForValue()
      .increment(seqKey(eventId));
    return seq != null ? seq : 0L;
  }

  // queue:seq:{eventId}
  private String seqKey(Long eventId) {
    return QUEUE_SEQ_KEY_PREFIX + eventId;
  }

  // queue:admitted:members:{eventId}
  private String admittedMembersKey(Long eventId) {
    return ADMITTED_MEMBERS_KEY_PREFIX + eventId;
  }
}