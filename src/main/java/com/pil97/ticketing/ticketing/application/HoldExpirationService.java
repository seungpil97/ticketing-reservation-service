package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.ticketing.domain.Hold;
import com.pil97.ticketing.ticketing.domain.HoldStatus;
import com.pil97.ticketing.ticketing.domain.repository.HoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HoldExpirationService {

  private final HoldRepository holdRepository;

  public void expireHolds(LocalDateTime now) {

    List<Hold> expiredTargets =
      holdRepository.findAllByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, now);

    for (Hold hold : expiredTargets) {
      hold.expire();
      hold.getShowtimeSeat().markAvailable();
    }

    log.info("Expired holds processed. count={}", expiredTargets.size());
  }
}