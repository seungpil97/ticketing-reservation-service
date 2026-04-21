package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final ReservationRepository reservationRepository;
  private final IdempotencyRedisRepository idempotencyRedisRepository;

  /**
   * 결제 처리
   * - idempotency key 누락 시 예외 발생
   * - 동일 idempotency key로 재요청 시 Redis 캐시 반환 (DB 처리 없음)
   * - 신규 요청은 비관적 락으로 예약을 조회하여 동시 중복 결제 차단
   */
  @Transactional
  public PaymentResponse pay(String idempotencyKey, CreatePaymentRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING);
    }

    return idempotencyRedisRepository.find(idempotencyKey)
      .orElseGet(() -> processPayment(idempotencyKey, request));
  }

  /**
   * 환불 처리
   * - paymentId로 결제를 조회한다
   * - 소유권 검증을 상태 검증보다 먼저 수행한다
   * (상태 검증 먼저 시 타인의 결제 상태 정보가 노출될 수 있음)
   * - SUCCESS 상태인 경우만 환불 가능
   * - Payment REFUNDED, 예약 CANCELLED, HOLD REFUNDED, 좌석 AVAILABLE 순서로 전환
   */
  @Transactional
  public PaymentResponse refund(Long paymentId, Member loginMember) {
    // 1) 결제 존재 여부 확인
    Payment payment = paymentRepository.findById(paymentId)
      .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

    // 2) 소유권 검증 - 상태 검증보다 먼저 수행
    validateOwnership(payment, loginMember);

    // 3) SUCCESS 상태인 경우만 환불 가능
    if (payment.getStatus() != PaymentStatus.SUCCESS) {
      throw new BusinessException(PaymentErrorCode.REFUND_NOT_ALLOWED);
    }

    // 4) Payment 상태 REFUNDED 전환
    payment.refund();

    // 5) 예약 상태 CANCELLED 전환 - 환불 경로 전용 메서드 사용
    Reservation reservation = payment.getReservation();
    reservation.cancelByRefund();

    // 6) HOLD 상태 REFUNDED 전환 - 시간 만료(EXPIRED)와 구분
    reservation.getHold().refund();

    // 7) 좌석 상태 AVAILABLE 전환
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
   * - 락 획득 후 PENDING 상태 재검증으로 선행 요청이 이미 처리된 경우 예외 반환
   */
  private PaymentResponse processPayment(String idempotencyKey, CreatePaymentRequest request) {
    // 비관적 락으로 조회 - 동시 요청 중 하나만 진입, 나머지는 락 해제 후 재검증에서 차단
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
      return PaymentResponse.of(savedPayment);
    }

    savedPayment.success();
    reservation.confirm();
    reservation.getHold().getShowtimeSeat().markReserved();
    reservation.getHold().confirm();

    PaymentResponse response = PaymentResponse.of(savedPayment);
    idempotencyRedisRepository.save(idempotencyKey, response);

    return response;
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
}