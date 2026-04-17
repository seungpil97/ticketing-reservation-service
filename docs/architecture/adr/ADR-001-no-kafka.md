# ADR-001: Kafka 미도입 — 단일 트랜잭션 직접 상태 전환 유지

## Status

Decided (2026-04) — 현재는 단일 트랜잭션 직접 상태 전환 유지, TASK-032에서 Spring Application
Events 전환 검토 예정

## Context

티켓팅 서비스에서는 예약 확정, 결제 완료, 좌석 상태 변경 같은 후속 처리를 현재는 단일 트랜잭션 내 직접 상태 전환으로 처리하고 있다.
향후 결합도 완화가 필요해질 경우 어떤 이벤트 처리 방식을 선택할지 함께 판단해야 했다.

현재 프로젝트의 제약 조건은 다음과 같다.

- 단일 Spring Boot 애플리케이션, 단일 MariaDB, 단일 JVM 환경
- 서비스 분리(MSA) 계획 없음
- 별도 Kafka 브로커 운영 인프라 없음

## Decision

Kafka를 도입하지 않는다. 현재는 Kafka와 Spring Events 모두 도입하지 않고 단일 `@Transactional` 경계 내에서
Payment, Reservation, Hold, ShowtimeSeat 상태 전환을 직접 처리한다. Spring
`ApplicationEventPublisher`는 TASK-032에서 결합도 완화가 필요해질 시점에 도입을 검토한다.

## Consequences

### Trade-offs

| 항목       | 현재 방식 (직접 상태 전환)     | Kafka 도입 시                       |
|----------|----------------------|----------------------------------|
| 운영 복잡도   | 낮음                   | Kafka 브로커, ZooKeeper/KRaft 운영 필요 |
| 트랜잭션 일관성 | DB 트랜잭션으로 원자성 보장     | 이벤트 발행과 DB 커밋 간 정합성 별도 처리 필요     |
| 재처리 보장   | 없음                   | Consumer Group 기반 재처리 가능         |
| 순서 보장    | 트랜잭션 내 순서 보장         | 파티션 단위 순서 보장                     |
| 다중 소비자   | 불가                   | Consumer Group으로 확장 가능           |
| 로컬 개발    | Docker Compose만으로 충분 | Kafka 컨테이너 추가 필요                 |

### Future Path

- TASK-032: Spring `ApplicationEventPublisher` 기반 이벤트 처리 도입
  → 도메인 간 직접 의존 제거, 이벤트 리스너로 후속 처리 분리
- 트래픽 급증 또는 서비스 분리 시점에 Kafka 전환 검토
  → `ApplicationEventPublisher` 기반 구조를 먼저 정리하면, 이후 Kafka 전환 시 변경 범위를 줄일 수 있다.
- Kafka 전환 시에는 Outbox Pattern(TASK-033)도 함께 적용하는 방향을 우선 검토한다.

## Related Code / Docs

- `payment/application/PaymentService.java` — 결제·예약·좌석 상태 전환 단일 트랜잭션 처리
- `reservation/application/ReservationService.java` — 예약 생성 및 취소 트랜잭션 경계
- `docs/architecture/adr/ADR-003-no-outbox.md` — Outbox 미적용 결정과 연계
- 연계 TASK: TASK-032 (Spring Events 전환), TASK-033 (Outbox Pattern)