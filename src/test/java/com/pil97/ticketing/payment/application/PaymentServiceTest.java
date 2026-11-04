package com.pil97.ticketing.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
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
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private ReservationRepository reservationRepository;

  @Mock
  private IdempotencyRedisRepository idempotencyRedisRepository;

  @Mock
  private ObjectMapper objectMapper;

  // TASK-062: 결제가 좌석 → 관련 HOLD 잠금에 참여하기 위한 의존성 (현행 구현에서는 미사용)
  @Mock
  private HoldRepository holdRepository;

  @Mock
  private ShowtimeSeatRepository showtimeSeatRepository;

  @InjectMocks
  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    // registerAfterCommit() 내 isSynchronizationActive() 체크를 통과시키기 위해 초기화
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  /**
   * afterCommit 콜백을 수동으로 실행하는 헬퍼
   * - 단위 테스트에서는 실제 트랜잭션 커밋이 발생하지 않으므로
   * 등록된 synchronization을 직접 꺼내 afterCommit()을 호출한다
   */
  private void triggerTransactionCommit() {
    TransactionSynchronizationManager.getSynchronizations()
      .forEach(sync -> {
        sync.afterCommit();
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
      });
  }

  /**
   * TASK-062 잠금 경로 stub (showtimeId=1000, seatId=2000, holdId=10, showtimeSeatId=100, 대상 HOLD가 유일한 활성 HOLD)
   * - 현행 구현은 이 경로를 호출하지 않으므로 lenient: 현행(RED)과 개선 후(GREEN) 모두에서 같은 테스트가 유효하도록 한다
   * - 잠금 우선 읽기 (WORK-4B): 좌석은 잠근 예약 행의 FK(showtime_id, seat_id)로 unique key 잠금 조회한다.
   *   HOLD에서 좌석 ID를 찾는 일관 읽기(findShowtimeSeatIdById)는 쓰지 않는다
   */
  private void stubSeatAndHoldLocks(Reservation reservation, Hold hold, ShowtimeSeat showtimeSeat) {
    Showtime showtime = mock(Showtime.class);
    Seat seat = mock(Seat.class);
    lenient().when(showtime.getId()).thenReturn(1000L);
    lenient().when(seat.getId()).thenReturn(2000L);
    lenient().when(reservation.getShowtime()).thenReturn(showtime);
    lenient().when(reservation.getSeat()).thenReturn(seat);
    lenient().when(hold.getId()).thenReturn(10L);
    lenient().when(hold.getStatus()).thenReturn(HoldStatus.ACTIVE);
    lenient().when(showtimeSeat.getId()).thenReturn(100L);
    lenient().when(showtimeSeat.getStatus()).thenReturn(ShowtimeSeatStatus.HELD);
    lenient().when(showtimeSeatRepository.findByShowtimeIdAndSeatIdWithLock(1000L, 2000L))
      .thenReturn(Optional.of(showtimeSeat));
    lenient().when(holdRepository.findTargetAndLiveHoldsForUpdate(eq(100L), eq(10L), anyCollection()))
      .thenReturn(List.of(hold));
  }

  /**
   * TASK-062 잠금 순서 계약: 예약 → 좌석 → 관련 HOLD, Payment 저장은 잠금·검증 이후
   * - 좌석 ID를 찾기 위한 HOLD 일관 읽기를 하지 않는다 (잠금 우선 읽기)
   */
  private void verifyLockOrderBeforeSave(Long reservationId) {
    InOrder inOrder = inOrder(reservationRepository, showtimeSeatRepository, holdRepository, paymentRepository);
    inOrder.verify(reservationRepository).findByIdWithLock(reservationId);
    inOrder.verify(showtimeSeatRepository).findByShowtimeIdAndSeatIdWithLock(1000L, 2000L);
    inOrder.verify(holdRepository).findTargetAndLiveHoldsForUpdate(
      eq(100L), eq(10L), argThat(statuses -> statuses.containsAll(List.of(HoldStatus.ACTIVE, HoldStatus.CONFIRMED))));
    inOrder.verify(paymentRepository).save(any(Payment.class));
    verify(holdRepository, never()).findShowtimeSeatIdById(any());
  }

  // ===================== 결제 케이스 =====================

  @Test
  @DisplayName("pay: 결제 성공 시 트랜잭션 커밋 후 Redis에 결과가 저장된다")
  void pay_success_savesAfterCommit() throws Exception {
    // given
    String idempotencyKey = "test-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(false);
    when(objectMapper.writeValueAsString(request)).thenReturn("{\"reservationId\":1}");

    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    // 현행 경로(HOLD → 좌석 탐색) stub - 개선 후에는 잠금 조회로 대체될 수 있어 lenient
    lenient().when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    when(reservation.getHold()).thenReturn(hold);
    stubSeatAndHoldLocks(reservation, hold, showtimeSeat);

    when(reservationRepository.findByIdWithLock(1L)).thenReturn(Optional.of(reservation));

    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(1L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.SUCCESS);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenReturn(Optional.empty());

    // when
    IdempotencyResult<PaymentResponse> result = paymentService.pay(idempotencyKey, request);

    // 커밋 전에는 save() 호출되지 않아야 함
    verify(idempotencyRedisRepository, never()).save(anyString(), anyString(), anyString(), any(), any());

    // afterCommit 수동 실행 - 실제 트랜잭션 커밋 흉내
    triggerTransactionCommit();

    // then
    assertThat(result.isReplayed()).isFalse();
    assertThat(result.getResponse().status()).isEqualTo("SUCCESS");

    verify(reservation).confirm();
    verify(showtimeSeat).markReserved();
    verify(hold).confirm();
    verify(savedPayment).success();
    // 커밋 후 Redis 저장 확인
    verify(idempotencyRedisRepository).save(anyString(), eq(idempotencyKey), anyString(), any(PaymentResponse.class), any());
    // TASK-062: 예약 → 좌석 → 관련 HOLD 잠금 후 Payment 저장 (mock 호출 계약, 실제 잠금 증거 아님)
    verifyLockOrderBeforeSave(1L);
  }

  @Test
  @DisplayName("pay: forceFailure=true이면 Payment FAIL, 예약 FAILED, HOLD EXPIRED, 좌석 AVAILABLE로 복구되고 lock이 해제된다")
  void pay_forceFailure_releasesLock() throws Exception {
    // given
    String idempotencyKey = "test-key-002";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(request.getAmount()).thenReturn(150000);
    when(request.isForceFailure()).thenReturn(true);
    when(objectMapper.writeValueAsString(request)).thenReturn("{\"reservationId\":1}");

    ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
    Hold hold = mock(Hold.class);
    // 현행 경로(HOLD → 좌석 탐색) stub - 개선 후에는 잠금 조회로 대체될 수 있어 lenient
    lenient().when(hold.getShowtimeSeat()).thenReturn(showtimeSeat);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.PENDING);
    when(reservation.getHold()).thenReturn(hold);
    stubSeatAndHoldLocks(reservation, hold, showtimeSeat);

    when(reservationRepository.findByIdWithLock(1L)).thenReturn(Optional.of(reservation));

    Payment savedPayment = mock(Payment.class);
    when(savedPayment.getId()).thenReturn(2L);
    when(savedPayment.getStatus()).thenReturn(PaymentStatus.FAIL);
    when(savedPayment.getPaidAt()).thenReturn(null);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenReturn(Optional.empty());

    // when
    IdempotencyResult<PaymentResponse> result = paymentService.pay(idempotencyKey, request);

    // then
    assertThat(result.isReplayed()).isFalse();
    assertThat(result.getResponse().status()).isEqualTo("FAIL");

    verify(savedPayment).fail();
    verify(reservation).fail();
    verify(reservation, never()).confirm();
    verify(hold).expire();
    verify(showtimeSeat).markAvailable();
    // 결제 실패 시 캐시 저장 안 함
    verify(idempotencyRedisRepository, never()).save(anyString(), anyString(), anyString(), any(), any());
    // 결제 실패 시 lock 즉시 해제 - 재시도 허용
    verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
    // TASK-062: forceFailure도 예약 → 좌석 → 관련 HOLD 잠금 후 Payment 저장 (mock 호출 계약)
    verifyLockOrderBeforeSave(1L);
  }

  @Test
  @DisplayName("pay: 존재하지 않는 reservationId이면 BusinessException(NOT_FOUND)을 던지고 lock이 해제된다")
  void pay_reservationNotFound_releasesLock() throws Exception {
    // given
    String idempotencyKey = "test-key-003";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(999L);
    when(objectMapper.writeValueAsString(request)).thenReturn("{\"reservationId\":999}");

    when(reservationRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());
    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentService.pay(idempotencyKey, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(ReservationErrorCode.NOT_FOUND));

    verifyNoInteractions(paymentRepository);
    // TASK-062: 예약 검증 실패 시 좌석·HOLD 잠금을 시도하지 않는다
    verifyNoInteractions(holdRepository, showtimeSeatRepository);
    // 예외 발생 시 finally에서 lock 해제 확인
    verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
  }

  @Test
  @DisplayName("pay: PENDING이 아닌 예약이면 BusinessException(PAYMENT_ALREADY_PROCESSED)을 던지고 lock이 해제된다")
  void pay_notPendingReservation_releasesLock() throws Exception {
    // given
    String idempotencyKey = "test-key-004";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(request.getReservationId()).thenReturn(1L);
    when(objectMapper.writeValueAsString(request)).thenReturn("{\"reservationId\":1}");

    Reservation reservation = mock(Reservation.class);
    when(reservation.getStatus()).thenReturn(ReservationStatus.CONFIRMED);
    when(reservationRepository.findByIdWithLock(1L)).thenReturn(Optional.of(reservation));
    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentService.pay(idempotencyKey, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED));

    verifyNoInteractions(paymentRepository);
    // TASK-062: PENDING 검증 실패 시 좌석·HOLD 잠금을 시도하지 않는다
    verifyNoInteractions(holdRepository, showtimeSeatRepository);
    // 예외 발생 시 finally에서 lock 해제 확인
    verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
  }

  // ===================== idempotency 케이스 =====================

  @Test
  @DisplayName("pay: 동일 idempotency key 재요청 시 Redis에서 기존 결과를 반환하고 DB 처리를 하지 않는다")
  void pay_duplicateKey_returnsExistingResponse() throws Exception {
    // given
    String idempotencyKey = "duplicate-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(objectMapper.writeValueAsString(request)).thenReturn("{}");

    PaymentResponse cachedResponse = mock(PaymentResponse.class);
    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenReturn(Optional.of(cachedResponse));

    // when
    IdempotencyResult<PaymentResponse> result = paymentService.pay(idempotencyKey, request);

    // then
    assertThat(result.isReplayed()).isTrue();
    assertThat(result.getResponse()).isEqualTo(cachedResponse);
    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
  }

  @Test
  @DisplayName("pay: 동일 key로 처리 중인 요청이 있으면 IDEMPOTENCY_IN_PROGRESS 예외를 던진다")
  void pay_inProgress_throwsBusinessException() throws Exception {
    // given
    String idempotencyKey = "in-progress-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(objectMapper.writeValueAsString(request)).thenReturn("{}");

    // SETNX lock 선점 실패 시나리오
    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenThrow(new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS));

    // when & then
    assertThatThrownBy(() -> paymentService.pay(idempotencyKey, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS));

    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
  }

  @Test
  @DisplayName("pay: 동일 key로 다른 본문이 전달되면 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH 예외를 던진다")
  void pay_payloadMismatch_throwsBusinessException() throws Exception {
    // given
    String idempotencyKey = "mismatch-key-001";

    CreatePaymentRequest request = mock(CreatePaymentRequest.class);
    when(objectMapper.writeValueAsString(request)).thenReturn("{}");

    // fingerprint 불일치 시나리오
    when(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(PaymentResponse.class)))
      .thenThrow(new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH));

    // when & then
    assertThatThrownBy(() -> paymentService.pay(idempotencyKey, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH));

    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
  }

  @Test
  @DisplayName("pay: idempotency key가 null이면 BusinessException(IDEMPOTENCY_KEY_MISSING)을 던진다")
  void pay_nullIdempotencyKey_throwsBusinessException() {
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);

    assertThatThrownBy(() -> paymentService.pay(null, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING));

    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
    verifyNoInteractions(idempotencyRedisRepository);
  }

  @Test
  @DisplayName("pay: idempotency key가 blank이면 BusinessException(IDEMPOTENCY_KEY_MISSING)을 던진다")
  void pay_blankIdempotencyKey_throwsBusinessException() {
    CreatePaymentRequest request = mock(CreatePaymentRequest.class);

    assertThatThrownBy(() -> paymentService.pay("   ", request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING));

    verifyNoInteractions(reservationRepository);
    verifyNoInteractions(paymentRepository);
    verifyNoInteractions(idempotencyRedisRepository);
  }

  // ===================== 환불 케이스 =====================

  @Test
  @DisplayName("refund: 환불 성공 시 Payment REFUNDED, 예약 CANCELLED, HOLD REFUNDED, 좌석 AVAILABLE로 전환된다")
  void refund_success() {
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

    paymentService.refund(1L, loginMember);

    verify(payment).refund();
    verify(reservation).cancelByRefund();
    verify(hold).refund();
    verify(showtimeSeat).markAvailable();
  }

  @Test
  @DisplayName("refund: 타인의 결제 환불 시도 시 BusinessException(REFUND_FORBIDDEN)을 던진다")
  void refund_notOwner_throwsBusinessException() {
    Member loginMember = mock(Member.class);
    when(loginMember.getId()).thenReturn(2L);

    Member owner = mock(Member.class);
    when(owner.getId()).thenReturn(1L);

    Reservation reservation = mock(Reservation.class);
    when(reservation.getMember()).thenReturn(owner);

    Payment payment = mock(Payment.class);
    when(payment.getReservation()).thenReturn(reservation);
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    assertThatThrownBy(() -> paymentService.refund(1L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(PaymentErrorCode.REFUND_FORBIDDEN));

    verify(payment, never()).refund();
  }

  @Test
  @DisplayName("refund: SUCCESS 상태가 아닌 환불 시도 시 BusinessException(REFUND_NOT_ALLOWED)을 던진다")
  void refund_notSuccess_throwsBusinessException() {
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

    assertThatThrownBy(() -> paymentService.refund(1L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(PaymentErrorCode.REFUND_NOT_ALLOWED));

    verify(payment, never()).refund();
  }

  @Test
  @DisplayName("refund: 이미 환불된 결제 시도 시 BusinessException(REFUND_NOT_ALLOWED)을 던진다")
  void refund_alreadyRefunded_throwsBusinessException() {
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

    assertThatThrownBy(() -> paymentService.refund(1L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(PaymentErrorCode.REFUND_NOT_ALLOWED));

    verify(payment, never()).refund();
  }

  @Test
  @DisplayName("refund: 존재하지 않는 paymentId 환불 시도 시 BusinessException(PAYMENT_NOT_FOUND)을 던진다")
  void refund_paymentNotFound_throwsBusinessException() {
    Member loginMember = mock(Member.class);
    when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.refund(999L, loginMember))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
  }
}