# Ticketing Reservation Service

티켓 예매 서비스를 위한 백엔드 API 프로젝트입니다.  
(현재는 공통 응답 포맷/전역 예외 처리/Member CRUD API까지 구현)

---

## Docs
- [API Specs](docs/api/README.md)
- [Devlog](docs/devlog/README.md)

---

## Run (dev)
- Profile: `dev`
- DB: MariaDB (`ticketing`)

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## Health Check

* `GET /health` → `ok`
* `GET /health/db` → `ok` (JDBC `SELECT 1`)

---

## Docs

* API Specs: `docs/api/README.md`
* Member API Spec (JSON/curl/에러 예시): `docs/api/member.md`

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

* `COMMON-xxx`: 공통 에러(검증 실패, 서버 에러 등)
* `MEMBER-xxx`: 회원 도메인 에러

### Examples

* `COMMON-001`: Validation failed (400)
* `COMMON-002`: Invalid request body (400)
* `COMMON-003`: Invalid request (400) // PATCH 변경값 없음 등
* `COMMON-405`: Method not allowed (405)
* `COMMON-500`: Internal server error (500)
* `MEMBER-404`: Member not found (404)
* `MEMBER-409`: Duplicate email (409)

---

## Tech

* Spring Boot
* Spring Data JPA
* MariaDB
