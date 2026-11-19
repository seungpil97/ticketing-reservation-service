## DB Setup (Flyway)

이 프로젝트는 **Flyway**로 DB 스키마를 버전 관리합니다.
애플리케이션 실행 시 `classpath:db/migration` 아래의 SQL이 자동 적용됩니다.

### Migration 규칙

* 위치: `src/main/resources/db/migration`
* 파일명: `V{버전}__{설명}.sql`
    * 예) `V1__init.sql`, `V2__seed_members.sql`
* **기존 migration 파일은 수정하지 않습니다.**
    * 변경이 필요하면 **새 버전 파일**을 추가합니다.

---

## 0) 포트 컨벤션

| 환경      | 컨테이너                   | DB               | 포트        | 용도              |
|---------|------------------------|------------------|-----------|-----------------|
| 로컬 dev  | ticketing-mariadb-dev  | ticketing_flyway | **13305** | 애플리케이션 개발       |
| 로컬 test | ticketing-mariadb-test | ticketing_test   | **13307** | 로컬 테스트 실행       |
| 로컬      | ticketing-redis        | Redis            | **6379**  | 분산락 / 캐시        |
| CI      | GitHub Actions service | ticketing_test   | **3306**  | Actions 내부 컨테이너 |

> 로컬에서 `./gradlew test` 실행 전 반드시 `docker-compose up -d` 로 두 컨테이너가 모두 올라와 있어야
> 합니다.
>
> Outbox relay 테스트(TASK-033)는 Testcontainers로 `mariadb:11.6` 컨테이너를 별도로 띄웁니다.
> 테스트를 실행하는 환경에서 Docker 데몬에 접근할 수 있어야 하고, 첫 실행 때 `mariadb:11.6`과 Testcontainers 보조 이미지를 내려받습니다.
> Docker Desktop이 아닌 런타임(예: Colima)에서는 소켓 위치를 지정해야 할 수 있습니다.
> 예: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew test -Dspring.profiles.active=test`

---

## 1) Local DB 준비

### A) docker-compose (추천)

프로젝트 루트의 `docker-compose.yml` + `.env`로 로컬 DB를 재현합니다.

#### 1) `.env` 준비

```bash
cp .env.example .env
```

`.env` 예시:

```dotenv
MARIADB_USER=ticketing_user
MARIADB_PASSWORD=local_password
MARIADB_ROOT_PASSWORD=root_password
```

> 주의: `.env`는 로컬 전용이며 git에 커밋하지 않습니다.

#### 2) DB 실행

```bash
docker-compose up -d
docker-compose ps
```

`ticketing-mariadb-dev`, `ticketing-mariadb-test` 두 컨테이너가 모두 `healthy` 상태인지
확인합니다.

#### 3) 애플리케이션 실행 (dev)

dev 프로필은 `ticketing_flyway` DB (포트 `13305`)를 바라봅니다.

```bash
export DEV_DB_PASSWORD='your_password'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

`DEV_DB_HOST`, `DEV_DB_USERNAME`을 생략하면 기본값(`127.0.0.1:13305`, `ticketing_user`)을
사용합니다.

#### 4) 테스트 실행 (test)

test 프로필은 `ticketing_test` DB (포트 `13307`)를 바라봅니다.

```bash
export TEST_DB_PASSWORD='your_password'
./gradlew test -Dspring.profiles.active=test
```

`TEST_DB_URL`, `TEST_DB_USERNAME`을 생략하면 기본값(`127.0.0.1:13307`, `ticketing_user`)
을 사용합니다.

#### 5) Colima 환경에서 localhost 포워딩이 안 되는 경우

일부 Colima 환경에서는 `localhost` 포워딩이 동작하지 않을 수 있습니다.
`colima status`로 VM IP를 확인한 뒤 직접 지정합니다.

```bash
colima status

# dev 실행 예시
export DEV_DB_HOST=192.168.106.2
export DEV_DB_PASSWORD='your_password'
./gradlew bootRun --args='--spring.profiles.active=dev'

# test 실행 예시
export TEST_DB_URL=jdbc:mariadb://192.168.106.2:13307/ticketing_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
export TEST_DB_PASSWORD='your_password'
./gradlew test -Dspring.profiles.active=test
```

#### 6) 검증

```bash
curl -i http://localhost:8080/health/db
```

---

### B) 수동으로 DB/User 생성 (옵션)

