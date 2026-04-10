# Queue API Spec

## Base URL

- Local: `http://localhost:8080`

## Response Format

모든 API 응답은 `ApiResponse<T>`로 래핑됩니다.

---

## 대기열 흐름

```
POST /queue/enter       → 대기열 등록 (Redis Sorted Set 순번 발급)
GET  /queue/status      → 대기 순번 / 입장 가능 여부 확인
                          ↓ admitted: true
POST /showtimes/{id}/hold → 좌석 선점 (입장 토큰 검증)
```

* 스케줄러가 주기적으로 대기열 상위 N명에게 입장 토큰을 발급한다 (TTL 30분).
* 입장 토큰 만료 시 `reEnterType: EXPIRED`로 재진입 안내.
* 한 번도 입장 허용된 적 없는 경우 `reEnterType: NONE`으로 안내.

---

## Error Codes

| 코드           | HTTP | 설명                          |
|--------------|------|-----------------------------|
| `QUEUE-001`  | 404  | User is not in queue        |
| `QUEUE-002`  | 403  | Admission token not found   |
| `QUEUE-003`  | 403  | Admission token has expired |
| `QUEUE-004`  | 404  | Event not found             |
| `COMMON-001` | 400  | Validation failed           |
| `COMMON-007` | 500  | Internal server error       |

---

## Endpoints

### 1) Enter Queue

* **POST** `/queue/enter`
* **200 OK**

설명

* 특정 이벤트의 대기열에 등록하고 현재 순번과 예상 대기 시간을 반환한다.
* 이미 등록된 유저는 기존 순번을 그대로 반환한다 (중복 등록 방지).
* Redis Sorted Set(`queue:event:{eventId}`)에 `score = 진입 timestamp`로 저장한다.
* JWT 인증 필수.

Request Header

```
Authorization: Bearer {accessToken}
```

Request Body

```json
{
  "eventId": 1
}
```

Response (200)

```json
{
  "data": {
    "rank": 5,
    "estimatedWaitSeconds": 150
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl -X POST http://localhost:8080/queue/enter \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"eventId": 1}'
```

Errors

* `400` `COMMON-001` (eventId 누락)
* `404` `QUEUE-004` (존재하지 않는 이벤트)

---

### 2) Get Queue Status

* **GET** `/queue/status?eventId={eventId}`
* **200 OK**

설명

* 현재 대기 순번 또는 입장 가능 여부를 반환한다.
* JWT 인증 필수.

| 상태     | admitted | reEnterType | 설명                                       |
|--------|----------|-------------|------------------------------------------|
| 대기 중   | false    | null        | 아직 입장 허용 전, rank/estimatedWaitSeconds 반환 |
| 입장 가능  | true     | null        | 입장 토큰 발급됨, 좌석 선점 가능                      |
| 최초 미진입 | false    | NONE        | 대기열 등록 전 또는 대기열에 없음                      |
| 토큰 만료  | false    | EXPIRED     | 입장 토큰 TTL 30분 초과, 재진입 필요                 |

Response (200) — 대기 중

```json
{
  "data": {
    "rank": 3,
    "estimatedWaitSeconds": 90,
    "admitted": false,
    "reEnterType": null
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

Response (200) — 입장 가능

```json
{
  "data": {
    "rank": 0,
    "estimatedWaitSeconds": 0,
    "admitted": true,
    "reEnterType": null
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

Response (200) — 토큰 만료

```json
{
  "data": {
    "rank": 0,
    "estimatedWaitSeconds": 0,
    "admitted": false,
    "reEnterType": "EXPIRED"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

Response (200) — 최초 미진입

```json
{
  "data": {
    "rank": 0,
    "estimatedWaitSeconds": 0,
    "admitted": false,
    "reEnterType": "NONE"
  },
  "error": null,
  "success": true,
  "timestamp": "..."
}
```

curl

```bash
curl -H "Authorization: Bearer eyJhbG..." \
  "http://localhost:8080/queue/status?eventId=1"
```

---

## curl Test Set

```bash
# -------------------------
# Enter Queue
# -------------------------

# enter success
curl -X POST http://localhost:8080/queue/enter \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"eventId": 1}'

# enter - eventId missing
curl -X POST http://localhost:8080/queue/enter \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{}'

# enter - event not found
curl -X POST http://localhost:8080/queue/enter \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbG..." \
  -d '{"eventId": 999999}'

# -------------------------
# Queue Status
# -------------------------

# status - waiting
curl -H "Authorization: Bearer eyJhbG..." \
  "http://localhost:8080/queue/status?eventId=1"

# status - admitted
curl -H "Authorization: Bearer eyJhbG..." \
  "http://localhost:8080/queue/status?eventId=1"

# status - token expired
curl -H "Authorization: Bearer eyJhbG..." \
  "http://localhost:8080/queue/status?eventId=1"
```