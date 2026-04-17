# ADR-002: Mock 결제 — PG 연동 없이 forceFailure 플래그로 성공/실패 재현

## Status

Decided (2026-04) — 실운영 전환 시 PaymentGateway 인터페이스 분리 후 구현체 교체 예정

## Context

티켓팅 서비스에서 예약→결제→확정 전체 흐름을 검증해야 한다.
실 결제 처리를 위해서는 PG사(Payment Gateway) 계약, 샌드박스 환경, webhook 수신 서버가 필요하다.

현재 프로젝트의 제약 조건은 다음과 같다.

- 포트폴리오 목적의 백엔드 프로젝트로 PG 계약 및 샌드박스 환경 없음
- 목표는 결제 성공/실패 시 예약·좌석·HOLD 상태 전환 흐름 검증
- PG 콜백(비동기 webhook) 처리, 결제 타임아웃, 망취소 흐름은 현재 범위 밖

## Decision

실 PG 연동 없이 `PaymentService.pay()`에서 `forceFailure` 플래그로 결제 성공/실패를 재현한다.

- `forceFailure = false`: Payment SUCCESS → Reservation CONFIRMED → ShowtimeSeat
  RESERVED
- `forceFailure = true`: Payment FAILED → Reservation FAILED → Hold EXPIRED →
  ShowtimeSeat AVAILABLE

현재는 포트폴리오 범위와 구현 복잡도를 고려해 `PaymentService`가 결제 처리를 직접 담당하며 외부 PG 추상화 계층은 아직 두지
않았다.

## Consequences

### Trade-offs

| 항목       | 현재 방식 (Mock)                    | 실 PG 연동 시                     |
|----------|---------------------------------|-------------------------------|
| 구현 복잡도   | 낮음                              | PG 어댑터, webhook 수신, 망취소 처리 필요 |
| 흐름 검증    | 성공/실패 시나리오 재현 가능                | 실제 결제 네트워크 흐름 검증 가능           |
| 비동기 처리   | 없음 (동기 처리)                      | PG 콜백 기반 비동기 처리 필요            |
| 멱등성      | Redis idempotency key로 중복 요청 방지 | PG측 멱등성 키와 함께 이중 방어 필요        |
| 타임아웃/망취소 | 미구현                             | 결제 타임아웃, 망취소 흐름 별도 처리 필요      |

### Future Path
- `PaymentGateway` 인터페이스를 추출하고 `MockPaymentGateway`, `PGPaymentGateway` 구현체로
  분리한다. 이후 `PaymentService`가 인터페이스에만 의존하도록 리팩토링하면 PG 전환 시 변경 범위를 줄일 수 있다.

- PG 전환 시 webhook 수신 엔드포인트, 결제 타임아웃 처리, 망취소 흐름 추가 구현 필요
- 멱등성 키는 현재 구조 그대로 PG 연동 후에도 재사용 가능

## Related Code / Docs

- `payment/application/PaymentService.java` — forceFailure 플래그 기반 결제 처리 및 상태 전환
- `infra/idempotency/IdempotencyRedisRepository.java` — 중복 결제 방지 Redis 멱등성 처리
- `docs/architecture/adr/ADR-003-no-outbox.md` — 단일 트랜잭션 상태 전환과 연계
- 연계 TASK: TASK-036 (결제 멱등성), TASK-036-1 (결제 실패/환불 처리)