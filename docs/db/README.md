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

> 현재는 로컬 편의를 위해 `password`를 직접 입력해도 됩니다.
> (추후 환경변수로 전환 가능)

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
    url: jdbc:mariadb://localhost:3306/ticketing_flyway?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
    username: ticketing_user
    password: your_password

  flyway:
    enabled: true
    locations: classpath:db/migration
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
./gradlew bootRun -Dspring.profiles.active=dev
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

## 6) Dependencies (Flyway)

* `spring-boot-starter-flyway`: 애플리케이션 시작 시 Flyway 자동 실행
* `flyway-mysql`: MariaDB/MySQL 호환 지원
