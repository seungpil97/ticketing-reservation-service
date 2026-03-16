package com.pil97.ticketing.ticketing.application;

import com.pil97.ticketing.ticketing.domain.Hold;
import com.pil97.ticketing.ticketing.domain.HoldStatus;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeat;
import com.pil97.ticketing.ticketing.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.ticketing.domain.repository.HoldRepository;
import com.pil97.ticketing.ticketing.domain.repository.ShowtimeSeatRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Transactional
class HoldExpirationServiceTest {

  @Autowired
  private HoldExpirationService holdExpirationService;

  @Autowired
  private HoldRepository holdRepository;

  @Autowired
  private ShowtimeSeatRepository showtimeSeatRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("만료된 ACTIVE HOLD를 EXPIRED로 변경하고 좌석을 AVAILABLE로 복구한다")
  void expireExpiredActiveHoldsAndReleaseSeats() {
    // given
    Long showtimeSeatId = jdbcTemplate.queryForObject(
      "select id from showtime_seat order by id limit 1",
      Long.class
    );
    assertThat(showtimeSeatId).isNotNull();

    Long memberId = jdbcTemplate.queryForObject(
      "select id from members order by id limit 1",
      Long.class
    );
    assertThat(memberId).isNotNull();

    // 만료 처리 대상이 되도록 좌석은 HELD, HOLD는 expiresAt < now 상태로 준비
    jdbcTemplate.update(
      "update showtime_seat set status = ? where id = ?",
      ShowtimeSeatStatus.HELD.name(),
      showtimeSeatId
    );

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiredAt = now.minusMinutes(10);

    jdbcTemplate.update(
      "insert into holds (showtime_seat_id, member_id, status, expires_at) values (?, ?, ?, ?)",
      showtimeSeatId,
      memberId,
      HoldStatus.ACTIVE.name(),
      Timestamp.valueOf(expiredAt)
    );

    Long holdId = jdbcTemplate.queryForObject(
      "select id from holds where showtime_seat_id = ? order by id desc limit 1",
      Long.class,
      showtimeSeatId
    );
    assertThat(holdId).isNotNull();

    // when
    holdExpirationService.expireHolds(now);

    entityManager.flush();
    entityManager.clear();

    // then
    Hold hold = holdRepository.findById(holdId).orElseThrow();
    ShowtimeSeat showtimeSeat = showtimeSeatRepository.findById(showtimeSeatId).orElseThrow();

    assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);
    assertThat(showtimeSeat.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE);
  }
}