package com.pil97.ticketing.payment.api;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.exception.GlobalExceptionHandler;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.application.PaymentService;
import com.pil97.ticketing.payment.error.PaymentErrorCode;
import com.pil97.ticketing.reservation.error.ReservationErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
  value = PaymentController.class,
  excludeAutoConfiguration = SecurityAutoConfiguration.class
)
class PaymentControllerTest {

  private MockMvc mockMvc;

  @MockitoBean
  private PaymentService paymentService;

  @Autowired
  private PaymentController paymentController;

  @Autowired
  private GlobalExceptionHandler globalExceptionHandler;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
      .standaloneSetup(paymentController)
      .setControllerAdvice(globalExceptionHandler)
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
      .build();
  }

  private void setAuthentication(Long memberId) {
    Member member = new Member("a@test.com", "testUser", "encoded");
    ReflectionTestUtils.setField(member, "id", memberId);
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(member, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  // ────────────────────────────────────────────────
  // POST /payments
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /payments: 결제 성공 → 201 + SUCCESS 반환")
  void pay_success() throws Exception {
    // given
    setAuthentication(42L);
    when(paymentService.pay(any(), any()))
      .thenReturn(new PaymentResponse(1L, "SUCCESS", LocalDateTime.of(2026, 4, 6, 10, 0, 0)));

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "test-key-001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 1,
            "amount": 150000,
            "forceFailure": false
          }
          """))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.paymentId").value(1))
      .andExpect(jsonPath("$.data.status").value("SUCCESS"))
      .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
  }

  @Test
  @DisplayName("POST /payments: forceFailure=true → 201 + FAIL 반환, paidAt null")
  void pay_forceFailure() throws Exception {
    // given
    setAuthentication(42L);
    when(paymentService.pay(any(), any()))
      .thenReturn(new PaymentResponse(2L, "FAIL", null));

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "test-key-002")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 1,
            "amount": 150000,
            "forceFailure": true
          }
          """))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.status").value("FAIL"))
      .andExpect(jsonPath("$.data.paidAt").doesNotExist());
  }

  @Test
  @DisplayName("POST /payments: Idempotency-Key 헤더 누락 → 400 + PAYMENT-004 반환")
  void pay_missingIdempotencyKey_returns400() throws Exception {
    // given
    setAuthentication(42L);
    when(paymentService.pay(eq(null), any()))
      .thenThrow(new BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING));

    // when & then
    mockMvc.perform(post("/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 1,
            "amount": 150000,
            "forceFailure": false
          }
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(PaymentErrorCode.IDEMPOTENCY_KEY_MISSING.getCode()));
  }

  @Test
  @DisplayName("POST /payments: 동일 Idempotency-Key 재요청 → 201 + 기존 결과 반환")
  void pay_duplicateIdempotencyKey_returnsCachedResponse() throws Exception {
    // given
    setAuthentication(42L);

    // Redis에서 기존 결과 반환 시나리오
    when(paymentService.pay(eq("duplicate-key-001"), any()))
      .thenReturn(new PaymentResponse(1L, "SUCCESS", LocalDateTime.of(2026, 4, 6, 10, 0, 0)));

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "duplicate-key-001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 1,
            "amount": 150000,
            "forceFailure": false
          }
          """))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.paymentId").value(1))
      .andExpect(jsonPath("$.data.status").value("SUCCESS"));
  }

  @Test
  @DisplayName("POST /payments: reservationId 누락 → 400")
  void pay_missingReservationId_returns400() throws Exception {
    // given
    setAuthentication(42L);

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "test-key-003")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "amount": 150000
          }
          """))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /payments: amount가 0이하 → 400")
  void pay_invalidAmount_returns400() throws Exception {
    // given
    setAuthentication(42L);

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "test-key-004")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 1,
            "amount": 0
          }
          """))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /payments: 존재하지 않는 reservationId → 404")
  void pay_reservationNotFound_returns404() throws Exception {
    // given
    setAuthentication(42L);
    when(paymentService.pay(any(), any()))
      .thenThrow(new BusinessException(ReservationErrorCode.NOT_FOUND));

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "test-key-005")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 999,
            "amount": 150000
          }
          """))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(ReservationErrorCode.NOT_FOUND.getCode()));
  }

  @Test
  @DisplayName("POST /payments: 이미 처리된 예약 → 409")
  void pay_alreadyProcessed_returns409() throws Exception {
    // given
    setAuthentication(42L);
    when(paymentService.pay(any(), any()))
      .thenThrow(new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED));

    // when & then
    mockMvc.perform(post("/payments")
        .header("Idempotency-Key", "test-key-006")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "reservationId": 1,
            "amount": 150000
          }
          """))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED.getCode()));
  }
}