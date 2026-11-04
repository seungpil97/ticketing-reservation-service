package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.ExpirationCandidate;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HOLD 만료 coordinator (TASK-062)
 * - 외부 트랜잭션 안에서 호출하면 IllegalTransactionStateException (NEVER) - 항목별 독립 트랜잭션 계약을 지키기 위함
 * - 후보 (HOLD ID, 좌석 ID)를 잠금 없이 조회한 뒤, 항목마다 별도 bean(HoldExpirationItemProcessor)의 독립 트랜잭션으로 처리한다
 * - 항목 실패는 그 항목의 rollback이 끝난 뒤 여기서 집계하고 다음 후보를 계속 처리한다 (밖으로 전파하지 않음)
 * - 요약 로그: 같은 이벤트에 expired·failed 기록. 1020 충돌 실패도 failed에 포함한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldExpirationService {

  private final HoldRepository holdRepository;
  private final HoldExpirationItemProcessor itemProcessor;

  @Transactional(propagation = Propagation.NEVER)
  public void expireHolds(LocalDateTime now) {

    // 후보 조회 - expiresAt < now (같은 시각은 대상 아님). 처리 전 항목 트랜잭션에서 잠금 후 재검증한다
    List<ExpirationCandidate> candidates = holdRepository.findExpirationCandidates(HoldStatus.ACTIVE, now);

    int expired = 0;
    int skipped = 0;
    int failed = 0;
    for (ExpirationCandidate candidate : candidates) {
      try {
        if (itemProcessor.expire(candidate, now)) {
          expired++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        // 항목 트랜잭션은 이미 rollback됐다. 다른 항목에 영향 없이 다음 후보를 처리한다
        failed++;
        log.error("Hold expiration failed. holdId={}", candidate.holdId(), e);
      }
    }

    log.info("Expired holds processed. candidates={} expired={} skipped={} failed={}",
      candidates.size(), expired, skipped, failed);
  }
}
