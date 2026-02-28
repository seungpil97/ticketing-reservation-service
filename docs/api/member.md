# Member API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success (example)
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
````

### Error - Validation (example)

Validation 실패 시 `COMMON-001`과 함께 필드 에러가 `details`로 내려옵니다.

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

## Error Codes

`error.code` 필드는 아래 코드 중 하나를 반환합니다.

### Member

* `MEMBER-404`: Member not found (404)
* `MEMBER-409`: Duplicate email (409)

### Common

* `COMMON-001`: Validation failed (400)
* `COMMON-002`: Invalid request body (400)
* `COMMON-003`: Invalid request (400) // PATCH 변경값 없음 등
* `COMMON-405`: Method not allowed (405)
* `COMMON-500`: Internal server error (500)

---

## Endpoints

### 1) Create Member

* **POST** `/members`
* **201 Created**
* Header: `Location: /members/{id}`

Request

```json
{
  "email": "a@test.com",
  "name": "sp"
}
```

curl

```bash
curl -X POST http://localhost:8080/members \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","name":"sp"}'
```

Errors

* `400` `COMMON-001`
* `409` `MEMBER-409`

---

### 2) Get Member

* **GET** `/members/{id}`
* **200 OK**

Response (200)

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

curl

```bash
curl http://localhost:8080/members/1
```

Errors

* `404` `MEMBER-404`

---

### 2-1) Get Member - Not Found

* **GET** `/members/{id}`
* **404 Not Found**

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "MEMBER-404",
    "details": [],
    "message": "Member not found",
    "path": "/members/999999",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl http://localhost:8080/members/999999
```

---

### 3) List Members

* **GET** `/members`
* **200 OK**

curl

```bash
curl http://localhost:8080/members
```

---

### 4) Update Member (PATCH)

* **PATCH** `/members/{id}` (부분 수정)
* **200 OK**

Request examples

```json
{ "name": "newName" }
```

```json
{ "email": "new@test.com" }
```

curl

```bash
curl -X PATCH http://localhost:8080/members/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"newName"}'
```

Errors

* `400` `COMMON-003` (no changes)
* `404` `MEMBER-404`
* `409` `MEMBER-409`

---

### 5) Delete Member

* **DELETE** `/members/{id}`
* **204 No Content**

curl

```bash
curl -i -X DELETE http://localhost:8080/members/1
```

Errors

* `404` `MEMBER-404`

---

## curl Test Set

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

# get not found
curl http://localhost:8080/members/999999

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
