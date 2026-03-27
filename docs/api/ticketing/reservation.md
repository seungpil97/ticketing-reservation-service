# Reservation API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success (example)
**POST /holds/{holdId}/reserve** → `201 Created`

```json
{
  "data": {
    "reservationId": 1,
    "holdId": 1,
    "showtimeId": 1,
    "seatId": 1,
    "memberId": 1,
    "seatStatus": "RESERVED",
    "holdStatus": "CONFIRMED"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
````

### Error - Not Found (example)

없는 HOLD로 예약 확정 요청 시 도메인 전용 에러 코드와 함께 내려옵니다.

```json
{
  "data": null,
  "error": {
    "code": "HOLD-001",
    "details": [],
    "message": "Hold not found",
    "path": "/holds/999999/reserve",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

## Error Codes

`error.code` 필드는 아래 코드 중 하나를 반환합니다.

### Hold

* `HOLD-001`: Hold not found (404)
* `HOLD-002`: Hold is not active (409)
* `HOLD-003`: Hold is expired (409)

### ShowtimeSeat

* `SHOWTIME-SEAT-002`: Showtime seat is not held (409)

### Reservation

* `RESERVATION-001`: Reservation not found (404)
* `RESERVATION-002`: Reservation is not confirmed (409)

### Common

* `COMMON-004`: Resource not found (404)
* `COMMON-005`: Method not allowed (405)
* `COMMON-007`: Internal server error (500)

---

## Endpoints

### 1) Confirm Reservation

* **POST** `/holds/{holdId}/reserve`
* **201 Created**

설명

* 유효한 HOLD를 최종 예약 확정한다.
* 요청 본문은 없고, `holdId` path variable로 대상 HOLD를 지정한다.
* 예약 확정에 성공하면 예약 row가 생성된다.
* 연결된 좌석 상태는 `HELD -> RESERVED`로 변경된다.
* HOLD 상태는 `ACTIVE -> CONFIRMED`로 변경된다.

Response (201)

```json
{
  "data": {
    "reservationId": 1,
    "holdId": 1,
    "showtimeId": 1,
    "seatId": 1,
    "memberId": 1,
    "seatStatus": "RESERVED",
    "holdStatus": "CONFIRMED"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/holds/1/reserve
```

Errors

* `404` `HOLD-001`
* `409` `HOLD-002`
* `409` `HOLD-003` (Hold is expired)
* `409` `SHOWTIME-SEAT-002`

---

### 1-1) Confirm Reservation - Hold Not Found

* **POST** `/holds/{holdId}/reserve`
* **404 Not Found**

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "HOLD-001",
    "details": [],
    "message": "Hold not found",
    "path": "/holds/999999/reserve",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/holds/999999/reserve
```

---

### 1-2) Confirm Reservation - Hold Not Active

* **POST** `/holds/{holdId}/reserve`
* **409 Conflict**

설명

* 이미 `CONFIRMED` 되었거나 `EXPIRED` 상태인 HOLD는 다시 예약 확정할 수 없다.
* 동일 HOLD로 중복 예약 시 이 케이스로 실패한다.

Response (409)

```json
{
  "data": null,
  "error": {
    "code": "HOLD-002",
    "details": [],
    "message": "Hold is not active",
    "path": "/holds/1/reserve",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/holds/1/reserve
```

---

### 1-3) Confirm Reservation - Hold Expired

* **POST** `/holds/{holdId}/reserve`
* **409 Conflict**

설명

* `expiresAt` 기준으로 이미 만료된 HOLD는 예약 확정할 수 없다.

Response (409)

```json
{
  "data": null,
  "error": {
    "code": "HOLD-003",
    "details": [],
    "message": "Hold is expired",
    "path": "/holds/2/reserve",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/holds/2/reserve
```

---

### 1-4) Confirm Reservation - Showtime Seat Not Held

* **POST** `/holds/{holdId}/reserve`
* **409 Conflict**

설명

* 연결된 `showtime_seat` 상태가 `HELD`가 아니면 예약 확정할 수 없다.
* 이미 `RESERVED` 이거나 다른 상태로 바뀐 경우를 방어한다.

Response (409)

```json
{
  "data": null,
  "error": {
    "code": "SHOWTIME-SEAT-002",
    "details": [],
    "message": "Showtime seat is not held",
    "path": "/holds/3/reserve",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/holds/3/reserve
```

---

## curl Test Set

```bash
# -------------------------
# Reservation
# -------------------------

# reserve success
curl -X POST http://localhost:8080/holds/1/reserve

# reserve - hold not found
curl -X POST http://localhost:8080/holds/999999/reserve

# reserve - hold not active
curl -X POST http://localhost:8080/holds/1/reserve

# reserve - hold expired
curl -X POST http://localhost:8080/holds/2/reserve

# reserve - showtime seat not held
curl -X POST http://localhost:8080/holds/3/reserve
```

---

### 2) Cancel Reservation

* **DELETE** `/reservations/{reservationId}`
* **204 No Content**

설명

* 예약 확정된 좌석을 취소한다.
* 취소 성공 시 좌석 상태는 `RESERVED → AVAILABLE`로 복구된다.
* 예약 상태는 `CONFIRMED → CANCELLED`로 변경된다.

curl
```bash
curl -i -X DELETE http://localhost:8080/reservations/1
```

Errors

* `404` `RESERVATION-001`
* `409` `RESERVATION-002`

---

### 2-1) Cancel Reservation - Not Found

* **DELETE** `/reservations/{reservationId}`
* **404 Not Found**

Response (404)
```json
{
  "data": null,
  "error": {
    "code": "RESERVATION-001",
    "details": [],
    "message": "Reservation not found",
    "path": "/reservations/999999",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl
```bash
curl -i -X DELETE http://localhost:8080/reservations/999999
```

---

### 2-2) Cancel Reservation - Already Cancelled

* **DELETE** `/reservations/{reservationId}`
* **409 Conflict**

설명

* 이미 `CANCELLED` 상태인 예약은 재취소할 수 없다.

Response (409)
```json
{
  "data": null,
  "error": {
    "code": "RESERVATION-002",
    "details": [],
    "message": "Reservation is not confirmed",
    "path": "/reservations/1",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl
```bash
curl -i -X DELETE http://localhost:8080/reservations/1
```

---

## curl Test Set (Cancel)
```bash
# cancel success
curl -i -X DELETE http://localhost:8080/reservations/1

# cancel - not found
curl -i -X DELETE http://localhost:8080/reservations/999999

# cancel - already cancelled
curl -i -X DELETE http://localhost:8080/reservations/1
```
