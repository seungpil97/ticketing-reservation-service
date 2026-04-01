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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 대기열 비즈니스 로직 및 입장 허용 스케줄러
 * <p>
 * 흐름:
 * 1. 유저가 POST /queue/enter 호출 → 대기열 등록 후 순번 반환
 * 2. 유저가 GET /queue/status 호출 → 현재 순번 또는 입장 가능 여부 반환
 * 3. 스케줄러가 주기적으로 상위 N명에게 입장 토큰 발급 후 대기열에서 제거
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

  private final QueueRepository queueRepository;
  private final EventRepository eventRepository;

  /**
   * 대기열 등록
   * <p>
   * 이미 등록된 유저는 ZADD NX 옵션으로 기존 순번 그대로 반환한다.
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

    // ZADD NX - 이미 등록된 유저는 score 갱신 없이 무시
    double score = System.currentTimeMillis();
    queueRepository.addIfAbsent(eventId, memberId, score);

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
   * 입장 토큰이 존재하면 admitted=true 반환
   * 대기열에 없으면 QueueErrorCode.NOT_IN_QUEUE 예외 발생
   *
   * @param eventId  이벤트 ID
   * @param memberId JWT에서 추출한 회원 ID
   * @return 현재 순번 + 예상 대기 시간 또는 입장 가능 여부
   */
  public QueueStatusResponse getStatus(Long eventId, Long memberId) {

    // 입장 토큰이 있으면 이미 입장 허용된 유저
    if (queueRepository.hasAdmissionToken(memberId)) {
      return QueueStatusResponse.ofAdmitted();
    }

    // 대기열 순번 조회
    Long rank = queueRepository.getRank(eventId, memberId);
    if (rank == null) {
      throw new BusinessException(QueueErrorCode.NOT_IN_QUEUE);
    }

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
   * 입장 허용 스케줄러
   * <p>
   * 실행 주기: application.yml queue.scheduler.fixed-delay-ms
   * 상위 batchSize명을 대기열에서 꺼내 입장 토큰을 발급하고 대기열에서 제거한다.
   * <p>
   * 토큰 값: UUID로 생성 (현재 토큰 값 자체는 검증에 사용하지 않고 key 존재 여부만 확인)
   */
  @Scheduled(fixedDelayString = "${queue.scheduler.fixed-delay-ms}")
  public void admitMembers() {

    // 전체 이벤트 ID 목록을 순회하며 각 대기열에서 상위 N명 입장 허용
    // 현재는 단순화를 위해 eventId를 외부에서 주입하지 않고 대기열 키를 직접 관리
    // TASK-030에서 이벤트별 대기열 관리 정책 확장 예정
    log.info("action=QUEUE_SCHEDULER_START batchSize={}", batchSize);
  }

  /**
   * 특정 이벤트 대기열에서 상위 N명 입장 허용
   * admitMembers 스케줄러에서 이벤트별로 호출한다.
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
   * 예상 대기 시간 계산
   * 공식: rank * (스케줄러 주기(초) / 배치 사이즈 N)
   *
   * @param rank 1-based 순번
   * @return 예상 대기 시간(초)
   */
  private long calculateEstimatedWait(long rank) {
    // fixedDelayMs를 초로 변환 후 계산
    // 설정값 직접 참조 대신 고정 주기(10초) 기준으로 계산
    // TASK-030에서 설정값 기반으로 개선 예정
    long schedulerIntervalSeconds = 10L;
    return rank * (schedulerIntervalSeconds / batchSize);
  }
}