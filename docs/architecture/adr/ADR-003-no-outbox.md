# ADR-003: Outbox Pattern 미적용 — 단일 트랜잭션으로 상태 일관성 유지

## Status

Decided (2026-04) — 현재는 단일 트랜잭션 유지, TASK-033에서 Outbox Pattern 도입 검토 예정

## Context

결제 완료 시 Payment, Reservation, Hold, ShowtimeSeat 4개 엔티티의 상태가 함께 전환된다.
이 상태 전환을 현재 구조에서 어떻게 일관되게 처리할지, 그리고 향후 외부 이벤트 발행이 필요해질 때 어떤 확장 경로를 가져갈지 결정해야 했다.

Outbox Pattern은 DB 트랜잭션 내에 이벤트를 outbox 테이블에 함께 저장하고,
별도 스케줄러가 이를 읽어 외부로 발행함으로써 이벤트 유실을 방지하는 패턴이다.

현재 프로젝트의 제약 조건은 다음과 같다.

- 외부 메시지 브로커(Kafka, RabbitMQ) 미도입
- 단일 DB 트랜잭션으로 모든 상태 전환 원자성 보장 가능
- 알림, 외부 시스템 연동 등 이벤트 발행 요건 없음

## Decision

Outbox Pattern을 적용하지 않는다.
현재는 `PaymentService.pay()` 단일 `@Transactional` 경계 내에서
Payment·Reservation·Hold·ShowtimeSeat 상태 전환을 직접 처리한다.

외부 이벤트 발행 요건이 없는 현재 구조에서는 DB 트랜잭션만으로 원자성과 일관성을 보장할 수 있으므로 Outbox 테이블과 재처리 스케줄러를
추가하지 않는다.

## Consequences

### Trade-offs

| 항목        | 현재 방식 (단일 트랜잭션)  | Outbox Pattern 도입 시                |
|-----------|------------------|------------------------------------|
| 구현 복잡도    | 낮음               | Outbox 테이블, 발행 스케줄러, 재처리 로직 필요     |
| 원자성 보장    | DB 트랜잭션으로 보장     | DB 저장 + 이벤트 발행 원자성 보장 가능           |
| 이벤트 유실    | 외부 발행 없으므로 해당 없음 | 재처리 스케줄러로 유실 방지 가능                 |
| 외부 시스템 연동 | 불가               | 브로커/외부 API 연동 가능                   |
| 서비스 분리 대응 | 단일 서비스에서만 유효     | 서비스 분리 및 외부 이벤트 발행 요건이 생기면 유력한 선택지 |

### Future Path

- TASK-033: Outbox 테이블 추가와 재처리 스케줄러 도입을 검토한다.
  → 외부 이벤트 발행 요건이 생기면 재처리 보장과 외부 시스템 연동을 위한 기반이 된다.
- Outbox 도입 시 ADR-001에서 검토 중인 Spring Events와의 연계 적용도 함께 검토한다.
  이벤트 저장(Outbox)과 후속 발행 구조를 분리하는 방향으로 확장할 수 있다.
- 서비스 분리(MSA)와 외부 이벤트 발행 요건이 생기면 Outbox + Kafka 조합 도입을 우선 검토한다.

## Related Code / Docs

- `payment/application/PaymentService.java` — 단일 트랜잭션 내 4개 엔티티 상태 전환 처리
- `docs/architecture/adr/ADR-001-no-kafka.md` — Kafka 미도입 결정과 연계
- `docs/architecture/adr/ADR-002-mock-payment.md` — Mock 결제 트랜잭션 경계와 연계
- 연계 TASK: TASK-033 (Outbox Pattern 도입 검토), TASK-032 (Spring Events 전환 검토)