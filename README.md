# Ticketing Reservation Service

티켓 예매 서비스를 위한 백엔드 API 프로젝트입니다.  
(현재는 공통 응답 포맷/전역 예외 처리/Member CRUD API까지 구현)

---

## Run (dev)
- Profile: `dev`
- DB: MariaDB (`ticketing`)

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
````

---

## Health Check

* `GET /health` → `ok`
* `GET /health/db` → `ok` (JDBC `SELECT 1`)

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

* `COMMON-003`: Invalid request (400)  // PATCH 변경값 없음 등

* `COMMON-405`: Method not allowed (405)

* `COMMON-500`: Internal server error (500)

* `MEMBER-404`: Member not found (404)

* `MEMBER-409`: Duplicate email (409)

---

## Sample API

### Create Member

* `POST /members`

Request Body:

```json
{
  "email": "a@test.com",
  "name": "sp"
}
```

### Get Member

* `GET /members/{id}`

### List Members

* `GET /members`

### Update Member (PATCH)

* `PATCH /members/{id}` (부분 수정)

Request Body examples:

```json
{ "name": "newName" }
```

```json
{ "email": "new@test.com" }
```

### Delete Member

* `DELETE /members/{id}`
* 성공 시 `204 No Content`

---

## Test (curl)

```bash
# -------------------------
# Create / Read
# -------------------------

# create success
curl -X POST http://localhost:8080/members \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","name":"sp"}'

# validation error
curl -X POST http://localhost:8080/members \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","name":""}'

# get by id
curl http://localhost:8080/members/1

# list
curl http://localhost:8080/members


# -------------------------
# Update / Delete
# -------------------------

# update success (200)
curl -X PATCH http://localhost:8080/members/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"newName"}'

# update not found (404)
curl -X PATCH http://localhost:8080/members/999999 \
  -H "Content-Type: application/json" \
  -d '{"name":"newName"}'

# update invalid (no changes) (400)
curl -X PATCH http://localhost:8080/members/1 \
  -H "Content-Type: application/json" \
  -d '{}'

# update duplicate email (409)
# ※ existing@test.com 자리에 "이미 존재하는 다른 회원의 이메일"을 넣어서 테스트
curl -X PATCH http://localhost:8080/members/1 \
  -H "Content-Type: application/json" \
  -d '{"email":"existing@test.com"}'

# delete success (204) - 헤더/상태코드 확인용으로 -i 옵션 사용
curl -i -X DELETE http://localhost:8080/members/1

# delete not found (404)
curl -i -X DELETE http://localhost:8080/members/999999
```

---

## Tech

* Spring Boot
* Spring Data JPA
* MariaDB