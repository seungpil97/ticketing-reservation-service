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
    when(paymentService.pay(any()))
      .thenReturn(new PaymentResponse(1L, "SUCCESS", LocalDateTime.of(2026, 4, 6, 10, 0, 0)));

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
    when(paymentService.pay(any()))
      .thenReturn(new PaymentResponse(2L, "FAIL", null));

    // when & then
    mockMvc.perform(post("/payments")
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
  @DisplayName("POST /payments: reservationId 누락 → 400")
  void pay_missingReservationId_returns400() throws Exception {
    // given
    setAuthentication(42L);

    // when & then
    mockMvc.perform(post("/payments")
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
    when(paymentService.pay(any()))
      .thenThrow(new BusinessException(ReservationErrorCode.NOT_FOUND));

    // when & then
    mockMvc.perform(post("/payments")
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
    when(paymentService.pay(any()))
      .thenThrow(new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSED));

    // when & then
    mockMvc.perform(post("/payments")
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