package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.ticketing.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 흐름 설명
 * 1. threadCount(10)개 스레드 생성
 * 2. 모든 스레드가 ready 상태가 될 때까지 대기
 * → 스레드가 준비되기도 전에 출발하면 동시성 테스트가 아님
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
  private JdbcTemplate jdbcTemplate;

  private Long showtimeId;
  private Long seatId;
  private Long memberId;

  @BeforeEach
  void setUp() {
    // seed 데이터 기준으로 첫 번째 showtime, seat, member를 사용
    showtimeId = jdbcTemplate.queryForObject(
      "select id from showtime order by id limit 1", Long.class);
    seatId = jdbcTemplate.queryForObject(
      "select id from seat order by id limit 1", Long.class);
    memberId = jdbcTemplate.queryForObject(
      "select id from members order by id limit 1", Long.class);

    // 테스트 전 해당 좌석을 AVAILABLE로 초기화
    jdbcTemplate.update(
      "update showtime_seat set status = ? where showtime_id = ? and seat_id = ?",
      ShowtimeSeatStatus.AVAILABLE.name(),
      showtimeId,
      seatId
    );

    // 해당 좌석에 걸린 HOLD 전부 삭제
    jdbcTemplate.update(
      "delete from holds where showtime_seat_id = ("
        + "select id from showtime_seat where showtime_id = ? and seat_id = ?)",
      showtimeId, seatId
    );
  }

  @Test
  @DisplayName("동일 좌석에 10개의 동시 요청이 들어오면 1개만 HOLD에 성공한다")
  void hold_concurrency_only_one_success() throws InterruptedException {
    // given
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount); // 모든 스레드 준비 완료 신호
    CountDownLatch start = new CountDownLatch(1);           // 동시 출발 신호
    CountDownLatch done = new CountDownLatch(threadCount);  // 모든 스레드 완료 신호

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    HoldCreateRequest request = new HoldCreateRequest();
    ReflectionTestUtils.setField(request, "seatId", seatId);
    ReflectionTestUtils.setField(request, "memberId", memberId);

    // when
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        ready.countDown();       // 준비 완료 알림
        try {
          start.await();         // 출발 신호 대기
          holdService.hold(showtimeId, request);
          successCount.incrementAndGet();
        } catch (Exception e) {
          failCount.incrementAndGet();
        } finally {
          done.countDown();      // 완료 알림
        }
      });
    }

    ready.await();   // 모든 스레드 준비될 때까지 대기
    start.countDown(); // 동시 출발
    done.await();    // 모든 스레드 완료될 때까지 대기
    executor.shutdown();

    // then
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);

    // DB에서도 HELD 상태인 showtime_seat이 1개만 존재하는지 확인
    Long heldCount = jdbcTemplate.queryForObject(
      "select count(*) from showtime_seat where showtime_id = ? and seat_id = ? and status = ?",
      Long.class,
      showtimeId, seatId, ShowtimeSeatStatus.HELD.name()
    );
    assertThat(heldCount).isEqualTo(1);
  }
}
