package com.pil97.ticketing.payment.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreatePaymentRequest {

  @NotNull(message = "예약 ID는 필수입니다")
  private Long reservationId;

  // 최소 1원 이상이어야 함
  @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다")
  private int amount;

  // true이면 강제 실패 처리 - Mock 결제 실패 시나리오 재현용
  // Mock 결제용 강제 실패 파라미터 - 실제 PG 연동 시 제거 예정
  private boolean forceFailure = false;
}