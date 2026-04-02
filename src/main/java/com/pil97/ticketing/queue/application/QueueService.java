package com.pil97.ticketing.queue.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.queue.api.dto.response.QueueEnterResponse;
import com.pil97.ticketing.queue.api.dto.response.QueueStatusResponse;
import com.pil97.ticketing.queue.domain.repository.QueueRepository;
import com.pil97.ticketing.queue.error.QueueErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 대기열 비즈니스 로직 담당 서비스
 * <p>
 * 흐름:
 * 1. 유저가 POST /queue/enter 호출 → 대기열 등록 후 순번 반환
 * 2. 유저가 GET /queue/status 호출 → 현재 순번 또는 입장 가능 여부 반환
 * 3. QueueScheduler가 주기적으로 상위 N명에게 입장 토큰 발급 후 대기열에서 제거
 * 4. 유저가 HOLD API 호출 시 입장 토큰 유효성 검사
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

  /**
   * 스케줄러 1회 실행 시 입장 허용할 최대 인원 수
   * application.yml: queue.scheduler.batch-size
   */
  @Value("${queue.scheduler.batch-size}")
  private int batchSize;

  /**
   * 스케줄러 실행 주기 (ms)
   * application.yml: queue.scheduler.fixed-delay-ms
   * 예상 대기 시간 계산에 사용한다.
   */
  @Value("${queue.scheduler.fixed-delay-ms}")
  private long fixedDelayMs;

  private final QueueRepository queueRepository;
  private final EventRepository eventRepository;

  /**
   * 대기열 등록
   * <p>
   * 이미 등록된 유저는 ZADD NX 옵션으로 기존 순번 그대로 반환한다.
   * 존재하지 않는 eventId 요청 시 QueueErrorCode.EVENT_NOT_FOUND 예외 발생
   * 대기열 등록 시 queue:active:events Set에 eventId를 추가한다.
   *
   * @param eventId  이벤트 ID
   * @param memberId JWT에서 추출한 회원 ID
   * @return 순번(1 - based), 예상 대기 시간(초)
   */
  public QueueEnterResponse enter(Long eventId, Long memberId) {

    // 이벤트 존재 여부 확인
    if (!eventRepository.existsById(eventId)) {
      throw new BusinessException(QueueErrorCode.EVENT_NOT_FOUND);
    }

    // ZADD NX - 이미 등록된 유저는 score 갱신 없이 무시
    double score = System.currentTimeMillis();
    queueRepository.addIfAbsent(eventId, memberId, score);

    // 활성 대기열 이벤트 목록에 등록 - 스케줄러가 이 Set을 순회하며 처리
    queueRepository.addActiveEvent(eventId);

    // 현재 순번 조회 (0-based → 1-based로 변환)
    Long rank = queueRepository.getRank(eventId, memberId);
    long rankOneBased = (rank != null ? rank : 0L) + 1;

    long estimatedWaitSeconds = calculateEstimatedWait(rankOneBased);

    log.info("memberId={} action=QUEUE_ENTERED eventId={} rank={}", memberId, eventId, rankOneBased);

    return new QueueEnterResponse(rankOneBased, estimatedWaitSeconds);
  }

  /**
   * 대기 상태 조회
   * <p>
   * 케이스 1: 입장 토큰 존재 → admitted=true 반환
   * 케이스 2: 대기열에 존재 → 현재 순번 + 예상 대기 시간 반환
   * 케이스 3: 토큰 만료 + 대기열 미등록 → reEnterRequired=true 반환 (재등록 안내)
   *
   * @param eventId  이벤트 ID
   * @param memberId JWT에서 추출한 회원 ID
   * @return 현재 순번 + 예상 대기 시간 또는 입장 가능 여부 또는 재등록 안내
   */
  public QueueStatusResponse getStatus(Long eventId, Long memberId) {

    // 케이스 1: 입장 토큰이 있으면 이미 입장 허용된 유저
    if (queueRepository.hasAdmissionToken(memberId)) {
      return QueueStatusResponse.ofAdmitted();
    }

    // 대기열 순번 조회
    Long rank = queueRepository.getRank(eventId, memberId);

    // 케이스 3: 토큰 만료 + 대기열 미등록 → 재등록 안내
    if (rank == null) {
      log.info("memberId={} action=QUEUE_REENTER_REQUIRED eventId={}", memberId, eventId);
      return QueueStatusResponse.ofReEnterRequired();
    }

    // 케이스 2: 대기열에 존재 → 순번 반환
    long rankOneBased = rank + 1;
    long estimatedWaitSeconds = calculateEstimatedWait(rankOneBased);

    return QueueStatusResponse.ofWaiting(rankOneBased, estimatedWaitSeconds);
  }

  /**
   * 입장 토큰 유효성 검사
   * HOLD API 호출 시 진입점에서 사용한다.
   *
   * @param memberId JWT에서 추출한 회원 ID
   */
  public void validateAdmissionToken(Long memberId) {
    if (!queueRepository.hasAdmissionToken(memberId)) {
      throw new BusinessException(QueueErrorCode.ADMISSION_TOKEN_NOT_FOUND);
    }
  }

  /**
   * 특정 이벤트 대기열에서 상위 N명 입장 허용
   * QueueScheduler에서 이벤트별로 호출한다.
   *
   * @param eventId 이벤트 ID
   */
  public void admitTopMembers(Long eventId) {
    Set<String> topMembers = queueRepository.getTopMembers(eventId, batchSize);

    for (String memberIdStr : topMembers) {
      Long memberId = Long.parseLong(memberIdStr);
      String token = UUID.randomUUID().toString();

      // 입장 토큰 발급 (TTL 30분)
      queueRepository.saveAdmissionToken(memberId, token);

      // 대기열에서 제거
      queueRepository.remove(eventId, memberId);

      log.info("memberId={} action=QUEUE_ADMITTED eventId={}", memberId, eventId);
    }
  }

  /**
   * 종료된 이벤트 대기열 정리
   * QueueScheduler에서 호출한다.
   * queue:event:{eventId} 삭제 + queue:active:events에서 제거
   *
   * @param eventId 이벤트 ID
   */
  public void cleanUpEndedQueue(Long eventId) {
    queueRepository.deleteQueue(eventId);
    queueRepository.removeActiveEvent(eventId);
    log.info("action=QUEUE_CLEANED_UP eventId={}", eventId);
  }

  /**
   * 활성 대기열 이벤트 ID 목록 조회
   * QueueScheduler에서 처리 대상 이벤트 목록을 가져올 때 사용한다.
   *
   * @return 활성 이벤트 ID 문자열 Set
   */
  public Set<String> getActiveEventIds() {
    return queueRepository.getActiveEventIds();
  }

  /**
   * 예상 대기 시간 계산
   * 공식: rank * (스케줄러 주기(초) / 배치 사이즈)
   *
   * @param rank 1-based 순번
   * @return 예상 대기 시간(초)
   */
  private long calculateEstimatedWait(long rank) {
    long schedulerIntervalSeconds = fixedDelayMs / 1000;
    return rank * (schedulerIntervalSeconds / batchSize);
  }
}