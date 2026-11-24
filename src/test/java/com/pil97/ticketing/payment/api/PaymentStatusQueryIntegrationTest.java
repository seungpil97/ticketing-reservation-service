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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-043 (#123) 예약 소유자 결제 상태 조회: 접근 제어·오류 응답·현재 상태 반환 (AC-3~AC-8)
 *
 * <p>공유 test MariaDB·실제 Redis를 사용한다. 각 테스트는 자신이 만든 회원·예약·결제만 쓰고,
 * 단언은 그 예약·결제·Outbox 행과 그 결제에 쓴 멱등 키로 한정한다.
 *
 * <p>오류 응답의 {@code data}·{@code error.details}는 키가 있고 값이 null인 형태를 JSON 트리로 구분해 단언한다
 * (WORK-9 관찰: 도메인 오류·미인증 모두 키 있음·null).
 *
 * <p>POST /payments는 TASK-028과 같이 Member가 아닌 principal로 인증한다(Rate Limit 제외).
 * GET과 환불은 Member principal로 인증한다. AC-8만 인증 정보 없이 요청한다.
 *
 * <p>AC-R(기존 POST 회귀)은 TASK-028 동결 테스트가 맡는다:
 * {@link PaymentIdempotencySaveFailureIntegrationTest} (5개), {@link PaymentConnectionAcquisitionFailureIntegrationTest} (1개).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class})
class PaymentStatusQueryIntegrationTest {

  private static final String PAYMENT_PREFIX = "idempotency:payment";

