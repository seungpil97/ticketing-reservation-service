package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private ReservationRepository reservationRepository;

  @Mock
  private IdempotencyRedisRepository idempotencyRedisRepository;

  @InjectMocks
  private PaymentService paymentService;

  // ===================== 기존 케이스 (idempotency key 파라미터 추가) =====================

  @Test
  @DisplayName("pay: 결제 성공 시 Payment SUCCESS, 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED로 전환된다")
  void pay_success() {
    // given
    String idempotencyKey = "test-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(false);

    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    when(reservation.getHold()).thenReturn(hold);

    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(1L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.SUCCESS);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    when(idempotencyRedisRepository.find(idempotencyKey)).thenReturn(Optional.empty());

    // when
    PaymentResponse response = paymentService.pay(idempotencyKey, request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo("SUCCESS");

    verify(reservation).confirm();
    verify(showtimeSeat).markReserved();
    verify(hold).confirm();
    verify(savedPayment).success();
    verify(idempotencyRedisRepository).save(eq(idempotencyKey), any(PaymentResponse.class));
  }

  @Test
  @DisplayName("pay: forceFailure=true이면 Payment FAIL, 예약 FAILED, HOLD EXPIRED, 좌석 AVAILABLE로 복구된다")
  void pay_forceFailure() {
    // given
    String idempotencyKey = "test-key-002";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(true);

    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    when(reservation.getHold()).thenReturn(hold);
    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(2L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.FAIL);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    when(idempotencyRedisRepository.find(idempotencyKey)).thenReturn(Optional.empty());

    // when
    PaymentResponse response = paymentService.pay(idempotencyKey, request);

    // then
    assertThat(response.status()).isEqualTo("FAIL");
    assertThat(response.paidAt()).isNull();

    verify(savedPayment).fail();
    verify(reservation).fail();
    verify(reservation, never()).confirm();
    verify(hold).expire();
    verify(showtimeSeat).markAvailable();
    verify(idempotencyRedisRepository, never()).save(any(), any());
  }

  @Test
  @DisplayName("pay: 존재하지 않는 reservationId이면 BusinessException(NOT_FOUND)을 던진다")
  void pay_reservationNotFound_throwsBusinessException() {
    // given
    String idempotencyKey = "test-key-003";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(999L);
    when(reservationRepository.findById(999L)).thenReturn(Optional.empty());
    when(idempotencyRedisRepository.find(idempotencyKey)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentService.pay(idempotencyKey, request))
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
    String idempotencyKey = "test-key-004";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.CONFIRMED);
    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
    when(idempotencyRedisRepository.find(idempotencyKey)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentService.pay(idempotencyKey, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED);
      });

    verifyNoInteractions(paymentRepository);
  }

  // ===================== idempotency 케이스 =====================

  @Test
  @DisplayName("pay: 동일 idempotency key 재요청 시 Redis에서 기존 결과를 반환하고 DB 처리를 하지 않는다")
  void pay_duplicateKey_returnsExistingResponse() {
    // given
    String idempotencyKey = "duplicate-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);

    PaymentResponse cachedResponse = mock(PaymentResponse.class);
    when(idempotencyRedisRepository.find(idempotencyKey)).thenReturn(Optional.of(cachedResponse));

    // when
    PaymentResponse response = paymentService.pay(idempotencyKey, request);

    // then
    assertThat(response).isEqualTo(cachedResponse);
    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
  }

  @Test
  @DisplayName("pay: idempotency key가 null이면 BusinessException(IDEMPOTENCY_KEY_MISSING)을 던진다")
  void pay_nullIdempotencyKey_throwsBusinessException() {
    // given
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);

    // when & then
    assertThatThrownBy(() -> paymentService.pay(null, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING);
      });

    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
    verifyNoInteractions(idempotencyRedisRepository);
  }

  @Test
  @DisplayName("pay: idempotency key가 blank이면 BusinessException(IDEMPOTENCY_KEY_MISSING)을 던진다")
  void pay_blankIdempotencyKey_throwsBusinessException() {
    // given
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);

    // when & then
    assertThatThrownBy(() -> paymentService.pay("   ", request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING);
      });

    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
    verifyNoInteractions(idempotencyRedisRepository);
  }

  @Test
  @DisplayName("pay: forceFailure=true 결제 실패 후 동일 key로 재요청 시 재처리가 허용된다")
  void pay_forceFailure_thenRetry_allowsReprocessing() {
    // given
    String idempotencyKey = "retry-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(false);

    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    when(reservation.getHold()).thenReturn(hold);
    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(1L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.SUCCESS);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    when(idempotencyRedisRepository.find(idempotencyKey)).thenReturn(Optional.empty());

    // when
    PaymentResponse response = paymentService.pay(idempotencyKey, request);

    // then
    assertThat(response.status()).isEqualTo("SUCCESS");
    verify(reservationRepository).findById(1L);
    verify(paymentRepository).save(any(Payment.class));
  }

  // ===================== 환불 케이스 =====================

  @Test
  @DisplayName("refund: 환불 성공 시 Payment REFUNDED, 예약 CANCELLED, HOLD EXPIRED, 좌석 AVAILABLE로 전환된다")
  void refund_success() {
    // given
    // 로그인 사용자와 예약 소유자가 동일한 경우
    Member loginMember = mock(Member.class);
    when(loginMember.getId()).thenReturn(1L);

    Member owner = mock(Member.class);
    when(owner.getId()).thenReturn(1L);

    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getHold()).thenReturn(hold);
    when(reservation.getMember()).thenReturn(owner);

    Payment payment = mock(Payment.class);
    when(payment.getId()).thenReturn(1L);
    when(payment.getStatus()).thenReturn(PaymentStatus.SUCCESS);
    when(payment.getReservation()).thenReturn(reservation);
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    // when
    paymentService.refund(1L, loginMember);

    // then
    verify(payment).refund();
    verify(reservation).cancel();
    verify(hold).expire();
    verify(showtimeSeat).markAvailable();
  }

  @Test
  @DisplayName("refund: 타인의 결제 환불 시도 시 BusinessException(REFUND_FORBIDDEN)을 던진다")
  void refund_notOwner_throwsBusinessException() {
    // given
    // 로그인 사용자(id=2)와 예약 소유자(id=1)가 다른 경우
    Member loginMember = mock(Member.class);
    when(loginMember.getId()).thenReturn(2L);

    Member owner = mock(Member.class);
    when(owner.getId()).thenReturn(1L);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getMember()).thenReturn(owner);

    Payment payment = mock(Payment.class);
    when(payment.getReservation()).thenReturn(reservation);
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    // when & then
    assertThatThrownBy(() -> paymentService.refund(1L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.REFUND_FORBIDDEN);
      });

    // 소유권 검증 실패 시 환불 처리 없음
    verify(payment, never()).refund();
  }

  @Test
  @DisplayName("refund: SUCCESS 상태가 아닌 결제 환불 시도 시 BusinessException(REFUND_NOT_ALLOWED)을 던진다")
  void refund_notSuccess_throwsBusinessException() {
    // given
    // 소유권 검증을 통과시키기 위해 로그인 사용자와 예약 소유자를 동일하게 설정
    Member loginMember = mock(Member.class);
    when(loginMember.getId()).thenReturn(1L);

    Member owner = mock(Member.class);
    when(owner.getId()).thenReturn(1L);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getMember()).thenReturn(owner);

    Payment payment = mock(Payment.class);
    when(payment.getStatus()).thenReturn(PaymentStatus.FAIL);
    when(payment.getReservation()).thenReturn(reservation);
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    // when & then
    assertThatThrownBy(() -> paymentService.refund(1L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.REFUND_NOT_ALLOWED);
      });

    verify(payment, never()).refund();
  }

  @Test
  @DisplayName("refund: 이미 환불된 결제 재환불 시도 시 BusinessException(REFUND_NOT_ALLOWED)을 던진다")
  void refund_alreadyRefunded_throwsBusinessException() {
    // given
    // 소유권 검증을 통과시키기 위해 로그인 사용자와 예약 소유자를 동일하게 설정
    Member loginMember = mock(Member.class);
    when(loginMember.getId()).thenReturn(1L);

    Member owner = mock(Member.class);
    when(owner.getId()).thenReturn(1L);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getMember()).thenReturn(owner);

    Payment payment = mock(Payment.class);
    when(payment.getStatus()).thenReturn(PaymentStatus.REFUNDED);
    when(payment.getReservation()).thenReturn(reservation);
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    // when & then
    assertThatThrownBy(() -> paymentService.refund(1L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.REFUND_NOT_ALLOWED);
      });

    verify(payment, never()).refund();
  }

  @Test
  @DisplayName("refund: 존재하지 않는 paymentId 환불 시도 시 BusinessException(PAYMENT_NOT_FOUND)을 던진다")
  void refund_paymentNotFound_throwsBusinessException() {
    // given
    Member loginMember = mock(Member.class);
    when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentService.refund(999L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
      });
  }
}