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
    Service -->|JPA Repository 호출| Repository
    Service -->|락/토큰/대기열 등 Redis 연산| Redis
    Repository -->|JPA| MariaDB
    Service -->|응답 DTO 반환| Controller
    Controller -->|HTTP Response| Client
```

---

## 2. 대기열 흐름

Redis Sorted Set 기반으로 순번을 발급하고 입장을 허용하는 흐름입니다.

```mermaid
sequenceDiagram
    actor User
    participant API
    participant QueueService
    participant HoldService
    participant DistributedLockService
    participant Redis
    participant Scheduler

    User->>API: POST /queue/enter (이벤트 입장 요청)
    API->>QueueService: 대기열 등록 요청
    QueueService->>Redis: INCR queue:seq:{eventId}
    Redis-->>QueueService: score 반환
    QueueService->>Redis: ZADD NX queue:event:{eventId} score userId
    QueueService->>Redis: ZRANK queue:event:{eventId} userId
    Redis-->>QueueService: 현재 순번 반환
    QueueService-->>API: 대기 순번 응답
    API-->>User: 200 OK (대기 순번, 예상 대기 시간)

    loop 스케줄러 주기적 실행
        Scheduler->>Redis: ZRANGE queue:event:{eventId} 0 N (상위 N명 조회)
        Redis-->>Scheduler: 입장 허용 대상 userId 목록
        Scheduler->>Redis: SET token:user:{userId} TTL 30분
        Scheduler->>Redis: SADD queue:admitted:members:{eventId} userId
        Scheduler->>Redis: ZREM queue:event:{eventId} userId
        Note over Redis: 입장 토큰은 TTL 만료 시 자동 삭제
    end

    User->>API: GET /queue/status?eventId={eventId} (대기 상태 확인)
    API->>QueueService: 상태 조회 요청
    QueueService->>Redis: EXISTS token:user:{userId}
    Redis-->>QueueService: 토큰 존재 여부
    
    alt 토큰 존재
        QueueService-->>API: 입장 가능 응답
    else 토큰 없음
        QueueService->>Redis: ZRANK queue:event:{eventId} userId
        Redis-->>QueueService: 현재 순번 또는 null
        QueueService->>Redis: SISMEMBER queue:admitted:members:{eventId} userId
        Redis-->>QueueService: 입장 이력 여부
        QueueService-->>API: 대기중 / 재진입 필요 응답
    end   
    API-->>User: 200 OK

    User->>API: POST /showtimes/{id}/hold (입장 토큰으로 좌석 선점)
    API->>HoldService: HOLD 요청
    HoldService->>QueueService: 입장 토큰 검증
    HoldService->>DistributedLockService: executeWithLock(...)
    DistributedLockService->>Redis: tryLock hold:seat:{showtimeId}:{seatId}
    DistributedLockService-->>HoldService: 락 획득
    HoldService->>HoldService: processHold(...)
    HoldService-->>API: HOLD 결과 반환
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
    Redis[(Redis\ncache: events)]
    DB[(MariaDB)]

    Client -->|GET /events| Controller
    Controller --> Service

    Service -->|Cache 조회\nGET events 캐시| Redis

    Redis -->|Cache Hit\nTTL 10분 이내| Service
    Redis -->|Cache Miss\nkey 없음 또는 만료| DB

    DB -->|조회 결과 반환| Service
    Service -->|Cache 저장\nSET events 캐시 TTL 10분| Redis

    Service -->|DTO 반환| Controller
    Controller -->|HTTP Response| Client

    style Redis fill:#ff6b6b,color:#fff
    style DB fill:#4dabf7,color:#fff
```

---

## Redis Key 요약

| Key                                    | 용도                  | TTL / 정리 방식                                      |
|----------------------------------------|---------------------|--------------------------------------------------|
| `refresh:{memberId}`                   | RefreshToken 저장     | 7일                                               |
| `blacklist:{accessToken}`              | AccessToken 블랙리스트   | 잔여 만료 시간                                         |
| `events 캐시 (events::...)`              | 이벤트 목록 캐시           | 10분                                              | 
| `queue:event:{eventId}`                | 대기열 순번 (Sorted Set) | 이벤트 종료 시 key 삭제                                  |
| `token:user:{userId}`                  | 대기열 입장 토큰           | 30분                                              |
| `hold:seat:{showtimeId}:{seatId}`      | 좌석 분산락              | Redisson leaseTime 기반 자동 해제                      |
| `idempotency:payment:{idempotencyKey}` | 결제 멱등성 키            | 24시간                                             |
| `queue:active:events`                  | 활성 대기열 이벤트 목록 (Set) | 종료된 이벤트는 Set에서 제거                                |
| `queue:admitted:members:{eventId}`     | 대기열 입장 허용 이력 (Set)  | 이벤트 종료 시 key 삭제                                  |
| `queue:seq:{eventId}`                  | 대기열 순번 카운터 (INCR)   | 현재 구현상 명시적 TTL 없음 / 종료 정리 정책은 TASK-057-1에서 보완 예정 |

---

## 4. 아키텍처 의사결정 기록 (ADR)

핵심 설계 결정의 배경과 트레이드오프를 ADR(Architecture Decision Record) 형식으로 기록합니다.

| ADR                                      | 제목                                            | 상태      |
|------------------------------------------|-----------------------------------------------|---------|
| [ADR-001](adr/ADR-001-no-kafka.md)       | Kafka 미도입 — 단일 트랜잭션 직접 상태 전환 유지               | Decided |
| [ADR-002](adr/ADR-002-mock-payment.md)   | Mock 결제 — PG 연동 없이 forceFailure 플래그로 성공/실패 재현 | Decided |
| [ADR-003](adr/ADR-003-no-outbox.md)      | Outbox Pattern 미적용 — 단일 트랜잭션으로 상태 일관성 유지      | Decided |
| [ADR-004](adr/ADR-004-no-soft-delete.md) | Soft Delete 미적용 — 상태값으로 비활성 데이터 처리            | Decided |