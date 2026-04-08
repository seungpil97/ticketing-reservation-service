package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
import com.pil97.ticketing.payment.api.dto.request.CreatePaymentRequest;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.domain.Payment;
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
   * - 동일 idempotency key로 재요청 시 Redis에서 기존 결과 반환 (DB 처리 없음)
   * - 예약이 존재하는지 검증한다
   * - 예약이 PENDING 상태인지 검증한다
   * - Payment를 PENDING 상태로 저장한다
   * - forceFailure가 true이면 결제 실패 처리한다 (Redis 저장 안 함 - 재시도 허용)
   * - 결제 성공 시: Payment SUCCESS, 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED, Redis 저장
   * - 결제 실패 시: Payment FAIL, 예약 FAILED, HOLD EXPIRED, 좌석 AVAILABLE 복구
   * - 실운영 시 PG 콜백 기반 비동기 처리로 전환 필요
   *
   * @param idempotencyKey 클라이언트가 전달한 idempotency key
   * @param request        결제 요청 DTO
   * @return 결제 처리 결과 응답
   */
  @Transactional
  public PaymentResponse pay(String idempotencyKey, CreatePaymentRequest request) {
    // 1) idempotency key 누락 검증
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING);
    }

    // 2) 동일 key로 재요청 시 Redis에서 기존 결과 반환 (DB 처리 없음)
    return idempotencyRedisRepository.find(idempotencyKey)
      .orElseGet(() -> processPayment(idempotencyKey, request));
  }

  /**
   * 환불 처리
   * - paymentId로 결제를 조회한다
   * - SUCCESS 상태인 경우만 환불 가능 - 그 외 상태는 예외 발생
   * - Payment REFUNDED, 예약 CANCELLED, HOLD EXPIRED, 좌석 AVAILABLE 순서로 전환
   *
   * @param paymentId 환불 대상 결제 ID
   * @return 환불 처리 결과 응답
   */
  @Transactional
  public PaymentResponse refund(Long paymentId) {
    // 1) 결제 존재 여부 확인
    Payment payment = paymentRepository.findById(paymentId)
      .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

    // 2) SUCCESS 상태인 경우만 환불 가능
    if (payment.getStatus() != com.pil97.ticketing.payment.domain.PaymentStatus.SUCCESS) {
      throw new BusinessException(PaymentErrorCode.REFUND_NOT_ALLOWED);
    }

    // 3) Payment 상태 REFUNDED 전환
    payment.refund();

    // 4) 예약 상태 CANCELLED 전환
    Reservation reservation = payment.getReservation();
    reservation.cancel();

    // 5) HOLD 상태 EXPIRED 전환 - 기존 만료 스케줄러와 동일한 상태값 사용
    reservation.getHold().expire();

    // 6) 좌석 상태 AVAILABLE 전환
    reservation.getHold().getShowtimeSeat().markAvailable();

    return PaymentResponse.of(payment);
  }

  /**
   * 실제 결제 처리 - 최초 요청에서만 실행
   * idempotency key 중복 확인 후 신규 요청일 때만 호출된다
   */
  private PaymentResponse processPayment(String idempotencyKey, CreatePaymentRequest request) {
    // 1) 예약 존재 여부 확인
    Reservation reservation = reservationRepository.findById(request.getReservationId())
      .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));

    // 2) 결제 가능한 예약인지 검증 - PENDING 상태만 허용
    validatePayable(reservation);

    // 3) Payment PENDING 상태로 저장
    Payment payment = Payment.create(reservation, request.getAmount());
    Payment savedPayment = paymentRepository.save(payment);

    // 4) forceFailure가 true이면 강제 실패 처리 - Redis 저장 안 함 (재시도 허용)
    if (request.isForceFailure()) {
      savedPayment.fail();
      reservation.fail();
      // 결제 실패 시 HOLD EXPIRED, 좌석 AVAILABLE 복구
      reservation.getHold().expire();
      reservation.getHold().getShowtimeSeat().markAvailable();
      return PaymentResponse.of(savedPayment);
    }

    // 5) 결제 성공 처리
    savedPayment.success();

    // 6) 예약 상태 CONFIRMED 전환
    reservation.confirm();

    // 7) 좌석 상태 RESERVED 전환
    reservation.getHold().getShowtimeSeat().markReserved();

    // 8) HOLD 상태 CONFIRMED 전환
    reservation.getHold().confirm();

    // 9) 결제 성공 결과 Redis에 저장 (TTL 24시간)
    PaymentResponse response = PaymentResponse.of(savedPayment);
    idempotencyRedisRepository.save(idempotencyKey, response);

    return response;
  }

  /**
   * 결제 가능한 예약인지 검증
   * - PENDING 상태의 예약만 결제 가능
   * - 이미 처리된 예약(CONFIRMED, FAILED, CANCELLED)은 결제 불가
   */
  private void validatePayable(Reservation reservation) {
    if (reservation.getStatus() != ReservationStatus.PENDING) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED);
    }
  }
}