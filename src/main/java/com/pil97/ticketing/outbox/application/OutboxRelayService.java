package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.domain.OutboxStatus;
import com.pil97.ticketing.outbox.domain.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 성공 Outbox relay coordinator (TASK-033)
 * - 외부 트랜잭션 안에서 호출하면 IllegalTransactionStateException (NEVER) - 항목별 독립 트랜잭션 계약을 지키기 위함
 * - 후보 ID를 잠금 없이 조회한 뒤, 항목마다 별도 bean(OutboxItemProcessor)의 독립 트랜잭션으로 처리한다
 * - 항목 실패는 그 항목의 rollback이 끝난 뒤 여기서 집계하고 다음 후보를 계속 처리한다 (밖으로 전파하지 않음)
 *
 * <p>순회 (단일 인스턴스, 위치는 메모리에만 둔다 - 재시작하면 새 순회부터):
 * <ul>
 *   <li>순회 시작 시 최대 PENDING ID를 상한으로 고정하고, 상한 이하 PENDING을 ID 오름차순으로 순회한다</li>
 *   <li>호출 1회는 직전 위치 뒤의 PENDING을 최대 batch건 시도하고, 성공·실패와 관계없이 위치를 전진한다</li>
 *   <li>상한에 도달하거나 batch보다 적게 선택되면 순회를 끝낸다 - 실패 건은 다음 순회에서 다시 시도된다</li>
 *   <li>진행 중 순회의 잔여 범위가 0건이면 같은 호출에서 새 순회를 최대 한 번 시작한다</li>
 * </ul>
 * 위치 조회부터 갱신까지 호출을 직렬화한다 (스케줄러와 수동 호출이 겹쳐도 위치가 섞이지 않는다).
 */
@Slf4j
@Service
public class OutboxRelayService {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxItemProcessor itemProcessor;
  private final int batchSize;

  // 진행 중 순회의 상한 (null이면 진행 중 순회 없음)과 마지막으로 시도한 ID
  private Long sweepUpper;
  private long sweepCursor;

  public OutboxRelayService(OutboxEventRepository outboxEventRepository, OutboxItemProcessor itemProcessor,
                            @Value("${outbox.relay.batch-size}") int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("outbox.relay.batch-size must be positive: " + batchSize);
    }
    this.outboxEventRepository = outboxEventRepository;
    this.itemProcessor = itemProcessor;
    this.batchSize = batchSize;
  }

  /**
   * PENDING Outbox 폴링 1회
   *
   * @return 처리 완료로 표시한 Outbox 건수
   */
  @Transactional(propagation = Propagation.NEVER)
  public synchronized int relay() {
    List<Long> candidates = nextCandidates();

    int processed = 0;
    int skipped = 0;
    int failed = 0;
    for (Long outboxEventId : candidates) {
      try {
        if (itemProcessor.process(outboxEventId)) {
          processed++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        // 항목 트랜잭션은 이미 rollback됐다. PENDING으로 남겨 다음 순회에서 재시도한다
        failed++;
        log.error("Outbox processing failed. outboxEventId={}", outboxEventId, e);
      }
    }
    advance(candidates);

    if (!candidates.isEmpty()) {
      log.info("Outbox relay processed. candidates={} processed={} skipped={} failed={}",
        candidates.size(), processed, skipped, failed);
    }
    return processed;
  }

  /**
   * 이번 호출의 후보 - 진행 중 순회의 잔여 범위, 비어 있으면 새 순회를 한 번 시작한다
   */
  private List<Long> nextCandidates() {
    if (sweepUpper != null) {
      List<Long> remaining = findRange(sweepCursor, sweepUpper);
      if (!remaining.isEmpty()) {
        return remaining;
      }
      endSweep();
    }

    Long upper = outboxEventRepository.findMaxIdByStatus(OutboxStatus.PENDING);
    if (upper == null) {
      return List.of();
    }
    sweepUpper = upper;
    sweepCursor = 0L;
    List<Long> candidates = findRange(sweepCursor, sweepUpper);
    if (candidates.isEmpty()) {
      endSweep();
    }
    return candidates;
  }

  private List<Long> findRange(long after, long upper) {
    return outboxEventRepository.findIdsInRange(OutboxStatus.PENDING, after, upper, Limit.of(batchSize));
  }

  /**
   * 시도한 마지막 ID로 위치를 전진하고, 상한 도달 또는 범위 소진이면 순회를 끝낸다
   */
  private void advance(List<Long> candidates) {
    if (candidates.isEmpty() || sweepUpper == null) {
      return;
    }
    sweepCursor = candidates.get(candidates.size() - 1);
    if (candidates.size() < batchSize || sweepCursor >= sweepUpper) {
      endSweep();
    }
  }

  private void endSweep() {
    sweepUpper = null;
    sweepCursor = 0L;
  }
}
