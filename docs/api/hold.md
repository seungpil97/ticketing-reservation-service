# Hold API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success (example)
**POST /showtimes/{showtimeId}/hold** → `201 Created`

```json
{
  "data": {
    "holdId": 1,
    "showtimeId": 1,
    "seatId": 3,
    "status": "HELD",
    "expiresAt": "2026-03-12T10:30:00"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

---

## Error Codes

| 코드 | HTTP | 설명 |
|---|---|---|
| `HOLD-001` | 404 | Hold not found |
| `HOLD-002` | 409 | Hold is not active (EXPIRED 또는 CONFIRMED 상태) |
| `HOLD-003` | 409 | Hold is expired (expiresAt 초과) |
| `QUEUE-002` | 403 | Admission token not found (입장 토큰 없음) |
| `QUEUE-003` | 403 | Admission token has expired (입장 토큰 만료) |
| `SHOWTIME-SEAT-001` | 404 | Showtime seat not found (해당 회차에 속하지 않는 seatId) |
| `SHOWTIME-SEAT-002` | 409 | Showtime seat is not held |
| `COMMON-001` | 400 | Validation failed |
| `COMMON-007` | 500 | Internal server error |

---

## Endpoints

### 1) Hold Seat

* **POST** `/showtimes/{showtimeId}/hold`
* **201 Created**

설명

* 특정 회차의 좌석을 10분간 선점한다.
* 선점 성공 시 `showtime_seat` 상태가 `AVAILABLE → HELD`로 변경된다.
* Redis 분산락으로 동시 선점 요청을 직렬화한다. 동일 좌석에 대한 중복 선점을 방지한다.
* 입장 토큰(`token:user:{userId}`)이 Redis에 존재해야 선점이 가능하다.
* 10분 내 예약 확정(`POST /holds/{holdId}/reserve`)을 하지 않으면 스케줄러가 자동 해제한다.

Request Body

```json
{
  "seatId": 3,
  "memberId": 1
}
```

Response (201)

```json
{
  "data": {
    "holdId": 1,
    "showtimeId": 1,
    "seatId": 3,
    "status": "HELD",
    "expiresAt": "2026-03-12T10:30:00"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/showtimes/1/hold \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"seatId": 3, "memberId": 1}'
```

Errors

* `400` `COMMON-001` (seatId 또는 memberId 누락)
* `403` `QUEUE-002` (입장 토큰 없음)
* `403` `QUEUE-003` (입장 토큰 만료)
* `404` `SHOWTIME-SEAT-001` (해당 회차에 속하지 않는 seatId)
* `409` `SHOWTIME-SEAT-002` (이미 선점 또는 예약된 좌석)

---

### 1-1) Hold Seat - Admission Token Not Found

* **POST** `/showtimes/{showtimeId}/hold`
* **403 Forbidden**

설명

* 대기열 입장 토큰 없이 선점을 시도한 경우.
* 대기열(`POST /queue/enter`) 등록 후 입장 허용을 기다려야 한다.

Response (403)

```json
{
  "data": null,
  "error": {
    "code": "QUEUE-002",
    "details": [],
    "message": "Admission token not found",
    "path": "/showtimes/1/hold",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

### 1-2) Hold Seat - Showtime Seat Not Found

* **POST** `/showtimes/{showtimeId}/hold`
* **404 Not Found**

설명

* 해당 회차에 속하지 않는 seatId로 선점을 시도한 경우.

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "SHOWTIME-SEAT-001",
    "details": [],
    "message": "Showtime seat not found",
    "path": "/showtimes/1/hold",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

### 1-3) Hold Seat - Already Held or Reserved

* **POST** `/showtimes/{showtimeId}/hold`
* **409 Conflict**

설명

* 이미 다른 사용자가 선점하거나 예약 확정된 좌석에 선점을 시도한 경우.

Response (409)

```json
{
  "data": null,
  "error": {
    "code": "SHOWTIME-SEAT-002",
    "details": [],
    "message": "Showtime seat is not held",
    "path": "/showtimes/1/hold",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

## curl Test Set

```bash
# hold success
curl -X POST http://localhost:8080/showtimes/1/hold \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"seatId": 3, "memberId": 1}'

# hold - admission token not found
curl -X POST http://localhost:8080/showtimes/1/hold \
  -H "Content-Type: application/json" \
  -d '{"seatId": 3, "memberId": 1}'

# hold - showtime seat not found
curl -X POST http://localhost:8080/showtimes/1/hold \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"seatId": 999, "memberId": 1}'

# hold - already held
curl -X POST http://localhost:8080/showtimes/1/hold \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"seatId": 3, "memberId": 2}'
```