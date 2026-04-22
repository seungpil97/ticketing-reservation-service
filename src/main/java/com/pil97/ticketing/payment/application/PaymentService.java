package com.pil97.ticketing.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.infra.idempotency.IdempotencyFingerprintUtil;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
import com.pil97.ticketing.infra.idempotency.IdempotencyResult;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.request.CreatePaymentRequest;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.domain.Payment;
import com.pil97.ticketing.payment.domain.PaymentStatus;
import com.pil97.ticketing.payment.domain.repository.PaymentRepository;
import com.pil97.ticketing.payment.error.PaymentErrorCode;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

  // 결제 멱등성 key prefix
  private static final String IDEMPOTENCY_PREFIX = "idempotency:payment";
  // 결제 멱등성 결과 보관 TTL
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

  private final PaymentRepository paymentRepository;
  private final ReservationRepository reservationRepository;
  private final IdempotencyRedisRepository idempotencyRedisRepository;
  private final ObjectMapper objectMapper;

  /**
   * 결제 처리
   * - idempotency key 누락 시 예외 발생
   * - 동일 key + 동일 본문 재요청 시 Redis 캐시 반환 (DB 처리 없음)
   * - 동일 key + 다른 본문 재요청 시 409 반환
   * - 동시 신규 요청 시 SETNX lock으로 1건만 처리
   */
  @Transactional
  public IdempotencyResult<PaymentResponse> pay(String idempotencyKey, CreatePaymentRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING);
    }

    String fingerprint = computeFingerprint(request);

    return idempotencyRedisRepository
      .find(IDEMPOTENCY_PREFIX, idempotencyKey, fingerprint, PaymentResponse.class)
      .map(IdempotencyResult::ofReplayed)
      .orElseGet(() -> IdempotencyResult.ofNew(
        processPayment(idempotencyKey, fingerprint, request)
      ));
  }

  /**
   * 환불 처리
   * - paymentId로 결제를 조회한다
   * - 소유권 검증을 상태 검증보다 먼저 수행한다
   * (상태 검증 먼저 시 타인의 결제 상태 정보가 노출될 수 있음)
   * - SUCCESS 상태인 경우만 환불 가능
   * - Payment REFUNDED, 예약 CANCELLED, HOLD REFUNDED, 좌석 AVAILABLE 전환
   */
  @Transactional
  public PaymentResponse refund(Long paymentId, Member loginMember) {
    Payment payment = paymentRepository.findById(paymentId)
      .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

    // 소유권 검증 - 상태 검증보다 먼저 수행
    validateOwnership(payment, loginMember);

    if (payment.getStatus() != PaymentStatus.SUCCESS) {
      throw new BusinessException(PaymentErrorCode.REFUND_NOT_ALLOWED);
    }

    payment.refund();

    Reservation reservation = payment.getReservation();
    reservation.cancelByRefund();
    reservation.getHold().refund();
    reservation.getHold().getShowtimeSeat().markAvailable();

    return PaymentResponse.of(payment);
  }

  /**
   * 결제 소유권 검증
   * - Payment -> Reservation -> Member 체인으로 예약 소유자 ID 확인
   * - 로그인 사용자와 예약 소유자가 다르면 403 예외 발생
   */
  private void validateOwnership(Payment payment, Member loginMember) {
    Long ownerId = payment.getReservation().getMember().getId();
    if (!ownerId.equals(loginMember.getId())) {
      throw new BusinessException(PaymentErrorCode.REFUND_FORBIDDEN);
    }
  }

  /**
   * 실제 결제 처리 - 최초 요청에서만 실행
   * - 비관적 락으로 예약을 조회하여 동시 중복 결제를 DB 레벨에서 차단
   * - 처리 성공 시 트랜잭션 커밋 후 Redis에 결과 저장 (커밋 전 저장 시 DB/Redis 불일치 위험)
   * - 처리 실패 또는 예외 발생 시 lock 해제하여 재시도 허용
   * - forceFailure 경로: 결제 실패 응답 반환 후 캐시 저장 안 함 - 동일 key 재시도 시 재처리
   */
  private PaymentResponse processPayment(String idempotencyKey, String fingerprint,
                                         CreatePaymentRequest request) {
    boolean success = false;
    try {
      Reservation reservation = reservationRepository.findByIdWithLock(request.getReservationId())
        .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));

      validatePayable(reservation);

      Payment payment = Payment.create(reservation, request.getAmount());
      Payment savedPayment = paymentRepository.save(payment);

      if (request.isForceFailure()) {
        savedPayment.fail();
        reservation.fail();
        reservation.getHold().expire();
        reservation.getHold().getShowtimeSeat().markAvailable();
        // 결제 실패 - lock 해제하여 재시도 허용 (캐시 저장 안 함)
        return PaymentResponse.of(savedPayment);
      }

      savedPayment.success();
      reservation.confirm();
      reservation.getHold().getShowtimeSeat().markReserved();
      reservation.getHold().confirm();

      PaymentResponse response = PaymentResponse.of(savedPayment);

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
  private void registerAfterCommit(String idempotencyKey, String fingerprint,
                                   PaymentResponse response) {
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
   * 결제 가능한 예약인지 검증
   * - PENDING 상태의 예약만 결제 가능
   * - 비관적 락 획득 후 재검증하여 선행 요청이 이미 처리한 경우 차단
   */
  private void validatePayable(Reservation reservation) {
    if (reservation.getStatus() != ReservationStatus.PENDING) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED);
    }
  }

  /**
   * 요청 본문의 SHA-256 fingerprint 계산
   * - 직렬화 실패 시 빈 문자열 hash로 대체 (처리는 계속 진행)
   */
  private String computeFingerprint(CreatePaymentRequest request) {
    try {
      return IdempotencyFingerprintUtil.hash(objectMapper.writeValueAsString(request));
    } catch (JsonProcessingException e) {
      log.warn("fingerprint 계산 실패 - 빈 문자열 hash로 대체");
      return IdempotencyFingerprintUtil.hash("");
    }
  }
}