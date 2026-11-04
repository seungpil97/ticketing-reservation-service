package com.pil97.ticketing.payment.api;

import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.consistency.SnapshotConflictTrigger;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.util.List;
import java.util.regex.Pattern;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * TASK-062 (#104) 결제 HTTP 경로의 잔여 1020 응답 계약 (RED_REQUIRED)
 *
 * <p>경로: 실제 MariaDB 1020 → Hibernate 변환 → JPA commit 경계 → 예외 변환기 → 트랜잭션 종료(rollback, afterCompletion)
 * → GlobalExceptionHandler → HTTP 409 COMMON-008
 *
 * <p>증거 범위
 * <ul>
 *   <li>1020 발생 지점은 테스트 전용 SnapshotConflictTrigger가 결제 트랜잭션의 예약 flush 시점에 인위적으로 만든다.
 *       오류 자체는 실제 DB가 그 결제 트랜잭션에서 낸 것이다 (관찰 이벤트로 확인, 없으면 Invalid)</li>
 *   <li>결제 업무 코드가 스스로 1020을 일으킨다는 증거는 아니다</li>
 *   <li>handler 매핑만의 증거는 GlobalExceptionHandlerConflictTest (mock), 변환 규칙은 SnapshotConflictExceptionTranslatorTest</li>
 * </ul>
 * 경쟁자 커밋은 trigger 내부 worker의 autocommit 갱신이며 gate로 취소·rollback되지 않는다. 종료 확인 실패 시 격리로 보호한다.
 * 인증은 Member가 아닌 principal로 두어 Rate Limit(회원 기준) 집계 key를 만들지 않는다.
 */
@SpringBootTest(properties =
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.consistency.SnapshotConflictTrigger")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, SqlObserverConfig.class})
class PaymentConflictHttpIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";
  private static final Pattern PROBE_LOCK = Pattern.compile("from showtime_seat where id = \\? for update");

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private MockMvc mockMvc;
  @Autowired private ConsistencyFixture fixture;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void setUp() {
    // 종료 미확인 worker가 격리 중이면 아무것도 지우거나 덮어쓰지 않고 HarnessException → 시나리오 미시작
    SqlObserver.requireNoIsolation();
    SnapshotConflictTrigger.reset();
    SqlObserver.clear();
    SnapshotConflictTrigger.bind(dataSource, entityManagerFactory.unwrap(SessionFactoryImplementor.class));
  }

  @AfterEach
  void tearDown() {
    // 정리 순서 - 무조건 finally로 fixture를 정리하지 않는다
    // (1) trigger 경쟁 worker 종료 요청·확인. 미확인 시 SqlObserver 격리 목록으로 옮겨지고 HARNESS 예외 → 이후 단계 미수행
    // (2) 공통 격리 확인 - 이전 테스트의 격리가 남아 있으면(setUp 실패 경로 포함) HARNESS 예외 → 이후 단계 미수행
    // (3) trigger reset, 관찰 정리 - (1)(2) 통과 시에만
    // (4) fixture 정리 - 살아 있는 worker가 없을 때만 (늦은 autocommit 갱신이 정리 후 행에 섞이지 않게)
    SnapshotConflictTrigger.terminateCompetitors();
    SqlObserver.requireNoIsolation();
    SnapshotConflictTrigger.reset();
    SqlObserver.clear();
    fixture.cleanup();
  }

  @Test
  @DisplayName("결제 트랜잭션 flush 중 실제 DB 1020 → HTTP 409 COMMON-008, 기존 오류 형식, DB rollback, Redis 결과·lock 정리, 경쟁자 커밋 보존")
  void pay_dbSnapshotConflict_returns409Common008AndCleansUp() throws Exception {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    SeatRef probe = fixture.seat(ShowtimeSeatStatus.AVAILABLE);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    String requestThread = Thread.currentThread().getName();

    SnapshotConflictTrigger.arm(reservationId, probe.showtimeSeatId(),
      () -> fixture.setSeatStatus(probe, ShowtimeSeatStatus.RESERVED));

    MvcResult result = mockMvc.perform(post("/payments")
        .with(authentication(new UsernamePasswordAuthenticationToken(
          "t062-http", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reservationId\":" + reservationId + ",\"amount\":150000,\"forceFailure\":false}"))
      .andReturn();

    // 사전조건: trigger가 정확히 1회 동작했고, 실제 DB가 이 요청 thread의 probe 잠금에서 1020을 냈어야 한다
    if (SnapshotConflictTrigger.triggerFailure() != null) {
      throw HarnessException.harness("trigger failed: " + SnapshotConflictTrigger.triggerFailure());
    }
    if (SnapshotConflictTrigger.firedCount() != 1) {
      throw HarnessException.harness("trigger fired " + SnapshotConflictTrigger.firedCount() + " times (expected 1)");
    }
    List<SqlObserver.Event> conflicts = SqlObserver.snapshotConflicts(requestThread).stream()
      .filter(e -> PROBE_LOCK.matcher(e.sql()).find())
      .toList();
    if (conflicts.isEmpty()) {
      throw HarnessException.precondition("DB did not raise 1020 on probe lock in request thread " + requestThread);
    }
    SqlObserver.requireSessionDiagnostics(requestThread);

    // HTTP 응답 (트랜잭션 경계 밖)
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    jsonPath("$.success").value(false).match(result);
    jsonPath("$.data").doesNotExist().match(result);
    jsonPath("$.error.code").value("COMMON-008").match(result);
    jsonPath("$.error.path").value("/payments").match(result);
    jsonPath("$.timestamp").exists().match(result);

    // DB rollback (guard: 현행에서도 DB가 트랜잭션 전체를 rollback한다)
    assertThat(fixture.payments(reservationId)).isEmpty();
    assertThat(fixture.reservationStatus(reservationId)).isEqualTo("PENDING");
    assertThat(fixture.holdStatus(holdId)).isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(seat)).isEqualTo("HELD");
    assertThat(fixture.seatStatus(probe)).as("competitor commit preserved").isEqualTo("RESERVED");

    // Redis 결과 캐시·멱등성 lock 정리 (guard: afterCommit 미실행, afterCompletion에서 lock 해제)
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).isFalse();
  }
}
