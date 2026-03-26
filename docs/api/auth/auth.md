# Auth API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

---

## Error Codes

`error.code` 필드는 아래 코드 중 하나를 반환합니다.

### Auth

* `AUTH-401`: Invalid credentials - 이메일/비밀번호 불일치 (401)
* `AUTH-401`: Invalid or expired refresh token - 유효하지 않거나 만료된 RefreshToken (401)
* `AUTH-401`: Refresh token not found - Redis에 존재하지 않는 RefreshToken (401)
* `AUTH-401`: Invalid token - 유효하지 않은 AccessToken (401)

### Common

* `COMMON-001`: Validation failed (400)
* `COMMON-500`: Internal server error (500)

---

## Endpoints

### 1) Login

* **POST** `/auth/login`
* **200 OK**

Request

```json
{
  "email": "a@test.com",
  "password": "rawPass1!"
}
```

Response (200)

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG..."
  },
  "error": null,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","password":"rawPass1!"}'
```

Errors

* `400` `COMMON-001`
* `401` `AUTH-401` (이메일/비밀번호 불일치)

---

### 2) Reissue (RefreshToken Rotation)

* **POST** `/auth/reissue`
* **200 OK**

> 재발급 시 AccessToken + RefreshToken 모두 교체됩니다.
> 클라이언트는 두 토큰 모두 갱신 후 저장해야 합니다.

Request

```json
{
  "refreshToken": "eyJhbG..."
}
```

Response (200)

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG..."
  },
  "error": null,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/auth/reissue \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbG..."}'
```

Errors

* `401` `AUTH-401` (만료/위조된 RefreshToken)
* `401` `AUTH-401` (Redis에 없는 RefreshToken - 로그아웃된 사용자)

---

### 3) Logout

* **POST** `/auth/logout`
* **200 OK**

> AccessToken은 Redis 블랙리스트에 등록되고, RefreshToken은 Redis에서 삭제됩니다.

Request Header

```
Authorization: Bearer {accessToken}
```

Response (200)

```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer eyJhbG..."
```

Errors

* `401` `AUTH-401` (Authorization 헤더 누락 또는 Bearer 형식 아닌 경우)

---

## JWT 인증 구조

| 토큰 | 유효기간 | 저장 위치 |
|---|---|---|
| AccessToken | 15분 | 클라이언트 |
| RefreshToken | 7일 | 클라이언트 + Redis (`refresh:{memberId}`) |

- AccessToken은 매 API 요청 시 `Authorization: Bearer {token}` 헤더로 전송
- AccessToken 만료 시 RefreshToken으로 `/auth/reissue` 호출하여 재발급
- 로그아웃 시 AccessToken은 Redis 블랙리스트 등록, RefreshToken은 Redis에서 즉시 삭제

---

## RefreshToken Rotation

### 정상 재발급 흐름

1. 클라이언트가 만료된 AccessToken 감지
2. `POST /auth/reissue` 에 RefreshToken 전송
3. 서버가 Redis 저장값과 요청값 일치 확인
4. 새 AccessToken + 새 RefreshToken 발급
5. Redis에 새 RefreshToken으로 교체 저장
6. 클라이언트는 두 토큰 모두 갱신 후 저장

### 탈취 감지 흐름

1. 해커가 탈취한 RefreshToken으로 `/auth/reissue` 호출
2. 새 AccessToken + 새 RefreshToken 발급, Redis 교체
3. 피해자가 기존 RefreshToken으로 재발급 시도
4. Redis 저장값 ≠ 요청값 → 탈취 감지
5. Redis에서 RefreshToken 즉시 삭제 (강제 로그아웃)
6. 피해자 + 해커 모두 재로그인 필요

---

## curl Test Set

```bash
# -------------------------
# Login
# -------------------------

# login success
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","password":"rawPass1!"}'

# login - invalid credentials
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.com","password":"wrongPass!"}'

# -------------------------
# Reissue
# -------------------------

# reissue success
curl -X POST http://localhost:8080/auth/reissue \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbG..."}'

# reissue - invalid token
curl -X POST http://localhost:8080/auth/reissue \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"invalid.token"}'

# -------------------------
# Logout
# -------------------------

# logout success
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer eyJhbG..."

# logout - missing header
curl -X POST http://localhost:8080/auth/logout

# logout - invalid bearer format
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: InvalidToken"
```