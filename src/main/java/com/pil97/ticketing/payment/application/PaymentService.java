package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.exception.BusinessException;
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

  /**
   * 결제 처리
   * - 예약이 존재하는지 검증한다
   * - 예약이 PENDING 상태인지 검증한다
   * - Payment를 PENDING 상태로 저장한다
   * - forceFailure가 true이면 결제 실패 처리한다
   * - 결제 성공 시: Payment SUCCESS, 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED
   * - 결제 실패 시: Payment FAIL, 예약 FAILED
   * - 좌석/HOLD 복구는 TASK-036-1에서 처리 예정
   * - 실운영 시 PG 콜백 기반 비동기 처리로 전환 필요
   *
   * @param request 결제 요청 DTO
   * @return 결제 처리 결과 응답
   */
  @Transactional
  public PaymentResponse pay(CreatePaymentRequest request) {
    // 1) 예약 존재 여부 확인
    Reservation reservation = reservationRepository.findById(request.getReservationId())
      .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));

    // 2) 결제 가능한 예약인지 검증 - PENDING 상태만 허용
    validatePayable(reservation);

    // 3) Payment PENDING 상태로 저장
    Payment payment = Payment.create(reservation, request.getAmount());
    Payment savedPayment = paymentRepository.save(payment);

    // 4) forceFailure가 true이면 강제 실패 처리
    if (request.isForceFailure()) {
      savedPayment.fail();
      reservation.fail();
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

    return PaymentResponse.of(savedPayment);
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