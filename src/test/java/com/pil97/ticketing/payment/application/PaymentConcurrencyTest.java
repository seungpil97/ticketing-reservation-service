package com.pil97.ticketing.payment.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.event.domain.Event;
import com.pil97.ticketing.event.domain.EventStatus;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import com.pil97.ticketing.payment.api.dto.request.CreatePaymentRequest;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.domain.repository.PaymentRepository;
import com.pil97.ticketing.payment.error.PaymentErrorCode;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.seat.domain.SeatGrade;
import com.pil97.ticketing.seat.domain.repository.SeatRepository;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtime.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

  @Autowired
  private PaymentService paymentService;

  @Autowired
  private PaymentRepository paymentRepository;

  @Autowired
  private ReservationRepository reservationRepository;

  @Autowired
  private HoldRepository holdRepository;

  @Autowired
  private ShowtimeSeatRepository showtimeSeatRepository;

  @Autowired
  private ShowtimeRepository showtimeRepository;

  @Autowired
  private SeatRepository seatRepository;

  @Autowired
  private EventRepository eventRepository;

  @Autowired
  private MemberRepository memberRepository;

  @MockitoBean
  private IdempotencyRedisRepository idempotencyRedisRepository;

  private Long paymentId;
  private Long reservationId;
  private Long holdId;
  private Long showtimeSeatId;
  private Long showtimeId;
  private Long seatId;
  private Long eventId;
  private Long memberId;

  @AfterEach
  void tearDown() {
    if (paymentId != null) paymentRepository.deleteById(paymentId);
    if (reservationId != null) reservationRepository.deleteById(reservationId);
    if (holdId != null) holdRepository.deleteById(holdId);
    if (showtimeSeatId != null)
      showtimeSeatRepository.deleteById(showtimeSeatId);
    if (showtimeId != null) showtimeRepository.deleteById(showtimeId);
    if (seatId != null) seatRepository.deleteById(seatId);
    if (eventId != null) eventRepository.deleteById(eventId);
    if (memberId != null) memberRepository.deleteById(memberId);

    paymentId = null;
    reservationId = null;
    holdId = null;
    showtimeSeatId = null;
    showtimeId = null;
    seatId = null;
    eventId = null;
    memberId = null;
  }

  @Test
  @DisplayName("동일 reservationId 동시 결제 10건 요청 시 1건만 SUCCESS, 나머지 9건은 PAYMENT_ALREADY_PROCESSED")
  void pay_sameReservation_concurrently_onlyOneSuccess() throws Exception {
    // given
    when(idempotencyRedisRepository.find(
      anyString(),
      anyString(),
      anyString(),
      eq(PaymentResponse.class)
    )).thenReturn(Optional.empty());

    doNothing().when(idempotencyRedisRepository).save(
      anyString(),
      anyString(),
      anyString(),
      any(PaymentResponse.class),
      any()
    );

    Reservation reservation = createPendingReservation();

    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    List<String> results = new CopyOnWriteArrayList<>();
    List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      executorService.submit(() -> {
        try {
          CreatePaymentRequest request = new CreatePaymentRequest();
          ReflectionTestUtils.setField(request, "reservationId", reservation.getId());
          ReflectionTestUtils.setField(request, "amount", 150000);
          ReflectionTestUtils.setField(request, "forceFailure", false);

          readyLatch.countDown();
          startLatch.await();

          paymentService.pay("concurrency-key-" + index, request);
          results.add("SUCCESS");
        } catch (BusinessException e) {
          if (e.getErrorCode() == PaymentErrorCode.PAYMENT_ALREADY_PROCESSED) {
            results.add("PAYMENT_ALREADY_PROCESSED");
          } else {
            unexpectedErrors.add(e);
          }
        } catch (Throwable t) {
          unexpectedErrors.add(t);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    readyLatch.await();
    startLatch.countDown();
    doneLatch.await();
    executorService.shutdown();

    // then
    long successCount = results.stream().filter("SUCCESS"::equals).count();
    long alreadyProcessedCount = results.stream().filter("PAYMENT_ALREADY_PROCESSED"::equals).count();

    assertThat(unexpectedErrors).isEmpty();
    assertThat(successCount).isEqualTo(1);
    assertThat(alreadyProcessedCount).isEqualTo(9);
    assertThat(paymentRepository.findByReservationId(reservation.getId())).isPresent();
    paymentId = paymentRepository.findByReservationId(reservation.getId())
      .orElseThrow()
      .getId();
  }

  private Reservation createPendingReservation() {
    String uniqueEmail = "pay-test-" + System.nanoTime() + "@test.com";
    LocalDateTime now = LocalDateTime.now();

    Member member = memberRepository.save(new Member(uniqueEmail, "tester", "encoded-password"));
    memberId = member.getId();

    Event event = BeanUtils.instantiateClass(Event.class);
    ReflectionTestUtils.setField(event, "name", "테스트 이벤트");
    ReflectionTestUtils.setField(event, "venue", "테스트 공연장");
    ReflectionTestUtils.setField(event, "status", EventStatus.ON_SALE);
    ReflectionTestUtils.setField(event, "endTime", now.plusDays(1));
    ReflectionTestUtils.setField(event, "createdAt", now);
    ReflectionTestUtils.setField(event, "updatedAt", now);
    event = eventRepository.save(event);
    eventId = event.getId();

    Seat seat = BeanUtils.instantiateClass(Seat.class);
    ReflectionTestUtils.setField(seat, "seatNumber", "A-1");
    ReflectionTestUtils.setField(seat, "grade", SeatGrade.VIP);
    ReflectionTestUtils.setField(seat, "rowLabel", "A");
    ReflectionTestUtils.setField(seat, "seatNo", 1);
    ReflectionTestUtils.setField(seat, "createdAt", now);
    ReflectionTestUtils.setField(seat, "updatedAt", now);
    seat = seatRepository.save(seat);
    seatId = seat.getId();

    Showtime showtime = BeanUtils.instantiateClass(Showtime.class);
    ReflectionTestUtils.setField(showtime, "event", event);
    ReflectionTestUtils.setField(showtime, "showAt", now.plusHours(2));
    ReflectionTestUtils.setField(showtime, "createdAt", now);
    ReflectionTestUtils.setField(showtime, "updatedAt", now);
    showtime = showtimeRepository.save(showtime);
    showtimeId = showtime.getId();

    ShowtimeSeat showtimeSeat = BeanUtils.instantiateClass(ShowtimeSeat.class);
    ReflectionTestUtils.setField(showtimeSeat, "showtime", showtime);
    ReflectionTestUtils.setField(showtimeSeat, "seat", seat);
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.HELD);
    ReflectionTestUtils.setField(showtimeSeat, "createdAt", now);
    ReflectionTestUtils.setField(showtimeSeat, "updatedAt", now);
    showtimeSeat = showtimeSeatRepository.save(showtimeSeat);
    showtimeSeatId = showtimeSeat.getId();

    Hold hold = Hold.create(showtimeSeat, member, now.plusMinutes(5));
    hold = holdRepository.save(hold);
    holdId = hold.getId();

    Reservation reservation = Reservation.create(hold, showtime, seat, member);
    reservation = reservationRepository.save(reservation);
    reservationId = reservation.getId();
    return reservation;
  }
}