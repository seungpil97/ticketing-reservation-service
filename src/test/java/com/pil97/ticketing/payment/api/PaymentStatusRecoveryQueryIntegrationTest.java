package com.pil97.ticketing.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-043 (#123) S1 복구 조회: 결제 커밋 후 Redis 결과 SET 실패 뒤 예약 소유자의 결제 상태 조회 (AC-1, AC-2, H-Q1, H-Q2)
 *
 * <p>공유 test MariaDB·실제 Redis를 사용한다. 실패는 afterCommit에서 호출되는 결과 키
 * {@code idempotency:payment:{key}}의 SET 직전에만 RedisConnectionFailureException으로 1회 주입한다.
 * 그 밖의 Redis 호출은 실제 Redis로 그대로 간다.
 *
 * <p>H-Q1 (AC-1·2, 위반 시 HarnessException, RED·GREEN 판정에서 제외):
 * <ol>
 *   <li>결과 SET 실패 주입이 대상 결과 키에서 정확히 1회 발생</li>
 *   <li>주입 직전 별도 JDBC 커넥션(DataSource에서 직접 획득)으로 대상 결제 행이 SUCCESS로 보임</li>
 *   <li>최초 POST 이후 대상 결과 키 없음</li>
 * </ol>
 *
 * <p>H-Q2 (AC-2): 락 존재 상태는 PEXPIRE 60초 연장, 락 부재 상태는 DELETE로 테스트가 조성한다.
 * 둘 다 테스트가 만든 상태이며 실제 TTL 만료를 관찰한 것이 아니다.
 *
 * <p>GET은 테스트가 결제 전에 보관한 reservationId로 호출한다. 최초 POST 201 본문은 paymentId·status 대조에만 사용한다.
 * paidAt의 기준은 DB다: 별도 조회한 해당 결제 행의 paid_at과 정확히 같아야 한다. 기존 POST 동작은 변경 대상이 아니다.
 * POST는 TASK-028과 같이 Member가 아닌 principal로 인증한다(Rate Limit 제외). GET은 예약 소유자 Member principal로 인증한다.
 *
 * <p>AC-R(기존 POST 회귀)은 이 클래스가 아니라 TASK-028 동결 테스트가 맡는다:
 * {@link PaymentIdempotencySaveFailureIntegrationTest} (5개), {@link PaymentConnectionAcquisitionFailureIntegrationTest} (1개).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class})
class PaymentStatusRecoveryQueryIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";
  private static final long LOCK_TTL_MS = 10_000L;
  private static final long EXTENDED_LOCK_TTL_MS = 60_000L;

  @Autowired private MockMvc mockMvc;
  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationContext context;
  @Autowired private ObjectMapper objectMapper;
  @MockitoSpyBean private StringRedisTemplate stringRedisTemplate;

  private ResultSetFault fault;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);

    // 결과 키 SET에만 주입하는 값 연산: 그 밖의 호출은 실제 ValueOperations로 위임
    ValueOperations<String, String> real = stringRedisTemplate.opsForValue();
    ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class, AdditionalAnswers.delegatesTo(real));
    fault = new ResultSetFault(real, dataSource);
    doAnswer(invocation -> {
      fault.onSet(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
      return null;
    }).when(ops).set(anyString(), anyString(), any(Duration.class));
    doReturn(ops).when(stringRedisTemplate).opsForValue();
  }

  @AfterEach
  void tearDown() {
    outbox.cleanup();
    fixture.cleanup();
  }

  // ===================== AC-1 S1 복구 조회 =====================

  @Test
  @DisplayName("AC-1 S1 장애 뒤 소유자가 결제 전부터 알던 reservationId로 GET: 200, paymentId·status(SUCCESS)는 최초 201과 같고 paidAt은 결제 행 paid_at과 같으며 대상 예약·결제·Outbox·키 무변경")
  void ac1_ownerQueriesAfterResultSaveFailure_returnsCommittedPayment() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    JsonNode firstData = firstRequestWithInjection(p, key);

    State before = state(p, key);
    MvcResult result = getPayment(p.reservationId(), p.member(), "AC-1");

    assertRecoveredPayment(result, firstData);
    // S1이 남긴 락은 최초 획득 후 10초 TTL로 자연 만료될 수 있으므로 AC-1에서는 락 존재 여부를 비교하지 않는다 (AC-2가 조성 상태로 검증)
    assertUnchanged(before, state(p, key), false);
  }

  // ===================== AC-2 락 상태 무관 =====================

  @Test
  @DisplayName("AC-2(a) S1 장애 뒤 락 존재 상태(PEXPIRE 60s 조성)에서 GET: AC-1과 같은 200, 결과 키 없음·락 키 유지, PTTL 연장 없음")
  void ac2a_lockPresent_returnsSameResultWithoutTouchingKeys() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    JsonNode firstData = firstRequestWithInjection(p, key);

    // H-Q2 (a): 테스트가 조성한 락 존재 상태
    if (!fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.precondition("lock key absent before lock-present reproduction");
    }
    Long originalPttl = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    System.out.println("[TASK-043 AC-2a] lock-present reproduction: original PTTL(ms)=" + originalPttl
      + " -> PEXPIRE " + EXTENDED_LOCK_TTL_MS);
    if (!Boolean.TRUE.equals(stringRedisTemplate.expire(lockKey(key), Duration.ofMillis(EXTENDED_LOCK_TTL_MS)))) {
      throw HarnessException.harness("PEXPIRE on lock key failed");
    }
    long pttlBefore = requireExpiringLock(key);
    if (pttlBefore <= LOCK_TTL_MS) {
      throw HarnessException.harness("lock PTTL not extended beyond original TTL: " + pttlBefore);
    }

    State before = state(p, key);
    MvcResult result = getPayment(p.reservationId(), p.member(), "AC-2a");

    assertRecoveredPayment(result, firstData);
    assertUnchanged(before, state(p, key), true);
    assertThat(fixture.redisKeyExists(lockKey(key))).as("조회 후 락 키 존재").isTrue();
    Long pttlAfter = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    // -2(키 없음)·-1(만료 없음)이 숫자 비교로 통과하지 않도록 양수 범위를 먼저 확인한다
    assertThat(pttlAfter).as("조회 후 락 PTTL(ms)").isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(pttlBefore);
  }

  @Test
  @DisplayName("AC-2(b) S1 장애 뒤 락 부재 상태(DELETE 조성)에서 GET: AC-1과 같은 200, 결과 키·락 키 모두 없음 유지")
  void ac2b_lockAbsent_returnsSameResultWithoutCreatingKeys() throws Exception {
    Pending p = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    JsonNode firstData = firstRequestWithInjection(p, key);

    // H-Q2 (b): 테스트가 조성한 락 부재 상태 (실제 TTL 만료 관찰 아님)
    Long originalPttl = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    System.out.println("[TASK-043 AC-2b] lock-absent reproduction (constructed by DELETE): original PTTL(ms)="
      + originalPttl + " -> DELETE");
    stringRedisTemplate.delete(lockKey(key));
    if (fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.harness("lock key still present after DELETE");
    }

    State before = state(p, key);
    MvcResult result = getPayment(p.reservationId(), p.member(), "AC-2b");

    assertRecoveredPayment(result, firstData);
    assertUnchanged(before, state(p, key), true);
    assertThat(fixture.redisKeyExists(lockKey(key))).as("조회 후 락 키 부재").isFalse();
  }

  // ===================== helpers =====================

  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId) {
  }

  /**
   * 대상 예약·결제·Outbox·키 상태 (단언 범위를 이 예약과 키로 한정)
   */
  private record State(String reservationStatus, List<Map<String, Object>> payments,
                       List<Map<String, Object>> outboxRows, long outboxCountOfReservation,
                       boolean resultKeyExists, boolean lockKeyExists) {
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
   * 주입을 무장하고 최초 POST를 보낸 뒤 H-Q1을 확인한다. 반환값은 최초 201 본문의 data (결과 대조 전용)
   */
  private JsonNode firstRequestWithInjection(Pending p, String key) throws Exception {
    if (fixture.redisKeyExists(resultKey(key)) || fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.precondition("idempotency keys exist before first request");
    }
    fault.arm(resultKey(key), p.reservationId());
    MvcResult first = mockMvc.perform(post("/payments")
        .with(authentication(new UsernamePasswordAuthenticationToken(
          "t043-http", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reservationId\":" + p.reservationId() + ",\"amount\":150000,\"forceFailure\":false}"))
      .andReturn();
    fault.disarm();
    fault.requireHarness();
    if (fixture.redisKeyExists(resultKey(key))) {
      throw HarnessException.harness("result key present after injected first request");
    }
    if (first.getResponse().getStatus() != 201) {
      throw HarnessException.precondition("first POST status " + first.getResponse().getStatus() + " (expected 201)");
    }
    return objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
  }

  private MvcResult getPayment(Long reservationId, Member principal, String label) throws Exception {
    MvcResult result = mockMvc.perform(get("/reservations/" + reservationId + "/payment")
        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()))))
      .andReturn();
    logDiagnostics(label, result);
    return result;
  }

  /**
   * RED 판정 보조: 상태·처리기·해결된 예외만 기록한다 (인증 정보는 기록하지 않음)
   */
  private static void logDiagnostics(String label, MvcResult result) {
    Exception resolved = result.getResolvedException();
    System.out.println("[TASK-043 " + label + "] status=" + result.getResponse().getStatus()
      + " handler=" + result.getHandler()
      + " resolvedException=" + (resolved == null ? "null" : resolved.getClass().getName() + ": " + resolved.getMessage()));
  }

  private void assertRecoveredPayment(MvcResult result, JsonNode firstData) throws Exception {
    assertThat(result.getResponse().getStatus()).as("GET HTTP 상태").isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("success").isBoolean() && body.path("success").booleanValue()).as("success=true").isTrue();
    JsonNode data = body.path("data");
    assertThat(data.isObject()).as("data 객체").isTrue();

    assertThat(data.path("paymentId").isIntegralNumber()).as("data.paymentId 숫자").isTrue();
    assertThat(data.path("paymentId").longValue()).as("data.paymentId = 최초 201 paymentId")
      .isEqualTo(firstData.path("paymentId").longValue());
    assertThat(data.path("status").asText()).as("data.status = 최초 201 status").isEqualTo(firstData.path("status").asText());
    assertThat(data.path("status").asText()).as("data.status").isEqualTo("SUCCESS");

    // paidAt: 조회 시각의 기준은 DB다. 별도 조회한 해당 결제 행의 paid_at과 같은 날짜·시각이어야 한다 (오차 허용 없음, 표기 차이는 파싱으로 흡수)
    assertThat(data.path("paidAt").isTextual()).as("data.paidAt 문자열").isTrue();
    LocalDateTime paidAt = LocalDateTime.parse(data.path("paidAt").asText());
    Map<String, Object> row = fixture.paymentRow(firstData.path("paymentId").longValue());
    assertThat(paidAt).as("data.paidAt = 결제 행 paid_at").isEqualTo(toLocalDateTime(row.get("paid_at")));
  }

  private void assertUnchanged(State before, State after, boolean compareLock) {
    assertThat(after.reservationStatus()).as("대상 예약 상태").isEqualTo("CONFIRMED").isEqualTo(before.reservationStatus());
    assertThat(after.payments()).as("대상 예약의 결제 행").hasSize(1).isEqualTo(before.payments());
    assertThat(after.payments().get(0).get("status")).as("결제 행 상태").isEqualTo("SUCCESS");
    assertThat(after.outboxRows()).as("결제의 Outbox 행").hasSize(1).isEqualTo(before.outboxRows());
    assertThat(after.outboxCountOfReservation()).as("대상 예약의 Outbox 행 수").isEqualTo(before.outboxCountOfReservation());
    assertThat(after.resultKeyExists()).as("결과 키 없음 유지").isFalse().isEqualTo(before.resultKeyExists());
    if (compareLock) {
      assertThat(after.lockKeyExists()).as("락 키 존재 여부 유지").isEqualTo(before.lockKeyExists());
    }
  }

  private State state(Pending p, String key) {
    List<Map<String, Object>> payments = fixture.payments(p.reservationId());
    List<Map<String, Object>> outboxRows = payments.size() == 1
      ? outbox.outboxRowsOfPayment(((Number) payments.get(0).get("id")).longValue())
      : List.of();
    return new State(
      fixture.reservationStatus(p.reservationId()),
      payments,
      outboxRows,
      outbox.outboxCountOfReservation(p.reservationId()),
      fixture.redisKeyExists(resultKey(key)),
      fixture.redisKeyExists(lockKey(key)));
  }

  /**
   * 락 키가 존재하고 만료가 설정돼 있을 때의 PTTL (-2 키 없음, -1 만료 없음은 harness 위반)
   */
  private long requireExpiringLock(String key) {
    Long pttl = stringRedisTemplate.getExpire(lockKey(key), TimeUnit.MILLISECONDS);
    if (pttl == null || pttl <= 0) {
      throw HarnessException.harness("lock key missing or without expiry: PTTL=" + pttl);
    }
    return pttl;
  }

  private static LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime;
    }
    throw new IllegalStateException("unexpected datetime column type: " + (value == null ? "null" : value.getClass()));
  }

  private static String resultKey(String key) {
    return PAYMENT_PREFIX + ":" + key;
  }

  private static String lockKey(String key) {
    return PAYMENT_PREFIX + ":lock:" + key;
  }

  /**
   * 대상 결과 키 SET 1회 주입기 (H-Q1 관찰 포함)
   * - 무장된 결과 키의 SET만 가로채고, 그 밖의 SET은 실제 ValueOperations로 위임한다
   * - 주입 직전 관찰: DataSource에서 직접 얻은 별도 커넥션(autocommit)으로 결제 행 상태
   */
  private static final class ResultSetFault {

    private final ValueOperations<String, String> real;
    private final DataSource dataSource;
    private final AtomicInteger injected = new AtomicInteger();

    private volatile String armedResultKey;
    private volatile Long reservationId;
    private volatile List<String> statusesAtInjection;
    private volatile Throwable observationFailure;

    private ResultSetFault(ValueOperations<String, String> real, DataSource dataSource) {
      this.real = real;
      this.dataSource = dataSource;
    }

    void arm(String resultKey, Long reservationId) {
      injected.set(0);
      statusesAtInjection = null;
      observationFailure = null;
      this.reservationId = reservationId;
      this.armedResultKey = resultKey;
    }

    void disarm() {
      armedResultKey = null;
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
      } catch (RuntimeException | SQLException e) {
        observationFailure = e;
      }
      throw new RedisConnectionFailureException("TASK-043 injected result SET failure");
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
