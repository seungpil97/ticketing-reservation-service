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
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
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

    @Test
    @DisplayName("PENDING 상태의 예약이면 취소에 성공한다")
    void cancel_success() {
      // given
      Long reservationId = 1L;

      Reservation reservation = mock(Reservation.class);
      Hold hold = mock(Hold.class);
      ShowtimeSeat showtimeSeat = mock(ShowtimeSeat.class);

      given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.PENDING);
      given(reservation.getHold()).willReturn(hold);
      given(hold.getShowtimeSeat()).willReturn(showtimeSeat);

      // when
      reservationService.cancel(reservationId);

      // then
      verify(showtimeSeat).markAvailable();
      verify(reservation).cancel();
      verify(hold, never()).confirm();
    }

    @Test
    @DisplayName("존재하지 않는 예약이면 예외가 발생한다")
    void cancel_reservationNotFound() {
      // given
      Long reservationId = 1L;
      given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.NOT_FOUND));

      verify(reservationRepository).findById(reservationId);
    }

    @Test
    @DisplayName("CONFIRMED 상태의 예약은 직접 취소 불가 - 환불 API 사용 안내")
    void cancel_confirmed_requiresRefund() {
      // given
      Long reservationId = 1L;
      Reservation reservation = mock(Reservation.class);

      given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.CONFIRMED);

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_REQUIRES_REFUND));

      verify(reservation, never()).cancel();
      verify(reservation, never()).getHold();
    }

    @Test
    @DisplayName("FAILED 상태의 예약은 취소할 수 없다")
    void cancel_failed_notAllowed() {
      // given
      Long reservationId = 1L;
      Reservation reservation = mock(Reservation.class);

      given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.FAILED);

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED));

      verify(reservation, never()).cancel();
      verify(reservation, never()).getHold();
    }

    @Test
    @DisplayName("이미 취소된 예약은 취소할 수 없다")
    void cancel_alreadyCancelled_notAllowed() {
      // given
      Long reservationId = 1L;
      Reservation reservation = mock(Reservation.class);

      given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
      given(reservation.getStatus()).willReturn(ReservationStatus.CANCELLED);

      // when & then
      assertThatThrownBy(() -> reservationService.cancel(reservationId))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
          .isEqualTo(ReservationErrorCode.RESERVATION_CANCEL_NOT_ALLOWED));

      verify(reservation, never()).cancel();
      verify(reservation, never()).getHold();
    }
  }
}