package com.pil97.ticketing.payment.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.payment.api.dto.request.CreatePaymentRequest;
import com.pil97.ticketing.payment.api.dto.response.PaymentResponse;
import com.pil97.ticketing.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "7. Payment", description = "결제 API - Mock 결제 처리")
@RestController
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  /**
   * POST /payments
   * <p>
   * 이 API의 목적:
   * - Mock 결제를 처리한다.
   * - 결제 성공 시 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED로 전환된다.
   * - 결제 실패 시 예약 FAILED, HOLD EXPIRED, 좌석 AVAILABLE로 복구된다.
   * - forceFailure: true이면 강제 실패 처리 (Mock 결제 실패 시나리오 재현용)
   * <p>
   * Idempotency-Key 정책:
   * - 클라이언트가 UUID를 생성해 헤더에 담아 전송
   * - 동일 key 재요청 시 기존 결과 반환 (DB 처리 없음)
   * - 헤더 누락 시 400 에러 반환 (PAYMENT-004)
   * <p>
   * 상태코드 정책:
   * - 결제 처리 성공 시 201 Created
   */
  @PostMapping("/payments")
  public ResponseEntity<ApiResponse<PaymentResponse>> pay(
    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
    @RequestBody @Valid CreatePaymentRequest request) {

    // Service 호출: idempotency key 검증 + 결제 처리
    PaymentResponse response = paymentService.pay(idempotencyKey, request);

    // 201 Created + 표준 응답
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.success(response));
  }

  /**
   * POST /payments/{paymentId}/refund
   * <p>
   * 이 API의 목적:
   * - 결제 성공(SUCCESS) 상태인 결제에 대해 환불을 처리한다.
   * - 환불 시 Payment REFUNDED, 예약 CANCELLED, HOLD EXPIRED, 좌석 AVAILABLE로 전환된다.
   * - SUCCESS 상태가 아닌 결제 환불 시도 시 409 Conflict 반환 (PAYMENT-005)
   * <p>
   * 상태코드 정책:
   * - 환불 처리 성공 시 200 OK
   */
  @PostMapping("/payments/{paymentId}/refund")
  public ResponseEntity<ApiResponse<PaymentResponse>> refund(
    @PathVariable Long paymentId) {

    // Service 호출: 환불 처리
    PaymentResponse response = paymentService.refund(paymentId);

    return ResponseEntity.ok(ApiResponse.success(response));
  }
}