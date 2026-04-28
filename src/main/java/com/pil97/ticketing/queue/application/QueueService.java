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
   * 대기열 등록 및 재진입
   * <p>
   * 최초 등록: ZADD NX(score=0)로 등록 선점 → 성공 시에만 INCR로 score 발급 후 교체
   * 재진입: INCR로 새 score 발급 후 ZADD로 맨 뒤 재등록
   * 존재하지 않는 eventId 요청 시 QueueErrorCode.EVENT_NOT_FOUND 예외 발생
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

    boolean isReEnter = queueRepository.hasAdmittedHistory(eventId, memberId);

    if (isReEnter) {
      // 재진입은 기존 대기열 위치를 초기화하고 맨 뒤로 보내야 하므로 새 score를 발급한다.
      double score = queueRepository.nextScore(eventId);
      queueRepository.addOrReplace(eventId, memberId, score);
      log.info("memberId={} action=QUEUE_REENTERED eventId={}", memberId, eventId);
    } else {
      /*
       * 최초 등록은 이미 대기열에 있는 사용자의 기존 순번을 유지해야 한다.
       * 따라서 먼저 ZADD NX로 등록 성공 여부를 확인하고,
       * 실제로 새로 등록된 경우에만 INCR로 score를 발급한다.
       *
       * 주의:
       * 등록 성공 여부와 무관하게 nextScore()를 먼저 호출하면
       * 중복 등록 시도만으로 queue:seq:{eventId}가 증가해 score가 건너뛸 수 있다.
       */
      boolean added = queueRepository.addIfAbsent(eventId, memberId, 0);

      if (added) {
        double score = queueRepository.nextScore(eventId);
        queueRepository.addOrReplace(eventId, memberId, score);
      }
    }

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
   * 케이스 3: 대기열 미등록 + 입장 이력 없음 → reEnterType=NONE (최초 미진입)
   * 케이스 4: 대기열 미등록 + 입장 이력 있음 → reEnterType=EXPIRED (토큰 만료 재진입)
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

    if (rank == null) {
      // 입장 이력 여부로 최초 미진입 vs 토큰 만료 구분
      boolean hasHistory = queueRepository.hasAdmittedHistory(eventId, memberId);
      if (hasHistory) {
        // 케이스 4: 토큰 만료 후 재진입 필요
        log.info("memberId={} action=QUEUE_REENTER_REQUIRED eventId={} type=EXPIRED", memberId, eventId);
        return QueueStatusResponse.ofReEnterRequired(QueueStatusResponse.ReEnterType.EXPIRED);
      } else {
        // 케이스 3: 최초 미진입
        log.info("memberId={} action=QUEUE_REENTER_REQUIRED eventId={} type=NONE", memberId, eventId);
        return QueueStatusResponse.ofReEnterRequired(QueueStatusResponse.ReEnterType.NONE);
      }
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
   * 입장 토큰 발급 시 입장 허용 이력을 함께 저장한다.
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

      // 입장 허용 이력 저장 - getStatus()에서 토큰 만료 케이스 구분에 사용
      queueRepository.saveAdmittedHistory(eventId, memberId);

      // 대기열에서 제거
      queueRepository.remove(eventId, memberId);

      log.info("memberId={} action=QUEUE_ADMITTED eventId={}", memberId, eventId);
    }
  }

  /**
   * 종료된 이벤트 대기열 정리
   * QueueScheduler에서 호출한다.
   * queue:event:{eventId} 삭제 + queue:active:events 제거 + 입장 허용 이력 삭제 + 순번 카운터 삭제
   *
   * @param eventId 이벤트 ID
   */
  public void cleanUpEndedQueue(Long eventId) {
    queueRepository.deleteQueue(eventId);
    queueRepository.removeActiveEvent(eventId);
    queueRepository.deleteAdmittedHistory(eventId);
    queueRepository.deleteSeq(eventId);
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