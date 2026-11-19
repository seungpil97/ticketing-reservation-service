# ADR-006: Outbox 처리 실패 기록·백오프·최종 실패와 수동 재처리

## Status

Decided (2026-11) — TASK-033-1(#115)에서 도입. [ADR-005](ADR-005-payment-outbox-notification.md)의 relay 동작 중
실패 처리(§2·§4의 "실패 건은 다음 순회에서 재시도")와 "현재 한계"의 시도 횟수·마지막 오류·다음 시도 시각 미기록을 대체한다.
결제와 Outbox 저장, 알림·처리 완료 같은 커밋, 중복 처리 방어, 스케줄러, FK·보존 정책은 ADR-005를 그대로 따른다.

## Context

ADR-005 relay는 항목 처리가 실패하면 롤백하고 PENDING으로 남긴 뒤 다음 순회에서 다시 시도했다.
실패 정보를 기록하지 않았으므로 다음 문제가 있었다.

- 영구적으로 실패하는 건(UNIQUE·FK 위반, 깨진 payload)이 순회마다 재시도되어 로그와 DB 부하를 만든다.
- 몇 번 실패했는지, 마지막 오류가 무엇인지 운영자가 알 수 없다.
- 계속 실패하는 건을 자동 순회에서 빼거나 사람이 다시 넣는 경로가 없다.
- 처리 지연이 얼마나 쌓였는지 관측할 수 없다.

전제는 ADR-005와 같다: 단일 인스턴스, 외부 브로커 없음([ADR-001](ADR-001-no-kafka.md)), HTTP 관리자 권한 체계 없음.

## Decision

### 1. 상태와 컬럼 (V16)

```text
PENDING --처리 성공-------------------------------> PROCESSED
PENDING --처리 실패, 기록된 실패 < max-attempts----> PENDING (다음 시도 시각 이후 다시 대상)
PENDING --처리 실패, 기록된 실패 = max-attempts----> FAILED  (자동 순회 제외)
FAILED  --수동 재처리 프로시저---------------------> PENDING
```

`V16__add_retry_state_to_outbox_event.sql`이 `outbox_event`에 다음 컬럼을 추가한다. 기존 행은 기본값(실패 0회, 다음 시도 시각 NULL = 즉시 대상, 상태 버전 0)을 갖는다.

| 컬럼 | 의미 |
|---|---|
| `failed_attempts` | **기록된 실패 수**. 실제 시도 수가 아니다 (§7) |
| `next_attempt_at` | 다음 시도 시각. NULL이면 즉시 대상 |
| `last_failed_at` | 마지막으로 기록된 실패의 감지 시각 (초 단위 버림) |
| `last_error_code`·`last_error_type`·`last_sql_state`·`last_vendor_code` | 허용 목록 분류·예외 클래스 단순명·SQLState·vendor code (§5) |
| `state_version` | 상태가 바뀔 때마다 1 증가하는 회차 토큰 (§3) |

### 2. 재시도 정책

- k번째 기록된 실패 뒤 대기 = `min(initial-backoff × 2^(k−1), max-backoff)`. 곱이 상한을 넘거나 k−1 ≥ 31이면 곱셈 없이 상한을 쓴다(overflow 방지).
- 기록된 실패가 `max-attempts`에 도달하면 FAILED·`next_attempt_at` NULL.
- 설정(`application-common.yml`, `OutboxRetryPolicy` 생성 시 검증, 범위 밖이면 기동 실패):

  | key | 기본값 | 허용 범위 |
  |---|---|---|
  | `outbox.relay.retry.max-attempts` | 5 | 1~10 |
  | `outbox.relay.retry.initial-backoff` | 30s | 1s~1h (0초는 기존 즉시 재시도와 같아지므로 거부) |
  | `outbox.relay.retry.max-backoff` | 10m | initial-backoff~24h |
  | `outbox.relay.backlog-log-interval` | 60s | 10s~1h |

- `delayAfter(k)`와 `isExhausted(k)`는 k ≤ 0을 `IllegalArgumentException`으로 거부한다.
- 기본값에서 대기는 30·60·120·240초이고, 5번째 기록된 실패에서 FAILED가 된다. 이 대기 합 450초는 **최소 대기의 합**이며
  처리 시간·잠금 대기·순회 위치·재시작 때문에 실제로는 더 길어진다. **FAILED까지의 시간 상한이 아니다.**
- 기록된 실패가 설정 횟수에 도달하면 자동 재시도를 중단한다. 그 도달 시간과 실제 시도 수의 상한은 보장하지 않는다 (§7).
- 기본값은 설계 판단이며 운영 측정에서 나온 값이 아니다.

### 3. 처리 흐름과 시각

| 시각 | 읽는 곳 | 쓰임 |
|---|---|---|
| T_poll | relay 호출 시작 (`OutboxRelayService`) | 후보 조회·순회 상한 (초 단위 버림) |
| T_lock | 처리기 행 잠금 직후 (`OutboxItemProcessor`) | 잠금 후 due 재확인 |
| T_done | 처리 완료 표시 직전 | `processed_at` = floor_s(T_done) |
| T_fail | 처리 실패를 coordinator가 받은 직후 (처리 트랜잭션 rollback 뒤) | `last_failed_at` = floor_s(T_fail) |
| T_rec | 실패 기록기 행 잠금 직후 (`OutboxFailureRecorder`) | `next_attempt_at` = **ceil_s(max(T_fail, T_rec) + delay)** |
| T_obs | relay가 항목 처리를 마친 뒤 다시 읽은 시각 (`OutboxRelayService`) | backlog 출력 간격 판정·`snapshot(T_obs)` 관측 기준 (WORK-52) |
| T_log | backlog 로그 호출 직후 다시 읽은 시각 | 다음 출력 간격의 기준(직전 출력 시각). 로깅 백엔드가 실제로 기록을 마친 시각은 아니다 |

- 시각은 `Clock` bean(`ClockConfig`, Asia/Seoul)에서 읽는다.
- due 판정은 `next_attempt_at IS NULL OR next_attempt_at <= floor_s(now)`.
- `next_attempt_at`을 초 단위로 **올림**하는 이유: 컬럼이 초 단위 `DATETIME`이라 버림으로 저장하면 최소 대기보다 최대 1초 미만 일찍 due가 될 수 있다.
  올림하면 저장값 ≥ T_rec + delay ≥ T_fail + delay가 된다.
- T_poll이 아니라 실패 감지·기록 시각을 기준으로 하는 이유: 처리 도중 잠금 대기 등으로 오래 기다린 뒤 실패하면, 폴링 시작 기준 다음 시각은 기록하는 순간 이미 지나 있을 수 있다.

**coordinator (`OutboxRelayService`, `@Transactional(NEVER)`, `synchronized`)**
- 지금 처리 대상인 PENDING의 최대 ID를 순회 상한으로 고정하고, 범위 안의 처리 대상 `[id, state_version]`을 ID 오름차순으로 batch만큼 조회한다.
  백오프 중인 행은 후보에서 빠지므로 batch 자리를 차지하지 않는다. 순회 커서·상한·batch·같은 호출 안 새 순회 1회 규칙은 ADR-005 §4와 같다.
- 처리기 예외를 받으면 T_fail을 읽고 분류한 뒤 기록기를 호출한다. 기록기 예외도 잡아서 로그만 남기고 다음 후보를 계속 처리한다.

**처리기 (`OutboxItemProcessor.process(Long)`, `REQUIRES_NEW`)**
- 행을 `FOR UPDATE`로 잠근 뒤 PENDING이고 지금 처리 대상인지 다시 확인한다. 후보 조회 뒤 다음 시도 시각이 미래로 바뀐 행은 건너뛴다.
- 성공하면 알림 INSERT와 처리 완료를 같은 커밋에 반영한다: PROCESSED, `processed_at`, `next_attempt_at` NULL, `state_version` +1.
  `failed_attempts`·`last_failed_at`·`last_error_*`는 실패 이력으로 남긴다.
- 시그니처 `process(Long)`는 유지했다. 처리 안전성은 잠금 후 PENDING·due 재확인과 `UNIQUE(type, payment_id)`가 맡고,
  commit 시점 실패는 메서드 밖(프록시)에서 나므로 잠금 시점 버전을 coordinator에 돌려줄 경로가 없기 때문이다.

**실패 기록기 (`OutboxFailureRecorder.record`, 별도 bean, `REQUIRES_NEW`)**
- 처리 트랜잭션과 분리한다. 같은 트랜잭션이면 처리 rollback이 실패 기록까지 지운다.
- 행을 `FOR UPDATE`로 잠근 뒤 **PENDING이고 `state_version`이 후보 조회 때 값과 같을 때만** 반영한다(`APPLIED`).
  그 밖(PROCESSED·FAILED·버전 불일치·행 없음)은 아무것도 바꾸지 않는다(`IGNORED_STALE`).
- 잠금만으로 부족한 이유: 잠금은 한 트랜잭션 안의 판단을 직렬화할 뿐, "처리 실패 → 기록" 사이 간격에 다른 경로(다른 JVM, 재처리 프로시저)가
  행을 다음 회차로 바꾼 경우를 구분하지 못한다. 단조 증가하는 버전을 비교해 이전 회차의 늦은 기록이 새 회차를 덮지 않게 한다.

### 4. 수동 재처리 — DB 프로시저

`R__outbox_requeue_failed.sql`(Flyway repeatable migration)의 `outbox_requeue_failed(IN p_ids JSON, OUT p_requeued INT)`.
운영 호출 경로는 DB 계정의 `CALL`이며 절차는 [`docs/db/README.md`](../../db/README.md) §8에 둔다.

- 입력 검증은 UPDATE 전에 모두 끝낸다. 위반은 `SQLSTATE '45000'`이며 어떤 행도 바꾸지 않는다.
  - NULL, JSON이 아님, 배열이 아님, 원소 0개 또는 100개 초과
  - 원소의 원래 JSON 타입이 INTEGER가 아님 (문자열·boolean·null·소수·객체·배열)
  - 원래 lexeme이 `^[1-9][0-9]{0,18}$`가 아니거나 19자리이면서 `9223372036854775807` 초과 (지수 표기 `1e3`, 0, 음수, BIGINT 초과)
    — `JSON_TYPE`은 `1e3`과 `9223372036854775808`을 모두 INTEGER로 보고하므로 lexeme을 따로 확인한다(TASK-033-1 M-2 관찰)
  - 중복 ID (정규화된 십진 표기라 텍스트 동일 = 변환 후 ID 동일)
- 반영: 검증된 숫자 목록으로 만든 단일 UPDATE `... WHERE id IN (...) AND status = 'FAILED'`를 `PREPARE`/`EXECUTE`한다.
  PENDING·실패 0·`next_attempt_at` NULL·`state_version` +1, `last_error_*`·`last_failed_at`은 유지한다.
- `p_requeued`: UPDATE가 (다른 트랜잭션의 잠금이 있으면 기다린 뒤) 최신 상태를 보고 FAILED→PENDING으로 전이한 행 수(UPDATE 직후 `ROW_COUNT()`).
  호출 시작 순간이 아니라 UPDATE가 행을 판단한 순간의 상태 기준이다(AC-10b). 같은 요청을 반복하면 그 사이 다시 FAILED가 되지 않은 한 0이다.
- 단일 문장이므로 도중 오류(잠금 timeout·deadlock·trigger 오류 등)는 전부 반영되지 않는다.
- 프로시저 안에서 `START TRANSACTION`·`COMMIT`하지 않는다. 커밋은 호출자의 autocommit 또는 호출자 트랜잭션이 맡는다.
- `SQL SECURITY DEFINER`. CALL 자체에는 실행 계정의 이 프로시저 EXECUTE가, 프로시저 본문에는 DEFINER(=migration 실행 계정)의 `outbox_event` 조회·갱신이,
  migration에는 CREATE ROUTINE·ALTER ROUTINE이 필요하다. 호출 전 FAILED 행 조회는 프로시저 밖이라 조회하는 계정의 `outbox_event` SELECT가 별도로 필요하다.
  실제 운영 권한 검증·부여는 하지 않았다.
- 기존 V16에 넣지 않고 repeatable로 둔 이유: 본문을 바꾸면 Flyway가 다시 적용한다. V 버전에 넣으면 이미 적용된 DB에서 checksum 불일치가 난다.

검토한 대안:

| 대안 | 판단 |
|---|---|
| HTTP 관리 API | 권한 체계가 없어 로그인한 회원 누구나 호출할 수 있다. 제외 |
| 같은 jar의 Runner형 운영 명령 | HOLD 만료·대기열 스케줄러가 조건 없이 함께 실행된다. 제외 |
| 검증된 SQL 스크립트를 운영자가 직접 실행 | 입력 검증과 중단 판단을 강제할 수 없다. 프로시저를 쓸 수 없을 때의 대안 |
| 애플리케이션 서비스만 구현 | 운영에서 호출할 진입점이 없다 |

### 5. 오류 정보와 로그

- 분류(`OutboxFailureClassifier`)는 예외 타입과 숫자 코드만 본다. `getMessage()`·`getLocalizedMessage()`·`toString()`을 호출하지 않는다.
  - `last_error_type`: 원인 체인의 허용 목록(Jackson 처리 예외 계열, `java.sql` 예외 계열) 중 가장 깊은 예외의 단순명, 없으면 `OTHER`
  - `last_sql_state`: 가장 깊은 `SQLException`의 값이 5자리 영대문자·숫자일 때만. `last_vendor_code`는 그 error code
  - `last_error_code`: Jackson 처리 예외 PAYLOAD_INVALID, vendor 1062 DUPLICATE_KEY, 1452 FOREIGN_KEY, 1205 LOCK_WAIT_TIMEOUT, 1213 DEADLOCK,
    그 밖 SQLException DB_ACCESS, 그 밖 UNKNOWN
- 새 오류 정보 컬럼(`last_error_code`·`last_error_type`·`last_sql_state`·`last_vendor_code`)에는 예외 원문 메시지·payload·접속 URL을 복사하지 않는다.
  기존 `payload` 컬럼은 ADR-005대로 이벤트 원문(JSON)을 그대로 저장한다.
- relay 로그는 Outbox ID와 위 분류 정보만 남긴다. 예외 객체를 로그에 첨부하지 않으므로 스택 트레이스도 남지 않는다(진단 비용).
- **보장 범위**: (1) 새 오류 정보 컬럼 (2) `com.pil97.ticketing.outbox` 아래 애플리케이션 logger가 남기는 로그 이벤트.
  애플리케이션 전체 출력(stdout·stderr)과 다른 logger의 비노출은 보장하지 않는다.
  전역 logger `org.hibernate.orm.jdbc.bind`가 payload 바인딩 값을 출력한 것을 관찰한 뒤, 공통 설정(`application-common.yml`)의 기본값을 `"OFF"`로 바꿨다 (§8).
  다른 프로필·외부 설정이 이 값을 바꾸는 경우는 보장하지 않는다.

### 6. 처리 지연 관측

- `OutboxBacklogQuery.snapshot(now)`: 지금 대상인 PENDING 건수, 백오프 중인 PENDING 건수, FAILED 건수,
  가장 오래된 대상 PENDING의 경과 초, PENDING 전체에서 가장 오래된 행의 경과 초.
- 가장 오래된 대상 PENDING의 경과 초는 그 행이 이미 기다린 시간으로, 그 행 처리 지연의 **하한**이다. 처리 지연이나 backlog 건수의 운영 상한을 보장하지 않는다.
- 경과 초는 DB가 채운 `created_at`과 애플리케이션 `Clock`의 차이라 두 시계가 섞인다.
- relay는 건수 중 하나라도 0보다 크고 직전 출력 후 `backlog-log-interval` 이상 지났을 때 `Outbox backlog. due=… backoff=… failed=… oldestDueAgeSeconds=… oldestPendingAgeSeconds=…`를 남긴다.
  대상이 0건이고 FAILED만 있어도 출력한다. 메트릭 인프라는 도입하지 않았다.
- 시각 (WORK-52 G-1 수정): 후보 조회는 T_poll, backlog 관측과 간격 판정은 항목 처리 후의 T_obs, 다음 간격의 기준은 로그 호출 직후의 T_log다.
  처리가 오래 걸려도 관측은 처리 후 상태 기준이고, 최소 간격은 실제 로그 호출 시각부터 센다. 관측 실패·빈 결과면 기준 시각을 갱신하지 않는다.
  대상·백오프 판정 조건 자체는 후보 조회와 같지만 기준 시각은 다를 수 있다. 비동기 로깅의 최종 기록 시각까지는 검증하지 않았다.
- 비용: 출력 간격 안이 아니면 relay 호출마다 snapshot을 조회한다. 같은 읽기 전용 트랜잭션에서 PENDING·FAILED 존재 조회를 먼저 하고,
  없으면 그 1개만 실행하고 빈 결과를 반환한다. 있으면 존재 조회와 기존 집계 5개로 총 6개를 실행한다 (격리 테스트에서 실행 SQL 수 측정).
  빈 조회 시각은 저장하지 않고 결과를 캐시하지 않는다. 실제 처리 시간과 대량 데이터에서의 성능은 측정하지 않았다.

## Consequences

### 7. 정확한 의미와 한계

- `failed_attempts`는 기록된 실패 수다. 다음 경우 실패가 기록되지 않아 실제 시도 수보다 작을 수 있다:
  실패 기록 트랜잭션 자체 실패, 처리 도중 프로세스 종료, 후보 조회 때 읽은 상태 버전과 처리 시점 회차가 달라 기록이 무시된 경우.
  수동 재처리는 횟수를 0으로 되돌리므로 재처리를 포함한 전체 시도 수에는 상한이 없다.
- 회차 보호는 오래된 기록이 새 회차를 덮지 않게 할 뿐이다. 다중 인스턴스 처리 분배를 지원하지 않는다(ADR-005 전제 유지).
- 실패 기록기는 엔티티를 잠그고 필드를 바꿔 flush하므로 payload를 포함한 전체 컬럼 UPDATE가 실행된다.
- 프로시저가 `PREPARE`에 세션 변수 `@outbox_requeue_failed_sql`을 쓴다. 정상 종료 시 NULL로 되돌리고 prepared statement를 해제한다.
  동적 SQL 구간의 오류는 `EXIT HANDLER`가 받아, 이번 호출이 PREPARE에 성공했으면 prepared statement를 해제하고 세션 변수를 NULL로 되돌린 뒤
  `RESIGNAL`로 원래 오류를 그대로 전달한다. 격리 테스트로 확인한 오류 경로는 UPDATE 잠금 대기 timeout(1205) 하나다 (§8).
- `Clock.system`은 벽시계라 시각이 뒤로 보정되면 재시도가 늦어질 수 있다.

### 8. 실제 검증 범위

TASK-033-1에서 격리 MariaDB 컨테이너(Testcontainers `mariadb:11.6`)로 실행한 근거다.

| 범위 | 근거 |
|---|---|
| 정책·분류 Unit | `OutboxRetryPolicyTest`·`OutboxFailureClassifierTest` 27건 GREEN (WORK-23). 분류의 1213 deadlock은 synthetic 값만 검증 |
| 실패 기록·시각 경계·소진·성공 후 이력 | `OutboxRetryIntegrationTest`·`OutboxRetryTimingIntegrationTest` GREEN (WORK-22) |
| 상태·회차 보호, 실제 잠금 대기 후 최신 상태 판단 | `OutboxFailureRecordGuardIntegrationTest`(AC-8·9·10a), `OutboxRequeueProcedureIntegrationTest`(AC-10b) GREEN |
| AC-10b 잠금 대기 관찰 하네스 | AC-10b만 테스트 전용 `PrimaryKeyLockProbe`를 쓴다. 대기 행의 `lock_data`가 비어 있으면 별도 연결의 기본키 `FOR UPDATE`로 같은 잠금 위치를 확인한다. probe 경로는 강제 선택한 격리 테스트(commit·rollback 수락, 다른 기본키 거부, 기한 경과·정리 실패 시 실패 처리)로 검증했다. 실제 `lock_data` NULL에서의 probe 성공은 격리 실행 1회(AC-10b 두 사례 중 PROBE 1회, DIRECT 1회)에서만 관찰했다. PROBE 사례가 commit 사례라는 것은 출력 순서·소요 시간에 근거한 추론이다. 다른 잠금 대기 사례는 기존 `LockWaitObserver`를 쓴다 |
| 실패 기록 트랜잭션 장애 시 부분 반영 없음 | AC-11b: 테스트 전용 trigger로 기록 UPDATE를 거부해 확인 |
| 실제 UNIQUE·FK·깨진 payload | AC-12: 23000/1062, 23000/1452, PAYLOAD_INVALID/JsonParseException. 기대값은 classifier와 무관한 직접 JDBC 관찰(H-7)에서 정했다 |
| 프로시저 입력 검증·반영 건수·반복 요청 | AC-13 |
| 프로시저 오류 후 세션 정리 | AC-13 잠금 timeout 사례: 같은 연결에서 원래 오류 HY000/1205 유지, 세션 변수 NULL, prepared statement 해제(재해제 시 1243) |
| 프로시저 부분 실패 원자성 | AC-13: 테스트 전용 trigger로 대상 한 행이 실제 갱신된 뒤 다음 행에서 장애를 일으켜 CALL 실패·전 대상 행 원래 값을 확인했다 (production 프로시저 증거) |
| DB 문장 rollback 메커니즘 | H-5: 테스트 probe 문장이 2행 갱신 뒤 잠금 timeout(1205)으로 실패하면 문장 전체가 rollback됨 (`innodb_rollback_on_timeout`=0). **production 프로시저 증거가 아니라 DB 메커니즘 증거다** |
| Outbox logger 비노출 | AC-14: payload 외 Outbox 저장 컬럼과 Outbox logger 이벤트를 직접 단정. 전체 캡처 출력과 전역 logger는 관찰만 한다 |
| 바인딩 값 로그 기본 차단 | AC-14 격리 실행에서 payload 사례의 bind 출력 없음 (공통 설정 `"OFF"` 적용 후) |
| 관측 | AC-15. 빈 상태 SQL 1개·대상 있음 6개는 `OutboxBacklogQueryCostIntegrationTest`. 긴 처리 후 관측 시각·최소 간격은 WORK-52 신규 2건(RED 2/2 → GREEN 2/2), 관련 2개 클래스 11/11 |
| 전체 회귀 (공유 테스트 DB) | WORK-47 (2026-11-16): 당시 소스(WORK-52 수정 **전**)로 `./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test` 1회, exit 0, 381/381, 51개 클래스. 공유 DB의 이전 `R__`가 현재 버전으로 repeatable 재적용됨(success). 종료 단계 stdout에 Netty `RejectedExecutionException` ERROR 1건 (원인·영향 미확정, 테스트 실패·오류 0과 구분) |
| 전체 회귀 (수정본, 공유 테스트 DB) | WORK-59 (2026-11-17): WORK-52~54 수정을 반영한 소스로 같은 명령 1회(`DOCKER_HOST`·`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 지정), exit 0, 383/383, 51개 클래스, 실패·오류·건너뜀 0. WORK-47 대비 클래스별 차이는 `OutboxBacklogIntegrationTest` 4→6(+2)뿐. 공유 DB에는 새 migration·`R__` 재적용이 없었고, 확인은 집계·스키마 범위다(`outbox_event` PENDING 99→110, notification·payment 0). 기존 행 각각의 불변은 확인하지 않았다. 프로필 미지정 Controller 슬라이스 3개 클래스 28건은 기본 프로필 `dev`로 실행됐다(`-D` 값은 테스트 JVM에 전달되지 않음). WORK-47의 Netty ERROR는 이번 1회에서 관찰되지 않았다 |
| 기존 계약 회귀 | `OutboxRelayIntegrationTest` 전체(AC-6·6B-2·6B-3은 백오프 반영 After 계약)·Sweep·Recovery·Scheduler·SchedulerIsolation GREEN |

검증하지 않은 범위:

- CI. 수정본 전체 회귀는 WORK-59 로컬 1회(383건)이며, 반복 실행이나 다른 환경의 결과는 없다
- WORK-47 종료 단계 Netty ERROR의 원인·영향·재현성 (후속 조사 항목). WORK-59 1회 미관찰은 해결이나 무해함의 근거가 아니다
- 프로필 미지정 Controller 슬라이스(`AuthControllerTest`·`PaymentControllerTest`·`QueueControllerTest`)는 기본 프로필 `dev`로 뜬다.
  현재 TASK 수정 근거는 확인되지 않았지만, 외부 접속의 전면 부재와 JVM 전역 로깅 영향은 입증하지 않았다
- 다중 인스턴스 동시 실행, relay 수준 동시성 전체(F-3)
- 운영·dev DB 권한과 프로시저 실제 운영 호출
- 전역 로그: 공통 설정 기본값을 `"OFF"`로 바꾼 뒤의 확인은 격리 테스트 실행 범위다. 운영 프로필·외부 설정의 실제 값은 확인하지 않았다.
  실패 기록기의 전체 컬럼 UPDATE가 payload를 다시 바인딩하는 구조는 그대로다.
- 프로시저 오류 정리: PREPARE 이전 단계 오류 등 잠금 timeout 이외의 오류 경로
- AC-10b probe: 실제 `lock_data` NULL 상황에서 probe 성공의 빈도·장기 안정성 (관찰은 1회)
- 결제 직후 처리 지연·backlog의 실제 운영 분포

검토 상태 (2026-11-17 기준, 판정 아님):

- Claude 1차 검토: WORK-51에서 Risk Reviewer로 수행·기록 (Critical 0, High 0, Medium 3, Low 5)
- GPT 독립 검토 REVIEW-1: 권고 "수정 후 진행", 항목 G-1~G-4 (MASTER 전달 기준. 검토 원문은 이 저장소에 없다)
- 대응: G-1(backlog 시각)은 WORK-52, G-3·G-4(검증 상태·표현 정정)는 WORK-53, G-2(후속 변경 추적)는 보완 기록과 Issue #115 Scope·Notes 원격 반영(WORK-57)
- GPT 재검토 REVIEW-2: G-1~G-4 종료와 검토 입력 누락 해소 (MASTER 인계 기준. 검토 원문은 이 저장소에 없다). 변경 파일 전체에 대한 신규 검토나 TASK 완료 판단은 아니다
- 수정본 전체 회귀: WORK-59 383/383 (위 표)
- 완료되지 않은 것: CI, Git·PR, 학습 gate(Issue #115 계획: merge 전 설명 중심 gate): 필수 설명은 사용자 전달 전 초안, 기록(INTERVIEW-NOTES)은 WORK-62에서 최신화했으나 MASTER 정확성 검토 전, 사용자 설명 수령 확인·MASTER 정확성 검토 미완료
- 상세: `docs/reviews/TASK-033-1/POST-FREEZE-ADDENDUM.md`

## Related Code / Docs

- `src/main/resources/db/migration/V16__add_retry_state_to_outbox_event.sql`
- `src/main/resources/db/migration/R__outbox_requeue_failed.sql`
- `outbox/domain/OutboxRetryPolicy.java`, `OutboxEvent.java`, `OutboxStatus.java`, `OutboxErrorCode.java`
- `outbox/domain/repository/OutboxEventRepository.java`
- `outbox/application/OutboxRelayService.java`, `OutboxItemProcessor.java`, `OutboxFailureRecorder.java`, `OutboxFailureClassifier.java`, `OutboxBacklogQuery.java`
- `common/config/ClockConfig.java`, `src/main/resources/application-common.yml`
- `docs/architecture/adr/ADR-005-payment-outbox-notification.md`, `docs/db/README.md`
- `docs/reviews/TASK-033-1/POST-FREEZE-ADDENDUM.md` — 동결 이후 변경 보완 기록과 검토 상태
- 연계 TASK: TASK-033 (#108), TASK-033-1 (#115)
