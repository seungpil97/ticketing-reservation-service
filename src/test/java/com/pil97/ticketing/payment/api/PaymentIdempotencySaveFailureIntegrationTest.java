package com.pil97.ticketing.payment.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.application.PaymentService;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-028 (#119) S1: 결제 DB 커밋 후 Redis 멱등성 결과 SET 실패 (Test Contract 개정 0, AC-S1-1~7, H-S1)
 *
 * <p>공유 test MariaDB·실제 Redis를 사용한다. 실패는 afterCommit에서 호출되는 결과 키
 * {@code idempotency:payment:{key}}의 SET 직전에만 RedisConnectionFailureException으로 1회 주입한다.
 * 그 밖의 Redis 호출(결과 GET, 락 SET NX·DELETE 등)은 실제 Redis로 그대로 간다.
 *
 * <p>H-S1 (위반 시 HarnessException, RED·GREEN 판정에서 제외):
 * <ol>
 *   <li>결과 SET 직전 주입이 대상 결과 키에서 정확히 1회만 발생</li>
 *   <li>주입 직전 별도 JDBC 커넥션(DataSource에서 직접 획득, 요청 트랜잭션 커넥션 아님)으로 대상 결제 행이 SUCCESS로 보임</li>
 *   <li>주입 직전 락 키 존재</li>
 * </ol>
 *
 * <p>요청은 Member가 아닌 principal로 인증하고 Authorization 헤더를 보내지 않는다:
 * JWT 필터의 DB·Redis 조회와 Rate Limit(회원 기준)을 건너뛴다.
 *
 * <p>AC-S1-1(첫 응답)과 AC-S1-4(ERROR 로그)는 서로 다른 테스트 메서드로 판정해 한쪽 실패가 다른 쪽 관찰을 막지 않게 한다.
 * 락 존재(AC-S1-5)는 PEXPIRE 60초 연장, 락 부재(AC-S1-6)는 DELETE로 재현한다 (실제 TTL 경과에 의존하지 않음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class})
class PaymentIdempotencySaveFailureIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";
  private static final String LOG_MARKER = "idempotency result save failed";
  private static final long LOCK_TTL_MS = 10_000L;

  @Autowired private MockMvc mockMvc;
  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationContext context;
  @MockitoSpyBean private StringRedisTemplate stringRedisTemplate;

  private ResultSetFault fault;
  private Logger paymentLogger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);

    // 결과 키 SET에만 주입하는 값 연산: 그 밖의 호출은 실제 ValueOperations로 위임
    ValueOperations<String, String> real = stringRedisTemplate.opsForValue();
    ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class, AdditionalAnswers.delegatesTo(real));
    fault = new ResultSetFault(real, dataSource, stringRedisTemplate);
    doAnswer(invocation -> {
      fault.onSet(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
      return null;
    }).when(ops).set(anyString(), anyString(), any(Duration.class));
    doReturn(ops).when(stringRedisTemplate).opsForValue();

    paymentLogger = (Logger) LoggerFactory.getLogger(PaymentService.class);
    originalLevel = paymentLogger.getLevel();
    paymentLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    paymentLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    try {
      paymentLogger.detachAppender(appender);
      appender.stop();
      paymentLogger.setLevel(originalLevel);
    } finally {
      outbox.cleanup();
      fixture.cleanup();
    }
  }

  // ===================== AC-S1-1 첫 응답 =====================

  @Test
  @DisplayName("AC-S1-1 커밋 후 결과 SET 실패: 첫 응답 201, data.paymentId·status가 별도 조회한 유일한 결제 행(SUCCESS)과 일치")
  void acS1_1_firstResponseIs201MatchingCommittedPayment() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    MvcResult first = firstRequestWithInjection(p, key);

    assertThat(first.getResponse().getStatus()).as("첫 응답 HTTP 상태").isEqualTo(201);
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).as("대상 예약의 결제 행").hasSize(1);
    Map<String, Object> row = payments.get(0);
    String body = first.getResponse().getContentAsString();
    assertThat(((Number) JsonPath.read(body, "$.data.paymentId")).longValue())
      .as("data.paymentId").isEqualTo(((Number) row.get("id")).longValue());
    assertThat((String) JsonPath.read(body, "$.data.status")).as("data.status").isEqualTo(row.get("status"));
    assertThat(row.get("status")).as("결제 행 상태").isEqualTo("SUCCESS");
  }

  // ===================== AC-S1-2·3 커밋 상태와 Redis 키 =====================

  @Test
  @DisplayName("AC-S1-2·3 커밋 후 결과 SET 실패 직후: 결제 SUCCESS·예약 CONFIRMED·좌석 RESERVED·HOLD CONFIRMED·Outbox 1행 PENDING, 결과 키 없음·락 키 0<PTTL≤10000ms")
  void acS1_2_3_committedStateAndRedisKeysAfterFailure() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    firstRequestWithInjection(p, key);

    // AC-S1-2
    Long paymentId = singlePaymentId(p);
    assertThat(fixture.paymentRow(paymentId).get("status")).as("결제 상태").isEqualTo("SUCCESS");
    assertThat(fixture.reservationStatus(p.reservationId())).as("예약 상태").isEqualTo("CONFIRMED");
    assertThat(fixture.seatStatus(p.seat())).as("좌석 상태").isEqualTo("RESERVED");
    assertThat(fixture.holdStatus(p.holdId())).as("HOLD 상태").isEqualTo("CONFIRMED");
    List<Map<String, Object>> outboxRows = outbox.outboxRowsOfPayment(paymentId);
    assertThat(outboxRows).as("결제의 Outbox(PAYMENT_SUCCEEDED) 행").hasSize(1);
    assertThat(outboxRows.get(0).get("status")).as("Outbox 상태").isEqualTo("PENDING");

    // AC-S1-3
    assertThat(fixture.redisKeyExists(resultKey(key))).as("결과 키").isFalse();
    assertThat(fixture.redisKeyExists(lockKey(key))).as("락 키").isTrue();
    Long pttl = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    assertThat(pttl).as("락 키 PTTL(ms)").isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(LOCK_TTL_MS);
  }

  // ===================== AC-S1-4 저장 실패 로그 =====================

  @Test
  @DisplayName("AC-S1-4 커밋 후 결과 SET 실패: PaymentService ERROR 1건(고정 문구·prefix·paymentId·reservationId·errorType), 멱등키 원문·응답 본문·금액 없음")
  void acS1_4_saveFailureLoggedOnceWithoutSensitiveValues() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    MvcResult first = firstRequestWithInjection(p, key);

    Long paymentId = singlePaymentId(p);
    List<ILoggingEvent> errors = List.copyOf(appender.list).stream()
      .filter(e -> e.getLevel() == Level.ERROR)
      .toList();
    assertThat(errors).as("PaymentService ERROR 로그").hasSize(1);
    ILoggingEvent event = errors.get(0);
    String message = event.getFormattedMessage();
    assertThat(message)
      .as("ERROR 로그 포맷 메시지")
      .contains(LOG_MARKER)
      .contains("prefix=" + PAYMENT_PREFIX)
      .contains("paymentId=" + paymentId)
      .contains("reservationId=" + p.reservationId())
      .contains("errorType=" + RedisConnectionFailureException.class.getSimpleName());

    List<String> logged = new ArrayList<>();
    logged.add(message);
    if (event.getArgumentArray() != null) {
      Arrays.stream(event.getArgumentArray()).map(String::valueOf).forEach(logged::add);
    }
    String responseBody = first.getResponse().getContentAsString();
    assertThat(logged).as("로그 메시지·인자").allSatisfy(text -> {
      assertThat(text).as("멱등키 원문 없음").doesNotContain(key);
      assertThat(text).as("금액 없음").doesNotContain("150000");
      assertThat(text).as("응답 본문 없음").doesNotContain("\"paymentId\"");
      if (!responseBody.isBlank()) {
        assertThat(text).as("응답 본문 없음").doesNotContain(responseBody);
      }
    });
  }

  // ===================== AC-S1-5 락 존재 재현 후 같은 키 재요청 =====================

  @Test
  @DisplayName("AC-S1-5 Redis 복구·결과 키 없음·락 존재 재현(PEXPIRE 60s) 후 같은 키 재요청: 409 IDEMPOTENCY-001, 결제·Outbox 1행 유지, 결과 키 없음·락 키 있음")
  void acS1_5_sameKeyRetryWhileLockPresent_returns409InProgress() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    firstRequestWithInjection(p, key);
    Long paymentId = singlePaymentId(p);
    requireRecoveredWithoutResultKey(key);
    if (!fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.precondition("lock key absent before lock-present reproduction");
    }
    Long originalPttl = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    System.out.println("[TASK-028 AC-S1-5] lock-present reproduction: original PTTL(ms)=" + originalPttl
      + " -> PEXPIRE 60000");
    if (!Boolean.TRUE.equals(stringRedisTemplate.expire(lockKey(key), Duration.ofSeconds(60)))) {
      throw HarnessException.harness("PEXPIRE on lock key failed");
    }

    MvcResult retry = payRequest(p.reservationId(), key);

    assertThat(retry.getResponse().getStatus()).as("재요청 HTTP 상태").isEqualTo(409);
    assertThat((String) JsonPath.read(retry.getResponse().getContentAsString(), "$.error.code"))
      .as("재요청 오류 코드").isEqualTo("IDEMPOTENCY-001");
    assertOnePaymentAndOneOutbox(p, paymentId);
    assertThat(fixture.redisKeyExists(resultKey(key))).as("결과 키").isFalse();
    assertThat(fixture.redisKeyExists(lockKey(key))).as("락 키").isTrue();
  }

  // ===================== AC-S1-6·7 락 부재 재현 후 같은 키·다른 키 재요청 =====================

  @Test
  @DisplayName("AC-S1-6·7 Redis 복구·결과 키 없음·락 부재 재현(DELETE) 후 같은 키 재요청 409 PAYMENT-002, 이어서 다른 키 재요청 409 PAYMENT-002, 결제·Outbox 1행 유지, 키 없음")
  void acS1_6_7_retryWhileLockAbsent_returns409AlreadyProcessed() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);

    firstRequestWithInjection(p, key);
    Long paymentId = singlePaymentId(p);
    requireRecoveredWithoutResultKey(key);
    Long originalPttl = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    System.out.println("[TASK-028 AC-S1-6] lock-absent reproduction (TTL expiry substitute): original PTTL(ms)="
      + originalPttl + " -> DELETE");
    stringRedisTemplate.delete(lockKey(key));
    if (fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.harness("lock key still present after DELETE");
    }

    // AC-S1-6 같은 키
    MvcResult sameKey = payRequest(p.reservationId(), key);

    assertThat(sameKey.getResponse().getStatus()).as("같은 키 재요청 HTTP 상태").isEqualTo(409);
    assertThat((String) JsonPath.read(sameKey.getResponse().getContentAsString(), "$.error.code"))
      .as("같은 키 재요청 오류 코드").isEqualTo("PAYMENT-002");
    assertOnePaymentAndOneOutbox(p, paymentId);
    assertThat(fixture.redisKeyExists(resultKey(key))).as("같은 키 결과 키").isFalse();
    assertThat(fixture.redisKeyExists(lockKey(key))).as("같은 키 락 키").isFalse();

    // AC-S1-7 다른 키
    String key2 = fixture.idempotencyKey(PAYMENT_PREFIX);
    MvcResult otherKey = payRequest(p.reservationId(), key2);

    assertThat(otherKey.getResponse().getStatus()).as("다른 키 재요청 HTTP 상태").isEqualTo(409);
    assertThat((String) JsonPath.read(otherKey.getResponse().getContentAsString(), "$.error.code"))
      .as("다른 키 재요청 오류 코드").isEqualTo("PAYMENT-002");
    assertOnePaymentAndOneOutbox(p, paymentId);
    assertThat(fixture.redisKeyExists(resultKey(key2))).as("다른 키 결과 키").isFalse();
    assertThat(fixture.redisKeyExists(lockKey(key2))).as("다른 키 락 키").isFalse();
  }

  // ===================== helpers =====================

  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId) {
  }

  private Pending pending() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    outbox.trackReservation(reservationId);
    return new Pending(seat, member, holdId, reservationId);
  }

  /**
   * 주입을 무장하고 첫 요청을 보낸 뒤 H-S1을 확인한다. 응답·상태 단언은 호출자가 한다.
   */
  private MvcResult firstRequestWithInjection(Pending p, String key) throws Exception {
    if (fixture.redisKeyExists(resultKey(key)) || fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.precondition("idempotency keys exist before first request");
    }
    fault.arm(resultKey(key), lockKey(key), p.reservationId());
    MvcResult first = payRequest(p.reservationId(), key);
    fault.disarm();
    fault.requireHarness();
    return first;
  }

  private MvcResult payRequest(Long reservationId, String key) throws Exception {
    return mockMvc.perform(post("/payments")
        .with(authentication(new UsernamePasswordAuthenticationToken(
          "t028-http", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reservationId\":" + reservationId + ",\"amount\":150000,\"forceFailure\":false}"))
      .andReturn();
  }

  /**
   * 재요청 사전조건: 주입 해제(Redis 접근 복구)와 결과 키 부재
   */
  private void requireRecoveredWithoutResultKey(String key) {
    if (fault.isArmed()) {
      throw HarnessException.harness("injection still armed before retry");
    }
    if (fixture.redisKeyExists(resultKey(key))) {
      throw HarnessException.precondition("result key present before retry");
    }
  }

  /**
   * 결제 행이 정확히 1개일 때 그 ID (첫 요청 이후 상태 확인의 기준)
   */
  private Long singlePaymentId(Pending p) {
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).as("대상 예약의 결제 행").hasSize(1);
    return ((Number) payments.get(0).get("id")).longValue();
  }

  private void assertOnePaymentAndOneOutbox(Pending p, Long paymentId) {
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    assertThat(payments).as("대상 예약의 결제 행").hasSize(1);
    assertThat(((Number) payments.get(0).get("id")).longValue()).as("결제 ID 유지").isEqualTo(paymentId);
    assertThat(outbox.outboxRowsOfPayment(paymentId)).as("결제의 Outbox 행").hasSize(1);
    assertThat(outbox.outboxCountOfReservation(p.reservationId())).as("대상 예약의 Outbox 행").isEqualTo(1L);
  }

  private static String resultKey(String key) {
    return PAYMENT_PREFIX + ":" + key;
  }

  private static String lockKey(String key) {
    return PAYMENT_PREFIX + ":lock:" + key;
  }

  /**
   * 대상 결과 키 SET 1회 주입기 (H-S1 관찰 포함)
   * - 무장된 결과 키의 SET만 가로채고, 그 밖의 SET은 실제 ValueOperations로 위임한다
   * - 주입 직전 관찰: DataSource에서 직접 얻은 별도 커넥션(autocommit)으로 결제 행 상태, 실제 Redis로 락 키 존재
   */
  private static final class ResultSetFault {

    private final ValueOperations<String, String> real;
    private final DataSource dataSource;
    private final StringRedisTemplate redis;
    private final AtomicInteger injected = new AtomicInteger();

    private volatile String armedResultKey;
    private volatile String armedLockKey;
    private volatile Long reservationId;
    private volatile List<String> statusesAtInjection;
    private volatile Boolean lockPresentAtInjection;
    private volatile Throwable observationFailure;

    private ResultSetFault(ValueOperations<String, String> real, DataSource dataSource, StringRedisTemplate redis) {
      this.real = real;
      this.dataSource = dataSource;
      this.redis = redis;
    }

    void arm(String resultKey, String lockKey, Long reservationId) {
      injected.set(0);
      statusesAtInjection = null;
      lockPresentAtInjection = null;
      observationFailure = null;
      this.reservationId = reservationId;
      this.armedLockKey = lockKey;
      this.armedResultKey = resultKey;
    }

    void disarm() {
      armedResultKey = null;
    }

    boolean isArmed() {
      return armedResultKey != null;
    }

    void onSet(String key, String value, Duration timeout) {
      String target = armedResultKey;
      if (target == null || !target.equals(key)) {
        real.set(key, value, timeout);
        return;
      }
      armedResultKey = null;
      injected.incrementAndGet();
      try {
        statusesAtInjection = committedPaymentStatuses(reservationId);
        lockPresentAtInjection = Boolean.TRUE.equals(redis.hasKey(armedLockKey));
      } catch (RuntimeException | SQLException e) {
        observationFailure = e;
      }
      throw new RedisConnectionFailureException("TASK-028 injected result SET failure");
    }

    void requireHarness() {
      if (observationFailure != null) {
        throw HarnessException.harness("observation at injection failed", observationFailure);
      }
      if (injected.get() != 1) {
        throw HarnessException.harness("result SET injection fired " + injected.get() + " times (expected 1)");
      }
      if (!List.of("SUCCESS").equals(statusesAtInjection)) {
        throw HarnessException.harness(
          "committed payment not visible from separate connection at injection: " + statusesAtInjection);
      }
      if (!Boolean.TRUE.equals(lockPresentAtInjection)) {
        throw HarnessException.harness("lock key absent at injection");
      }
    }

    private List<String> committedPaymentStatuses(Long reservationId) throws SQLException {
      List<String> statuses = new ArrayList<>();
      try (Connection connection = dataSource.getConnection();
           PreparedStatement statement = connection.prepareStatement(
             "select status from payment where reservation_id = ? order by id")) {
        connection.setAutoCommit(true);
        statement.setLong(1, reservationId);
        try (ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            statuses.add(rs.getString(1));
          }
        }
      }
      return statuses;
    }
  }
}
