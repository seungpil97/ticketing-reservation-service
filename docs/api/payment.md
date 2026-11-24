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
* 동일한 키의 결과가 Redis에 저장돼 있고 요청 fingerprint가 일치하면, 새로운 결제를 처리하지 않고 기존 결제 결과를 `200 OK`로 반환합니다.
  결과 키가 없는 경우는 아래 [첫 결제는 커밋됐고 결과 키가 기록되지 않은 경우](#첫-결제는-커밋됐고-결과-키가-기록되지-않은-경우)를 참고합니다.
* 네트워크 재시도로 인한 중복 결제를 방지합니다.
* 키는 클라이언트가 UUID 등으로 생성해 전달합니다.
* 키는 Redis에 저장되며 TTL은 24시간입니다.

### 첫 결제는 커밋됐고 결과 키가 기록되지 않은 경우

결제가 DB에 커밋된 뒤 Redis에 결과를 저장하는 단계에서 Redis 접근 오류가 나면 다음과 같이 동작합니다 (TASK-028, [ADR-007](../architecture/adr/ADR-007-payment-redis-db-failure.md)).

* 첫 요청은 커밋된 결제 결과로 `201`을 응답합니다.
* 결과 저장(SET) 자체가 실패해 결과 키가 기록되지 않았다면, Redis 접근이 복구된 뒤 같은 `Idempotency-Key`로 재요청해도 기존 결과가 replay(`200`)되지 않습니다.
  * 처리 중 락 키가 남아 있으면 `409` `IDEMPOTENCY-001`. 락 키 TTL은 10초이며 첫 요청이 락을 획득한 시각부터 셉니다.
  * 락 키가 없으면 결제 처리로 진입한 뒤 이미 확정된 예약의 상태 가드에 의해 `409` `PAYMENT-002`.
  * 어느 경우에도 결제는 추가로 생성되지 않습니다.
* 결과 저장(SET)은 성공하고 처리 중 락 삭제만 실패한 경우에는 결과 키가 남아 있으므로, Redis 복구 후 같은 키 재요청이 replay(`200`)될 수 있습니다. 코드상 예상이며 별도로 검증하지 않았습니다.
* 이 동작은 위 경우에 한정됩니다. 다른 Redis·DB 장애에서의 응답을 보장하지 않습니다.

#### 결제 응답을 받지 못했다면: 예약 ID로 현재 상태 조회

결제를 요청한 뒤 응답을 받지 못했다면 예약 ID로 현재 결제 상태를 조회합니다 → [3) Get Payment of Reservation](#3-get-payment-of-reservation) (TASK-043, [ADR-008](../architecture/adr/ADR-008-payment-status-recovery-query.md)).

* 결제 요청 본문에 넣었던 `reservationId`를 그대로 사용합니다. `Idempotency-Key`는 필요 없습니다.
* 예약 소유자는 첫 결제 응답을 받지 못했을 때 같은 예약 ID로 조회를 요청할 수 있습니다. 먼저 `POST /payments`를 재시도하거나 `409`를 받을 필요는 없습니다.
* 조회 결과는 첫 결제 응답을 다시 보내 주는 것(replay)이 아니라, 조회 시점에 DB에 기록된 결제 상태입니다.
* 결제 행이 없을 때의 `404` `PAYMENT-001`은 "조회 시점에 이 예약의 결제가 기록돼 있지 않다"는 뜻입니다. 결제 실패가 확정됐다거나 즉시 다시 결제해도 된다는 보장이 아닙니다 (처리 중인 결제가 아직 커밋되지 않았을 수 있습니다).

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
| `IDEMPOTENCY-001` | 409 | The same request is already being processed (같은 `Idempotency-Key`의 처리 중 락이 남아 있음) |
| `RESERVATION-001` | 404 | Reservation not found (결제 상태 조회: 예약이 없거나 본인 예약이 아님) |
| `AUTH-003` | 401 | Unauthorized (인증 정보 없음) |
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
* 동일한 `Idempotency-Key`의 결과가 저장돼 있고 요청 fingerprint가 일치하면 기존 결제 결과를 `200 OK`로 반환한다 (중복 결제 방지).
  결과 키가 없는 경우는 [첫 결제는 커밋됐고 결과 키가 기록되지 않은 경우](#첫-결제는-커밋됐고-결과-키가-기록되지-않은-경우)를 참고한다.
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

### 3) Get Payment of Reservation

* **GET** `/reservations/{reservationId}/payment`
* **200 OK**

설명

* 예약 소유자가 그 예약의 현재 결제 상태를 조회한다 (TASK-043, [ADR-008](../architecture/adr/ADR-008-payment-status-recovery-query.md)).
* 사용 예: 결제를 요청(`POST /payments`)한 뒤 응답을 받지 못했을 때, 결제 요청에 넣었던 `reservationId`로 결제 결과를 확인한다.
* 인증: `Authorization: Bearer {accessToken}` (결제·환불과 같은 JWT 인증). `Idempotency-Key`는 필요 없다.
* 조회 가능한 회원: 예약 소유자(예약의 회원)만 조회할 수 있다. 결제를 요청한 회원이 아니라 예약 소유자를 기준으로 판단한다.
* 조회 값은 요청 시점에 DB에 기록된 결제 상태다. 첫 결제 응답을 재현하지 않으며, 결제 성공 뒤 환불했다면 `REFUNDED`가 조회된다.
* 읽기 전용이다. 결제·예약 상태와 Redis 멱등성 결과 키·처리 중 락 키를 변경하지 않으며, 결과 키가 없거나 락이 남아 있어도 조회된다.
* 결제 행이 있으면 실패 결제(`FAIL`)도 `200`으로 조회된다. 결제 행 자체가 없을 때만 `404` `PAYMENT-001`이다.

Request Header

```
Authorization: Bearer {accessToken}
```

Response Fields (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `paymentId` | number | 결제 ID |
| `status` | string | 현재 결제 상태: `SUCCESS` · `FAIL` · `REFUNDED` |
| `paidAt` | string \| null | 결제 완료 시각 (DB `payment.paid_at`, 초 단위). `SUCCESS`·`REFUNDED`는 값 있음, `FAIL`은 `null` |
| `refundedAt` | string \| null | 환불 완료 시각 (DB `payment.refunded_at`). `REFUNDED`만 값 있음, 그 외 `null` |

Response (200) — 결제 성공 (`SUCCESS`)

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

Response (200) — 결제 실패 (`FAIL`, 결제 행 있음)

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

Response (200) — 환불 완료 (`REFUNDED`, `paidAt`은 환불 전 값 유지)

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
curl http://localhost:8080/reservations/1/payment \
  -H "Authorization: Bearer eyJhbG..."
```

Errors

| HTTP | 코드 | 상황 |
|---|---|---|
| `401` | `AUTH-003` | 인증 정보 없음 |
| `404` | `RESERVATION-001` | 예약이 없거나 본인 예약이 아님 (두 경우 응답이 같아 다른 회원 예약의 존재 여부를 알 수 없다) |
| `404` | `PAYMENT-001` | 본인 예약이지만 결제 행이 없음 (결제 실패 확정이나 재결제 허용을 뜻하지 않는다) |

오류 응답에서 `data`와 `error.details`는 `null`이다.

---

### 3-1) Get Payment of Reservation - Not Found (Reservation)

* **GET** `/reservations/{reservationId}/payment`
* **404 Not Found**

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "RESERVATION-001",
    "details": null,
    "message": "Reservation not found",
    "path": "/reservations/999/payment",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

### 3-2) Get Payment of Reservation - Not Found (Payment)

* **GET** `/reservations/{reservationId}/payment`
* **404 Not Found**

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "PAYMENT-001",
    "details": null,
    "message": "Payment not found",
    "path": "/reservations/1/payment",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

### 3-3) Get Payment of Reservation - Unauthorized

* **GET** `/reservations/{reservationId}/payment`
* **401 Unauthorized**

Response (401)

```json
{
  "data": null,
  "error": {
    "code": "AUTH-003",
    "details": null,
    "message": "Unauthorized",
    "path": "/reservations/1/payment",
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

# -------------------------
# Get Payment of Reservation
# -------------------------

# payment status of own reservation
curl http://localhost:8080/reservations/1/payment \
  -H "Authorization: Bearer eyJhbG..."

# other member's reservation (404 RESERVATION-001)
curl http://localhost:8080/reservations/1/payment \
  -H "Authorization: Bearer eyJhbG_other..."

# no token (401 AUTH-003)
curl http://localhost:8080/reservations/1/payment
```