```sql
-- dev DB
CREATE
DATABASE ticketing_flyway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

-- test DB
CREATE
DATABASE ticketing_test
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

CREATE
USER 'ticketing_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON ticketing_flyway.* TO
'ticketing_user'@'%';
GRANT ALL PRIVILEGES ON ticketing_test.* TO
'ticketing_user'@'%';
FLUSH
PRIVILEGES;
```

---

## 2) 설정 파일 (Profiles)

### `application-dev.yml`

```yml
server:
  port: 8080

spring:
  config:
    activate:
      on-profile: dev
    import:
      - classpath:application-common.yml

  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://${DEV_DB_HOST:127.0.0.1}:${DEV_DB_PORT:13305}/ticketing_flyway?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
    username: ${DEV_DB_USERNAME:ticketing_user}
    password: ${DEV_DB_PASSWORD}

  flyway:
    enabled: true
    locations: classpath:db/migration
    encoding: UTF-8
    validate-on-migrate: true
```

### `application-test.yml`

로컬 테스트는 `13307`, CI는 `TEST_DB_URL` 환경변수로 `3306`을 주입합니다.

```yml
spring:
  config:
    activate:
      on-profile: test
    import:
      - classpath:application-common.yml

  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: ${TEST_DB_URL:jdbc:mariadb://127.0.0.1:13307/ticketing_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul}
    username: ${TEST_DB_USERNAME:ticketing_user}
    password: ${TEST_DB_PASSWORD:}

  flyway:
    enabled: true
    locations: classpath:db/migration
    encoding: UTF-8
    validate-on-migrate: true

logging:
  level:
    org.hibernate.SQL: off
    org.hibernate.orm.jdbc.bind: off
```

> `TEST_DB_URL`이 주입되지 않으면 기본값(`127.0.0.1:13307`)을 사용합니다.
> CI에서는 `TEST_DB_URL=jdbc:mariadb://127.0.0.1:3306/...`으로 덮어씁니다.

### SQL 로그와 바인딩 값 로그 (`application-common.yml`)

Hibernate 로그는 두 logger로 나뉩니다.

- `org.hibernate.SQL`: SQL 문장 출력. 공통 설정은 `debug`이며 이번 변경에서 바꾸지 않았습니다.
- `org.hibernate.orm.jdbc.bind`: `?` 자리에 바인딩되는 값(Outbox `payload` 포함) 출력. 공통 설정에서 `"OFF"`로 기본 차단합니다 (TASK-033-1).

```yml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: "OFF"
```

- 위 `application-test.yml`의 `logging … off` 두 줄만으로는 test 프로필에서 바인딩 값 출력이 꺼지지 않은 것이 관찰되었습니다 (TASK-033-1 테스트 출력). 그래서 바인딩 값 차단은 import되는 공통 설정에 둡니다.
- 운영 환경의 실제 로그 설정은 이 저장소에서 확인하지 않았습니다. 운영 프로필이나 외부 설정이 이 값을 바꾸는지는 배포 환경에서 따로 확인해야 합니다.

---

## 3) 실행/검증

```bash
# DB 컨테이너 기동
docker-compose up -d

# 애플리케이션 실행 (dev)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 테스트 실행 (test)
./gradlew test -Dspring.profiles.active=test
```

### 정상 적용 확인

애플리케이션 로그에서 아래 중 하나가 보이면 정상입니다.

* 최초 실행: `Migrating schema ... to version "1 - init"`
* 재실행: `Schema ... is up to date. No migration necessary.`

---

## 4) Health Check

* `GET /health` → `ok`
* `GET /health/db` → DB 연결 + `members` 조회로 검증

---

## 5) Troubleshooting

### A) `Unknown column ...`

seed SQL의 컬럼이 실제 테이블 컬럼과 다를 때 발생합니다.
seed SQL의 컬럼을 테이블 컬럼과 동일하게 맞춥니다.

### B) `Validate failed: Detected failed migration`

이전 실패 기록이 `flyway_schema_history`에 남아 재실행이 막힙니다.

* 로컬 개발용 빠른 해결:
    1. 실패한 row 삭제 또는 DB 초기화
    2. 애플리케이션 재실행

> 본 프로젝트는 Spring Boot의 Flyway 자동 실행 방식을 사용합니다.
> `./gradlew flywayRepair` 같은 태스크는 제공되지 않습니다.

### C) `Connection refused`

포트 컨벤션을 먼저 확인합니다.

