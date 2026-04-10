package com.pil97.ticketing.payment.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.request.CreatePaymentRequest;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "7. Payment", description = "결제 API - Mock 결제 처리")
@RestController
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  /**
   * POST /payments
   * - Mock 결제를 처리한다.
   * - 결제 성공 시 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED로 전환된다.
   * - 결제 실패 시 예약 FAILED, HOLD EXPIRED, 좌석 AVAILABLE로 복구된다.
   * - forceFailure: true이면 강제 실패 처리 (Mock 결제 실패 시나리오 재현용)
   * - Idempotency-Key 헤더 누락 시 400 에러 반환 (PAYMENT-004)
   */
  @PostMapping("/payments")
  public ResponseEntity<ApiResponse<PaymentResponse>> pay(
    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
    @RequestBody @Valid CreatePaymentRequest request) {

    PaymentResponse response = paymentService.pay(idempotencyKey, request);

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.success(response));
  }

  /**
   * POST /payments/{paymentId}/refund
   * - 결제 성공(SUCCESS) 상태인 결제에 대해 환불을 처리한다.
   * - 본인 소유 결제가 아닌 경우 403 반환 (PAYMENT-006)
   * - SUCCESS 상태가 아닌 결제 환불 시도 시 409 반환 (PAYMENT-005)
   * - Controller는 요청/응답 변환만 담당 - 소유권 검증은 Service에서 처리
   */
  @PostMapping("/payments/{paymentId}/refund")
  public ResponseEntity<ApiResponse<PaymentResponse>> refund(
    @PathVariable Long paymentId,
    @AuthenticationPrincipal Member loginMember) {

    PaymentResponse response = paymentService.refund(paymentId, loginMember);

    return ResponseEntity.ok(ApiResponse.success(response));
  }
}