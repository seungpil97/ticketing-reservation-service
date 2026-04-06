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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
   * - 결제 실패 시 예약 FAILED로 전환된다.
   * - forceFailure: true이면 강제 실패 처리 (Mock 결제 실패 시나리오 재현용)
   * <p>
   * 상태코드 정책:
   * - 결제 처리 성공 시 201 Created
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @PostMapping("/payments")
  public ResponseEntity<ApiResponse<PaymentResponse>> pay(
    @RequestBody @Valid CreatePaymentRequest request) {

    // 서비스 호출: 결제 처리
    PaymentResponse response = paymentService.pay(request);

    // 201 Created + 표준 응답
    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(ApiResponse.success(response));
  }
}