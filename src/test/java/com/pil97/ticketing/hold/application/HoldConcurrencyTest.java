package com.pil97.ticketing.hold.application;

import com.pil97.ticketing.event.domain.Event;
import com.pil97.ticketing.event.domain.EventStatus;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.hold.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import com.pil97.ticketing.queue.domain.repository.QueueRepository;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.seat.domain.SeatGrade;
import com.pil97.ticketing.seat.domain.repository.SeatRepository;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtime.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 흐름 설명
 * 1. threadCount(10)개 스레드 생성
 * 2. 모든 스레드가 ready 상태가 될 때까지 대기
 * 3. start.countDown()으로 10개 스레드 동시 출발
 * 4. 1개만 락 획득 → HOLD 성공 → successCount++
 * 5. 나머지 9개는 HELD 상태 확인 후 예외 → failCount++
 * 6. successCount == 1, failCount == 9 검증
 * 7. DB에서도 HELD가 1개만 있는지 이중 검증
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class HoldConcurrencyTest {

  @Autowired
  private HoldService holdService;

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

  @Autowired
  private QueueRepository queueRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long showtimeId;
  private Long seatId;
  private Long memberId;
  private Long showtimeSeatId;
  private Long eventId;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();

    Member member = memberRepository.save(
      new Member("hold-concurrency-" + System.nanoTime() + "@test.com", "tester", "encoded-pw")
    );
    memberId = member.getId();

    Event event = BeanUtils.instantiateClass(Event.class);
    ReflectionTestUtils.setField(event, "name", "동시성 테스트 이벤트");
    ReflectionTestUtils.setField(event, "venue", "테스트 공연장");
    ReflectionTestUtils.setField(event, "status", EventStatus.ON_SALE);
    ReflectionTestUtils.setField(event, "endTime", now.plusDays(1));
    ReflectionTestUtils.setField(event, "createdAt", now);
    ReflectionTestUtils.setField(event, "updatedAt", now);
    event = eventRepository.save(event);
    eventId = event.getId();

    Seat seat = BeanUtils.instantiateClass(Seat.class);
    ReflectionTestUtils.setField(seat, "seatNumber", "B-99");
    ReflectionTestUtils.setField(seat, "grade", SeatGrade.VIP);
    ReflectionTestUtils.setField(seat, "rowLabel", "B");
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
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.AVAILABLE);
    ReflectionTestUtils.setField(showtimeSeat, "createdAt", now);
    ReflectionTestUtils.setField(showtimeSeat, "updatedAt", now);
    showtimeSeat = showtimeSeatRepository.save(showtimeSeat);
    showtimeSeatId = showtimeSeat.getId();

    // 동시성 테스트에서는 입장 토큰 검증 우회
    queueRepository.saveAdmissionToken(memberId, "test-token-" + System.nanoTime());
  }

  @AfterEach
  void tearDown() {
    // 생성된 HOLD 전부 삭제 (동시성 테스트로 여러 건 생성 시도될 수 있음)
    jdbcTemplate.update(
      "delete from holds where showtime_seat_id = ?", showtimeSeatId
    );
    if (showtimeSeatId != null) showtimeSeatRepository.deleteById(showtimeSeatId);
    if (showtimeId != null) showtimeRepository.deleteById(showtimeId);
    if (seatId != null) seatRepository.deleteById(seatId);
    if (eventId != null) eventRepository.deleteById(eventId);
    if (memberId != null) memberRepository.deleteById(memberId);
  }

  @Test
  @DisplayName("동일 좌석에 10개의 동시 요청이 들어오면 1개만 HOLD에 성공한다")
  void hold_concurrency_only_one_success() throws InterruptedException {
    // given
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    HoldCreateRequest request = new HoldCreateRequest();
    ReflectionTestUtils.setField(request, "seatId", seatId);
    ReflectionTestUtils.setField(request, "memberId", memberId);

    // when
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        ready.countDown();
        try {
          start.await();
          holdService.hold(showtimeId, request);
          successCount.incrementAndGet();
        } catch (Exception e) {
          failCount.incrementAndGet();
        } finally {
          done.countDown();
        }
      });
    }

    ready.await();
    start.countDown();
    done.await();
    executor.shutdown();

    // then
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);

    // DB에서도 HELD 상태인 showtimeSeat가 1개만 존재하는지 확인
    Long heldCount = jdbcTemplate.queryForObject(
      "select count(*) from showtime_seat where id = ? and status = ?",
      Long.class,
      showtimeSeatId, ShowtimeSeatStatus.HELD.name()
    );
    assertThat(heldCount).isEqualTo(1);
  }
}