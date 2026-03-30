# 아키텍처 다이어그램

---

## 1. 전체 시스템 흐름

클라이언트 요청이 API 레이어를 거쳐 Service → DB/Redis로 처리되는 전체 흐름입니다.

```mermaid
flowchart TD
    Client([Client])

    subgraph API["API Layer"]
        JwtFilter[JwtAuthenticationFilter]
        Controller[Controller]
    end

    subgraph Application["Application Layer"]
        Service[Service]
    end

    subgraph Domain["Domain Layer"]
        Repository[Repository]
    end

    subgraph Infra["Infrastructure"]
        MariaDB[(MariaDB)]
        Redis[(Redis)]
    end

    Client -->|HTTP Request| JwtFilter
    JwtFilter -->|인증 통과| Controller
    JwtFilter -->|인증 실패 401| Client
    Controller -->|비즈니스 로직 위임| Service
    Service -->|데이터 조회/저장| Repository
    Service -->|캐시 조회/저장 분산락| Redis
    Repository -->|JPA| MariaDB
    Service -->|응답 DTO 반환| Controller
    Controller -->|HTTP Response| Client
```

---

## 2. 대기열 흐름

> **초안 문서입니다.** TASK-029 구현 완료 후 실제 구현 기준으로 업데이트 예정입니다.

Redis Sorted Set 기반으로 순번을 발급하고 입장을 허용하는 흐름입니다.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant QueueService
    participant Redis
    participant Scheduler

    User->>API: POST /queue/enter (이벤트 입장 요청)
    API->>QueueService: 대기열 등록 요청
    QueueService->>Redis: ZADD queue:event:{eventId} timestamp userId
    Redis-->>QueueService: 순번 반환
    QueueService-->>API: 대기 순번 응답
    API-->>User: 200 OK (대기 순번, 예상 대기 시간)

    loop 스케줄러 주기적 실행
        Scheduler->>Redis: ZRANGE queue:event:{eventId} 0 N (상위 N명 조회)
        Redis-->>Scheduler: 입장 허용 대상 userId 목록
        Scheduler->>Redis: SET token:user:{userId} (입장 토큰 발급, TTL 30분)
        Scheduler->>Redis: ZREM queue:event:{eventId} userId (대기열에서 제거)
    end

    User->>API: GET /queue/status (대기 상태 확인)
    API->>Redis: ZRANK queue:event:{eventId} userId
    Redis-->>API: 현재 순번
    API-->>User: 200 OK (현재 순번 또는 입장 가능 여부)

    User->>API: POST /showtimes/{id}/hold (입장 토큰으로 좌석 선점)
    API->>Redis: GET token:user:{userId} (입장 토큰 검증)
    Redis-->>API: 토큰 유효
    API-->>User: 201 Created (HOLD 성공)
```

---

## 3. 캐시 흐름

이벤트 목록 조회 시 Redis 캐시를 우선 조회하고 miss 시 DB에서 가져오는 흐름입니다.

```mermaid
flowchart TD
    Client([Client])
    Controller[EventController]
    Service[EventService]
    Redis[(Redis\ncache: events:list)]
    DB[(MariaDB)]

    Client -->|GET /events| Controller
    Controller --> Service

    Service -->|Cache 조회\nGET events:list| Redis

    Redis -->|Cache Hit\nTTL 10분 이내| Service
    Redis -->|Cache Miss\nkey 없음 또는 만료| DB

    DB -->|조회 결과 반환| Service
    Service -->|Cache 저장\nSET events:list TTL 10분| Redis

    Service -->|DTO 반환| Controller
    Controller -->|HTTP Response| Client

    style Redis fill:#ff6b6b,color:#fff
    style DB fill:#4dabf7,color:#fff
```

---

## Redis Key 요약

| Key | 용도 | TTL |
|---|---|---|
| `refresh:{memberId}` | RefreshToken 저장 | 7일 |
| `blacklist:{accessToken}` | AccessToken 블랙리스트 | 잔여 만료 시간 |
| `events:list` | 이벤트 목록 캐시 | 10분 |
| `queue:event:{eventId}` | 대기열 순번 (Sorted Set) | 이벤트 종료 시 |
| `token:user:{userId}` | 대기열 입장 토큰 | 30분 |
| `lock:seat:{seatId}` | 좌석 분산락 | 락 획득 TTL |