| 접속 대상      | 확인할 포트 |
|------------|--------|
| 로컬 dev DB  | 13305  |
| 로컬 test DB | 13307  |
| CI DB      | 3306   |

순서대로 확인합니다.

```bash
# 컨테이너 상태 확인
docker-compose ps

# 포트 점유 확인
lsof -i :13305
lsof -i :13307

# 로컬 포워딩 확인
nc -vz 127.0.0.1 13305
nc -vz 127.0.0.1 13307
```

Colima 환경에서 localhost 포워딩이 실패하는 경우:

```bash
colima status
# → VM IP 확인 후 DEV_DB_HOST / TEST_DB_URL에 직접 지정
```

---

## 6) Dependencies

* `flyway-core`: 애플리케이션 시작 시 Flyway 자동 실행
* `flyway-mysql`: MariaDB/MySQL 호환 지원
* `redisson-spring-boot-starter`: Redis 분산락 / 캐시

---

## 7) Migration 히스토리

| 버전  | 파일명                                               | 내용                              | 설계 의도                                                                   |
|-----|---------------------------------------------------|---------------------------------|-------------------------------------------------------------------------|
| V1  | `V1__init.sql`                                    | members 테이블 생성                  | 회원 기본 정보 (이메일/비밀번호/이름)                                                  |
| V2  | `V2__seed_members.sql`                            | 회원 시드 데이터                       | 로컬 개발용 테스트 회원 데이터                                                       |
| V3  | `V3__ticketing_schema.sql`                        | 티켓팅 기초 스키마                      | 좌석 마스터(`seat`)와 회차별 상태(`showtime_seat`) 분리 — 같은 좌석이 회차마다 다른 상태를 가질 수 있음 |
| V4  | `V4__seed_ticketing.sql`                          | 티켓팅 시드 데이터                      | event 1건 / showtime 2건 / seat 20건 / showtime_seat 40건 (초기값 AVAILABLE)   |
| V5  | `V5__create_holds_table.sql`                      | holds 테이블 생성                    | 좌석 선점과 예약 확정을 분리 — 일정 시간 내 미확정 시 자동 해제                                  |
| V6  | `V6__add_status_to_holds.sql`                     | holds 상태 컬럼 추가                  | ACTIVE / EXPIRED / CONFIRMED 상태로 선점 생명주기 관리                             |
| V7  | `V7__create_reservations.sql`                     | reservations 테이블 생성             | holds와 1:1 관계로 예약 확정 보장 (`uk_reservations_hold_id`)                     |
| V8  | `V8__add_member_id_to_holds.sql`                  | holds 회원 ID 컬럼 추가               | 선점 소유자 식별 및 본인 소유 검증                                                    |
| V9  | `V9__add_indexes.sql`                             | 인덱스 추가                          | 회차별 좌석 상태 조회 / 만료 HOLD 스케줄러 / 예약 조회 성능 최적화                              |
| V10 | `V10__add_status_to_reservations.sql`             | reservations 상태 컬럼 추가           | CONFIRMED / CANCELLED 상태로 예약 취소 흐름 지원                                   |
| V11 | `V11__add_password_and_updated_at_to_members.sql` | members 비밀번호 / updated_at 컬럼 추가 | Spring Security + JWT 인증 도입에 따른 비밀번호 저장                                 |
| V12 | `V12__add_end_time_to_event.sql`                  | event 종료 시각 컬럼 추가               | 대기열 TTL 관리 및 이벤트 종료 시 대기열 정리 기준                                         |
| V13 | `V13__create_payments_table.sql`                  | payment 테이블 생성                  | 결제와 예약을 분리 — PENDING / SUCCESS / FAIL / REFUNDED 생명주기 관리                |
| V14 | `V14__add_refunded_at_to_payment.sql`             | payment 환불 시각 컬럼 추가             | 환불 완료 시각 기록으로 환불 처리 이력 추적                                               |
| V15 | `V15__create_outbox_event_and_notification.sql`   | outbox_event / notification 테이블 생성 | 결제 성공 Outbox를 결제와 같은 트랜잭션에 저장하고 폴링으로 결제 건당 내부 알림 1건 생성 — Outbox는 FK 없음, 알림은 결제·회원 RESTRICT FK ([ADR-005](../architecture/adr/ADR-005-payment-outbox-notification.md)) |
| V16 | `V16__add_retry_state_to_outbox_event.sql`        | outbox_event 재시도 상태 컬럼 추가 | 기록된 실패 수·다음 시도 시각·마지막 실패 시각·허용 목록 오류 분류·상태 버전 — 기존 행은 실패 0회·즉시 대상·버전 0 ([ADR-006](../architecture/adr/ADR-006-outbox-retry-and-failure.md)) |
| R   | `R__outbox_requeue_failed.sql`                    | FAILED Outbox 수동 재처리 프로시저 (repeatable) | 본문이 바뀌면 Flyway가 다시 적용한다. 운영 절차는 아래 §8 ([ADR-006](../architecture/adr/ADR-006-outbox-retry-and-failure.md)) |

