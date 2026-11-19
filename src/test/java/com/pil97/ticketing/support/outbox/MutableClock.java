package com.pil97.ticketing.support.outbox;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트 전용 시각 소스 - 지정한 시각에 멈춰 있고 명시적으로만 진행한다 (TASK-033-1)
 * - 여러 스레드가 함께 읽고 진행할 수 있다
 * - Config를 @Import하면 production Clock bean 대신 주입된다 (@Primary, 시작 시각 START)
 */
public class MutableClock extends Clock {

  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  public static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {
    @Bean
    @Primary
    public MutableClock mutableClock() {
      return new MutableClock(START, ZONE);
    }
  }

  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  public MutableClock(LocalDateTime start, ZoneId zone) {
    this.zone = zone;
    this.now = new AtomicReference<>(start.atZone(zone).toInstant());
  }

  public void set(LocalDateTime time) {
    now.set(time.atZone(zone).toInstant());
  }

  public void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
  }

  public LocalDateTime localNow() {
    return LocalDateTime.ofInstant(now.get(), zone);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("MutableClock does not support zone change");
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
