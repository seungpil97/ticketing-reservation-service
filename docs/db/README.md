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

### 주의사항

* 기존 migration 파일은 수정하지 않고, 변경 사항은 새 버전으로 추가합니다.
* 이미 적용된 파일을 수정하면 checksum mismatch가 발생합니다.
* 로컬 개발 중 수정이 필요하면 DB를 초기화한 뒤 재실행해 검증합니다.

````