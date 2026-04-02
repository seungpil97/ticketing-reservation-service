package com.pil97.ticketing.queue.application.scheduler;

import com.pil97.ticketing.event.domain.Event;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.queue.application.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 대기열 입장 허용 스케줄러
 * <p>
 * QueueService에서 분리하여 스케줄러 진입점 역할만 담당한다.
 * 비즈니스 로직은 QueueService에 위임한다.
 * <p>
 * 실행 흐름:
 * 1. queue:active:events Set에서 활성 이벤트 ID 목록 조회
 * 2. 종료된 이벤트는 대기열 정리 후 active:events에서 제거
 * 3. 활성 이벤트별로 상위 N명 입장 허용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

  private final QueueService queueService;
  private final EventRepository eventRepository;

  /**
   * 입장 허용 스케줄러
   * <p>
   * 실행 주기: application.yml queue.scheduler.fixed-delay-ms
   * active:events Set을 순회하며 이벤트별로 처리한다.
   * 종료된 이벤트는 대기열을 정리하고, 활성 이벤트는 상위 N명 입장 허용한다.
   */
  @Scheduled(fixedDelayString = "${queue.scheduler.fixed-delay-ms}")
  public void admitMembers() {
    // queue:active:events Set에서 활성 이벤트 ID 목록 조회
    Set<String> activeEventIds = queueService.getActiveEventIds();

    if (activeEventIds.isEmpty()) {
      return;
    }

    log.info("action=QUEUE_SCHEDULER_START activeEventCount={}", activeEventIds.size());

    for (String eventIdStr : activeEventIds) {
      Long eventId = Long.parseLong(eventIdStr);

      // 이벤트 종료 여부 확인 - end_time 기준
      Event event = eventRepository.findById(eventId).orElse(null);
      if (event == null) {
        // 이벤트가 DB에 없으면 대기열 정리
        queueService.cleanUpEndedQueue(eventId);
        continue;
      }

      if (event.isEnded()) {
        // 종료된 이벤트 대기열 정리
        queueService.cleanUpEndedQueue(eventId);
        log.info("action=QUEUE_EXPIRED eventId={}", eventId);
        continue;
      }

      // 활성 이벤트 - 상위 N명 입장 허용
      queueService.admitTopMembers(eventId);
    }
  }
}