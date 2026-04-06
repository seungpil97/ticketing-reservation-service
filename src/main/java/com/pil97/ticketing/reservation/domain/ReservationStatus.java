package com.pil97.ticketing.reservation.domain;

public enum ReservationStatus {
  // 결제 대기 중 - 예약 생성 직후 초기 상태
  PENDING,
  // 결제 완료 - 결제 성공 시 전환
  CONFIRMED,
  // 결제 실패 - forceFailure 또는 결제 오류 시 전환
  FAILED,
  // 예약 취소 - 사용자 취소 요청 시 전환
  CANCELLED
}
