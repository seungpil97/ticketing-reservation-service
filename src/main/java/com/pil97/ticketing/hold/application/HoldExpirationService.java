package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
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

    // fetch join으로 showtimeSeat를 한 번에 로드해서 N+1 방지
    List<Hold> expiredTargets =
      holdRepository.findAllByStatusAndExpiresAtBeforeWithSeat(HoldStatus.ACTIVE, now);

    for (Hold hold : expiredTargets) {
      hold.expire();
      hold.getShowtimeSeat().markAvailable(); // 추가 쿼리 없이 접근 가능
    }

    log.info("Expired holds processed. count={}", expiredTargets.size());
  }
}