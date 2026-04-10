# Ticketing Reservation Service

## 이 프로젝트는 무엇을 해결하나요?

티켓팅 서비스는 특정 시간에 트래픽이 폭증하고, 동일 좌석에 수백 명이 동시에 선점을 시도합니다.
이 프로젝트는 그 문제를 직접 구현하고 검증한 백엔드 포트폴리오입니다.

- 좌석 중복 선점 방지 — Redis 분산락으로 동시 요청 직렬화
- 트래픽 폭증 대응 — Redis Sorted Set 기반 대기열로 입장 순서 제어
- 결제 중복 처리 방지 — Idempotency Key 기반 멱등성 보장
- 인증 보안 강화 — RefreshToken Rotation + Redis 블랙리스트 로그아웃

> Java 17 / Spring Boot 3.4.3 / MariaDB / Redis / Docker Compose / GitHub
> Actions

---

## 전체 예약 플로우

```

로그인
→ 대기열 입장 (Redis Sorted Set 순번 발급)
→ 입장 토큰 발급 (스케줄러가 상위 N명 허용, TTL 30분)
→ 좌석 선점 HOLD (Redis 분산락으로 중복 방지, 10분 유효)
→ 예약 확정 RESERVE
→ 결제 (Mock 결제 + Idempotency Key 멱등성 보장)
→ 취소 / 환불 (본인 소유권 검증)

```

---

## 핵심 기술 결정

| 결정           | 선택                         | 대안 대비 이유                                                                 |
|--------------|----------------------------|--------------------------------------------------------------------------|
| 동시성 제어       | Redis 분산락 (Redisson)       | 비관적 락은 DB 커넥션을 점유해 트래픽 폭증 시 DB 부하 집중. Redis 분산락은 DB 외부에서 락을 관리해 부하 분산 가능 |
| 대기열          | Redis Sorted Set           | score를 진입 timestamp로 사용해 O(log N)으로 순번 조회/정렬 가능. 별도 MQ 없이 단일 Redis로 처리   |
| RefreshToken | Rotation 방식                | 토큰 탈취 시 피해자가 재사용을 시도하면 Redis 불일치로 즉시 감지 후 강제 로그아웃                        |
| 결제 멱등성       | Idempotency Key (Redis 저장) | 네트워크 재시도로 인한 중복 결제를 방지. 동일 키 재요청 시 DB 접근 없이 기존 결과 반환                     |
| 이벤트 캐시       | Redis Cache (TTL 10분)      | 공연 목록은 변경 빈도가 낮고 조회 빈도가 높음. 캐시로 DB 조회 부하 감소                              |

---

## 구현 현황

### 인증

- `POST /auth/login` — JWT 로그인 (AccessToken 15분 / RefreshToken 7일)
- `POST /auth/reissue` — RefreshToken Rotation 기반 토큰 재발급
- `POST /auth/logout` — AccessToken 블랙리스트 등록 + RefreshToken 삭제

### 티켓팅 조회

- `GET /events` — 공연 목록 조회 (Redis 캐시 TTL 10분)
- `GET /events/{eventId}/showtimes` — 회차 목록 조회
- `GET /showtimes/{showtimeId}/seats` — 좌석 목록 및 상태 조회

### 예약 플로우

- `POST /showtimes/{showtimeId}/hold` — 좌석 선점 (Redis 분산락 + 10분 유효)
- `POST /holds/{holdId}/reserve` — 예약 확정
- `DELETE /reservations/{reservationId}` — 예약 취소 (본인 소유권 검증)

### 결제

- `POST /payments` — Mock 결제 (Idempotency Key 멱등성 보장)
- `POST /payments/{paymentId}/refund` — 환불 처리 (본인 소유권 검증)

### 대기열

- `POST /queue/enter` — 대기열 등록 (Redis Sorted Set 순번 발급)
- `GET /queue/status` — 현재 순번 / 입장 토큰 여부 확인
- 스케줄러 — 상위 N명 입장 허용 + 입장 토큰 발급 (TTL 30분)
- 스케줄러 — 만료 토큰 / 이탈 처리 + reEnterType 구분

### 회원

- `POST /members` — 회원 생성
- `GET /members/{id}` — 회원 조회
- `GET /members` — 회원 목록 (페이징/정렬)
- `PATCH /members/{id}` — 회원 수정
- `DELETE /members/{id}` — 회원 삭제

### 인프라

- HOLD 만료 해제 스케줄링 (30초 주기)
- N+1 제거 (fetch join) + 인덱스 설계
- GitHub Actions CI

---

## Tech Stack

