package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.domain.OutboxEvent;
import com.pil97.ticketing.outbox.domain.OutboxRetryPolicy;
import com.pil97.ticketing.outbox.domain.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Outbox 처리 실패 기록 - 처리 트랜잭션이 rollback된 뒤 독립 트랜잭션으로 기록한다 (TASK-033-1)
 * - OutboxRelayService(coordinator)와 다른 bean이어야 트랜잭션 프록시가 적용된다
 * - 행을 잠근 뒤 PENDING이고 상태 버전이 후보 조회 때와 같을 때만 반영한다: 이미 처리·소진·재처리된 행이나
 *   다른 회차의 행에 늦게 도착한 기록은 아무것도 바꾸지 않는다
 * - 다음 시도 시각 = ceil_s(max(실패 감지 시각, 잠금 획득 시각) + 대기 시간). 기록된 실패가 소진되면 FAILED
 */
@Component
public class OutboxFailureRecorder {

  public enum Result {
    APPLIED,
    IGNORED_STALE
  }

  private final OutboxEventRepository outboxEventRepository;
  private final Clock clock;
  private final OutboxRetryPolicy retryPolicy;

  public OutboxFailureRecorder(OutboxEventRepository outboxEventRepository, Clock clock,
                               @Value("${outbox.relay.retry.max-attempts}") int maxAttempts,
                               @Value("${outbox.relay.retry.initial-backoff}") Duration initialBackoff,
                               @Value("${outbox.relay.retry.max-backoff}") Duration maxBackoff) {
    this.outboxEventRepository = outboxEventRepository;
    this.clock = clock;
    this.retryPolicy = new OutboxRetryPolicy(maxAttempts, initialBackoff, maxBackoff);
  }

  /**
   * @param expectedVersion 후보 조회 때 읽은 상태 버전
   * @param failedAt        실패 감지 시각 (T_fail)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Result record(Long outboxEventId, long expectedVersion, LocalDateTime failedAt,
                       OutboxFailureClassifier.Failure failure) {
    Optional<OutboxEvent> locked = outboxEventRepository.findByIdForUpdate(outboxEventId);
    if (locked.isEmpty() || !locked.get().isPending() || locked.get().getStateVersion() != expectedVersion) {
      return Result.IGNORED_STALE;
    }
    LocalDateTime recordedAt = LocalDateTime.now(clock);
    locked.get().recordFailure(retryPolicy, failedAt, recordedAt,
      failure.code(), failure.type(), failure.sqlState(), failure.vendorCode());
    return Result.APPLIED;
  }
}
