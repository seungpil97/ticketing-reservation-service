package com.pil97.ticketing.outbox.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Outbox 재시도 정책 (TASK-033-1)
 * - k번째 기록된 실패 뒤 대기 시간 = min(initialBackoff × 2^(k-1), maxBackoff), 곱이 상한을 넘거나 k-1 ≥ 31이면 곱셈 없이 상한
 * - 기록된 실패가 maxAttempts에 도달하면 소진 (FAILED)
 * - 설정 범위: maxAttempts 1~10, initialBackoff 1초~1시간, maxBackoff initialBackoff~24시간. 범위 밖은 생성 시 거부한다
 *   (0초 백오프는 기존 즉시 재시도와 같아지므로 허용하지 않는다)
 */
public final class OutboxRetryPolicy {

  private static final int MIN_ATTEMPTS = 1;
  private static final int MAX_ATTEMPTS = 10;
  private static final Duration MIN_INITIAL = Duration.ofSeconds(1);
  private static final Duration MAX_INITIAL = Duration.ofHours(1);
  private static final Duration MAX_BACKOFF = Duration.ofHours(24);
  private static final int MAX_SHIFT = 31;

  private final int maxAttempts;
  private final Duration initialBackoff;
  private final Duration maxBackoff;

  public OutboxRetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
    if (maxAttempts < MIN_ATTEMPTS || maxAttempts > MAX_ATTEMPTS) {
      throw new IllegalArgumentException("outbox retry max-attempts must be 1..10: " + maxAttempts);
    }
    if (initialBackoff == null || maxBackoff == null) {
      throw new IllegalArgumentException("outbox retry backoff must not be null");
    }
    if (initialBackoff.compareTo(MIN_INITIAL) < 0 || initialBackoff.compareTo(MAX_INITIAL) > 0) {
      throw new IllegalArgumentException("outbox retry initial-backoff must be 1s..1h: " + initialBackoff);
    }
    if (maxBackoff.compareTo(initialBackoff) < 0 || maxBackoff.compareTo(MAX_BACKOFF) > 0) {
      throw new IllegalArgumentException("outbox retry max-backoff must be initial-backoff..24h: " + maxBackoff);
    }
    this.maxAttempts = maxAttempts;
    this.initialBackoff = initialBackoff;
    this.maxBackoff = maxBackoff;
  }

  /**
   * @param recordedFailures 이번 실패를 포함해 기록된 실패 횟수 (1부터)
   */
  public Duration delayAfter(int recordedFailures) {
    if (recordedFailures <= 0) {
      throw new IllegalArgumentException("recorded failures must be positive: " + recordedFailures);
    }
    int shift = recordedFailures - 1;
    if (shift >= MAX_SHIFT) {
      return maxBackoff;
    }
    long factor = 1L << shift;
    // initial × factor > max 를 곱셈 없이 판정해 overflow를 피한다
    if (initialBackoff.toNanos() > maxBackoff.toNanos() / factor) {
      return maxBackoff;
    }
    Duration delay = initialBackoff.multipliedBy(factor);
    return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
  }

  /**
   * @param recordedFailures 이번 실패를 포함해 기록된 실패 횟수
   */
  public boolean isExhausted(int recordedFailures) {
    if (recordedFailures <= 0) {
      throw new IllegalArgumentException("recorded failures must be positive: " + recordedFailures);
    }
    return recordedFailures >= maxAttempts;
  }

  /**
   * 초 단위 버림 (DATETIME 저장 시각)
   */
  public static LocalDateTime floorSeconds(LocalDateTime time) {
    return time.truncatedTo(ChronoUnit.SECONDS);
  }

  /**
   * 초 단위 올림 - 다음 시도 시각을 최소 대기보다 일찍 만들지 않는다
   */
  public static LocalDateTime ceilSeconds(LocalDateTime time) {
    LocalDateTime floor = floorSeconds(time);
    return floor.equals(time) ? floor : floor.plusSeconds(1);
  }
}
