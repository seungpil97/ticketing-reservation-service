# ADR-005: 결제 성공 Outbox와 폴링 기반 내부 알림

## Status

Decided (2026-11) — TASK-033(#108)에서 도입. [ADR-003](ADR-003-no-outbox.md)을 대체한다.
재시도 정책과 운영 확장은 TASK-033-1에서 다룬다.

## Context

ADR-003은 "알림·외부 연동 등 이벤트 발행 요건 없음"을 근거로 Outbox를 적용하지 않았다.
TASK-033에서 결제 완료 내부 알림을 새 요구사항으로 승인하면서 이 전제가 바뀌었다.

- 알림은 결제 커밋 이후에 생성된다. 알림 생성이 실패하거나 애플리케이션이 재시작되어도 커밋된 결제의 알림이 유실되지 않아야 한다.
- 알림은 결제 건당 1건으로 수렴해야 한다.
- TASK-032 이후 결제·예약·HOLD·좌석 상태 전이는 결제 트랜잭션 안의 동기 리스너(`@Order(0)`)가 처리한다. 이 전이와 TASK-062 잠금 순서, Redis 멱등성 결과의 커밋 후 저장은 바꾸지 않는다.
- 외부 메시지 브로커는 도입하지 않는다 ([ADR-001](ADR-001-no-kafka.md)). 애플리케이션은 단일 인스턴스로 운영한다고 전제한다.

## Decision

결제 성공 1종에 대해 Outbox를 결제와 같은 트랜잭션에 저장하고, 커밋된 Outbox를 폴링해 내부 알림을 만든다.

### 1. 결제와 Outbox 저장 — 같은 트랜잭션

- `PaymentOutboxListener`가 `PaymentSucceededEvent`를 `@EventListener @Order(1) @Transactional(MANDATORY)`로 받아 `outbox_event`에 `PAYMENT_SUCCEEDED` 행을 `PENDING`으로 저장한다.
- 상태 전이 리스너(`@Order(0)`) 뒤에 실행되므로 상태 전이가 실패하면 Outbox를 쓰지 않는다. 어느 리스너의 예외든 결제 트랜잭션 전체가 롤백되고, 커밋되지 않았으므로 Redis 성공 결과도 저장되지 않는다.
- payload(JSON)에 `paymentId`, `reservationId`, `memberId`를 담는다. 소비 시 결제·예약 행을 다시 조회하지 않는다.
- 리스너는 새 잠금을 잡지 않는다. 예약은 같은 persistence context의 잠긴 엔티티이고, 회원 ID는 LAZY proxy ID로 읽는다.
- 결제 실패(forceFailure)·검증 거절·멱등 재요청은 성공 이벤트가 없으므로 Outbox를 만들지 않는다.

### 2. 알림과 처리 완료 — 항목별 독립 트랜잭션

- `OutboxRelayService`(coordinator, `@Transactional(NEVER)`)가 PENDING 후보 ID를 잠금 없이 조회하고, 항목마다 `OutboxItemProcessor.process`(`REQUIRES_NEW`)를 호출한다.
- 처리기는 Outbox 행을 `SELECT ... FOR UPDATE`로 잠그고 `PENDING`인지 재확인한 뒤, 알림 INSERT와 `PROCESSED` 표시(`processed_at`)를 같은 커밋에 포함한다.
- 커밋 flush를 포함한 어느 단계에서 실패해도 해당 항목만 롤백된다. 알림 기록도 함께 취소되고 Outbox는 `PENDING`으로 남는다. coordinator는 실패를 집계·로그하고 같은 배치의 다음 항목을 계속 처리한다.
- 처리기는 Outbox 행을 잠근 뒤 알림을 INSERT한다. 이 INSERT의 FK 검사는 참조하는 `payment`·`members` 행에 공유 잠금을 요청하므로, 다른 트랜잭션(예: 환불 중인 결제 갱신)이 해당 행을 잠그고 있으면 그 트랜잭션이 끝날 때까지 기다릴 수 있다. 결제 행을 잠근 상태에서 relay 항목이 알림 INSERT에서 대기하는 것은 `OutboxRelaySchedulerIsolationIntegrationTest`에서 실제로 관찰했다.
- 처리기는 예약·좌석·HOLD를 잠그지 않는다. `payment`·`members` 사이의 내부 잠금 순서와 교착 불가능성은 실행으로 검증하지 않았으며, 이후 `payment`·`members`를 잠근 채 Outbox·알림을 다루는 경로를 추가하면 잠금 순서를 다시 검토한다.
- 알림은 결제 성공이 커밋된 시점의 사실을 기록한다. relay가 처리하기 전이나 후에 환불되더라도 결제 완료 알림을 만들지 않거나 취소·수정하지 않는다 (처리 시점에 결제 상태를 다시 확인하지 않는다). 환불 알림이 필요해지면 별도 이벤트·알림 종류로 다룬다.

### 3. 중복 처리 방어

| 경로 | 동작 |
|---|---|
| 소비 경로 재전달 | 같은 Outbox ID로 `process`가 다시 호출되면 잠금 후 `PROCESSED`를 확인하고 알림 없이 `false`를 반환한다 |
| 폴러 선택 | 후보 조회가 `status = 'PENDING'`만 선택하므로 처리 완료 행에는 소비 경로를 호출하지 않는다 |
| DB 최종 방어 | `notification`의 `UNIQUE(type, payment_id)`, `outbox_event`의 `UNIQUE(event_type, aggregate_id)` |

정상 흐름에서는 "알림은 있는데 Outbox가 PENDING"인 상태가 만들어지지 않는다 (알림 기록과 처리 완료 표시를 함께 커밋).
이 상태가 외부 요인으로 생기면 유일성 위반으로 처리에 실패하고 PENDING으로 남는다. 자동 복구는 두지 않는다.

### 4. 순회·배치 제한·재시작

relay는 단일 인스턴스 전제에서 순회 위치를 메모리에 둔다.

- 순회 시작 시 그 시점의 최대 PENDING ID를 상한으로 고정하고, 상한 이하 PENDING을 ID 오름차순으로 순회한다.
- 호출 1회는 직전 위치 뒤의 PENDING을 최대 `outbox.relay.batch-size`건 시도하고, 성공·실패와 관계없이 위치를 전진한다.
- 상한에 도달하거나 batch보다 적게 선택되면 순회를 끝낸다. 실패 건은 다음 순회에서 다시 시도된다.
- 진행 중 순회의 잔여 범위가 0건이면 같은 호출에서 새 순회를 최대 한 번 시작한다.
- 상한 뒤에 추가된 행은 다음 순회에서 다루므로, 큰 ID가 계속 추가되어도 앞쪽 실패 건이 다시 선택된다.
- 커서 조회부터 갱신까지 호출을 직렬화한다 (`synchronized`).
- 재시작하면 위치가 초기화되어 가장 오래된 PENDING부터 새 순회를 시작한다. 처리 상태는 DB에만 있으므로 유실은 없다.
- 커서가 지나간 뒤 커밋된 작은 ID 행은 다음 순회에서 처리된다 (지연은 있으나 유실은 없음).

### 5. 스케줄러

- `OutboxRelayScheduler`가 `@Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms}")`로 relay를 호출한다. fixedDelay라 스케줄러 자체는 겹쳐 실행되지 않는다.
- `@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)`: 값이 없으면 등록하고, test profile(`application-test.yml`의 `outbox.relay.enabled: false`)에서는 등록하지 않는다.
- `enabled`를 공통 설정(`application-common.yml`)에 두지 않는다. import된 설정 파일이 import한 파일보다 우선하므로 공통 설정의 값이 profile 설정을 덮을 수 있기 때문이다.
- 기본값: `fixed-delay-ms: 5000`, `batch-size: 100` (`application-common.yml`).
- 스케줄 작업(HOLD 만료·대기열·Outbox relay)은 Spring Boot 자동 설정의 공용 스케줄러(`taskScheduler`)를 쓴다. 기본 스레드 수 1에서는 relay가 DB 잠금 대기 등으로 길어지면 다른 스케줄 작업도 함께 멈추므로 `spring.task.scheduling.pool.size`를 현재 작업 수인 3으로 둔다 (`application-common.yml`). 스케줄 작업을 추가하면 이 값을 함께 조정한다.
- 이 설정은 세 스케줄 작업 사이의 **실행 스레드 경합을 완화**하는 것이다. DB 행 잠금·연결 풀·Redis 등 다른 자원의 경합까지 격리하는 보장은 아니다.
- 영향: 이전에는 한 스레드에서 차례로 실행되던 HOLD 만료·대기열·relay가 이제 서로 병렬로 실행될 수 있다. 각 작업은 fixedDelay라 자기 자신과는 겹치지 않는다. HOLD 만료는 좌석·HOLD 행 잠금(TASK-062), 대기열은 Redis, relay는 Outbox·알림 행과 FK 참조 행을 다루며 세 작업이 같은 행을 잠그는 경로는 코드상 없다. 이 작업들은 이미 HTTP 요청 스레드와 병렬로 실행되던 것이다.
- relay 전용 `TaskScheduler` bean은 두지 않는다. 사용자 정의 `TaskScheduler`가 있으면 자동 설정 스케줄러가 생성되지 않아 기존 작업의 실행 스케줄러도 바뀌기 때문이다.

### 6. FK와 보존 정책

| 테이블 | FK | 원본 결제 삭제 시 | 근거 |
|---|---|---|---|
| `outbox_event` | 없음 | Outbox 행은 이벤트 발생 기록으로 남는다 | 소비는 payload만 사용한다. 기존 테스트 정리 코드가 결제 행을 직접 삭제하므로 FK가 있으면 회귀 테스트 정리가 실패한다 |
| `notification` | `payment_id → payment(id)`, `member_id → members(id)`, `ON DELETE RESTRICT` | 알림이 있는 결제는 삭제할 수 없다 | production에는 결제 삭제 경로가 없다. 예약이 있는 회원은 이미 `fk_reservations_member`로 삭제가 막혀 있어 새 제약이 아니다 |

결제가 삭제된 Outbox(테스트·수동 조작으로만 생김)는 알림 FK 위반으로 처리에 실패하고 PENDING으로 남는다. 순회 방식이므로 다른 건을 막지 않는다.

## Consequences

### Trade-offs

| 항목 | ADR-003 (단일 트랜잭션) | ADR-005 (Outbox + 폴링) |
|---|---|---|
| 알림 유실 | 해당 없음 (알림 없음) | 커밋된 Outbox가 남아 있으면 재시작 후에도 처리 |
| 결제 응답 지연 | 없음 | Outbox INSERT 1건 추가 (알림 생성은 결제 응답 밖) |
| 알림 지연 | 해당 없음 | 최대 폴링 주기 + 순회 진행 시간 |
| 전달 보장 | — | at-least-once 시도 + 상태 재확인·유일성 제약으로 결제 건당 알림 1건 |
| 운영 범위 | 단일 서비스 | 단일 인스턴스 전제 (다중 인스턴스 미지원) |

### 대안

- `@TransactionalEventListener(AFTER_COMMIT)`로 즉시 알림 생성: 커밋 후 프로세스가 종료되면 알림이 유실되고 재처리 근거가 남지 않는다.
- CDC, Kafka: 외부 브로커 미도입 결정(ADR-001)과 맞지 않는다.

### 현재 한계

- 시도 횟수·마지막 오류·다음 시도 시각을 기록하지 않는다. 계속 실패하는 건은 순회마다 재시도된다.
- 순회 위치가 메모리에 있으므로 재시작이 순회 주기보다 잦으면 뒤쪽 건이 지연될 수 있다.
- 처리 완료 행 정리(보존 기간) 정책이 없다.
- 다중 인스턴스에서 relay가 동시에 실행되면 같은 행을 두고 경합한다 (행 잠금·상태 재확인·유일성으로 중복 알림은 막지만 처리 분배는 하지 않는다).
- 결제 직후 환불된 건에도 결제 완료 알림이 남는다 (Decision §2의 의미에 따른 의도된 동작).
- 기존 결제 회귀 테스트(`ConsistencyFixture.cleanup`, `PaymentConcurrencyTest`)는 결제만 지우므로 공유 테스트 DB(`ticketing_test`)에 Outbox 행이 실행마다 남는다. test profile은 relay를 끄고 TASK-033 테스트는 결제 ID로 세거나 격리 DB를 쓰므로 현재 회귀는 통과한다. 다만 행이 계속 누적되고, 이후 공유 DB에서 relay를 켜거나 Outbox 전체 건수를 가정하는 테스트가 생기면 간섭할 수 있다. 정리하려면 동결된 회귀 테스트 정리 코드 수정 승인이 필요하다.

### Future Path (TASK-033-1 및 이후)

- TASK-033-1: 백오프, 다음 시도 시각, 시도 횟수와 마지막 오류 기록, 재시도 소진과 최종 실패 상태, 수동 재처리, 처리 지연 관측.
- TASK-033-1 후속 제안 (이슈 미생성, 아직 검증하지 않음):
  - 동시 실행 검증: 같은 Outbox ID에 대한 `process` 동시 호출 시 알림 1건·한쪽 건너뜀, `relay` 동시 호출 시 소비 경로 호출 합계가 후보 수와 같음. 행 잠금이나 `synchronized`를 제거하면 실패하는 테스트로 작성한다.
  - 실제 실패 경로 검증: 이미 알림이 있는 PENDING 행(UNIQUE 위반), 결제가 삭제된 PENDING 행(FK 위반), 깨진 payload 각각에서 해당 항목만 롤백되고 PENDING이 유지되며 같은 배치의 다음 항목이 처리되는지.
- 다중 인스턴스가 필요해지면 후보 조회에 `SKIP LOCKED` 또는 분산 스케줄 잠금을 검토한다.
- 외부 발행 요건이 생기면 같은 Outbox를 브로커 발행의 원천으로 확장하는 방향을 우선 검토한다 (ADR-001).

## Related Code / Docs

- `payment/application/event/PaymentOutboxListener.java` — 결제 트랜잭션 안 Outbox 저장 (`@Order(1)`, MANDATORY)
- `outbox/application/OutboxRelayService.java` — 순회·배치 제한·항목 실패 격리 (coordinator, NEVER)
- `outbox/application/OutboxItemProcessor.java` — 행 잠금·재확인·알림 기록·처리 완료 (REQUIRES_NEW)
- `outbox/application/scheduler/OutboxRelayScheduler.java` — fixedDelay 스케줄러, `outbox.relay.enabled`
- `outbox/domain/OutboxEvent.java`, `notification/domain/Notification.java`
- `src/main/resources/db/migration/V15__create_outbox_event_and_notification.sql`
- `docs/architecture/adr/ADR-003-no-outbox.md` — 대체된 결정
- 연계 TASK: TASK-033 (#108), TASK-033-1 (Outbox 재처리 실패 시나리오)
