package com.pil97.ticketing.payment.domain;

public enum PaymentStatus {
  // 결제 요청 직후 초기 상태
  PENDING,
  // 결제 성공
  SUCCESS,
  // 결제 실패
  FAIL
}