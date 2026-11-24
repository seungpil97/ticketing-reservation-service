# ADR-008: 결제 결과 저장 실패 후 복구 경로 — 예약 소유자의 결제 상태 조회

## Status

Decided (2026-11) — TASK-043([#123](https://github.com/seungpil97/ticketing-reservation-service/issues/123))에서 결정.
[ADR-007](ADR-007-payment-redis-db-failure.md)의 S1 결정(커밋 후 결과 저장 실패 시 첫 응답 201 유지)과 결제 멱등성 구조, `POST /payments` 계약은 그대로 두고,
첫 응답을 받지 못한 클라이언트가 결제 결과를 확인하는 조회 경로만 추가한다.

## Context

ADR-007 S1: 결제가 DB에 커밋된 뒤 `afterCommit`에서 Redis 결과 키 `idempotency:payment:{key}` SET이 실패하면,
서버는 커밋된 결제로 201을 응답하고 ERROR 로그를 남긴다. 결과 키는 기록되지 않고 처리 중 락 키는 최초 획득 시각부터 10초 TTL로 남는다(ADR-007 §1·§3).

클라이언트가 이 첫 201 응답까지 받지 못하면, 같은 `Idempotency-Key`로 `POST /payments`를 다시 보내도 첫 응답을 복원할 수 없다.

- replay(200)는 저장된 결과 키가 있을 때만 일어난다. 결과 키가 없으므로 replay되지 않는다.
- 락 키가 남아 있으면 409 `IDEMPOTENCY-001`, 락 키가 없으면 이미 CONFIRMED인 예약의 상태 가드에서 409 `PAYMENT-002`다(ADR-007 §3).
- 결제 상태를 확인할 다른 API도 없었다(ADR-007 §4 "결제 상태 단건 조회 API는 없다").

멱등키와 fingerprint는 Redis에서 쓰이는 방식이 다르다(`IdempotencyRedisRepository`, `IdempotencyEntry`).

- 멱등키(`Idempotency-Key` 헤더 원문)는 두 Redis 키의 이름에만 쓰인다.
  - 결과 키 `idempotency:payment:{key}`(TTL 24시간)
  - 처리 중 락 키 `idempotency:payment:lock:{key}`(SETNX, TTL 10초, 값은 고정 문자열 `processing`)
- fingerprint(요청 DTO를 JSON으로 직렬화한 문자열의 SHA-256)는 결과 키의 값 `IdempotencyEntry{fingerprint, responseBody}`에만 저장된다. `responseBody`는 직렬화한 첫 응답(`paymentId`·`status`·`paidAt`·`refundedAt`)이다.
- 락 키의 값에는 fingerprint, 응답 본문, 예약 ID, 결제 ID가 없다.

S1에서는 결과 키가 기록되지 않고 락 키만 TTL이 끝날 때까지 남는다.
락 키 이름은 그 멱등키로 처리 중인 요청이 있었다는 것만 알려 준다.
fingerprint, 첫 응답 본문, 그리고 이 멱등키가 어느 예약·결제의 요청인지는 남은 락 키로 복원할 수 없다. 락이 만료되면 Redis에도 해당 정보가 남지 않는다.

DB에 남는 정보도 첫 응답을 그대로 재현하기에 부족하다.

| 값 | DB 저장 | Redis에 일시적으로 남는 위치 | 결과 키 없이 확인 |
|---|---|---|---|
| 결제 ID·상태·금액·결제 시각·환불 시각 | `payment` (V13, V14) | 결과 키 값의 `responseBody`, 금액은 제외 (S1에서는 기록되지 않음) | 가능 (DB) |
| 예약 소유자 | `reservations.member_id` (V7) | 없음 | 가능 (DB) |
| 멱등키 | 저장하지 않음 | 결과 키·락 키의 이름 | 불가 (락 키 이름에는 남지만 예약·결제와 연결되지 않음) |
| 요청 fingerprint | 저장하지 않음 | 결과 키 값에만 있음. 락 키 값에는 없음 | 불가 |
| 결제 요청자 | 저장하지 않음 (`POST /payments`는 로그인 회원을 받지 않음) | 없음 | 불가 |
| 첫 응답의 결제 시각 원래 정밀도 | `payment.paid_at`은 `DATETIME`(초 단위) | 결과 키 값의 `responseBody` (S1에서는 기록되지 않음) | 불가 (응답은 커밋 전 엔티티 값) |

또한 결제 행은 이후 바뀔 수 있다. 환불하면 `status`가 `REFUNDED`가 되고 `refunded_at`이 기록된다(`Payment.refund()`).

## Decision

### 1. 예약 소유자 전용 DB 조회 추가

`GET /reservations/{reservationId}/payment`를 추가한다(`PaymentController.getPaymentOfReservation` → `PaymentService.getPaymentOfReservation`).

처리 순서:

1. `reservationRepository.findById(reservationId)`로 예약을 조회한다(잠금 없음).
2. 예약 회원 ID와 로그인 회원 ID가 같은지 확인한다. 예약이 없거나 소유자가 아니면 같은 404 `RESERVATION-001`이다.
   두 경우를 같은 응답으로 만들어, 다른 회원 예약의 존재 여부를 드러내지 않는다.
3. 소유자 확인 뒤에만 `paymentRepository.findByReservationId`로 결제를 조회한다. 결제 행이 없으면 404 `PAYMENT-001`이다.
4. 기존 `PaymentResponse.of(payment)`로 `paymentId`·`status`·`paidAt`·`refundedAt`을 반환한다(200, `ApiResponse.success`).

- 인증은 기존 방식을 따른다(`@AuthenticationPrincipal Member`, 미인증은 보안 entry point의 401 `AUTH-003`).
- 판단 기준은 예약 소유자다. 결제 요청자와 예약 소유자가 같다고 가정하지 않는다.
- 조회 값은 요청 시점에 DB에 기록된 상태다. `SUCCESS`·`FAIL`·`REFUNDED`가 그대로 나오고, 결제 행이 있는 `FAIL`도 200이다.
- 서비스 클래스 기본값인 `@Transactional(readOnly = true)`에서 실행한다. 엔티티를 수정하지 않고 이벤트를 발행하지 않는다.
- `PaymentService.getPaymentOfReservation`은 예약(`reservationRepository.findById`)과 결제(`paymentRepository.findByReservationId`)를 DB에서 조회한다.
  Redis 멱등 결과 키와 처리 중 락에는 접근하지 않는다(`IdempotencyRedisRepository`·`StringRedisTemplate` 호출 없음).
  그래서 이 메서드는 결과 키 존재 여부나 처리 중 락 유무와 관계없이 같은 DB 결과를 반환하고, 결과 키와 락 키를 바꾸지 않는다.
  유효한 Bearer 토큰으로 인증하는 요청은 이 메서드 호출 전에 `JwtAuthenticationFilter`에서 Redis 블랙리스트 확인을 거친다(§4 참조).
- `POST /payments`, 환불 API, DB 스키마, 오류 코드는 바꾸지 않는다(신규 오류 코드 없음).

### 2. 비교한 방안

| 항목 | 선택: 예약 소유자의 현재 상태 DB 조회 | 검토: 같은 키 재요청에 첫 POST 응답 재현 (DB 기반 멱등성) |
|---|---|---|
| 사용자가 받는 결과 | 조회 시점의 결제 상태 | 첫 요청과 같은 결제 응답(200 replay) |
| 필요한 저장 정보 | 이미 있는 `payment` 행과 `reservations.member_id` | 멱등키·fingerprint·요청자(·응답)를 결제 트랜잭션 안에서 DB에 기록 |
| 스키마 변경 | 없음 | 새 migration(테이블 또는 컬럼), 멱등키 UNIQUE 설계 |
| 환불 뒤 상태 변화 | 환불 뒤에는 `REFUNDED`를 반환 (현재 상태와 일치) | 첫 응답(`SUCCESS`)을 재현하려면 응답 스냅샷을 따로 보관해야 함 |
| 기존 POST 계약 영향 | 없음. TASK-028 동결 테스트 6개를 회귀 기준으로 그대로 사용 | S1 재요청 기대값(409 `IDEMPOTENCY-001`·`PAYMENT-002`)이 바뀌어 동결 계약 변경 필요 |
| 접근 통제 | 예약 소유자 기준 (기존 환불의 소유권 검증 패턴) | 멱등키가 회원 단위가 아니고 POST가 요청자를 받지 않아, 키 범위·요청자 검증 재설계 필요 |

선택 근거:

- 필요한 값이 모두 DB에 있어 스키마 변경 없이 검증할 수 있다.
- 결제의 사실 근거는 DB라는 ADR-007의 판단과 맞는다.
- 환불 같은 이후 상태 변화를 그대로 반영한다.
- 기존 POST 계약과 TASK-028 동결 테스트를 바꾸지 않는다.

첫 응답 재현은 스키마 변경, 동결 계약 변경, 키 범위 재설계를 함께 요구하므로 이번 범위에서 제외한다(§6).

### 3. paidAt의 기준

조회 응답의 `paidAt`은 DB `payment.paid_at` 값이다. 첫 201 응답의 `paidAt`은 커밋 전 엔티티 값이라 초 미만 자릿수가 있을 수 있다.
반면 `paid_at`은 `DATETIME`(초 단위)이다. 따라서 조회 응답의 `paidAt`을 첫 응답과 문자열 그대로 같다고 보장하지 않는다.
테스트는 별도 DB 조회로 읽은 해당 결제 행의 `paid_at`과 같은 시각인지를 단언한다.
이 차이를 맞추려고 기존 POST 동작을 바꾸지는 않는다.

## Consequences

### 4. 한계

- 상태 조회이며 멱등 응답 재현이 아니다. 결제 성공 뒤 환불했다면 `REFUNDED`가 조회된다.
- 결제 행이 없을 때의 404 `PAYMENT-001`은 "조회 시점에 기록된 결제가 없다"는 뜻이다. 결제 실패 확정이나 즉시 재결제 허용을 뜻하지 않는다.
  처리 중인 결제가 아직 커밋되지 않았을 수 있다(진행 중 POST와 동시 조회는 검증하지 않음).
- 한 예약에 결제 행이 2개 이상인 경우는 검증하지 않았다. 현재 흐름(예약 비관락·PENDING 가드, 실패 시 예약 FAILED)에서는 생기지 않지만 `payment.reservation_id` UNIQUE 제약은 없다.
  여러 행이 생기면 `findByReservationId`가 단건 조회에 실패한다.
- 실제 Redis 전체 단절이나 DB 장애 중에도 조회된다고 보장하지 않는다.
  - 이 조회는 DB에 의존한다.
  - 동결된 조회 테스트는 `SecurityMockMvcRequestPostProcessors.authentication(...)`으로 인증된 `Member`를 테스트에서 제공하고, `Authorization` 헤더는 보내지 않는다.
  - `JwtAuthenticationFilter`는 Bearer 토큰이 없으면 바로 다음 필터로 넘긴다(코드상). 토큰이 있을 때만 토큰 검증, Redis 블랙리스트 확인(`TokenService.isBlacklisted`), 회원 DB 조회(`memberRepository.findById`)를 수행한다.
  - 따라서 유효한 Bearer 토큰의 블랙리스트 확인과 회원 조회를 포함한 실제 인증 경로는 이 테스트로 검증하지 않았다. 그 경로에서 Redis·DB 장애가 났을 때의 동작도 확인하지 않았다.
  - 미인증 401 `AUTH-003`(AC-8)은 인증 정보와 `Authorization` 헤더가 모두 없는 요청으로 검증했다. 보안 entry point의 응답을 확인한 것이며, 위의 토큰 인증 경로나 장애 시 동작을 검증한 것은 아니다.
  - Redis·DB 장애 중의 조회는 어떤 테스트도 재현하지 않았다. 장애 주입은 첫 POST의 결과 키 SET 1회 실패(AC-1·2)에 한정된다.
- `POST /payments`의 요청자·예약 소유자 대조와 회원 단위 멱등키 범위는 이번에 확인·변경하지 않았다. 다른 계층까지 확인한 결함으로 확정하지 않은 별도 조사 후보다.

### 5. 실제 검증 범위

테스트 계약은 Issue #123 본문 `## Test`의 Test Contract다. 실행 전 동결 기록은
[FREEZE-RECORD](https://github.com/seungpil97/ticketing-reservation-service/issues/123#issuecomment-7055830274)다.

| 범위 | 근거 |
|---|---|
| S1 복구 조회 (AC-1·2) | `src/test/java/com/pil97/ticketing/payment/api/PaymentStatusRecoveryQueryIntegrationTest.java` (3개 메서드). 결과 키 SET 1회 실패 주입(H-Q1: 주입 1회, 별도 커넥션으로 SUCCESS 커밋 확인, 결과 키 부재, 첫 POST 201). 결과 키 없음에서 200, paymentId·status는 첫 201과, paidAt은 DB `paid_at`과 같음. 락 존재(PEXPIRE 60초 조성)·락 부재(DELETE 조성)에서 같은 200 결과, 락 키 유지·PTTL 연장 없음(H-Q2, 실제 TTL 만료 관찰 아님). 대상 예약·결제·Outbox·키 무변경 |
| 접근 통제·오류·현재 상태 (AC-3~8) | `src/test/java/com/pil97/ticketing/payment/api/PaymentStatusQueryIntegrationTest.java` (6개 메서드). 다른 회원·없는 예약 모두 404 `RESERVATION-001`, 두 응답 구별 불가, 결과 키 값 유지·PTTL 연장 없음. 결제 없음 404 `PAYMENT-001`. 환불 뒤 `REFUNDED`(paidAt 유지, refundedAt = DB 값). 실패 결제 `FAIL`(시각 null). 미인증 401 `AUTH-003`. 오류 응답의 `data`·`error.details`는 키 있음·null |
| 기존 POST 회귀 (AC-R) | TASK-028 동결 테스트 `PaymentIdempotencySaveFailureIntegrationTest`(5개), `PaymentConnectionAcquisitionFailureIntegrationTest`(1개), 수정 없음 |
| Valid RED (WORK-13) | 구현 전 같은 15개 실행: 신규 8개(AC-1, AC-2 두 경우, AC-3~7)가 사전 조건 충족 후 GET 상태 단언에서 실패했다. 테스트별 진단에서 직접 확인한 값은 HTTP 500, 처리기 `ResourceHttpRequestHandler`, `NoResourceFoundException`이며, 대상 GET 매핑 부재가 원인이다. AC-8과 AC-R 6개는 통과. WORK-13 테스트별 진단·발췌에는 응답 본문의 오류 코드가 출력되지 않았다. 이 500이 `COMMON-007`이라는 근거는 `GlobalExceptionHandler`의 `Exception` 처리기 매핑(`CommonErrorCode.INTERNAL_ERROR`)과 Issue #123 §F의 기준 HEAD 사전 관찰(WORK-9)이다 |
| GREEN (WORK-14) | 구현 후 같은 15개 실행: 15개 통과(실패·오류·건너뜀 0), 동결 테스트 파일 해시는 FREEZE-RECORD와 같음 |
| 전체 회귀 (WORK-16) | 저장소 표준 명령 `./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test` 1회(로컬, `DOCKER_HOST`·`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 지정): exit 0, 55개 클래스 398건, 실패·오류·건너뜀 0 |

실행 증거(콘솔·JUnit XML)는 저장소 밖에 보존했다(MASTER 전달 기준). 위 실행은 모두 로컬이며 CI 결과는 아직 없다.

검증하지 않은 범위:

- 진행 중인 POST와 동시 조회, 한 예약의 결제 행 2개 이상
- 유효한 Bearer 토큰으로 실제 인증 경로(블랙리스트 확인·회원 조회)를 거친 조회
- 실제 Redis 연결 단절·timeout, DB 장애 중 조회
- 락 TTL 자연 만료

### 6. 범위 밖 (후속 후보)

- DB 기반 멱등성(첫 응답 재현), 결과 재기록, `payment.reservation_id` UNIQUE
- 커넥션 획득 실패의 503 매핑, Circuit Breaker(TASK-034), Failover 테스트(TASK-055)
- `POST /payments` 요청자 소유권 대조와 회원 단위 멱등키 범위(별도 조사 후보)
- `ReservationService`의 같은 afterCommit 구조

## Related Code / Docs

- `payment/api/PaymentController.java` — `getPaymentOfReservation`
- `payment/application/PaymentService.java` — `getPaymentOfReservation`
- `payment/domain/repository/PaymentRepository.java` — `findByReservationId`
- `payment/api/dto/response/PaymentResponse.java` — `of`
- `src/test/java/com/pil97/ticketing/payment/api/PaymentStatusRecoveryQueryIntegrationTest.java`
- `src/test/java/com/pil97/ticketing/payment/api/PaymentStatusQueryIntegrationTest.java`
- `docs/api/payment.md` — 3) Get Payment of Reservation, 결제 응답을 받지 못했을 때의 조회 안내
- [ADR-007](ADR-007-payment-redis-db-failure.md) — S1 결과 저장 실패, 재요청 409 동작
- 연계 TASK: TASK-043 ([#123](https://github.com/seungpil97/ticketing-reservation-service/issues/123)), TASK-028 ([#119](https://github.com/seungpil97/ticketing-reservation-service/issues/119))
