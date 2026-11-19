package com.pil97.ticketing.payment.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.pil97.ticketing.common.exception.GlobalExceptionHandler;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.infra.idempotency.IdempotencyRedisRepository;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-028 (#119) S2: 결제 트랜잭션 시작 시 Hikari pool 고갈에 의한 커넥션 획득 실패 (Test Contract 개정 0, AC-S2-1~3, H-S2)
 *
 * <p>격리 MariaDB 컨테이너와 이 클래스 전용 Hikari pool(maximum-pool-size=2, connection-timeout=250ms)을 사용한다.
 * 테스트 스레드가 pool의 커넥션 2개를 모두 점유해 실제 획득 timeout을 유발한다. 응답 계약은 바꾸지 않는다(500 COMMON-007).
 *
 * <p>격리 근거: 이 클래스만 격리 DB에 쓴다. HOLD 만료·대기열 스케줄러는 SchedulerExclusionConfig로 제외되고,
 * Outbox relay는 test profile의 outbox.relay.enabled=false로 등록되지 않는다.
 * Outbox 신규 행은 격리 DB의 전체 outbox_event id 집합을 요청 전(B)·점유 해제 후(A)·복구 요청 후(A')로 비교해 판정한다.
 *
 * <p>요청은 Member가 아닌 principal로 인증하고 Authorization 헤더를 보내지 않는다:
 * JWT 필터의 DB 조회와 Rate Limit(회원 기준)을 건너뛰어, 요청의 첫 커넥션 획득 지점이 결제 트랜잭션 시작이 된다.
 *
 * <p>H-S2 (위반 시 HarnessException, RED·GREEN 판정에서 제외):
 * <ol>
 *   <li>요청 직전 HikariPoolMXBean active=2·idle=0 (pool 고갈은 예외 타입이 아니라 이 상태로 확정)</li>
 *   <li>IdempotencyRedisRepository.find 호출 0회 (spy 관찰)</li>
 *   <li>GlobalExceptionHandler가 로그로 남긴 예외가 CannotCreateTransactionException이고 원인 체인에 SQLTransientConnectionException</li>
 *   <li>fixture 정리 전 active=0</li>
 * </ol>
 * 커넥션 점유는 try/finally와 @AfterEach에서 반드시 해제한다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "spring.datasource.hikari.maximum-pool-size=2",
  "spring.datasource.hikari.minimum-idle=2",
  "spring.datasource.hikari.connection-timeout=250"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class})
