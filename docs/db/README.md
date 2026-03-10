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

## 1) Local (dev) DB 준비

로컬에서 아래 DB/User를 준비합니다.

* DB: `ticketing_flyway`
* User: `ticketing_user`
* 권한: 해당 DB에 대한 DDL/DML 권한

### A) docker-compose (추천)

프로젝트 루트의 `docker-compose.yml` + `.env`로 로컬 DB를 재현합니다.

#### 1) `.env` 준비

```bash
cp .env.example .env
````

> 주의: `.env`는 로컬 전용이며 git에 커밋하지 않습니다.

#### 2) DB 실행

```bash
docker compose up -d
docker compose ps
```

`mariadb` 컨테이너가 `healthy` 상태인지 확인합니다.

#### 3) 애플리케이션 실행(dev)

기본적으로 dev 프로필은 아래 환경변수를 사용해 DB 접속 정보를 읽습니다.

* `DEV_DB_HOST` (기본값: `localhost`)
* `DEV_DB_USERNAME` (기본값: `ticketing_user`)
* `DEV_DB_PASSWORD` (필수)

일반적인 경우(localhost 포워딩 정상):

```bash
export DEV_DB_PASSWORD='your_password'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

`DEV_DB_HOST`, `DEV_DB_USERNAME`를 지정하지 않으면 기본값(`localhost`, `ticketing_user`)을 사용합니다.

#### 4) Colima 환경에서 localhost 포워딩이 안 되는 경우

일부 Colima 환경에서는 `localhost:3306` 포워딩이 동작하지 않을 수 있습니다.
이 경우 `colima status`로 VM IP를 확인한 뒤, 해당 IP를 `DEV_DB_HOST`로 사용합니다.

```bash
colima status
```

예시:

```bash
export DEV_DB_HOST=192.168.106.2
export DEV_DB_USERNAME=ticketing_user
export DEV_DB_PASSWORD='your_password'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

#### 5) 검증

```bash
curl -i http://localhost:8080/health/db
```

---

### B) 수동으로 DB/User 생성 (옵션)

예시 SQL:

```sql
CREATE DATABASE ticketing_flyway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

CREATE USER 'ticketing_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON ticketing_flyway.* TO 'ticketing_user'@'%';
FLUSH PRIVILEGES;
```

---

## 2) 설정 파일 (Profiles)

### `application-dev.yml`

dev 환경에서는 DB host/user/password를 환경변수로 주입받고, host/user는 기본값을 사용합니다.

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
    url: jdbc:mariadb://${DEV_DB_HOST:localhost}:3306/ticketing_flyway?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
    username: ${DEV_DB_USERNAME:ticketing_user}
    password: ${DEV_DB_PASSWORD}

  flyway:
    enabled: true
    locations: classpath:db/migration
    encoding: UTF-8
    validate-on-migrate: true
```

### `application-test.yml` (CI용)

테스트 환경에서는 **GitHub Actions Secrets**로 DB 접속 정보를 주입합니다.

```yml
spring:
  config:
    activate:
      on-profile: test
    import:
      - classpath:application-common.yml

  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: ${TEST_DB_URL}
    username: ${TEST_DB_USERNAME}
    password: ${TEST_DB_PASSWORD}

  flyway:
    enabled: true
    locations: classpath:db/migration
    encoding: UTF-8
    validate-on-migrate: true
    baseline-on-migrate: true

logging:
  level:
    org.hibernate.SQL: off
    org.hibernate.orm.jdbc.bind: off
```

---

## 3) 실행/검증

### 실행

