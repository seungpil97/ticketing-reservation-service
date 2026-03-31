# Ticketing Reservation Service

티켓 예매 서비스를 위한 백엔드 API 프로젝트입니다.

현재 구현 상황:
- 공통 응답 포맷
- 전역 예외 처리
- Member CRUD API
- Flyway 기반 DB 버전관리
- 고객용 티켓팅 조회 API
  - `/events`
  - `/events/{eventId}/showtimes`
  - `/showtimes/{showtimeId}/seats`
- 좌석 선점(HOLD) API
  - `POST /showtimes/{showtimeId}/hold`
- HOLD 만료 해제 스케줄링
- 예약 확정(RESERVE) API
  - `POST /holds/{holdId}/reserve`
- 예약 취소(CANCEL) API
  - `DELETE /reservations/{reservationId}`
- 동시성 제어 (Redis 분산락)
- 이벤트 목록 Redis 캐시
- CI(test)
- Spring Security + JWT 인증
  - `POST /auth/login`
  - `POST /auth/reissue` (RefreshToken Rotation)
  - `POST /auth/logout` (Redis 블랙리스트)

---

## Docs

* API Specs: `docs/api/README.md`
* Member API: `docs/api/member/member.md`
* Event API: `docs/api/ticketing/event.md`
* Showtime API: `docs/api/ticketing/showtime.md`
* Reservation API: `docs/api/ticketing/reservation.md`
* Auth API: `docs/api/auth/auth.md`
* ERD: [`docs/erd/README.md`](docs/erd/README.md)
* 아키텍처 다이어그램: [`docs/architecture/README.md`](docs/architecture/README.md)
* Swagger UI: `http://localhost:8080/swagger-ui/index.html`
* Devlog: `docs/devlog/README.md`
* DB Setup (Flyway): `docs/db/README.md`

---

## Local Setup

### 사전 요구사항

* Docker (또는 Colima)
* Java 17

### 1. 환경변수 파일 생성

`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다.
```bash
cp .env.example .env
```

### 2. 컨테이너 실행
```bash
docker compose up -d
```

| 컨테이너 | 용도 | 포트 |
|---|---|---|
| ticketing-mariadb-dev | 로컬 개발 DB (ticketing_flyway) | 13305 |
| ticketing-mariadb-test | 로컬 테스트 DB (ticketing_test) | 13307 |
| ticketing-redis | Redis 분산락 / 캐시 | 6379 |

> Colima 환경에서 localhost 포트포워딩 이슈가 있으면 환경변수로 DB host를 지정해 실행합니다.
> `DEV_DB_HOST=<colima-ip> ./gradlew bootRun --args='--spring.profiles.active=dev'`

### 3. 애플리케이션 실행 (dev)
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

> IntelliJ 실행 시 VM Options: `-Dspring.profiles.active=dev`

### 4. 테스트 실행
```bash
./gradlew test -Dspring.profiles.active=test
```

> 테스트 실행 전 `docker compose up -d` 로 컨테이너가 올라와 있어야 합니다.

### DB 접속 가이드

자세한 접속 정보 및 Flyway 운영 기준은 [`docs/db/README.md`](docs/db/README.md)를 참고하세요.

---

## Health Check

* `GET /health` → `ok`
* `GET /health/db` → `ok` (DB 연결 + `members` 조회/검증)

---

## API Response Format

모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success Response (example)

**POST /members** → `201 Created` + `Location: /members/{id}`
```json
{
  "data": {
    "id": 1,
    "email": "a@test.com",
    "name": "sp"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

### Error Response (example)

Validation 실패 시 `COMMON-001`과 함께 필드 에러가 `details`로 내려옵니다.

**POST /members** (name 공백) → `400 Bad Request`
```json
{
  "data": null,
  "error": {
    "code": "COMMON-001",
    "details": [
      {
        "field": "name",
        "message": "name is required"
      }
    ],
    "message": "Validation failed",
    "path": "/members",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

## Error Code Policy

에러 코드는 **도메인/범위별 Prefix**로 구분합니다.

* `COMMON-xxx`: 공통 에러 (검증 실패, 서버 에러 등)
* `AUTH-xxx`: 인증/인가 에러
* `MEMBER-xxx`: 회원 도메인 에러
* `EVENT-xxx`: 공연 도메인 에러
* `SHOWTIME-xxx`: 회차 도메인 에러
* `SHOWTIME-SEAT-xxx`: 회차별 좌석 도메인 에러
* `SEAT-xxx`: 좌석 도메인 에러
* `HOLD-xxx`: 선점 도메인 에러
* `RESERVATION-xxx`: 예약 도메인 에러

전체 에러 코드 목록은 각 도메인 패키지의 `XxxErrorCode.java`를 참고하세요.

---

## Security

JWT 기반 인증 구조 및 RefreshToken Rotation 흐름은 [`docs/api/auth/auth.md`](docs/api/auth/auth.md)를 참고하세요.

---

## Tech

* Spring Boot 3.4.3
* Spring Data JPA
* MariaDB
* Flyway
* Redis (분산락 / 캐시)
* Docker Compose
* GitHub Actions (CI)