### 주의사항

* 기존 migration 파일은 수정하지 않고, 변경 사항은 새 버전으로 추가합니다.
* 이미 적용된 파일을 수정하면 checksum mismatch가 발생합니다.
* 로컬 개발 중 수정이 필요하면 DB를 초기화한 뒤 재실행해 검증합니다.

---

## 8) Outbox 수동 재처리 (TASK-033-1)

기록된 실패가 최대 횟수(기본 5회)에 도달한 Outbox는 `FAILED`가 되어 자동 relay 대상에서 빠집니다.
원인을 확인·해결한 뒤 `outbox_requeue_failed` 프로시저로 다시 `PENDING`에 넣습니다.
설계와 검증 범위는 [ADR-006](../architecture/adr/ADR-006-outbox-retry-and-failure.md)을 따릅니다.

> 이 절의 명령은 TASK-033-1 격리 테스트 DB에서 같은 프로시저로 검증한 동작을 바탕으로 한 안내입니다.
> 운영·dev DB에서 실제로 실행해 확인한 절차가 아니며, 운영 권한 검증·부여도 하지 않았습니다.

### A) 호출 전 확인

1. 접속 대상 DB와 계정을 확인합니다. 비밀번호는 문서·명령 기록에 남기지 않습니다.

   ```sql
   SELECT DATABASE(), CURRENT_USER();
   ```

2. 재처리할 FAILED 행과 마지막 실패 정보를 확인합니다. 원인(예: `DUPLICATE_KEY`, `FOREIGN_KEY`, `PAYLOAD_INVALID`)을 해결하지 않고 재처리하면 다시 실패합니다.
   이 조회에는 `outbox_event`의 `SELECT` 권한이 필요합니다. 재처리 실행 계정에 조회 권한이 없으면, 조회 권한이 있는 확인 담당자가 사전 조회를 하고 실행 담당자는 CALL만 합니다 (아래 E).

   ```sql
   SELECT id, aggregate_id, failed_attempts, last_failed_at, last_error_code, last_error_type,
          last_sql_state, last_vendor_code, state_version
   FROM outbox_event
   WHERE status = 'FAILED'
   ORDER BY id;
   ```

### B) 입력 규칙

- JSON 배열 1~100건, 각 원소는 **양의 signed BIGINT 범위의 JSON 정수**입니다 (`1` ~ `9223372036854775807`).
- 다음은 `SQLSTATE 45000`으로 거부되며, 유효한 ID가 함께 있어도 **어떤 행도 바뀌지 않습니다**.
  - `NULL`, JSON이 아닌 문자열, 배열이 아닌 값(객체 등), 빈 배열, 101건 이상
  - 문자열(`"7"`), boolean, `null` 원소, 소수(`1.0`), 지수 표기(`1e3`), 0, 음수, BIGINT 초과(`9223372036854775808`), 앞자리 0(`01`, JSON 아님), 중첩 배열
  - 중복 ID (`[7, 7]`)
- 존재하지 않는 ID와 `FAILED`가 아닌 ID(`PENDING`·`PROCESSED`)는 오류 없이 반영 건수에서 빠집니다.

### C) 호출

autocommit 상태(기본)에서 호출하는 것을 기본으로 합니다. 프로시저 안의 단일 UPDATE가 끝나면 그 문장이 바로 커밋됩니다.

```sql
CALL outbox_requeue_failed('[101, 102, 103]', @requeued);
SELECT @requeued;
```

- `@requeued`: UPDATE가 (다른 트랜잭션의 잠금이 있으면 기다린 뒤) 최신 상태를 보고 `FAILED`→`PENDING`으로 전이한 행 수입니다. 호출을 시작한 순간의 상태가 아니라 UPDATE가 행을 판단한 순간의 상태 기준입니다.
- 전이된 행: `status = 'PENDING'`, `failed_attempts = 0`, `next_attempt_at = NULL`(다음 relay에서 즉시 대상), `state_version` +1.
  `last_error_*`와 `last_failed_at`은 이력으로 남습니다.
