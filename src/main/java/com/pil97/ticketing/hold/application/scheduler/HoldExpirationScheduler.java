package com.pil97.ticketing.hold.application.scheduler;

import com.pil97.ticketing.hold.application.HoldExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HoldExpirationScheduler {

  private final HoldExpirationService holdExpirationService;

  @Scheduled(fixedDelay = 30000)
  public void expireHolds() {
    holdExpirationService.expireHolds(LocalDateTime.now());
  }
}
