# ADR-004: Soft Delete 미적용 — 상태값으로 비활성 데이터 처리

## Status

Decided (2026-04) — 감사 요건 발생 시 `@SQLDelete` + `@Where` 기반 전환 검토 예정

## Context

예약 취소, HOLD 만료, 결제 실패 등 비활성 데이터를 어떻게 처리할지 결정해야 했다.
일반적으로 두 가지 방식이 있다.

- Soft Delete: `deleted_at` 컬럼을 추가하고 DELETE 대신 UPDATE로 처리. 데이터 복구와 감사 이력 추적에 유리
- 상태값 처리: `deleted_at` 없이 상태 Enum(`CANCELLED`, `EXPIRED`, `FAILED`)으로 비활성 상태를 표현

현재 프로젝트의 제약 조건은 다음과 같다.

- 예약·HOLD·결제 데이터를 물리 삭제하지 않고 상태로 관리하는 도메인 특성
- 데이터 복구, 감사 로그, 규제 요건 없음
- 상태 기반 쿼리만으로 비활성 데이터 필터링 가능

## Decision

`deleted_at` 컬럼을 추가하지 않는다.
비활성 데이터는 `ReservationStatus.CANCELLED`, `HoldStatus.EXPIRED`,
`PaymentStatus.FAILED` 등 상태 Enum으로 표현한다.

티켓팅 도메인 특성상 예약·HOLD·결제는 상태 전이가 명확하고,
비활성 여부를 상태값으로 충분히 표현할 수 있으므로 별도의 삭제 플래그가 필요하지 않다.

## Consequences

### Trade-offs

| 항목       | 현재 방식 (상태값 처리)          | Soft Delete 도입 시                              |
|----------|-------------------------|-----------------------------------------------|
| 구현 복잡도   | 낮음                      | `deleted_at` 컬럼, `@SQLDelete`, `@Where` 설정 필요 |
| 데이터 복구   | 불가 (상태 전환은 가능)          | `deleted_at` 초기화로 복구 가능                       |
| 감사 이력 추적 | 상태 전환 이력으로 부분 추적 가능     | 삭제 시점 이력 추적 가능                                |
| 쿼리 복잡도   | 상태 조건으로 필터링             | `WHERE deleted_at IS NULL` 조건 항상 필요           |
| 도메인 표현력  | 상태 Enum으로 비활성 의미 명확히 표현 | 삭제 여부와 상태가 별도로 관리되어 혼재 가능                     |

### Future Path

- 감사 요건 또는 데이터 복구 요건 발생 시 `@SQLDelete` + `@Where` 어노테이션으로 전환 검토
  → `Entity` 레벨에서 선언하면 `JPA` 쿼리에 자동으로 조건이 추가되어 기존 쿼리 변경 범위를 줄일 수 있다.
- 이력 추적 요건이 생기면 Soft Delete보다 별도 감사 테이블(`reservation_history`) 도입이
  더 적합할 수 있으므로 요건 확정 후 방식 결정 필요

## Related Code / Docs

- `reservation/domain/ReservationStatus.java` — PENDING / CONFIRMED /
  CANCELLED / FAILED 상태 Enum
- `hold/domain/HoldStatus.java` — ACTIVE / EXPIRED / CONFIRMED 상태 Enum
- `payment/domain/PaymentStatus.java` — PENDING / SUCCESS / FAILED / REFUNDED 상태
  Enum
- `reservation/application/ReservationService.java` — 취소 시 상태 전환 처리
- 연계 TASK: TASK-058 (CONFIRMED 상태 예약 취소 정책화)