package com.pil97.ticketing.hold.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.FaultInjectingInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TASK-062 (#104) 만료 항목별 실패 격리 통합 테스트 (AC-9b, RED_REQUIRED)
 * - 테스트 전용 Hibernate Interceptor를 test property로만 등록해 특정 좌석의 flush에 의도적 장애를 주입한다
 * - 진입점은 기존·수정 후 모두 public expireHolds(now)
 * - 로그 계약 (logger 범위: com.pil97.ticketing.hold.application 패키지)
 *   - 실패 항목: ERROR 수준 이벤트의 formatted message에 "holdId={id}"
 *   - 요약: 하나의 이벤트 formatted message에 "expired={n}"과 "failed={n}"
 * - logback ListAppender를 테스트 동안만 붙이고, 종료 시 appender 제거와 logger level 복원을 수행한다
 */
@SpringBootTest(properties =
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.consistency.FaultInjectingInterceptor")
@ActiveProfiles("test")
@Import(ConsistencyFixture.class)
class HoldExpirationIsolationIntegrationTest {

  private static final String LOGGER_NAME = "com.pil97.ticketing.hold.application";

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private ConsistencyFixture fixture;
  @Autowired private HoldExpirationService holdExpirationService;

  private Logger logger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    FaultInjectingInterceptor.reset();
    logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
    originalLevel = logger.getLevel();
    // INFO 요약과 ERROR 항목 로그를 모두 수집하기 위해 테스트 동안만 INFO로 둔다
    logger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    try {
      logger.detachAppender(appender);
      appender.stop();
      logger.setLevel(originalLevel);
    } finally {
      FaultInjectingInterceptor.reset();
      fixture.cleanup();
    }
  }

  @Test
  @DisplayName("AC-9b: 중간 항목 flush 장애 시 실패 항목은 원상태, 앞·뒤 정상 항목은 커밋, ERROR 로그·요약 집계 기록")
  void expireHolds_itemFailure_isolatedAndContinues() {
    LocalDateTime expiresAt = nowSeconds().plusMinutes(1);
    Member member = fixture.member();
    // 생성 순서 = id 오름차순 = 처리 순서: before → failing → after
    SeatRef seatBefore = fixture.seat(ShowtimeSeatStatus.HELD);
    Long holdBefore = fixture.hold(seatBefore, member, HoldStatus.ACTIVE, expiresAt);
    SeatRef seatFailing = fixture.seat(ShowtimeSeatStatus.HELD);
    Long holdFailing = fixture.hold(seatFailing, member, HoldStatus.ACTIVE, expiresAt);
    SeatRef seatAfter = fixture.seat(ShowtimeSeatStatus.HELD);
    Long holdAfter = fixture.hold(seatAfter, member, HoldStatus.ACTIVE, expiresAt);
    LocalDateTime now = expiresAt.plusSeconds(1);
    fixture.requireExpirationCandidates(now, Set.of(holdBefore, holdFailing, holdAfter));

    FaultInjectingInterceptor.failFlushOfShowtimeSeat(seatFailing.showtimeSeatId());

    assertThatCode(() -> holdExpirationService.expireHolds(now)).doesNotThrowAnyException();

    assertThat(FaultInjectingInterceptor.injectedCount()).as("fault injected exactly once").isEqualTo(1);
    assertThat(fixture.holdStatus(holdBefore)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seatBefore)).isEqualTo("AVAILABLE");
    assertThat(fixture.holdStatus(holdFailing)).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(seatFailing)).isEqualTo("HELD");
    assertThat(fixture.holdStatus(holdAfter)).isEqualTo("EXPIRED");
    assertThat(fixture.seatStatus(seatAfter)).isEqualTo("AVAILABLE");

    List<ILoggingEvent> events = List.copyOf(appender.list);
    assertThat(events)
      .as("ERROR log for failed item holdId=%s", holdFailing)
      .anySatisfy(e -> {
        assertThat(e.getLevel()).isEqualTo(Level.ERROR);
        assertThat(e.getFormattedMessage()).containsPattern(holdIdPattern(holdFailing));
      });
    assertThat(events)
      .as("no ERROR log for successful items")
      .noneSatisfy(e -> {
        assertThat(e.getLevel()).isEqualTo(Level.ERROR);
        assertThat(e.getFormattedMessage())
          .containsPattern(holdIdPattern(holdBefore) + "|" + holdIdPattern(holdAfter));
      });
    assertThat(events)
      .as("summary log with expired=2 and failed=1 in one event")
      .anySatisfy(e -> assertThat(e.getFormattedMessage())
        .containsPattern("expired=2(\\D|$)")
        .containsPattern("failed=1(\\D|$)"));
  }

  /**
   * 다른 id의 접두사와 혼동하지 않도록 숫자 경계를 둔 holdId 패턴
   */
  private static String holdIdPattern(Long holdId) {
    return "holdId=" + holdId + "(\\D|$)";
  }
}