  @Autowired private MockMvc mockMvc;
  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private ApplicationContext context;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
  }

  @AfterEach
  void tearDown() {
    outbox.cleanup();
    fixture.cleanup();
  }

  // ===================== AC-3 다른 회원 =====================

  @Test
  @DisplayName("AC-3 다른 회원이 결제 완료 예약 GET: 404 RESERVATION-001, 결제 데이터 미반환, 없는 예약 응답과 구별 불가, 대상 예약·결제·Outbox·키 무변경")
  void ac3_otherMember_isIndistinguishableFromMissingReservation() throws Exception {
    Pending a = pending();
    Member other = fixture.member();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    pay(a.reservationId(), key, false);
    Long paymentId = requireSinglePayment(a.reservationId(), "SUCCESS");
    if (!fixture.redisKeyExists(resultKey(key)) || fixture.redisKeyExists(lockKey(key))) {
      throw HarnessException.precondition("expected result key present and lock key absent after successful payment");
    }
    String resultValueBefore = stringRedisTemplate.opsForValue().get(resultKey(key));
    long resultPttlBefore = requireExpiringKey(resultKey(key));
    Snapshot before = snapshot(a.reservationId());
    Long missingId = missingReservationId();

    String otherUri = uri(a.reservationId());
    String missingUri = uri(missingId);
    MvcResult otherResult = getPayment(a.reservationId(), other, "AC-3 other");
    MvcResult missingResult = getPayment(missingId, other, "AC-3 missing");

    JsonNode otherBody = assertErrorResponse(otherResult, 404, "RESERVATION-001", "Reservation not found", otherUri);
    JsonNode missingBody = assertErrorResponse(missingResult, 404, "RESERVATION-001", "Reservation not found", missingUri);

    // 고정 값 비교: 상태·success·data·error.code·error.message·error.details와 두 응답의 키 집합 상호 비교 (path·timestamp 값 제외)
    assertThat(otherResult.getResponse().getStatus()).as("HTTP 상태 동일").isEqualTo(missingResult.getResponse().getStatus());
    assertThat(otherBody.get("success")).as("success 동일").isEqualTo(missingBody.get("success"));
    assertThat(otherBody.get("data")).as("data 동일").isEqualTo(missingBody.get("data"));
    assertThat(otherBody.get("error").get("code")).as("error.code 동일").isEqualTo(missingBody.get("error").get("code"));
    assertThat(otherBody.get("error").get("message")).as("error.message 동일")
      .isEqualTo(missingBody.get("error").get("message"));
    assertThat(otherBody.get("error").get("details")).as("error.details 동일")
      .isEqualTo(missingBody.get("error").get("details"));
    assertThat(fieldNames(otherBody)).as("최상위 키 집합 동일").isEqualTo(fieldNames(missingBody));
    assertThat(fieldNames(otherBody.get("error"))).as("error 키 집합 동일").isEqualTo(fieldNames(missingBody.get("error")));

    Snapshot after = snapshot(a.reservationId());
    assertThat(after).as("대상 예약·결제·Outbox 무변경").isEqualTo(before);
    assertThat(after.reservationStatus()).as("대상 예약 상태").isEqualTo("CONFIRMED");
    assertThat(outbox.outboxRowsOfPayment(paymentId)).as("결제의 Outbox 행").hasSize(1);
    assertThat(stringRedisTemplate.opsForValue().get(resultKey(key))).as("결과 키 값 유지").isEqualTo(resultValueBefore);
    Long resultPttlAfter = stringRedisTemplate.getExpire(resultKey(key), TimeUnit.MILLISECONDS);
    assertThat(resultPttlAfter).as("결과 키 PTTL(ms) 연장 없음").isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(resultPttlBefore);
    assertThat(fixture.redisKeyExists(lockKey(key))).as("락 키 부재 유지").isFalse();
  }

  // ===================== AC-4 결제 없음 =====================

  @Test
  @DisplayName("AC-4 결제 전 PENDING 예약을 소유자가 GET: 404 PAYMENT-001, 결제 데이터 미반환, 예약 PENDING·결제 0행 유지")
  void ac4_ownReservationWithoutPayment_returns404PaymentNotFound() throws Exception {
    Pending a = pending();
    Snapshot before = snapshot(a.reservationId());
    if (!before.payments().isEmpty()) {
      throw HarnessException.precondition("payment exists before AC-4 request");
    }

    MvcResult result = getPayment(a.reservationId(), a.member(), "AC-4");

    assertErrorResponse(result, 404, "PAYMENT-001", "Payment not found", uri(a.reservationId()));
    Snapshot after = snapshot(a.reservationId());
    assertThat(after).as("대상 예약·결제·Outbox 무변경").isEqualTo(before);
    assertThat(after.reservationStatus()).as("대상 예약 상태").isEqualTo("PENDING");
    assertThat(after.payments()).as("대상 예약의 결제 행").isEmpty();
  }

  // ===================== AC-5 없는 예약 =====================

  @Test
  @DisplayName("AC-5 존재하지 않는 reservationId를 회원이 GET: 404 RESERVATION-001, 결제 데이터 미반환")
  void ac5_missingReservation_returns404ReservationNotFound() throws Exception {
    Member member = fixture.member();
    Long missingId = missingReservationId();

    MvcResult result = getPayment(missingId, member, "AC-5");

    assertErrorResponse(result, 404, "RESERVATION-001", "Reservation not found", uri(missingId));
  }

  // ===================== AC-6 환불된 결제 =====================

  @Test
  @DisplayName("AC-6 결제 성공 후 환불된 예약을 소유자가 GET: 200, status REFUNDED, paidAt은 환불 전 값 유지, refundedAt은 결제 행 값")
  void ac6_refundedPayment_returnsCurrentRefundedState() throws Exception {
    Pending a = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    pay(a.reservationId(), key, false);
    Long paymentId = requireSinglePayment(a.reservationId(), "SUCCESS");
    LocalDateTime paidAtBeforeRefund = toLocalDateTime(fixture.paymentRow(paymentId).get("paid_at"));

    MvcResult refund = mockMvc.perform(post("/payments/" + paymentId + "/refund")
        .with(memberAuth(a.member())))
      .andReturn();
    if (refund.getResponse().getStatus() != 200) {
      throw HarnessException.precondition("refund status " + refund.getResponse().getStatus() + " (expected 200)");
    }
    Map<String, Object> refundedRow = fixture.paymentRow(paymentId);
    if (!"REFUNDED".equals(refundedRow.get("status")) || refundedRow.get("refunded_at") == null) {
      throw HarnessException.precondition("payment row not REFUNDED with refunded_at after refund: " + refundedRow);
    }

    MvcResult result = getPayment(a.reservationId(), a.member(), "AC-6");

    JsonNode data = assertSuccessData(result);
    assertThat(data.path("paymentId").longValue()).as("data.paymentId").isEqualTo(paymentId);
    assertThat(data.path("status").asText()).as("data.status").isEqualTo("REFUNDED");
    assertThat(data.path("paidAt").isTextual()).as("data.paidAt 문자열").isTrue();
    assertThat(LocalDateTime.parse(data.path("paidAt").asText())).as("data.paidAt = 환불 전 paid_at")
      .isEqualTo(paidAtBeforeRefund);
    assertThat(data.path("refundedAt").isTextual()).as("data.refundedAt 문자열").isTrue();
    assertThat(LocalDateTime.parse(data.path("refundedAt").asText())).as("data.refundedAt = 결제 행 refunded_at")
      .isEqualTo(toLocalDateTime(refundedRow.get("refunded_at")));
  }

  // ===================== AC-7 실패한 결제 =====================

  @Test
  @DisplayName("AC-7 forceFailure 결제의 예약을 소유자가 GET: 200, status FAIL, paidAt·refundedAt 키 있음·null")
  void ac7_failedPayment_returnsCurrentFailState() throws Exception {
    Pending a = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    pay(a.reservationId(), key, true);
    Long paymentId = requireSinglePayment(a.reservationId(), "FAIL");

    MvcResult result = getPayment(a.reservationId(), a.member(), "AC-7");

    JsonNode data = assertSuccessData(result);
    assertThat(data.path("paymentId").longValue()).as("data.paymentId").isEqualTo(paymentId);
    assertThat(data.path("status").asText()).as("data.status").isEqualTo("FAIL");
    assertExplicitNull(data, "paidAt");
    assertExplicitNull(data, "refundedAt");
  }

  // ===================== AC-8 인증 없음 =====================

  @Test
  @DisplayName("AC-8 인증 정보 없이 결제 완료 예약 GET: 401 AUTH-003, 결제 데이터 미반환, 대상 예약·결제·Outbox 무변경")
  void ac8_unauthenticated_returns401WithoutPaymentData() throws Exception {
    Pending a = pending();
    String key = fixture.idempotencyKey(PAYMENT_PREFIX);
    pay(a.reservationId(), key, false);
    requireSinglePayment(a.reservationId(), "SUCCESS");
    Snapshot before = snapshot(a.reservationId());

    MvcResult result = mockMvc.perform(get(uri(a.reservationId()))).andReturn();
    logDiagnostics("AC-8", result);

    assertErrorResponse(result, 401, "AUTH-003", "Unauthorized", uri(a.reservationId()));
    assertThat(MediaType.parseMediaType(result.getResponse().getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON))
      .as("Content-Type JSON 호환").isTrue();
    assertThat(snapshot(a.reservationId())).as("대상 예약·결제·Outbox 무변경").isEqualTo(before);
  }

  // ===================== helpers =====================

  private record Pending(SeatRef seat, Member member, Long holdId, Long reservationId) {
  }

  /**
   * 대상 예약의 상태·결제 행·Outbox 행 (단언 범위를 이 예약으로 한정)
   */
  private record Snapshot(String reservationStatus, List<Map<String, Object>> payments,
                          List<List<Map<String, Object>>> outboxRowsByPayment, long outboxCountOfReservation) {
  }

  private Pending pending() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.ACTIVE, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.PENDING);
    outbox.trackReservation(reservationId);
    return new Pending(seat, member, holdId, reservationId);
  }

  private void pay(Long reservationId, String key, boolean forceFailure) throws Exception {
    mockMvc.perform(post("/payments")
        .with(authentication(new UsernamePasswordAuthenticationToken(
          "t043-http", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reservationId\":" + reservationId + ",\"amount\":150000,\"forceFailure\":" + forceFailure + "}"))
      .andReturn();
  }

  private Long requireSinglePayment(Long reservationId, String status) {
    List<Map<String, Object>> payments = fixture.payments(reservationId);
    if (payments.size() != 1 || !status.equals(payments.get(0).get("status"))) {
      throw HarnessException.precondition("expected exactly one " + status + " payment, found " + payments);
    }
    return ((Number) payments.get(0).get("id")).longValue();
  }

  private MvcResult getPayment(Long reservationId, Member principal, String label) throws Exception {
    MvcResult result = mockMvc.perform(get(uri(reservationId)).with(memberAuth(principal))).andReturn();
    logDiagnostics(label, result);
    return result;
  }

  private static RequestPostProcessor memberAuth(Member member) {
    return authentication(new UsernamePasswordAuthenticationToken(member, null, List.of()));
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

  private static String uri(Long reservationId) {
    return "/reservations/" + reservationId + "/payment";
  }

  /**
   * 오류 응답 공통 단언 후 본문 트리를 반환한다
   * - data·error.details: 키가 있고 값이 null
   * - 결제 응답 필드(paymentId·status·paidAt·refundedAt)가 어디에도 없음
   */
  private JsonNode assertErrorResponse(MvcResult result, int status, String code, String message, String requestUri)
    throws Exception {
    assertThat(result.getResponse().getStatus()).as("HTTP 상태").isEqualTo(status);
    String raw = result.getResponse().getContentAsString();
    JsonNode body = objectMapper.readTree(raw);
    assertThat(body.path("success").isBoolean() && !body.path("success").booleanValue()).as("success=false").isTrue();
    assertExplicitNull(body, "data");
    JsonNode error = body.path("error");
    assertThat(error.isObject()).as("error 객체").isTrue();
    assertThat(error.path("code").asText()).as("error.code").isEqualTo(code);
    assertThat(error.path("message").asText()).as("error.message").isEqualTo(message);
    assertThat(error.path("path").asText()).as("error.path = 요청 URI").isEqualTo(requestUri);
    assertExplicitNull(error, "details");
    assertThat(raw).as("결제 응답 필드 미노출")
      .doesNotContain("\"paymentId\"")
      .doesNotContain("\"status\"")
      .doesNotContain("\"paidAt\"")
      .doesNotContain("\"refundedAt\"");
    return body;
  }

  private JsonNode assertSuccessData(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).as("GET HTTP 상태").isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("success").isBoolean() && body.path("success").booleanValue()).as("success=true").isTrue();
    JsonNode data = body.path("data");
    assertThat(data.isObject()).as("data 객체").isTrue();
    assertThat(data.path("paymentId").isIntegralNumber()).as("data.paymentId 숫자").isTrue();
    return data;
  }

  private static void assertExplicitNull(JsonNode node, String field) {
    assertThat(node.has(field)).as(field + " 키 존재").isTrue();
    assertThat(node.get(field).isNull()).as(field + " 값 null").isTrue();
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private Snapshot snapshot(Long reservationId) {
    List<Map<String, Object>> payments = fixture.payments(reservationId);
    List<List<Map<String, Object>>> outboxRows = payments.stream()
      .map(row -> outbox.outboxRowsOfPayment(((Number) row.get("id")).longValue()))
      .toList();
    return new Snapshot(
      fixture.reservationStatus(reservationId),
      payments,
      outboxRows,
      outbox.outboxCountOfReservation(reservationId));
  }

  /**
   * 현재 존재하지 않는 예약 ID (공유 DB의 특정 ID에 의존하지 않고 요청 직전 부재를 확인)
   */
  private Long missingReservationId() {
    Long candidate = jdbcTemplate.queryForObject(
      "select coalesce(max(id), 0) + 1000000 from reservations", Long.class);
    Integer count = jdbcTemplate.queryForObject(
      "select count(*) from reservations where id = ?", Integer.class, candidate);
    if (count == null || count != 0) {
      throw HarnessException.precondition("candidate missing reservation id exists: " + candidate);
    }
    return candidate;
  }

  private long requireExpiringKey(String key) {
    Long pttl = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    if (pttl == null || pttl <= 0) {
      throw HarnessException.precondition("key missing or without expiry: PTTL=" + pttl);
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
}