- 같은 요청을 다시 보내면, 그 사이 해당 행이 다시 `FAILED`가 되지 않은 한 `0`입니다. 다시 `FAILED`가 된 행은 같은 요청으로 다시 전이됩니다.

명시적 트랜잭션 안에서 호출할 수도 있습니다. 프로시저는 내부에서 `START TRANSACTION`·`COMMIT`을 하지 않으므로 커밋·롤백은 호출자 책임이며,
커밋할 때까지 대상 행의 잠금이 유지되어 relay 처리와 다른 재처리를 기다리게 할 수 있습니다 (격리 테스트에서 미커밋 갱신이 다른 재처리 CALL을 대기시키는 것을 확인).

```sql
START TRANSACTION;
CALL outbox_requeue_failed('[101, 102, 103]', @requeued);
SELECT @requeued;
-- 결과를 확인한 뒤 호출자가 결정한다
COMMIT;   -- 또는 ROLLBACK;
```

### D) 오류의 구분과 호출자 조치

| 오류 | 의미 | 반영 | 호출자 조치 |
|---|---|---|---|
| `SQLSTATE 45000` (메시지 `outbox_requeue_failed: ...`) | 입력 거부 | 없음 (UPDATE 전 검증) | 입력을 고쳐 다시 호출 |
| vendor `1205` 잠금 대기 timeout | 대상 행을 다른 트랜잭션이 잠그고 있음 | 해당 문장 전체 미반영 | 잠금 원인을 확인한 뒤 다시 호출. 명시적 트랜잭션이면 계속할지·ROLLBACK할지 호출자가 판단 |
| vendor `1213` deadlock | 교착으로 트랜잭션이 rollback됨 (InnoDB 일반 동작, 이 프로젝트에서 재처리 deadlock은 미검증) | 미반영. 명시적 트랜잭션이면 그 트랜잭션의 이전 작업도 rollback됨 | 트랜잭션을 처음부터 다시 실행 |

- 재처리 UPDATE는 단일 문장이라, 일부 행을 갱신한 뒤 오류가 나도 대상 행 전체가 반영되지 않습니다
  (격리 테스트에서 한 행 실제 갱신 후 장애·전 행 원래 값 확인, ADR-006 §8).
- 프로시저는 세션 변수 `@outbox_requeue_failed_sql`과 prepared statement `outbox_requeue_failed_stmt`를 씁니다.
  정상 종료 시 정리하고, 동적 SQL 구간에서 오류가 나면 핸들러가 (PREPARE에 성공한 경우) prepared statement를 해제하고 세션 변수를 NULL로 되돌린 뒤
  원래 오류를 그대로 전달합니다. 격리 테스트로 확인한 오류 경로는 잠금 대기 timeout(`1205`)뿐입니다 (같은 연결에서 세션 변수 NULL, 재해제 시 `1243`).
  다른 오류 경로의 정리는 검증하지 않았습니다.

### E) 권한과 계정

| 계정 | 필요한 권한 |
|---|---|
| migration 실행 계정 | `CREATE ROUTINE`, `ALTER ROUTINE` (`R__` 재적용 시 `DROP PROCEDURE`) |
| DEFINER 계정 (`SQL SECURITY DEFINER`, migration 실행 계정이 DEFINER가 됨) | `outbox_event` 조회·갱신 |
| 재처리 실행 계정 | 이 프로시저의 `EXECUTE` (CALL 자체에 필요) |
| 사전·사후 확인 계정 | `outbox_event`의 `SELECT` (A의 조회에 필요. 실행 계정과 같을 필요는 없음) |

- DEFINER 계정이 삭제되거나 권한을 잃으면 프로시저 호출이 실패합니다. migration 실행 계정을 바꿀 때 함께 확인합니다.
- `SQL SECURITY DEFINER`이므로 CALL에는 실행 계정의 `outbox_event` 권한이 필요하지 않습니다. 사전 조회는 프로시저 밖에서 실행되므로 조회하는 계정의 `SELECT` 권한이 별도로 필요합니다.
- 위 권한의 운영 환경 검증·부여는 TASK-033-1에서 하지 않았습니다. 격리 테스트 컨테이너에서는 테스트 계정으로 CREATE/DROP PROCEDURE가 성공한 것만 확인했습니다.