| 분류                   | 기술                               |
|----------------------|----------------------------------|
| Language / Framework | Java 17, Spring Boot 3.4.3       |
| ORM / DB             | Spring Data JPA, MariaDB, Flyway |
| Cache / Lock / Queue | Redis, Redisson                  |
| Auth                 | Spring Security, JWT (jjwt)      |
| Docs                 | Swagger (springdoc-openapi)      |
| Infra                | Docker Compose, GitHub Actions   |
| Test                 | JUnit5, Mockito, MockMvc         |

---

## Local Setup

### 사전 요구사항

- Docker (또는 Colima)
- Java 17

### 1. 환경변수 파일 생성

```bash
cp .env.example .env
```

### 2. 컨테이너 실행

```bash
docker compose up -d
```

| 컨테이너                   | 용도                          | 포트    |
|------------------------|-----------------------------|-------|
| ticketing-mariadb-dev  | 로컬 개발 DB (ticketing_flyway) | 13305 |
| ticketing-mariadb-test | 로컬 테스트 DB (ticketing_test)  | 13307 |
| ticketing-redis        | Redis 분산락 / 캐시 / 대기열        | 6379  |

### 3. 애플리케이션 실행 (dev)

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

> Colima 환경에서 localhost 포워딩 이슈 시:
> `DEV_DB_HOST=<colima-ip> ./gradlew bootRun --args='--spring.profiles.active=dev'`

### 4. 테스트 실행

```bash
./gradlew test -Dspring.profiles.active=test
```

---

## Health Check

- `GET /health` → `ok`
- `GET /health/db` → `ok` (DB 연결 + members 조회 검증)

---

## API Response Format

모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success

```json
{
  "data": {
    ...
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

### Error

```json
{
  "data": null,
  "error": {
    "code": "HOLD-001",
    "message": "Hold not found",
    "details": [],
    "path": "/holds/999/reserve",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

## Error Code Policy

에러 코드는 도메인별 Prefix로 구분합니다.

| Prefix              | 도메인               |
|---------------------|-------------------|
| `COMMON-xxx`        | 공통 (검증 실패, 서버 에러) |
| `AUTH-xxx`          | 인증/인가             |
| `MEMBER-xxx`        | 회원                |
| `EVENT-xxx`         | 공연                |
| `SHOWTIME-xxx`      | 회차                |
| `SHOWTIME-SEAT-xxx` | 회차별 좌석            |
| `HOLD-xxx`          | 좌석 선점             |
| `RESERVATION-xxx`   | 예약                |
| `PAYMENT-xxx`       | 결제                |
| `QUEUE-xxx`         | 대기열               |

전체 에러 코드는 각 도메인 패키지의 `XxxErrorCode.java`를 참고하세요.

---

## Docs

| 문서                | 경로                                            |
|-------------------|-----------------------------------------------|
| API 명세 전체         | `docs/api/README.md`                          |
| Auth API          | `docs/api/auth.md`                            |
| Member API        | `docs/api/member.md`                          |
| Event API         | `docs/api/event.md`                           |
| Showtime API      | `docs/api/showtime.md`                        |
| Hold API          | `docs/api/hold.md`                            |
| Reservation API   | `docs/api/reservation.md`                     |
| Payment API       | `docs/api/payment.md`                         |
| Queue API         | `docs/api/queue.md`                           |
| ERD               | `docs/erd/README.md`                          |
| 아키텍처 다이어그램        | `docs/architecture/README.md`                 |
| DB Setup (Flyway) | `docs/db/README.md`                           |
| Devlog            | `docs/devlog/README.md`                       |
| Swagger UI        | `http://localhost:8080/swagger-ui/index.html` |

---

## Devlog

- [2026-02-28: W1 — 공통 응답 포맷 + Member CRUD](docs/devlog/2026-02-28-w1-wrapup.md)
- [2026-03-07: W2 — Flyway + 티켓팅 조회 API + HOLD API + CI](docs/devlog/2026-03-07-w2-wrapup.md)
- [2026-03-14: W3 — 티켓팅 스키마 + HOLD API + 만료 스케줄링](docs/devlog/2026-03-14-w3-wrapup.md)
- [2026-03-20: W4 — 비관적 락 → Redis 분산락 + N+1 제거 + 예약 취소](docs/devlog/2026-03-20-w4-wrapup.md)
- [2026-03-27: W5 — Redis 캐시 + JWT 인증 + RefreshToken Rotation + 에러코드 리팩토링](docs/devlog/2026-03-27-w5-wrapup.md)
- [2026-04-03: W6 — 도메인형 패키지 + ERD/아키텍처/Swagger + 대기열 시스템](docs/devlog/2026-04-03-w6-wrapup.md)
- [2026-04-10: W7 — Mock 결제 + 멱등성 + 환불 + 소유권 검증 + README 갱신](docs/devlog/2026-04-10-w7-wrapup.md)
