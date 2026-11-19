package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.domain.OutboxRetryPolicy;
import com.pil97.ticketing.outbox.domain.OutboxStatus;
import com.pil97.ticketing.outbox.domain.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 성공 Outbox relay coordinator (TASK-033)
 * - 외부 트랜잭션 안에서 호출하면 IllegalTransactionStateException (NEVER) - 항목별 독립 트랜잭션 계약을 지키기 위함
 * - 후보 [ID, 상태 버전]을 잠금 없이 조회한 뒤, 항목마다 별도 bean(OutboxItemProcessor)의 독립 트랜잭션으로 처리한다
 * - 항목 실패는 그 항목의 rollback이 끝난 뒤 여기서 실패 감지 시각을 읽고, 별도 bean(OutboxFailureRecorder)의 독립 트랜잭션으로
 *   후보 조회 때의 상태 버전과 함께 기록한다. 기록 실패도 밖으로 전파하지 않고 다음 후보를 계속 처리한다 (TASK-033-1)
 * - 로그에는 Outbox ID와 허용된 분류 정보만 남긴다: 예외 메시지·payload·접속 정보·예외 객체를 남기지 않는다 (TASK-033-1)
 *
 * <p>순회 (단일 인스턴스, 위치는 메모리에만 둔다 - 재시작하면 새 순회부터):
 * <ul>
 *   <li>순회 시작 시 지금 처리 대상인 최대 PENDING ID를 상한으로 고정하고, 상한 이하 처리 대상을 ID 오름차순으로 순회한다</li>
 *   <li>처리 대상: PENDING이고 다음 시도 시각이 없거나 호출 시작 시각(초 단위 버림) 이하 (TASK-033-1)</li>
 *   <li>호출 1회는 직전 위치 뒤의 처리 대상을 최대 batch건 시도하고, 성공·실패와 관계없이 위치를 전진한다</li>
 *   <li>상한에 도달하거나 batch보다 적게 선택되면 순회를 끝낸다 - 실패 건은 다음 시도 시각 이후 순회에서 다시 시도된다</li>
 *   <li>진행 중 순회의 잔여 범위가 0건이면 같은 호출에서 새 순회를 최대 한 번 시작한다</li>
 * </ul>
 * 위치 조회부터 갱신까지 호출을 직렬화한다 (스케줄러와 수동 호출이 겹쳐도 위치가 섞이지 않는다).
 *
 * <p>처리 지연 관측 (TASK-033-1): 대상·백오프·FAILED 건수 중 하나라도 있으면 직전 출력 후
 * outbox.relay.backlog-log-interval 이상 지났을 때만 backlog 로그를 남긴다.
 * 관측은 항목 처리를 마친 뒤 Clock을 다시 읽은 시각 기준이고(후보 조회 시각과 분리), 간격은 실제 출력 시각부터 센다.
 */
@Slf4j
@Service
public class OutboxRelayService {

  private static final Duration MIN_BACKLOG_INTERVAL = Duration.ofSeconds(10);
  private static final Duration MAX_BACKLOG_INTERVAL = Duration.ofHours(1);

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxItemProcessor itemProcessor;
  private final OutboxFailureClassifier failureClassifier;
  private final OutboxFailureRecorder failureRecorder;
  private final OutboxBacklogQuery backlogQuery;
  private final Clock clock;
  private final int batchSize;
  private final Duration backlogLogInterval;

  // 진행 중 순회의 상한 (null이면 진행 중 순회 없음)과 마지막으로 시도한 ID
  private Long sweepUpper;
  private long sweepCursor;
  // 마지막으로 backlog 로그를 실제 출력한 시각 (null이면 아직 출력하지 않음). 관측 실패·출력 생략 시에는 갱신하지 않는다
  private LocalDateTime lastBacklogLogAt;

  private record Candidate(Long id, long stateVersion) {
  }

