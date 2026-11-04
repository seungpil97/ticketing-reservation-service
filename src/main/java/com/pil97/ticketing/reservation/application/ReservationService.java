package com.pil97.ticketing.reservation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.LockedSeatHolds;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.infra.idempotency.IdempotencyFingerprintUtil;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
import com.pil97.ticketing.infra.idempotency.IdempotencyResult;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

  // 예약 멱등성 key prefix
  private static final String IDEMPOTENCY_PREFIX = "idempotency:reservation";
  // 예약 멱등성 결과 보관 TTL
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

  private final HoldRepository holdRepository;
  private final ReservationRepository reservationRepository;
  private final ShowtimeSeatRepository showtimeSeatRepository;
  private final IdempotencyRedisRepository idempotencyRedisRepository;
  private final ObjectMapper objectMapper;

  /**
   * 예약 생성 처리 (결제 대기 상태)
   * - idempotency key 누락 시 예외 발생
   * - 동일 key + 동일 본문 재요청 시 Redis 캐시 반환 (DB 처리 없음)
   * - 동일 key + 다른 본문 재요청 시 409 반환
   * - 동시 신규 요청 시 SETNX lock으로 1건만 처리
   * - 예약 성공 후 좌석/HOLD 상태 변경은 결제 완료(PaymentService) 시점에 처리
   *
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   * @param holdId         예약 대상 HOLD ID
   * @return 예약 생성 결과 응답
   */
  @Transactional
  public IdempotencyResult<ReservationResponse> reserve(
    String idempotencyKey,
    Long holdId
  ) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING);
    }

    String fingerprint = computeFingerprint(holdId);

    return idempotencyRedisRepository
      .find(IDEMPOTENCY_PREFIX, idempotencyKey, fingerprint, ReservationResponse.class)
      .map(IdempotencyResult::ofReplayed)
      .orElseGet(() -> IdempotencyResult.ofNew(
        processReserve(idempotencyKey, fingerprint, holdId)
      ));
  }

  /**
   * 예약 취소 처리 (PENDING 전용)
   * - CONFIRMED: 직접 취소 불가 - 환불 API 경로 사용
   * - FAILED, CANCELLED: 취소 불가
   * - PENDING: 결제 전 취소 (TASK-062)
   *   - 잠금 순서: 예약 → 좌석(예약 FK의 showtimeId·seatId unique key) → 대상 및 같은 좌석의 live HOLD (id 오름차순)
   *   - 예약 상태 거절은 좌석·HOLD 잠금 전에 판정한다
   *   - 좌석 해제 권한은 HOLD 종료 전에 계산한다 (대상 ACTIVE + 좌석 HELD + 다른 live HOLD 없음)
   *   - 대상 HOLD가 ACTIVE면 CANCELLED, 이미 종료된 HOLD는 바꾸지 않는다
   *   - 해제 권한이 있을 때만 좌석 AVAILABLE (재선점·결제 완료 좌석과 타인 HOLD 보존)
   *
   * @param reservationId 취소 대상 예약 ID
   */
  @Transactional
  public void cancel(Long reservationId) {
    Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
      .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));

    validateCancellable(reservation);

    // 좌석 ID를 찾기 위해 HOLD를 일관 읽기로 먼저 조회하지 않는다 (예약 FK의 LAZY proxy ID 사용)
    ShowtimeSeat showtimeSeat = showtimeSeatRepository
      .findByShowtimeIdAndSeatIdWithLock(reservation.getShowtime().getId(), reservation.getSeat().getId())
      .orElseThrow(() -> new BusinessException(ShowtimeSeatErrorCode.NOT_FOUND));
    LockedSeatHolds holds = LockedSeatHolds.require(
      holdRepository.findTargetAndLiveHoldsForUpdate(
        showtimeSeat.getId(), reservation.getHold().getId(), LockedSeatHolds.LIVE_STATUSES),
      reservation.getHold().getId());

    // HOLD 종료 전 해제 권한 계산
    boolean releaseSeat = holds.ownsSeat(showtimeSeat);

    Hold hold = holds.target();
    if (hold.getStatus() == HoldStatus.ACTIVE) {
      hold.cancel();
    }
    if (releaseSeat) {
      showtimeSeat.markAvailable();
    }
    reservation.cancel();
  }

  /**
   * 실제 예약 처리 - 최초 요청에서만 실행
   * - 처리 성공 시 트랜잭션 커밋 후 Redis에 결과 저장 (커밋 전 저장 시 DB/Redis 불일치 위험)
   * - 처리 실패 또는 예외 발생 시 lock 해제하여 재시도 허용
   */
  private ReservationResponse processReserve(
    String idempotencyKey,
    String fingerprint,
    Long holdId
  ) {
    boolean success = false;
    try {
      Hold hold = holdRepository.findByIdWithLock(holdId)
        .orElseThrow(() -> new BusinessException(HoldErrorCode.NOT_FOUND));

      validateReservableHold(hold);

      ShowtimeSeat showtimeSeat = hold.getShowtimeSeat();

      Reservation reservation = Reservation.create(
        hold,
        showtimeSeat.getShowtime(),
        showtimeSeat.getSeat(),
        hold.getMember()
      );

      Reservation savedReservation = reservationRepository.save(reservation);

      ReservationResponse response = new ReservationResponse(
        savedReservation.getId(),
        hold.getId(),
        showtimeSeat.getShowtime().getId(),
        showtimeSeat.getSeat().getId(),
        hold.getMember().getId(),
        showtimeSeat.getStatus().name(),
        hold.getStatus().name()
      );

      // 트랜잭션 커밋 후 Redis 저장
      // - 커밋 전 저장 시 롤백되면 DB에는 실패, Redis에는 성공 응답이 남는 불일치 발생
      // - afterCommit 콜백에서 실행하여 DB 커밋 성공이 보장된 후 저장
      registerAfterCommit(idempotencyKey, fingerprint, response);

      success = true;
      return response;

    } finally {
      // 성공 시에는 afterCommit에서 lock 해제, 실패 시에는 즉시 해제
      if (!success) {
        idempotencyRedisRepository.releaseLock(IDEMPOTENCY_PREFIX, idempotencyKey);
      }
    }
  }

  /**
   * 트랜잭션 커밋 후 Redis 결과 저장 및 lock 해제 등록
   * - afterCommit: 커밋 성공 시에만 실행
   * - afterCompletion: 롤백 포함 모든 완료 시 실행 - lock 해제 보장
   */
  private void registerAfterCommit(
    String idempotencyKey,
    String fingerprint,
    ReservationResponse response
  ) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("Transaction synchronization is not active");
    }

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

      @Override
      public void afterCommit() {
        // DB 커밋 성공 후 Redis에 결과 저장 및 lock 해제
        idempotencyRedisRepository.save(
          IDEMPOTENCY_PREFIX, idempotencyKey, fingerprint, response, IDEMPOTENCY_TTL);
      }

      @Override
      public void afterCompletion(int status) {
        // 롤백 등 커밋 외 완료 시 lock만 해제 (afterCommit이 실행된 경우 save()에서 이미 해제됨)
        if (status != STATUS_COMMITTED) {
          idempotencyRedisRepository.releaseLock(IDEMPOTENCY_PREFIX, idempotencyKey);
        }
      }
    });
  }

  /**
   * 예약 가능한 HOLD인지 검증
   */
  private void validateReservableHold(Hold hold) {
    validateActiveHold(hold);
    validateNotExpired(hold);
    validateHeldSeat(hold);
  }

  /**
   * HOLD 상태가 ACTIVE인지 검증
   */
  private void validateActiveHold(Hold hold) {
    if (hold.getStatus() != HoldStatus.ACTIVE) {
      throw new BusinessException(HoldErrorCode.NOT_ACTIVE);
    }
  }

  /**
   * HOLD가 만료되지 않았는지 검증
   */
  private void validateNotExpired(Hold hold) {
    if (!hold.getExpiresAt().isAfter(LocalDateTime.now())) {
      throw new BusinessException(HoldErrorCode.EXPIRED);
    }
  }

  /**
   * 연결된 회차별 좌석 상태가 HELD인지 검증
   */
  private void validateHeldSeat(Hold hold) {
    if (hold.getShowtimeSeat().getStatus() != ShowtimeSeatStatus.HELD) {
      throw new BusinessException(ShowtimeSeatErrorCode.NOT_HELD);
    }
  }

  /**
   * 취소 가능한 예약인지 검증
   * - PENDING: 결제 전 취소 허용
   * - CONFIRMED: 직접 취소 불가, 환불 API 경로로만 취소 가능
   * - FAILED, CANCELLED: 취소 불가
   */
  private void validateCancellable(Reservation reservation) {
    ReservationStatus status = reservation.getStatus();

    if (status == ReservationStatus.CONFIRMED) {
      throw new BusinessException(ReservationErrorCode.RESERVATION_CANCEL_REQUIRES_REFUND);
    }

    if (status != ReservationStatus.PENDING) {
      throw new BusinessException(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED);
    }
  }

  /**
   * holdId 기반 fingerprint 계산
   * - 예약 요청의 유일한 식별자는 holdId이므로 holdId를 직렬화하여 hash 계산
   */
  private String computeFingerprint(Long holdId) {
    try {
      return IdempotencyFingerprintUtil.hash(objectMapper.writeValueAsString(holdId));
    } catch (JsonProcessingException e) {
      log.warn("fingerprint 계산 실패 - 빈 문자열 hash로 대체");
      return IdempotencyFingerprintUtil.hash("");
    }
  }
}