package com.pil97.ticketing.reservation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.common.error.IdempotencyErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
import com.pil97.ticketing.infra.idempotency.IdempotencyResult;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.api.dto.response.ReservationResponse;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock
  private HoldRepository holdRepository;

  @Mock
  private ReservationRepository reservationRepository;

  @Mock
  private IdempotencyRedisRepository idempotencyRedisRepository;

  @Mock
  private ObjectMapper objectMapper;

  // TASK-062: 취소가 좌석 행 잠금에 참여하기 위한 의존성 (현행 구현에서는 미사용)
  @Mock
  private ShowtimeSeatRepository showtimeSeatRepository;

  @InjectMocks
  private ReservationService reservationService;

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
   * 트랜잭션 커밋 흐름을 수동으로 재현하는 헬퍼
   * - 단위 테스트에서는 실제 트랜잭션 커밋이 발생하지 않으므로
   * 등록된 synchronization을 직접 꺼내 콜백을 호출한다
   */
  private void triggerTransactionCommit() {
    TransactionSynchronizationManager.getSynchronizations()
      .forEach(sync -> {
        sync.afterCommit();
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
      });
  }

  @Nested
  @DisplayName("reserve")
  class Reserve {

    @Test
    @DisplayName("유효한 HOLD면 예약을 PENDING 상태로 생성하고 커밋 후 Redis에 저장된다")
    void reserve_success_savesAfterCommit() throws Exception {
      // given
      String idempotencyKey = "test-key-001";
      Long holdId = 1L;
      Long reservationId = 1L;
      Long showtimeId = 10L;
      Long seatId = 20L;
      Long memberId = 30L;

      Hold hold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);
      Showtime showtime = mock(Showtime.class);
      Seat seat = mock(Seat.class);
      Member member = mock(Member.class);
      Reservation savedReservation = mock(Reservation.class);

      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willReturn(Optional.empty());
      given(objectMapper.writeValueAsString(holdId)).willReturn("1");

      given(holdRepository.findByIdWithLock(holdId)).willReturn(Optional.of(hold));
      given(hold.getId()).willReturn(holdId);
      given(hold.getStatus()).willReturn(HoldStatus.ACTIVE);
      given(hold.getExpiresAt()).willReturn(LocalDateTime.now().plusMinutes(5));
      given(hold.getShowtimeSeat()).willReturn(showtimeSeat);
      given(hold.getMember()).willReturn(member);

      given(showtimeSeat.getStatus()).willReturn(ShowtimeSeatStatus.HELD);
      given(showtimeSeat.getShowtime()).willReturn(showtime);
      given(showtimeSeat.getSeat()).willReturn(seat);

      given(showtime.getId()).willReturn(showtimeId);
      given(seat.getId()).willReturn(seatId);
      given(member.getId()).willReturn(memberId);

      given(savedReservation.getId()).willReturn(reservationId);
      given(reservationRepository.save(any(Reservation.class))).willReturn(savedReservation);

      // when
      IdempotencyResult<ReservationResponse> result = reservationService.reserve(idempotencyKey, holdId);

      // 커밋 전에는 Redis 저장 안 됨
      verify(idempotencyRedisRepository, never()).save(anyString(), anyString(), anyString(), any(), any());

      // afterCommit 수동 실행
      triggerTransactionCommit();

      // then
      assertThat(result.isReplayed()).isFalse();
      assertThat(result.getResponse().reservationId()).isEqualTo(reservationId);
      assertThat(result.getResponse().holdId()).isEqualTo(holdId);
      assertThat(result.getResponse().seatStatus()).isEqualTo("HELD");
      assertThat(result.getResponse().holdStatus()).isEqualTo("ACTIVE");

      verify(reservationRepository).save(any(Reservation.class));
      // 좌석/HOLD 상태 변경은 PaymentService에서 처리하므로 호출되지 않아야 함
      verify(showtimeSeat, never()).markReserved();
      verify(hold, never()).confirm();
      // 커밋 후 Redis 저장 확인
      verify(idempotencyRedisRepository).save(anyString(), eq(idempotencyKey), anyString(), any(ReservationResponse.class), any());
    }

    @Test
    @DisplayName("동일 key + 동일 본문 재요청 시 Redis 캐시 응답을 반환하고 DB 처리를 하지 않는다")
    void reserve_duplicateKey_returnsExistingResponse() throws Exception {
      // given
      String idempotencyKey = "duplicate-key-001";
      Long holdId = 1L;

      ReservationResponse cachedResponse = mock(ReservationResponse.class);
      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willReturn(Optional.of(cachedResponse));

      // when
      IdempotencyResult<ReservationResponse> result = reservationService.reserve(idempotencyKey, holdId);

      // then
      assertThat(result.isReplayed()).isTrue();
      assertThat(result.getResponse()).isEqualTo(cachedResponse);
      verifyNoInteractions(holdRepository);
      verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("동일 key로 처리 중인 요청이 있으면 IDEMPOTENCY_IN_PROGRESS 예외를 던진다")
    void reserve_inProgress_throwsBusinessException() throws Exception {
      // given
      String idempotencyKey = "in-progress-key-001";
      Long holdId = 1L;

      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willThrow(new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS));

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(idempotencyKey, holdId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS));

      verifyNoInteractions(holdRepository);
      verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("동일 key로 다른 본문이 전달되면 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH 예외를 던진다")
    void reserve_payloadMismatch_throwsBusinessException() throws Exception {
      // given
      String idempotencyKey = "mismatch-key-001";
      Long holdId = 1L;

      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willThrow(new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH));

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(idempotencyKey, holdId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH));

      verifyNoInteractions(holdRepository);
      verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("Idempotency-Key가 null이면 IDEMPOTENCY_KEY_MISSING 예외를 던진다")
    void reserve_nullIdempotencyKey_throwsBusinessException() {
      // when & then
      assertThatThrownBy(() -> reservationService.reserve(null, 1L))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING));

      verifyNoInteractions(holdRepository);
      verifyNoInteractions(reservationRepository);
      verifyNoInteractions(idempotencyRedisRepository);
    }

    @Test
    @DisplayName("Idempotency-Key가 blank이면 IDEMPOTENCY_KEY_MISSING 예외를 던진다")
    void reserve_blankIdempotencyKey_throwsBusinessException() {
      // when & then
      assertThatThrownBy(() -> reservationService.reserve("   ", 1L))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING));

      verifyNoInteractions(holdRepository);
      verifyNoInteractions(reservationRepository);
      verifyNoInteractions(idempotencyRedisRepository);
    }

    @Test
    @DisplayName("존재하지 않는 HOLD면 예외가 발생하고 lock이 해제된다")
    void reserve_holdNotFound_releasesLock() throws Exception {
      // given
      String idempotencyKey = "test-key-002";
      Long holdId = 1L;

      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willReturn(Optional.empty());
      given(holdRepository.findByIdWithLock(holdId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(idempotencyKey, holdId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(HoldErrorCode.NOT_FOUND));

      verify(reservationRepository, never()).save(any());
      // 예외 발생 시 finally에서 lock 해제 확인
      verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
    }

    @Test
    @DisplayName("HOLD 상태가 ACTIVE가 아니면 예외가 발생하고 lock이 해제된다")
    void reserve_holdNotActive_releasesLock() throws Exception {
      // given
      String idempotencyKey = "test-key-003";
      Long holdId = 1L;
      Hold hold = mock(Hold.class);

      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willReturn(Optional.empty());
      given(holdRepository.findByIdWithLock(holdId)).willReturn(Optional.of(hold));
      given(hold.getStatus()).willReturn(HoldStatus.CONFIRMED);

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(idempotencyKey, holdId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(HoldErrorCode.NOT_ACTIVE));

      verify(reservationRepository, never()).save(any());
      verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
    }

    @Test
    @DisplayName("HOLD가 만료되었으면 예외가 발생하고 lock이 해제된다")
    void reserve_holdExpired_releasesLock() throws Exception {
      // given
      String idempotencyKey = "test-key-004";
      Long holdId = 1L;
      Hold hold = mock(Hold.class);

      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willReturn(Optional.empty());
      given(holdRepository.findByIdWithLock(holdId)).willReturn(Optional.of(hold));
      given(hold.getStatus()).willReturn(HoldStatus.ACTIVE);
      given(hold.getExpiresAt()).willReturn(LocalDateTime.now().minusMinutes(1));

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(idempotencyKey, holdId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(HoldErrorCode.EXPIRED));

      verify(reservationRepository, never()).save(any());
      verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
    }

    @Test
    @DisplayName("연결된 좌석 상태가 HELD가 아니면 예외가 발생하고 lock이 해제된다")
    void reserve_seatNotHeld_releasesLock() throws Exception {
      // given
      String idempotencyKey = "test-key-005";
      Long holdId = 1L;
      Hold hold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

      given(objectMapper.writeValueAsString(holdId)).willReturn("1");
      given(idempotencyRedisRepository.find(anyString(), eq(idempotencyKey), anyString(), eq(ReservationResponse.class)))
        .willReturn(Optional.empty());
      given(holdRepository.findByIdWithLock(holdId)).willReturn(Optional.of(hold));
      given(hold.getStatus()).willReturn(HoldStatus.ACTIVE);
      given(hold.getExpiresAt()).willReturn(LocalDateTime.now().plusMinutes(5));
      given(hold.getShowtimeSeat()).willReturn(showtimeSeat);
      given(showtimeSeat.getStatus()).willReturn(ShowtimeSeatStatus.RESERVED);

      // when & then
      assertThatThrownBy(() -> reservationService.reserve(idempotencyKey, holdId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ShowtimeSeatErrorCode.NOT_HELD));

      verify(reservationRepository, never()).save(any());
      verify(idempotencyRedisRepository).releaseLock(anyString(), eq(idempotencyKey));
    }
  }

  @Nested
  @DisplayName("cancel")
  class Cancel {

    /**
     * 예약 조회 stub - 현행(findById)과 개선 후(findByIdWithLock) 경로 중 하나만 사용되므로 각각 lenient
     */
    private void stubReservationLookup(Long reservationId, Optional<Reservation> result) {
      lenient().when(reservationRepository.findById(reservationId)).thenReturn(result);
      lenient().when(reservationRepository.findByIdWithLock(reservationId)).thenReturn(result);
    }

    /**
     * TASK-062 잠금 경로 stub (showtimeId=1000, seatId=2000, holdId=10, showtimeSeatId=100) - 현행 구현은 호출하지 않으므로 lenient
     * - 현행 경로(HOLD → 좌석 탐색) stub도 함께 둔다 (개선 후 미사용 가능)
     * - 잠금 우선 읽기 (WORK-4B): 좌석은 잠근 예약 행의 FK(showtime_id, seat_id)로 unique key 잠금 조회한다
     */
    private void stubSeatAndHoldLocks(Reservation reservation, Hold target, ShowtimeSeat showtimeSeat,
                                      List<Hold> lockedHolds) {
      Showtime showtime = mock(Showtime.class);
      Seat seat = mock(Seat.class);
      lenient().when(showtime.getId()).thenReturn(1000L);
      lenient().when(seat.getId()).thenReturn(2000L);
      lenient().when(reservation.getShowtime()).thenReturn(showtime);
      lenient().when(reservation.getSeat()).thenReturn(seat);
      lenient().when(target.getShowtimeSeat()).thenReturn(showtimeSeat);
      lenient().when(target.getId()).thenReturn(10L);
      lenient().when(showtimeSeat.getId()).thenReturn(100L);
      lenient().when(showtimeSeatRepository.findByShowtimeIdAndSeatIdWithLock(1000L, 2000L))
        .thenReturn(Optional.of(showtimeSeat));
      lenient().when(holdRepository.findTargetAndLiveHoldsForUpdate(eq(100L), eq(10L), anyCollection()))
        .thenReturn(lockedHolds);
    }

    private void verifyLockOrder(Long reservationId) {
      InOrder inOrder = inOrder(reservationRepository, showtimeSeatRepository, holdRepository);
      inOrder.verify(reservationRepository).findByIdWithLock(reservationId);
      inOrder.verify(showtimeSeatRepository).findByShowtimeIdAndSeatIdWithLock(1000L, 2000L);
      inOrder.verify(holdRepository).findTargetAndLiveHoldsForUpdate(
        eq(100L), eq(10L), argThat(statuses -> statuses.containsAll(List.of(HoldStatus.ACTIVE, HoldStatus.CONFIRMED))));
      // 좌석 ID를 찾기 위한 HOLD 일관 읽기를 하지 않는다 (잠금 우선 읽기)
      verify(holdRepository, never()).findShowtimeSeatIdById(any());
    }

    @Test
    @DisplayName("PENDING 상태의 예약이면 취소에 성공한다 - 선점자 HOLD는 CANCELLED, 좌석 해제 (TASK-062)")
    void cancel_success() {
      // given
      Long reservationId = 1L;

      Reservation reservation = mock(Reservation.class);
      Hold hold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

      stubReservationLookup(reservationId, Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.PENDING);
      given(reservation.getHold()).willReturn(hold);
      lenient().when(hold.getStatus()).thenReturn(HoldStatus.ACTIVE);
      lenient().when(showtimeSeat.getStatus()).thenReturn(ShowtimeSeatStatus.HELD);
      stubSeatAndHoldLocks(reservation, hold, showtimeSeat, List.of(hold));

      // when
      reservationService.cancel(reservationId);

      // then
      verify(showtimeSeat).markAvailable();
      verify(reservation).cancel();
      verify(hold, never()).confirm();
      // TASK-062: HOLD 종료와 잠금 순서 (mock 호출 계약, 실제 잠금 증거 아님)
      verify(hold).cancel();
      verifyLockOrder(reservationId);
    }

    @Test
    @DisplayName("이미 종료된 HOLD(EXPIRED)의 PENDING 예약 취소는 좌석을 해제하지 않고 예약만 취소한다 (TASK-062)")
    void cancel_holdAlreadyTerminated_doesNotReleaseSeat() {
      // given
      Long reservationId = 1L;

      Reservation reservation = mock(Reservation.class);
      Hold hold = mock(Hold.class);
      Hold otherLiveHold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

      stubReservationLookup(reservationId, Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.PENDING);
      given(reservation.getHold()).willReturn(hold);
      lenient().when(hold.getStatus()).thenReturn(HoldStatus.EXPIRED);
      lenient().when(otherLiveHold.getId()).thenReturn(20L);
      lenient().when(otherLiveHold.getStatus()).thenReturn(HoldStatus.ACTIVE);
      lenient().when(showtimeSeat.getStatus()).thenReturn(ShowtimeSeatStatus.HELD);
      stubSeatAndHoldLocks(reservation, hold, showtimeSeat, List.of(hold, otherLiveHold));

      // when
      reservationService.cancel(reservationId);

      // then
      verify(reservation).cancel();
      verify(hold, never()).cancel();
      verify(showtimeSeat, never()).markAvailable();
      verifyLockOrder(reservationId);
    }

    @Test
    @DisplayName("대상 HOLD가 ACTIVE지만 같은 좌석에 다른 활성 HOLD가 있으면 HOLD만 CANCELLED, 좌석은 해제하지 않는다 (TASK-062)")
    void cancel_activeHoldButNotOwner_cancelsHoldOnly() {
      // given
      Long reservationId = 1L;

      Reservation reservation = mock(Reservation.class);
      Hold hold = mock(Hold.class);
      Hold otherLiveHold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

      stubReservationLookup(reservationId, Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.PENDING);
      given(reservation.getHold()).willReturn(hold);
      lenient().when(hold.getStatus()).thenReturn(HoldStatus.ACTIVE);
      lenient().when(otherLiveHold.getId()).thenReturn(20L);
      lenient().when(otherLiveHold.getStatus()).thenReturn(HoldStatus.ACTIVE);
      lenient().when(showtimeSeat.getStatus()).thenReturn(ShowtimeSeatStatus.HELD);
      stubSeatAndHoldLocks(reservation, hold, showtimeSeat, List.of(hold, otherLiveHold));

      // when
      reservationService.cancel(reservationId);

      // then
      verify(reservation).cancel();
      verify(hold).cancel();
      verify(showtimeSeat, never()).markAvailable();
      verifyLockOrder(reservationId);
    }

    @Test
    @DisplayName("존재하지 않는 예약이면 예외가 발생한다")
    void cancel_reservationNotFound() {
      // given
      Long reservationId = 1L;
      stubReservationLookup(reservationId, Optional.empty());

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.NOT_FOUND));

      // TASK-062: 예약 조회는 잠금 조회로 수행하고, 실패 시 좌석·HOLD 잠금을 시도하지 않는다
      verify(reservationRepository).findByIdWithLock(reservationId);
      verifyNoInteractions(holdRepository, showtimeSeatRepository);
    }

    @Test
    @DisplayName("CONFIRMED 상태의 예약은 직접 취소 불가 - 환불 API 사용 안내")
    void cancel_confirmed_requiresRefund() {
      // given
      Long reservationId = 1L;
      Reservation reservation = mock(Reservation.class);

      stubReservationLookup(reservationId, Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.CONFIRMED);

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_REQUIRES_REFUND));

      verify(reservation, never()).cancel();
      verify(reservation, never()).getHold();
      // TASK-062: 상태 검증 실패 시 좌석·HOLD 잠금을 시도하지 않는다
      verifyNoInteractions(holdRepository, showtimeSeatRepository);
    }

    @Test
    @DisplayName("FAILED 상태의 예약은 취소할 수 없다")
    void cancel_failed_notAllowed() {
      // given
      Long reservationId = 1L;
      Reservation reservation = mock(Reservation.class);

      stubReservationLookup(reservationId, Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.FAILED);

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED));

      verify(reservation, never()).cancel();
      verify(reservation, never()).getHold();
      verifyNoInteractions(holdRepository, showtimeSeatRepository);
    }

    @Test
    @DisplayName("이미 취소된 예약은 취소할 수 없다")
    void cancel_alreadyCancelled_notAllowed() {
      // given
      Long reservationId = 1L;
      Reservation reservation = mock(Reservation.class);

      stubReservationLookup(reservationId, Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.CANCELLED);

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED));

      verify(reservation, never()).cancel();
      verify(reservation, never()).getHold();
      verifyNoInteractions(holdRepository, showtimeSeatRepository);
    }
  }
}