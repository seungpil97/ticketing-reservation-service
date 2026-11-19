# ADR-007: 결제 경로의 Redis 멱등성 결과 저장 실패와 DB 커넥션 획득 실패

## Status

Decided (2026-11) — TASK-028([#119](https://github.com/seungpil97/ticketing-reservation-service/issues/119))에서 결정.
결제 멱등성 구조(Redis 결과 키·락 키, 커밋 후 결과 저장)와 공통 오류 응답 계약은 그대로 두고,
두 장애 시나리오(S1·S2)의 동작만 정하고 검증한다. 그 밖의 Redis·DB 장애 경로는 이 ADR의 결정 대상이 아니다(§6).

## Context

`POST /payments`는 다음 순서로 동작한다(`PaymentService`).

1. 트랜잭션 시작(`@Transactional`, `JpaTransactionManager`가 커넥션 획득)
2. `IdempotencyRedisRepository.find`: 결과 키 `idempotency:payment:{key}` GET → 없으면 락 키 `idempotency:payment:lock:{key}` `SET NX` (TTL 10초)
3. `processPayment`: 예약 비관락 조회·PENDING 검증 → 결제 저장(SUCCESS) → 같은 트랜잭션의 리스너가 예약 CONFIRMED·좌석 RESERVED·HOLD CONFIRMED·Outbox(`PAYMENT_SUCCEEDED`) 저장
4. DB 커밋 후 `afterCommit`: `IdempotencyRedisRepository.save`가 결과 키 SET(24시간) 후 락 키 DELETE

변경 전 확인된 문제는 두 가지였다.

- **S1**: 4단계의 결과 SET이 Redis 접근 오류로 실패하면 예외가 `afterCommit`에서 호출자에게 전파돼 500 `COMMON-007`이 응답됐다.
  DB에는 결제가 SUCCESS로 커밋됐으므로 클라이언트가 결제 결과를 실패로 오인한다 (WORK-8 RED로 확인, §5).
- **S2**: 1단계에서 커넥션을 얻지 못할 때의 응답과 상태를 검증한 테스트가 없었다.

`payment.reservation_id`에는 UNIQUE 제약이 없다(V13은 인덱스만). 중복 결제 방어는 예약 비관락과 PENDING 상태 가드가 맡는다.

## Decision

### 1. S1 — 커밋 후 결과 저장 실패: 첫 응답 201 유지

`PaymentService.registerAfterCommit`의 `afterCommit`에서 `save()` 호출만 `try/catch (DataAccessException)`로 감싼다.

- catch에서는 ERROR 로그 1건만 남기고, 이미 커밋된 결제 결과를 정상 응답(201)으로 반환한다.
  ```text
  idempotency result save failed: prefix=idempotency:payment, paymentId={id}, reservationId={id}, errorType={예외 단순 클래스명}
  ```
- 로그에 멱등키 원문·응답 본문·금액을 넣지 않는다. 예외 객체도 첨부하지 않는다(예외 메시지 노출 방지, ADR-006 §5와 같은 방식).
- 추가 락 삭제·재시도는 하지 않는다. 남은 락 키는 기존 TTL(최초 락 획득 시각부터 10초)로 만료된다.
- `DataAccessException`이 아닌 예외는 기존대로 전파한다. `afterCompletion`의 락 해제(커밋 외 완료 시)는 바꾸지 않는다.

201을 유지하는 이유: 4단계 시점에 결제·예약·좌석·HOLD·Outbox는 이미 커밋됐다. Redis 결과 키는 같은 키 재요청의 replay를 위한 캐시이고,
결제의 사실 근거는 DB다. 커밋된 결제를 500으로 응답하면 클라이언트가 실패로 판단해 다시 결제를 시도하게 만든다.
재요청의 중복 결제 방어는 Redis가 아니라 예약 비관락·PENDING 가드가 맡는다(§3).

**catch 범위와 직접 검증 범위**

| 경우 | 발생 지점 | catch 범위 | 키 상태 | 직접 검증 |
|---|---|---|---|---|
| 결과 SET 실패 | `save()` 결과 SET | 포함 | 결과 키 없음, 락 키는 TTL까지 남음 | **예** — 결과 SET 직전 `RedisConnectionFailureException` 1회 주입 |
| 결과 SET 성공 후 락 DELETE 실패 | `save()` 락 DELETE | 포함 | 결과 키 있음, 락 키는 TTL까지 남음 | 아니오 (코드상 예상: 이후 같은 키 재요청은 200 replay 가능) |
| 직렬화 오류 | `save()` 내부 | 해당 없음 (`save()`가 이미 처리) | 결과 키 없음, 락 삭제 | 아니오 |
| `DataAccessException` 외 예외 | — | 제외 (기존대로 전파) | — | 아니오 |

### 2. S2 — 결제 트랜잭션 시작 시 커넥션 획득 실패: 기존 500 유지

코드를 바꾸지 않는다. pool 고갈로 1단계에서 커넥션을 얻지 못하면 `CannotCreateTransactionException`이 공통 처리기로 가서 500 `COMMON-007`이 응답된다.
메서드 본문 전이므로 Redis `find`도, DB 변경도 일어나지 않는다.

이번 TASK에서 공통 예외 매핑(500 `COMMON-007`)을 유지한 이유:

- 실패는 트랜잭션 프록시가 커넥션을 얻는 단계(`JpaTransactionManager.doBegin`)에서 `pay()` 본문에 들어가기 전에 난다.
  따라서 `pay()` 내부에서 이 실패를 잡아 다른 응답으로 바꿀 수 없다.
- 예외 타입만으로 pool 고갈을 특정하기 어렵다. HikariCP 5.1.0은 pool 고갈과 DB 연결 실패를 같은 `SQLTransientConnectionException`으로 던지고
  (`HikariPool.createTimeoutException`), 메시지 문자열로 구분하는 방식은 불안정하다.
- 이번 TASK에서는 커넥션 획득 예외를 503으로 일괄 매핑하지 않고, 결제 트랜잭션 시작 시 풀 고갈로 획득 시간이 초과된 경우 기존 500 `COMMON-007` 응답이 반환되는지 검증했다.

### 3. 시나리오 비교

| 단계 | S1 결과 SET 실패 (주입) | S2 커넥션 획득 실패 (pool 고갈) |
|---|---|---|
| 실패 시점 | DB 커밋 후 `afterCommit`의 결과 SET 직전 | 결제 트랜잭션 시작(본문 실행 전) |
| 첫 응답 | 201, `data.paymentId`·`status`가 별도 조회한 유일한 결제 행의 id·`SUCCESS`와 같음 | 500 `COMMON-007`, `error.path=/payments`, 본문에 pool 이름·SQL·호스트 정보 없음 |
| DB 상태 | 결제 1행 SUCCESS, 예약 CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED, 결제의 Outbox 1행 PENDING | 대상 예약의 결제 0행, 예약 PENDING, HOLD ACTIVE, 좌석 HELD, `outbox_event` id 집합이 요청 전(B)과 점유 해제 후(A)에 같음 |
| Redis 상태 | 결과 키 없음, 락 키 있음(`0 < PTTL ≤ 10000ms`) | 이 요청의 결과 키·락 키 없음 (`find` 호출 0회) |
| 복구 후 같은 키 재요청 | 락 키가 남아 있으면 409 `IDEMPOTENCY-001`, 락 키가 없으면 409 `PAYMENT-002`. 두 경우 모두 결제 1행·Outbox 1행 유지 | 201, 결제 1행 SUCCESS(id = 응답 `paymentId`), 예약 CONFIRMED, A′ − B가 1행이고 그 행이 `PAYMENT_SUCCEEDED`·`aggregate_id`=새 결제 ID·payload `reservationId`=대상 예약 |
| 다른 키 재요청 | 409 `PAYMENT-002`, 결제·Outbox 1행 유지 | (검증하지 않음) |

S1 재요청의 409 두 가지는 락 키 TTL에 따라 갈린다. 락 키가 있으면 `find`의 `SET NX`가 실패해 `IDEMPOTENCY-001`,
없으면 `processPayment`로 들어가 이미 CONFIRMED인 예약의 상태 가드(`validatePayable`)에서 `PAYMENT-002`가 되고 `finally`에서 락을 해제한다.

## Consequences

### 4. 한계

- S1에서 결과 키가 기록되지 않으면 같은 키 재요청은 replay(200)되지 않고 409를 받는다. 클라이언트는 결제 성공 사실을 첫 응답(201)으로만 안다.
  결제 상태 단건 조회 API는 없다.
- S1 로그는 `com.pil97.ticketing.payment.application.PaymentService` logger 이벤트 범위만 단언했다. 애플리케이션 전체 출력의 비노출은 보장하지 않는다.
- 실제 Redis 단절에서 어떤 예외 타입이 나오는지, 그것이 `DataAccessException`으로 변환되는지는 사용 중인 Redis ConnectionFactory(Lettuce·Redisson)에 달려 있고 확인하지 않았다.
- `ReservationService`의 같은 afterCommit 구조는 바꾸지 않았다.

### 5. 실제 검증 범위

테스트 계약은 Issue #119 본문의 "Test Contract 개정 0"이고, 실행 전 동결 기록은
[FREEZE-RECORD](https://github.com/seungpil97/ticketing-reservation-service/issues/119#issuecomment-7003517818)다.

| 범위 | 근거 |
|---|---|
| S1 테스트 | `src/test/java/com/pil97/ticketing/payment/api/PaymentIdempotencySaveFailureIntegrationTest.java` (5개 메서드, AC-S1-1~7). 공유 test MariaDB·실제 Redis. 주입은 결과 키 SET 1회뿐이고 그 밖의 Redis 호출은 실제 Redis |
| S1 harness (H-S1) | 주입 1회, 주입 직전 별도 JDBC 커넥션으로 결제 행 SUCCESS(커밋 완료), 주입 직전 락 키 존재. 위반 시 `HarnessException` |
| S1 락 조건 | 락 존재는 PEXPIRE 60초 연장, 락 부재는 DELETE로 **재현**했다. 락 TTL 자연 만료와 실제 Redis 단절은 관찰하지 않았다 |
| S2 테스트 | `src/test/java/com/pil97/ticketing/payment/api/PaymentConnectionAcquisitionFailureIntegrationTest.java` (1개 메서드, AC-S2-1~3). 격리 MariaDB 컨테이너(`IsolatedMariaDb`), 이 클래스 전용 Hikari pool(`maximum-pool-size=2`, `connection-timeout=250ms`)의 커넥션 2개를 테스트가 점유 |
| S2 harness (H-S2) | 요청 직전 active=2·idle=0, `find` 호출 0회, 처리기 로그의 `CannotCreateTransactionException` ← `SQLTransientConnectionException`, 점유 해제 후 active=0 |
| S2 테스트 전제 | Member가 아닌 principal로 인증하고 Authorization 헤더를 보내지 않는다. JWT 필터의 DB·Redis 조회와 Rate Limit(회원 기준)을 건너뛰어 요청의 첫 커넥션 획득 지점을 결제 트랜잭션 시작으로 만든다. 실제 JWT 인증 요청에서는 필터의 회원 조회가 먼저 커넥션을 쓴다 |
| S2 Outbox 판정 | 격리 DB에서 이 클래스만 쓰므로(스케줄러 제외, relay 미등록) 전체 `outbox_event` id 집합을 요청 전·점유 해제 후·복구 요청 후로 비교했다 |
| Valid RED (WORK-8) | 동결 테스트 커밋 `05e0244`, production 변경 전 1회 실행: 6개 중 2개 실패. AC-S1-1 `expected 201 but was 500`(원인: 주입 예외가 `afterCommit` → `processCommit`에서 전파), AC-S1-4 `PaymentService` ERROR 기대 1건·실제 0건. 두 실패 모두 H-S1 충족 후 assertion 실패이고 나머지 4개는 통과 |
| GREEN (WORK-9) | production 변경 후 같은 동결 테스트 6개 1회 실행: 6/6 통과, 동결 테스트 파일 해시는 FREEZE-RECORD와 같음 |
| 전체 회귀 (WORK-10) | 같은 코드(커밋 전 작업트리)로 `./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test` 1회(`DOCKER_HOST`·`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 지정): exit 0, 389건, 실패·오류·건너뜀 0 |
| 커밋 대조 (WORK-11) | production 변경 커밋 `3aacf27`의 `PaymentService.java` SHA-256이 WORK-10에서 실행한 파일과 같음을 확인해 위 전체 회귀를 이 커밋의 근거로 연결했다 |

실행 증거(콘솔·JUnit XML)는 저장소 밖에 보존했다(MASTER 전달 기준).

검증하지 않은 범위:

- 결과 SET 성공 후 락 DELETE 실패, 직렬화 오류, `DataAccessException` 외 예외
- 실제 Redis 연결 단절·timeout과 락 TTL 자연 만료
- DB 서버 다운·네트워크 단절·쿼리 도중 연결 끊김, 실제 JWT 인증을 거친 요청의 커넥션 획득 실패
- CI와 반복 실행 (전체 회귀는 로컬 1회)

### 6. 범위 밖 (후속 후보)

- `ReservationService` 동일 구조, DB 기반 멱등성 저장, `payment.reservation_id` UNIQUE
- 커넥션 획득 실패의 503 매핑, Circuit Breaker(TASK-034), 장애 복구 전략(TASK-043)
- 그 밖의 Redis 경로(분산락, 이벤트 목록 캐시, 인증 토큰, 대기열 등)와 DB 경로

## Related Code / Docs

- `payment/application/PaymentService.java` — `registerAfterCommit`
- `infra/idempotency/IdempotencyRedisRepository.java` — `find`(락 TTL 10초), `save`
- `common/exception/GlobalExceptionHandler.java`, `common/error/CommonErrorCode.java`
- `src/test/java/com/pil97/ticketing/payment/api/PaymentIdempotencySaveFailureIntegrationTest.java`
- `src/test/java/com/pil97/ticketing/payment/api/PaymentConnectionAcquisitionFailureIntegrationTest.java`
- `docs/api/payment.md` — Idempotency-Key 정책
- `docs/architecture/adr/ADR-006-outbox-retry-and-failure.md` — 오류 정보 로그 방식
- 연계 TASK: TASK-028 ([#119](https://github.com/seungpil97/ticketing-reservation-service/issues/119))
