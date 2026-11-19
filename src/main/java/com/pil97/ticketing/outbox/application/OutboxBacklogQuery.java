package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.domain.OutboxRetryPolicy;
import com.pil97.ticketing.outbox.domain.OutboxStatus;
import com.pil97.ticketing.outbox.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 처리 지연 관측 (TASK-033-1)
 * - 대상·백오프 판정 조건(next_attempt_at이 없거나 now(초 단위 버림) 이하이면 대상)은 relay 후보 조회와 같다.
 *   단 기준 시각은 다를 수 있다: relay는 후보를 폴링 시작 시각(T_poll)으로 조회하고, backlog는 항목 처리 후 다시 읽은 시각을 now로 넘긴다
 * - 경과 시간은 DB가 채운 created_at부터 now까지의 초다 (애플리케이션 시계와 DB 시계가 섞인다)
 */
@Component
@RequiredArgsConstructor
public class OutboxBacklogQuery {

  private final OutboxEventRepository outboxEventRepository;

  /**
   * @param dueCount                지금 처리 대상인 PENDING 건수
   * @param backoffCount            다음 시도 시각 전인 PENDING 건수
   * @param failedCount             FAILED 건수
   * @param oldestDueAgeSeconds     대상 PENDING 중 가장 오래된 행의 경과 시간 (없으면 null) - 그 행 처리 지연의 하한
   * @param oldestPendingAgeSeconds PENDING 전체 중 가장 오래된 행의 경과 시간 (없으면 null)
   */
  public record Snapshot(long dueCount, long backoffCount, long failedCount,
                         Long oldestDueAgeSeconds, Long oldestPendingAgeSeconds) {

    public boolean isEmpty() {
      return dueCount == 0 && backoffCount == 0 && failedCount == 0;
    }
  }

  private static final List<OutboxStatus> BACKLOG_STATUSES = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
  private static final Snapshot EMPTY = new Snapshot(0, 0, 0, null, null);

  @Transactional(readOnly = true)
  public Snapshot snapshot(LocalDateTime now) {
    // PENDING·FAILED가 하나도 없으면 집계 조회를 생략한다 (같은 읽기 전용 트랜잭션 안에서 판단)
    if (!outboxEventRepository.existsByStatusIn(BACKLOG_STATUSES)) {
      return EMPTY;
    }
    LocalDateTime at = OutboxRetryPolicy.floorSeconds(now);
    return new Snapshot(
      outboxEventRepository.countDue(OutboxStatus.PENDING, at),
      outboxEventRepository.countBackoff(OutboxStatus.PENDING, at),
      outboxEventRepository.countByStatus(OutboxStatus.FAILED),
      ageSeconds(outboxEventRepository.findOldestDueCreatedAt(OutboxStatus.PENDING, at), now),
      ageSeconds(outboxEventRepository.findOldestCreatedAt(OutboxStatus.PENDING), now));
  }

  private static Long ageSeconds(LocalDateTime createdAt, LocalDateTime now) {
    return createdAt == null ? null : Duration.between(createdAt, now).getSeconds();
  }
}