  public OutboxRelayService(OutboxEventRepository outboxEventRepository, OutboxItemProcessor itemProcessor,
                            OutboxFailureClassifier failureClassifier, OutboxFailureRecorder failureRecorder,
                            OutboxBacklogQuery backlogQuery, Clock clock,
                            @Value("${outbox.relay.batch-size}") int batchSize,
                            @Value("${outbox.relay.backlog-log-interval}") Duration backlogLogInterval) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("outbox.relay.batch-size must be positive: " + batchSize);
    }
    if (backlogLogInterval == null || backlogLogInterval.compareTo(MIN_BACKLOG_INTERVAL) < 0
        || backlogLogInterval.compareTo(MAX_BACKLOG_INTERVAL) > 0) {
      throw new IllegalArgumentException("outbox.relay.backlog-log-interval must be 10s..1h: " + backlogLogInterval);
    }
    this.outboxEventRepository = outboxEventRepository;
    this.itemProcessor = itemProcessor;
    this.failureClassifier = failureClassifier;
    this.failureRecorder = failureRecorder;
    this.backlogQuery = backlogQuery;
    this.clock = clock;
    this.batchSize = batchSize;
    this.backlogLogInterval = backlogLogInterval;
  }

  /**
   * 처리 대상 Outbox 폴링 1회
   *
   * @return 처리 완료로 표시한 Outbox 건수
   */
  @Transactional(propagation = Propagation.NEVER)
  public synchronized int relay() {
    LocalDateTime pollAt = LocalDateTime.now(clock);
    List<Candidate> candidates = nextCandidates(OutboxRetryPolicy.floorSeconds(pollAt));

    int processed = 0;
    int skipped = 0;
    int failed = 0;
    for (Candidate candidate : candidates) {
      try {
        if (itemProcessor.process(candidate.id())) {
          processed++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        // 항목 트랜잭션은 이미 rollback됐다. 실패를 별도 트랜잭션으로 기록하고 다음 시도 시각 이후 다시 시도한다
        failed++;
        recordFailure(candidate, LocalDateTime.now(clock), e);
      }
    }
    advance(candidates);

    if (!candidates.isEmpty()) {
      log.info("Outbox relay processed. candidates={} processed={} skipped={} failed={}",
        candidates.size(), processed, skipped, failed);
    }
    logBacklog();
    return processed;
  }

  private void recordFailure(Candidate candidate, LocalDateTime failedAt, RuntimeException failure) {
    OutboxFailureClassifier.Failure classified = failureClassifier.classify(failure);
    log.error("Outbox processing failed. outboxEventId={} errorCode={} errorType={} sqlState={} vendorCode={}",
      candidate.id(), classified.code(), classified.type(), classified.sqlState(), classified.vendorCode());
    try {
      failureRecorder.record(candidate.id(), candidate.stateVersion(), failedAt, classified);
    } catch (RuntimeException recordFailure) {
      // 기록 트랜잭션도 rollback됐다. 해당 행은 횟수·버전이 그대로 남아 다음 순회에서 다시 대상이 된다
      OutboxFailureClassifier.Failure recordClassified = failureClassifier.classify(recordFailure);
      log.error("Outbox failure record failed. outboxEventId={} errorCode={} errorType={} sqlState={} vendorCode={}",
        candidate.id(), recordClassified.code(), recordClassified.type(), recordClassified.sqlState(),
        recordClassified.vendorCode());
    }
  }

  private void logBacklog() {
    // 관측 기준 시각: 항목 처리 이후의 현재 시각 (후보 조회에 쓴 폴링 시작 시각을 쓰지 않는다)
    LocalDateTime observedAt = LocalDateTime.now(clock);
    if (lastBacklogLogAt != null && observedAt.isBefore(lastBacklogLogAt.plus(backlogLogInterval))) {
      return;
    }
    try {
      OutboxBacklogQuery.Snapshot snapshot = backlogQuery.snapshot(observedAt);
      if (snapshot.isEmpty()) {
        return;
      }
      log.info("Outbox backlog. due={} backoff={} failed={} oldestDueAgeSeconds={} oldestPendingAgeSeconds={}",
        snapshot.dueCount(), snapshot.backoffCount(), snapshot.failedCount(),
        snapshot.oldestDueAgeSeconds(), snapshot.oldestPendingAgeSeconds());
      // 간격 기준은 실제 출력 시각 (snapshot 조회가 지연돼도 관측 기준 시각과 구분한다)
      lastBacklogLogAt = LocalDateTime.now(clock);
    } catch (RuntimeException e) {
      // 관측 실패는 relay 처리 결과에 영향을 주지 않는다
      log.warn("Outbox backlog snapshot failed. errorType={}", failureClassifier.classify(e).type());
    }
  }

  /**
   * 이번 호출의 후보 - 진행 중 순회의 잔여 범위, 비어 있으면 새 순회를 한 번 시작한다
   */
  private List<Candidate> nextCandidates(LocalDateTime now) {
    if (sweepUpper != null) {
      List<Candidate> remaining = findRange(sweepCursor, sweepUpper, now);
      if (!remaining.isEmpty()) {
        return remaining;
      }
      endSweep();
    }

    Long upper = outboxEventRepository.findMaxDueId(OutboxStatus.PENDING, now);
    if (upper == null) {
      return List.of();
    }
    sweepUpper = upper;
    sweepCursor = 0L;
    List<Candidate> candidates = findRange(sweepCursor, sweepUpper, now);
    if (candidates.isEmpty()) {
      endSweep();
    }
    return candidates;
  }

  private List<Candidate> findRange(long after, long upper, LocalDateTime now) {
    return outboxEventRepository.findDueCandidatesInRange(OutboxStatus.PENDING, after, upper, now, Limit.of(batchSize))
      .stream()
      .map(row -> new Candidate((Long) row[0], ((Number) row[1]).longValue()))
      .toList();
  }

  /**
   * 시도한 마지막 ID로 위치를 전진하고, 상한 도달 또는 범위 소진이면 순회를 끝낸다
   */
  private void advance(List<Candidate> candidates) {
    if (candidates.isEmpty() || sweepUpper == null) {
      return;
    }
    sweepCursor = candidates.get(candidates.size() - 1).id();
    if (candidates.size() < batchSize || sweepCursor >= sweepUpper) {
      endSweep();
    }
  }

  private void endSweep() {
    sweepUpper = null;
    sweepCursor = 0L;
  }
}
