package com.pil97.ticketing.hold.domain;

public enum HoldStatus {
  ACTIVE,
  EXPIRED,
  CONFIRMED,
  // 환불로 종료된 선점 상태 - 시간 만료(EXPIRED)와 구분
  REFUNDED,
  // 결제 전 예약 직접 취소로 종료된 선점 상태 - 시간 만료(EXPIRED)와 구분
  CANCELLED
}
