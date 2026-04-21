package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.seat.domain.SeatGrade;
import com.pil97.ticketing.seat.domain.repository.SeatRepository;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtime.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import com.pil97.ticketing.event.domain.Event;
import com.pil97.ticketing.event.domain.EventStatus;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class HoldExpirationServiceTest {

  @Autowired
  private HoldExpirationService holdExpirationService;

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

  private Long holdId;
  private Long showtimeSeatId;
  private Long showtimeId;
  private Long seatId;
  private Long eventId;
  private Long memberId;

  @AfterEach
  void tearDown() {
    if (holdId != null) holdRepository.deleteById(holdId);
    if (showtimeSeatId != null)
      showtimeSeatRepository.deleteById(showtimeSeatId);
    if (showtimeId != null) showtimeRepository.deleteById(showtimeId);
    if (seatId != null) seatRepository.deleteById(seatId);
    if (eventId != null) eventRepository.deleteById(eventId);
    if (memberId != null) memberRepository.deleteById(memberId);
  }

  @Test
  @DisplayName("만료된 ACTIVE HOLD를 EXPIRED로 변경하고 좌석을 AVAILABLE로 복구한다")
  void expireExpiredActiveHoldsAndReleaseSeats() {
    // given
    LocalDateTime now = LocalDateTime.now();

    Member member = memberRepository.save(
      new Member("expiration-test-" + System.nanoTime() + "@test.com", "tester", "encoded-pw")
    );
    memberId = member.getId();

    Event event = BeanUtils.instantiateClass(Event.class);
    ReflectionTestUtils.setField(event, "name", "만료 테스트 이벤트");
    ReflectionTestUtils.setField(event, "venue", "테스트 공연장");
    ReflectionTestUtils.setField(event, "status", EventStatus.ON_SALE);
    ReflectionTestUtils.setField(event, "endTime", now.plusDays(1));
    ReflectionTestUtils.setField(event, "createdAt", now);
    ReflectionTestUtils.setField(event, "updatedAt", now);
    event = eventRepository.save(event);
    eventId = event.getId();

    Seat seat = BeanUtils.instantiateClass(Seat.class);
    ReflectionTestUtils.setField(seat, "seatNumber", "A-99");
    ReflectionTestUtils.setField(seat, "grade", SeatGrade.VIP);
    ReflectionTestUtils.setField(seat, "rowLabel", "A");
    ReflectionTestUtils.setField(seat, "seatNo", 99);
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

    // 만료 대상: ACTIVE 상태 + expiresAt < now
    Hold hold = Hold.create(showtimeSeat, member, now.minusMinutes(10));
    hold = holdRepository.save(hold);
    holdId = hold.getId();

    // when
    holdExpirationService.expireHolds(now);

    // then
    Hold result = holdRepository.findById(holdId).orElseThrow();
    ShowtimeSeat resultSeat = showtimeSeatRepository.findById(showtimeSeatId).orElseThrow();

    assertThat(result.getStatus()).isEqualTo(HoldStatus.EXPIRED);
    assertThat(resultSeat.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE);
  }
}