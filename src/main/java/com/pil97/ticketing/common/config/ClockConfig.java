package com.pil97.ticketing.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 시각 소스 (TASK-033-1)
 * - Outbox relay·처리기·실패 기록의 시각 계약(후보 조회·잠금 후 재확인·처리 완료·실패 감지·기록 시각)에서 사용한다
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
