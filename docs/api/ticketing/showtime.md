# Showtime API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success (example)
**GET /showtimes/{showtimeId}/seats** → `200 OK`

```json
{
  "data": [
    {
      "seatNumber": "A1",
      "grade": "VIP",
      "price": 150000,
      "status": "AVAILABLE"
    }
  ],
  "error": null,
  "success": true,
  "timestamp": "..."
}
````

### Error - Not Found (example)

없는 회차 조회 시 도메인 전용 에러 코드와 함께 내려옵니다.

```json
{
  "data": null,
  "error": {
    "code": "SHOWTIME-404",
    "details": [],
    "message": "Showtime not found",
    "path": "/showtimes/999999/seats",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

---

## Error Codes

`error.code` 필드는 아래 코드 중 하나를 반환합니다.

### Ticketing

* `SHOWTIME-404`: Showtime not found (404)

### Common

* `COMMON-404`: Resource not found (404)
* `COMMON-405`: Method not allowed (405)
* `COMMON-500`: Internal server error (500)

---

## Endpoints

### 1) List Seats by Showtime

* **GET** `/showtimes/{showtimeId}/seats`
* **200 OK**

설명

* 특정 회차의 좌석 목록을 조회한다.
* 좌석 번호, 좌석 등급, 가격, 좌석 상태를 함께 반환한다.
* 좌석은 `rowLabel`, `seatNo` 기준 오름차순으로 반환된다.

Response (200)

```json
{
  "data": [
    {
      "seatNumber": "A1",
      "grade": "VIP",
      "price": 150000,
      "status": "AVAILABLE"
    },
    {
      "seatNumber": "A2",
      "grade": "VIP",
      "price": 150000,
      "status": "AVAILABLE"
    },
    {
      "seatNumber": "A3",
      "grade": "VIP",
      "price": 150000,
      "status": "AVAILABLE"
    }
  ],
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl http://localhost:8080/showtimes/1/seats
```

Errors

* `404` `SHOWTIME-404`

---

### 1-1) List Seats by Showtime - Not Found

* **GET** `/showtimes/{showtimeId}/seats`
* **404 Not Found**

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "SHOWTIME-404",
    "details": [],
    "message": "Showtime not found",
    "path": "/showtimes/999999/seats",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl http://localhost:8080/showtimes/999999/seats
```

---

## curl Test Set

```bash
# -------------------------
# Seats
# -------------------------

# list seats by showtime
curl http://localhost:8080/showtimes/1/seats

# seats - showtime not found
curl http://localhost:8080/showtimes/999999/seats
```
