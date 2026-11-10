package com.pil97.ticketing.outbox.application.scheduler;

import com.pil97.ticketing.outbox.application.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox relay 스케줄러 (TASK-033)
 * - fixedDelay: 이전 실행이 끝난 뒤 다음 실행을 시작하므로 스케줄러 자체는 겹쳐 실행되지 않는다
 * - outbox.relay.enabled=false면 등록하지 않는다 (test profile). 값이 없으면 등록한다
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

  private final OutboxRelayService outboxRelayService;

  @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms}")
  public void relay() {
    outboxRelayService.relay();
  }
}