@DirtiesContext
class PaymentConnectionAcquisitionFailureIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";
  private static final int POOL_SIZE = 2;

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationContext context;
  @MockitoSpyBean private IdempotencyRedisRepository idempotencyRedisRepository;

  private final List<Connection> occupied = new ArrayList<>();
  private Logger handlerLogger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    assertThat(outbox.currentDatabase()).as("격리 DB 사용").isEqualTo(IsolatedMariaDb.DATABASE);
    if (!(dataSource instanceof HikariDataSource)) {
      throw HarnessException.precondition("DataSource is not HikariDataSource: " + dataSource.getClass());
    }

    handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    originalLevel = handlerLogger.getLevel();
    handlerLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    handlerLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    try {
      releaseOccupied();
      handlerLogger.detachAppender(appender);
      appender.stop();
      handlerLogger.setLevel(originalLevel);
      // H-S2(4): fixture 정리 전 점유 해제 확인
      int active = pool().getActiveConnections();
      if (active != 0) {
        throw HarnessException.harness("active connections before fixture cleanup: " + active);
      }
    } finally {
      outbox.cleanup();
      fixture.cleanup();
    }
  }

  @Test
  @DisplayName("AC-S2-1~3 pool 고갈로 결제 트랜잭션 시작 실패: 500 COMMON-007, 점유 해제 후 DB·Redis 무변경(Outbox A == B), 복구 후 같은 키 재요청 201·새 결제 ID의 Outbox A'−B 1행")
  void acS2_connectionAcquisitionFailure_keeps500AndNoChange_thenRecovers() throws Exception {
    // 점유 전: fixture 생성과 Outbox id 집합 B
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    outbox.trackReservation(reservationId);
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    Set<Long> before = outboxIds();

    // 점유 중 요청
    MvcResult failed;
    long elapsedMs;
    int activeAtRequest;
    int idleAtRequest;
    try {
      occupyAll();
      activeAtRequest = pool().getActiveConnections();
      idleAtRequest = pool().getIdleConnections();
      long start = System.nanoTime();
      failed = payRequest(reservationId, key);
      elapsedMs = (System.nanoTime() - start) / 1_000_000;
    } finally {
      releaseOccupied();
    }
    System.out.println("[TASK-028 O-S2] response elapsed(ms)=" + elapsedMs + " (observation only)");

    // H-S2(1)~(3)
    if (activeAtRequest != POOL_SIZE || idleAtRequest != 0) {
      throw HarnessException.harness(
        "pool not exhausted at request: active=" + activeAtRequest + ", idle=" + idleAtRequest);
    }
    long findCalls = Mockito.mockingDetails(idempotencyRedisRepository).getInvocations().stream()
      .filter(i -> i.getMethod().getName().equals("find"))
      .count();
    if (findCalls != 0) {
      throw HarnessException.harness("IdempotencyRedisRepository.find called " + findCalls + " times (expected 0)");
    }
    requireTransactionStartFailureLogged();
    // H-S2(4) 중 요청 직후 확인 (tearDown에서 다시 확인)
    if (pool().getActiveConnections() != 0) {
      throw HarnessException.harness("active connections after release: " + pool().getActiveConnections());
    }

    // AC-S2-1 실패 응답
    String failedBody = failed.getResponse().getContentAsString();
    assertThat(failed.getResponse().getStatus()).as("실패 응답 HTTP 상태").isEqualTo(500);
    assertThat((Boolean) JsonPath.read(failedBody, "$.success")).as("success").isFalse();
    assertThat((String) JsonPath.read(failedBody, "$.error.code")).as("error.code").isEqualTo("COMMON-007");
    assertThat((String) JsonPath.read(failedBody, "$.error.path")).as("error.path").isEqualTo("/payments");
    String poolName = ((HikariDataSource) dataSource).getPoolName();
    assertThat(failedBody).as("응답 본문 내부 정보 노출")
      .doesNotContain(poolName)
      .doesNotContainIgnoringCase("select")
      .doesNotContainIgnoringCase("jdbc:")
      .doesNotContain(DB.getHost() + ":" + DB.getFirstMappedPort())
      .doesNotContain("Connection is not available");

    // AC-S2-2 점유 해제 후 별도 조회: DB·Redis 무변경
    assertThat(fixture.payments(reservationId)).as("대상 예약의 결제 행").isEmpty();
    assertThat(fixture.reservationStatus(reservationId)).as("예약 상태").isEqualTo("PENDING");
    assertThat(fixture.holdStatus(holdId)).as("HOLD 상태").isEqualTo("ACTIVE");
    assertThat(fixture.seatStatus(seat)).as("좌석 상태").isEqualTo("HELD");
    Set<Long> after = outboxIds();
    assertThat(after).as("outbox_event id 집합 A == B").isEqualTo(before);
    assertThat(outboxIdsOfReservationPayload(reservationId))
      .as("보조: payload reservationId가 대상 예약인 Outbox 행").isEmpty();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).as("결과 키").isFalse();
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":lock:" + key)).as("락 키").isFalse();

    // AC-S2-3 복구 후 같은 키 재요청
    MvcResult recovered = payRequest(reservationId, key);

    assertThat(recovered.getResponse().getStatus()).as("복구 후 HTTP 상태").isEqualTo(201);
    long paymentId = ((Number) JsonPath.read(recovered.getResponse().getContentAsString(), "$.data.paymentId"))
      .longValue();
    List<Map<String, Object>> payments = fixture.payments(reservationId);
    assertThat(payments).as("대상 예약의 결제 행").hasSize(1);
    assertThat(((Number) payments.get(0).get("id")).longValue()).as("결제 ID = 응답 paymentId").isEqualTo(paymentId);
    assertThat(payments.get(0).get("status")).as("결제 상태").isEqualTo("SUCCESS");
    assertThat(fixture.reservationStatus(reservationId)).as("예약 상태").isEqualTo("CONFIRMED");
    Set<Long> recoveredIds = outboxIds();
    Set<Long> added = new HashSet<>(recoveredIds);
    added.removeAll(before);
    assertThat(added).as("A' − B").hasSize(1);
    Map<String, Object> row = jdbcTemplate.queryForMap(
      "select event_type, aggregate_id, json_value(payload, '$.reservationId') as payload_reservation_id "
        + "from outbox_event where id = ?", added.iterator().next());
    assertThat(row.get("event_type")).as("event_type").isEqualTo("PAYMENT_SUCCEEDED");
    assertThat(((Number) row.get("aggregate_id")).longValue()).as("aggregate_id = 새 결제 ID").isEqualTo(paymentId);
    assertThat(String.valueOf(row.get("payload_reservation_id")))
      .as("payload reservationId = 대상 예약").isEqualTo(String.valueOf(reservationId));
    assertThat(fixture.redisKeyExists(PAYMENT_PREFIX + ":" + key)).as("복구 후 결과 키").isTrue();
  }

  // ===================== helpers =====================

  private MvcResult payRequest(Long reservationId, String key) throws Exception {
    return mockMvc.perform(post("/payments")
        .with(authentication(new UsernamePasswordAuthenticationToken(
          "t028-http", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reservationId\":" + reservationId + ",\"amount\":150000,\"forceFailure\":false}"))
      .andReturn();
  }

  private HikariPoolMXBean pool() {
    HikariPoolMXBean bean = ((HikariDataSource) dataSource).getHikariPoolMXBean();
    if (bean == null) {
      throw HarnessException.precondition("Hikari pool not started");
    }
    return bean;
  }

  private void occupyAll() throws SQLException {
    for (int i = 0; i < POOL_SIZE; i++) {
      occupied.add(dataSource.getConnection());
    }
  }

  private void releaseOccupied() {
    List<SQLException> failures = new ArrayList<>();
    for (Connection connection : occupied) {
      try {
        connection.close();
      } catch (SQLException e) {
        failures.add(e);
      }
    }
    occupied.clear();
    if (!failures.isEmpty()) {
      throw HarnessException.harness("failed to release occupied connections: " + failures);
    }
  }

  private Set<Long> outboxIds() {
    return new HashSet<>(jdbcTemplate.queryForList("select id from outbox_event", Long.class));
  }

  private List<Long> outboxIdsOfReservationPayload(Long reservationId) {
    return jdbcTemplate.queryForList(
      "select id from outbox_event where json_value(payload, '$.reservationId') = ?",
      Long.class, String.valueOf(reservationId));
  }

  /**
   * H-S2(3): GlobalExceptionHandler가 남긴 ERROR의 예외가 CannotCreateTransactionException이고
   * 원인 체인에 SQLTransientConnectionException이 있어야 한다
   */
  private void requireTransactionStartFailureLogged() {
    List<ILoggingEvent> errors = List.copyOf(appender.list).stream()
      .filter(e -> e.getLevel() == Level.ERROR && e.getThrowableProxy() != null)
      .toList();
    if (errors.size() != 1) {
      throw HarnessException.harness("GlobalExceptionHandler ERROR with throwable count=" + errors.size());
    }
    IThrowableProxy top = errors.get(0).getThrowableProxy();
    if (!CannotCreateTransactionException.class.getName().equals(top.getClassName())) {
      throw HarnessException.harness("unexpected failure type at transaction start: " + top.getClassName());
    }
    for (IThrowableProxy cause = top.getCause(); cause != null; cause = cause.getCause()) {
      if (SQLTransientConnectionException.class.getName().equals(cause.getClassName())) {
        return;
      }
    }
    throw HarnessException.harness("SQLTransientConnectionException not in cause chain of " + top.getClassName());
  }
}
