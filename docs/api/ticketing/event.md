# Event API Spec

## Base URL
- Local: `http://localhost:8080`

## Response Format
모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

### Success (example)
**GET /events** → `200 OK`

```json
{
  "data": [
    {
      "id": 1,
      "name": "뮤지컬 레미제라블",
      "venueName": "블루스퀘어",
      "startAt": "2026-03-10T19:00:00",
      "bookingOpen": true
    }
  ],
  "error": null,
  "success": true,
  "timestamp": "..."
}
````

### Error - Not Found (example)

없는 공연 조회 시 도메인 전용 에러 코드와 함께 내려옵니다.

```json
{
  "data": null,
  "error": {
    "code": "EVENT-404",
    "details": [],
    "message": "Event not found",
    "path": "/events/999999/showtimes",
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

* `EVENT-404`: Event not found (404)

### Common

* `COMMON-404`: Resource not found (404)
* `COMMON-405`: Method not allowed (405)
* `COMMON-500`: Internal server error (500)

---

## Endpoints

### 1) List Events

* **GET** `/events`
* **200 OK**

설명

* 공연 목록을 조회한다.
* 각 공연의 대표 시작 일시는 가장 빠른 회차(`min(showAt)`) 기준이다.
* `bookingOpen`은 공연 상태가 `ON_SALE` 인 경우 `true` 이다.
* Redis 캐시가 적용되어 있으며 TTL은 10분이다. (첫 번째 요청은 DB 조회, 이후 요청은 캐시에서 반환)

Response (200)

```json
{
  "data": [
    {
      "id": 1,
      "name": "뮤지컬 레미제라블",
      "venueName": "블루스퀘어",
      "startAt": "2026-03-10T19:00:00",
      "bookingOpen": true
    },
    {
      "id": 2,
      "name": "콘서트 Cold Night",
      "venueName": "KSPO DOME",
      "startAt": "2026-03-12T20:00:00",
      "bookingOpen": false
    }
  ],
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl http://localhost:8080/events
```

---

### 2) List Showtimes by Event

* **GET** `/events/{eventId}/showtimes`
* **200 OK**

설명

* 특정 공연의 회차 목록을 조회한다.
* 회차는 `showAt` 오름차순으로 반환된다.

Response (200)

```json
{
  "data": [
    {
      "id": 1,
      "showAt": "2026-03-10T19:00:00"
    },
    {
      "id": 2,
      "showAt": "2026-03-11T19:00:00"
    }
  ],
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl http://localhost:8080/events/1/showtimes
```

Errors

* `404` `EVENT-404`

---

### 2-1) List Showtimes by Event - Not Found

* **GET** `/events/{eventId}/showtimes`
* **404 Not Found**

Response (404)

```json
{
  "data": null,
  "error": {
    "code": "EVENT-404",
    "details": [],
    "message": "Event not found",
    "path": "/events/999999/showtimes",
    "timestamp": "..."
  },
  "success": false,
  "timestamp": "..."
}
```

curl

```bash
curl http://localhost:8080/events/999999/showtimes
```

---

## curl Test Set

```bash
# -------------------------
# Events
# -------------------------

# list events
curl http://localhost:8080/events

# -------------------------
# Showtimes
# -------------------------

# list showtimes by event
curl http://localhost:8080/events/1/showtimes

# showtimes - event not found
curl http://localhost:8080/events/999999/showtimes
```

