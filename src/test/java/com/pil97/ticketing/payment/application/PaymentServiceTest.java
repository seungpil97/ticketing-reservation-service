package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
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
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private ReservationRepository reservationRepository;

  @InjectMocks
  private PaymentService paymentService;

  @Test
  @DisplayName("pay: 결제 성공 시 Payment SUCCESS, 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED로 전환된다")
  void pay_success() {
    // given
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(false);

    // ShowtimeSeat, Hold, Reservation mock 구성
    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    when(reservation.getHold()).thenReturn(hold);

    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

    // Payment 저장 목 구성
    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(1L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.SUCCESS);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    // when
    PaymentResponse response = paymentService.pay(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo("SUCCESS");

    // 예약/좌석/HOLD 상태 전환 검증
    verify(reservation).confirm();
    verify(showtimeSeat).markReserved();
    verify(hold).confirm();
    verify(savedPayment).success();
  }

  @Test
  @DisplayName("pay: forceFailure=true이면 Payment FAIL, 예약 FAILED로 전환된다")
  void pay_forceFailure() {
    // given
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(true);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);

    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(2L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.FAIL);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    // when
    PaymentResponse response = paymentService.pay(request);

    // then
    assertThat(response.status()).isEqualTo("FAIL");
    assertThat(response.paidAt()).isNull();

    // 실패 시 예약/좌석/HOLD 상태 전환 없음을 검증
    verify(savedPayment).fail();
    verify(reservation).fail();
    verify(reservation, never()).confirm();
  }

  @Test
  @DisplayName("pay: 존재하지 않는 reservationId이면 BusinessException(NOT_FOUND)을 던진다")
  void pay_reservationNotFound_throwsBusinessException() {
    // given
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(999L);
    when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentService.pay(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ReservationErrorCode.NOT_FOUND);
      });

    verify(reservationRepository).findById(999L);
    verifyNoInteractions(paymentRepository);
  }

  @Test
  @DisplayName("pay: PENDING이 아닌 예약이면 BusinessException(PAYMENT_ALREADY_PROCESSED)을 던진다")
  void pay_notPendingReservation_throwsBusinessException() {
    // given
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);

    Reservation reservation = mock(Reservation.class);
    // 이미 결제 완료된 예약
    when(reservation.getStatus()).thenReturn(ReservationStatus.CONFIRMED);
    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

    // when & then
    assertThatThrownBy(() -> paymentService.pay(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED);
      });

    verifyNoInteractions(paymentRepository);
  }
}