# Payment API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success (example)
**POST /payments** → `201 Created`

```json
{
  "data": {
    "paymentId": 1,
    "status": "SUCCESS",
    "paidAt": "2026-04-07T10:00:00",
    "refundedAt": null
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

---

## Idempotency-Key 정책

* 모든 결제 요청(`POST /payments`)은 `Idempotency-Key` 헤더가 필수입니다.
* 동일한 키로 재요청 시 새로운 결제를 처리하지 않고 기존 결과를 그대로 반환합니다.
* 네트워크 재시도로 인한 중복 결제를 방지합니다.
* 키는 클라이언트가 UUID 등으로 생성해 전달합니다.
* 키는 Redis에 저장되며 TTL은 24시간입니다.

---

## Error Codes

| 코드 | HTTP | 설명 |
|---|---|---|
| `PAYMENT-001` | 404 | Payment not found |
| `PAYMENT-002` | 409 | Payment already processed |
| `PAYMENT-003` | 400 | Payment failed (forceFailure 또는 내부 오류) |
| `PAYMENT-004` | 400 | Idempotency-Key header is required |
| `PAYMENT-005` | 409 | Refund is only allowed for successful payments |
| `PAYMENT-006` | 403 | You are not allowed to refund this payment |
| `COMMON-001` | 400 | Validation failed |
| `COMMON-007` | 500 | Internal server error |

---

## Endpoints

### 1) Pay

* **POST** `/payments`
* **201 Created**

설명

* Mock 결제를 처리한다.
* `Idempotency-Key` 헤더가 누락되면 `PAYMENT-004`를 반환한다.
* 동일한 `Idempotency-Key`로 재요청 시 기존 결과를 반환한다 (중복 결제 방지).
* `forceFailure: true`이면 강제 실패 처리한다 (Mock 결제 실패 시나리오 재현용).
* 결제 성공 시: 예약 `CONFIRMED`, 좌석 `RESERVED`, HOLD `CONFIRMED`로 전환된다.
* 결제 실패 시: 예약 `FAILED`, HOLD `EXPIRED`, 좌석 `AVAILABLE`로 복구된다.

Request Header

```
Idempotency-Key: {uuid}
Authorization: Bearer {accessToken}
```

Request Body

```json
{
  "reservationId": 1,
  "amount": 150000,
  "forceFailure": false
}
```

Response (201) — 결제 성공

```json
{
  "data": {
    "paymentId": 1,
    "status": "SUCCESS",
    "paidAt": "2026-04-07T10:00:00",
    "refundedAt": null
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

Response (201) — 결제 실패 (`forceFailure: true`)

```json
{
  "data": {
    "paymentId": 2,
    "status": "FAIL",
    "paidAt": null,
    "refundedAt": null
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
# 결제 성공
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"reservationId": 1, "amount": 150000, "forceFailure": false}'

# 결제 강제 실패
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"reservationId": 1, "amount": 150000, "forceFailure": true}'
```

Errors

* `400` `PAYMENT-003` (forceFailure: true)
* `400` `PAYMENT-004` (Idempotency-Key 헤더 누락)
* `409` `PAYMENT-002` (동일 예약 중복 결제)

---

### 1-1) Pay - Idempotency-Key Missing

* **POST** `/payments`
* **400 Bad Request**

Response (400)

```json
{
  "data": null,
  "error": {
    "code": "PAYMENT-004",
    "details": [],
    "message": "Idempotency-Key header is required",
    "path": "/payments",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"reservationId": 1, "amount": 150000}'
```

---

### 2) Refund

* **POST** `/payments/{paymentId}/refund`
* **200 OK**

설명

* `SUCCESS` 상태인 결제에 대해 환불을 처리한다.
* 본인 소유 결제가 아닌 경우 `PAYMENT-006`을 반환한다.
* `SUCCESS` 상태가 아닌 결제 환불 시도 시 `PAYMENT-005`를 반환한다.
* 환불 성공 시 결제 상태가 `SUCCESS → REFUNDED`로 변경된다.

Request Header

```
Authorization: Bearer {accessToken}
```

Response (200)

```json
{
  "data": {
    "paymentId": 1,
    "status": "REFUNDED",
    "paidAt": "2026-04-07T10:00:00",
    "refundedAt": "2026-04-07T10:20:00"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/payments/1/refund \
  -H "Authorization: Bearer eyJhbG..."
```

Errors

* `403` `PAYMENT-006` (본인 소유 아닌 결제)
* `404` `PAYMENT-001` (결제 없음)
* `409` `PAYMENT-005` (SUCCESS 상태 아님)

---

### 2-1) Refund - Not Owner

* **POST** `/payments/{paymentId}/refund`
* **403 Forbidden**

Response (403)

```json
{
  "data": null,
  "error": {
    "code": "PAYMENT-006",
    "details": [],
    "message": "You are not allowed to refund this payment",
    "path": "/payments/1/refund",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

### 2-2) Refund - Not Refundable

* **POST** `/payments/{paymentId}/refund`
* **409 Conflict**

설명

* `FAIL` 또는 이미 `REFUNDED` 상태인 결제는 환불할 수 없다.

Response (409)

```json
{
  "data": null,
  "error": {
    "code": "PAYMENT-005",
    "details": [],
    "message": "Refund is only allowed for successful payments",
    "path": "/payments/1/refund",
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
# Pay
# -------------------------

# pay success
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"reservationId": 1, "amount": 150000, "forceFailure": false}'

# pay - force failure
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"reservationId": 1, "amount": 150000, "forceFailure": true}'

# pay - idempotency key missing
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"reservationId": 1, "amount": 150000}'

# pay - duplicate (same idempotency key)
IDEM_KEY=$(uuidgen)
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{"reservationId": 1, "amount": 150000}'
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{"reservationId": 1, "amount": 150000}'

# -------------------------
# Refund
# -------------------------

# refund success
curl -X POST http://localhost:8080/payments/1/refund \
  -H "Authorization: Bearer eyJhbG..."

# refund - not owner
curl -X POST http://localhost:8080/payments/1/refund \
  -H "Authorization: Bearer eyJhbG_other..."

# refund - not refundable
curl -X POST http://localhost:8080/payments/2/refund \
  -H "Authorization: Bearer eyJhbG..."
```