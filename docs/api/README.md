# API Specs

## 인증

- [Auth API](auth.md) — 로그인 / RefreshToken Rotation / 로그아웃

## 회원

- [Member API](member.md) — 회원 생성/조회/수정/삭제

## 티켓팅

- [Event API](event.md) — 공연 목록 / 회차 목록 조회
- [Showtime API](showtime.md) — 회차별 좌석 목록 조회

## 예약 플로우

- [Hold API](hold.md) — 좌석 선점 (Redis 분산락)
- [Reservation API](reservation.md) — 예약 확정 / 예약 취소
- [Payment API](payment.md) — Mock 결제 / 환불 (Idempotency-Key)

## 대기열

- [Queue API](queue.md) — 대기열 등록 / 대기 상태 조회