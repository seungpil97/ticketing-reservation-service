# Reservation API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

---

## 예약 흐름

```
POST /showtimes/{id}/hold    → 좌석 선점 (hold.md 참고)
POST /holds/{holdId}/reserve → 예약 확정
POST /payments               → 결제 (payment.md 참고)
DELETE /reservations/{id}    → 예약 취소
POST /payments/{id}/refund   → 환불 (payment.md 참고)
```

---

## Error Codes

| 코드 | HTTP | 설명 |
|---|---|---|
| `HOLD-001` | 404 | Hold not found |
| `HOLD-002` | 409 | Hold is not active (EXPIRED 또는 CONFIRMED 상태) |
| `HOLD-003` | 409 | Hold is expired (expiresAt 초과) |
| `RESERVATION-001` | 404 | Reservation not found |
| `RESERVATION-002` | 409 | Reservation is not confirmed |
| `SHOWTIME-SEAT-002` | 409 | Showtime seat is not held |
| `COMMON-007` | 500 | Internal server error |

---

## Endpoints

### 1) Confirm Reservation

* **POST** `/holds/{holdId}/reserve`
* **201 Created**

설명

* 유효한 HOLD를 최종 예약 확정한다.
* 예약 확정 후 `POST /payments`로 결제를 진행해야 예약이 완료된다.
* 결제 없이 HOLD가 만료되면 스케줄러가 자동 해제한다.
* 성공 시 상태 전이:
  * `showtime_seat`: `HELD → RESERVED`
  * `hold`: `ACTIVE → CONFIRMED`
  * `reservation`: 새 row 생성 (`CONFIRMED`)

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
curl -X POST http://localhost:8080/holds/1/reserve \
  -H "Authorization: Bearer eyJhbG..."
```

Errors

* `404` `HOLD-001` (Hold 없음)
* `409` `HOLD-002` (ACTIVE 아닌 상태)
* `409` `HOLD-003` (만료된 Hold)
* `409` `SHOWTIME-SEAT-002` (좌석 상태 HELD 아님)

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

---

### 1-2) Confirm Reservation - Hold Not Active

* **POST** `/holds/{holdId}/reserve`
* **409 Conflict**

설명

* 이미 `CONFIRMED`되었거나 `EXPIRED` 상태인 HOLD는 재확정할 수 없다.

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

---

### 1-3) Confirm Reservation - Hold Expired

* **POST** `/holds/{holdId}/reserve`
* **409 Conflict**

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

---

### 2) Cancel Reservation

* **DELETE** `/reservations/{reservationId}`
* **204 No Content**

설명

* 예약 확정(`CONFIRMED`) 상태인 예약을 취소한다.
* 취소 성공 시 상태 전이:
  * `reservation`: `CONFIRMED → CANCELLED`
  * `showtime_seat`: `RESERVED → AVAILABLE`
* 결제가 완료된 예약을 취소하는 경우 `POST /payments/{paymentId}/refund`로 별도 환불을 진행해야 한다.
* 본인 소유 예약인지 검증한다.

curl

```bash
curl -i -X DELETE http://localhost:8080/reservations/1 \
  -H "Authorization: Bearer eyJhbG..."
```

Errors

* `404` `RESERVATION-001` (예약 없음)
* `409` `RESERVATION-002` (CONFIRMED 상태 아님)

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

---

### 2-2) Cancel Reservation - Already Cancelled

* **DELETE** `/reservations/{reservationId}`
* **409 Conflict**

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

---

## curl Test Set

```bash
# -------------------------
# Confirm Reservation
# -------------------------

# reserve success
curl -X POST http://localhost:8080/holds/1/reserve \
  -H "Authorization: Bearer eyJhbG..."

# reserve - hold not found
curl -X POST http://localhost:8080/holds/999999/reserve \
  -H "Authorization: Bearer eyJhbG..."

# reserve - hold not active
curl -X POST http://localhost:8080/holds/1/reserve \
  -H "Authorization: Bearer eyJhbG..."

# reserve - hold expired
curl -X POST http://localhost:8080/holds/2/reserve \
  -H "Authorization: Bearer eyJhbG..."

# -------------------------
# Cancel Reservation
# -------------------------

# cancel success
curl -i -X DELETE http://localhost:8080/reservations/1 \
  -H "Authorization: Bearer eyJhbG..."

# cancel - not found
curl -i -X DELETE http://localhost:8080/reservations/999999 \
  -H "Authorization: Bearer eyJhbG..."

# cancel - already cancelled
curl -i -X DELETE http://localhost:8080/reservations/1 \
  -H "Authorization: Bearer eyJhbG..."
```