```bash
./gradlew clean test
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 정상 적용 확인

애플리케이션 로그에서 아래 중 하나가 보이면 정상입니다.

* 최초 실행:

  * `Migrating schema ... to version "1 - init"`
* 재실행:

  * `Schema ... is up to date. No migration necessary.`

또한 DB에 아래 테이블이 생성됩니다.

* `flyway_schema_history`
* `members` (V1에서 생성)

---

## 4) Health Check

* `GET /health` → `ok`
* `GET /health/db` → DB 연결 + `members` 조회로 검증

예시:

```java
@GetMapping("/health/db")
public String healthDb() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class);
    return (count != null) ? "ok" : "fail";
}
```

---

## 5) Troubleshooting

### A) `Unknown column ...` (예: `updated_at`)

`V2__seed_members.sql`의 INSERT 컬럼이 실제 `members` 테이블 컬럼과 다를 때 발생합니다.

* 해결: seed SQL의 컬럼을 **테이블 컬럼과 동일하게** 맞춥니다.

  * (`members`에 없는 컬럼을 INSERT에 넣지 않기)

---

### B) `Validate failed: Detected failed migration`

이전 실패 기록이 `flyway_schema_history`에 남아 재실행이 막힙니다.

* 로컬에서 빠른 해결(개발용):

  1. 실패한 row 삭제 또는 DB 초기화
  2. 애플리케이션 재실행

> 참고: 본 프로젝트는 **Spring Boot의 Flyway 자동 실행 방식**을 사용합니다.
> Gradle Flyway 플러그인을 적용하지 않았기 때문에 `./gradlew flywayRepair` 같은 태스크는 제공되지 않습니다.

---

### C) `Connection refused`

다음을 순서대로 확인합니다.

* `brew services stop mariadb`

  * 로컬 MariaDB가 `3306` 포트를 점유 중인지 확인
* `lsof -i :3306`

  * 어떤 프로세스가 `3306`을 사용 중인지 확인
* `docker compose up -d`
* `docker compose ps`

  * `mariadb` 컨테이너가 `healthy` 상태인지 확인
* `colima status`

  * Colima 사용 시 VM IP 확인
* `nc -vz localhost 3306`

  * localhost 포워딩 확인
* localhost 접속이 실패하면 Colima IP로 직접 접속 시도

예시:

```bash
nc -vz 192.168.106.2 3306
```

Colima VM IP로는 접속되지만 `localhost:3306`은 실패하는 경우, dev 실행 시 `DEV_DB_HOST`에 Colima IP를 지정합니다.

```bash
export DEV_DB_HOST=192.168.106.2
export DEV_DB_USERNAME=ticketing_user
export DEV_DB_PASSWORD='your_password'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## 6) Dependencies (Flyway)

* `spring-boot-starter-flyway`: 애플리케이션 시작 시 Flyway 자동 실행
* `flyway-mysql`: MariaDB/MySQL 호환 지원

---

## 7) Ticketing Schema (V3 / V4)

티켓팅 도메인 기초 스키마는 `V3__ticketing_schema.sql`, seed 데이터는 `V4__seed_ticketing.sql`로 관리합니다.

### 추가된 테이블

* `event`

  * 공연 기본 정보
* `showtime`

  * 공연 회차 정보
* `seat`

  * 좌석 마스터 정보
  * 좌석 번호(`A1~A20`)와 좌석 등급(`VIP / R / S`) 관리
* `seat_grade_price`

  * 공연별 좌석 등급 가격 정보
* `showtime_seat`

  * 회차별 좌석 상태 정보

### 설계 의도

티켓팅 도메인에서는 같은 좌석이라도 회차마다 예약 상태가 달라질 수 있으므로, 좌석 마스터와 회차별 좌석 상태를 분리했습니다.

* `seat`

  * 좌석 자체의 고정 정보(좌석 번호, 등급)
* `seat_grade_price`

  * 공연별 좌석 등급 가격 정책
* `showtime_seat`

  * 회차별 좌석 상태(`AVAILABLE`, `HELD`, `RESERVED`)

예를 들어 같은 `A1` 좌석이라도:

* 3/10 19:00 회차에서는 `RESERVED`
* 3/11 19:00 회차에서는 `AVAILABLE`

상태가 다를 수 있으므로 `showtime_seat`에서 별도로 관리합니다.

### Seed 데이터

`V4__seed_ticketing.sql` 기준으로 아래 데이터가 입력됩니다.

* `event` : 1건
* `showtime` : 2건
* `seat` : 20건 (`A1 ~ A20`)
* `seat_grade_price` : 3건 (`VIP / R / S`)
* `showtime_seat` : 40건 (`2회차 × 20좌석`, 초기값 `AVAILABLE`)

### 확인 포인트

애플리케이션을 dev 프로필로 실행한 뒤 DBeaver에서 아래를 확인합니다.

* `event` 1건
* `showtime` 2건
* `seat` 20건
* `seat_grade_price` 3건
* `showtime_seat` 40건

추가로 아래 값도 확인합니다.

* `seat.grade` → `VIP / R / S`
* `showtime_seat.status` → 초기값 `AVAILABLE`

### 주의사항

* 기존 migration(`V1`, `V2`)은 수정하지 않고, 변경 사항은 새 버전(`V3`, `V4`)으로 추가합니다.
* Flyway가 이미 적용한 migration 파일을 수정하면 checksum mismatch가 발생할 수 있습니다.
* 로컬 개발 중 migration 수정이 필요할 경우, DB를 초기화한 뒤 다시 실행해 검증합니다.
