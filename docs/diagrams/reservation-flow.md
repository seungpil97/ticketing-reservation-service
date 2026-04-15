# 전체 예약 흐름 시나리오

이 문서는 티켓팅 서비스의 전체 예약 흐름을 시나리오별로 정리한 설계 기준 문서입니다.

- README의 요약 다이어그램은 전체 흐름을 압축하여 보여줍니다.
- 이 문서는 시나리오별 상세 흐름과 예외 분기를 포함합니다.
- 이후 ADR, 정합성 전략, E2E 테스트 기준 문서의 공통 기준선으로 사용됩니다.

---

## 시나리오 1. 로그인 및 토큰 발급

사용자가 인증에 성공하고 AccessToken / RefreshToken을 발급받는 흐름입니다.

- JWT 기반 인증
- RefreshToken은 Redis에 저장하여 재발급 및 로그아웃 제어 기반 확보

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthService
    participant Redis

    Client->>AuthController: POST /auth/login (email, password)
    AuthController->>AuthService: 자격 증명 검증 요청

    alt 자격 증명 불일치
        AuthService-->>AuthController: 인증 실패 예외
        AuthController-->>Client: 401 Unauthorized
    else 자격 증명 일치
        AuthService->>Redis: SET refresh:{memberId} (TTL 7일)
        AuthService-->>AuthController: 토큰 발급 결과
        AuthController-->>Client: AccessToken + RefreshToken 반환
    end
```

---

## 시나리오 2. 대기열 진입 및 입장 가능 상태 전환

사용자가 이벤트 대기열에 진입하고, Scheduler가 비동기로 입장 가능 상태로 전환하며,
사용자가 별도 조회 API로 입장 가능 여부를 확인하는 흐름입니다.

- Redis Sorted Set으로 대기 순서 관리 (score = 진입 timestamp 기반 전역 카운터)
- Scheduler가 비동기로 상위 N명을 입장 가능 상태로 전환
- Client 응답은 별도 조회 흐름에서 확인 (진입 응답과 입장 가능 응답은 분리)

```mermaid
sequenceDiagram
    participant Client
    participant QueueController
    participant QueueService
    participant Redis
    participant Scheduler

    Note over Client, Redis: 2-1. 대기열 진입

    Client->>QueueController: POST /queue/events/{eventId}/join (AccessToken)
    QueueController->>QueueService: 대기열 등록 요청

    alt 존재하지 않는 eventId
        QueueService-->>QueueController: 예외 발생 (EVENT_NOT_FOUND)
        QueueController-->>Client: 404 Not Found
    else 유효한 eventId    
        QueueService->>Redis: ZADD queue:event:{eventId} (score = 전역 카운터)
        QueueService->>Redis: SADD queue:active:events {eventId}
        QueueService-->>QueueController: 현재 순번 + 예상 대기 시간
        QueueController-->>Client: 현재 순번 + 예상 대기 시간 반환
    end

    Note over Scheduler, Redis: 2-2. 입장 가능 상태 전환 (비동기)

    loop 스케줄러 주기마다 실행
        Scheduler->>QueueService: admitTopMembers(eventId) 호출
        QueueService->>Redis: ZPOPMIN queue:event:{eventId} (상위 N명)
        QueueService->>Redis: SET token:user:{memberId} (TTL 30분)
        QueueService->>Redis: SADD queue:admitted:{eventId} {memberId}
        QueueService->>Redis: ZREM queue:event:{eventId} {memberId}
    end

    Note over Client, Redis: 2-3. 입장 가능 여부 확인 (Client 폴링)

    Client->>QueueController: GET /queue/events/{eventId}/status (AccessToken)
    QueueController->>QueueService: 상태 조회 요청

    alt 입장 토큰 존재 (admitted=true)
        QueueService->>Redis: GET token:user:{memberId}
        QueueService-->>QueueController: admitted=true
        QueueController-->>Client: admitted=true 반환
    else 대기열에 존재
        QueueService->>Redis: ZRANK queue:event:{eventId} {memberId}
        QueueService-->>QueueController: 현재 순번 + 예상 대기 시간
        QueueController-->>Client: 현재 순번 + 예상 대기 시간 반환
    else 대기열 미등록 + 입장 이력 없음
        QueueService-->>QueueController: reEnterType=NONE
        QueueController-->>Client: reEnterType=NONE (최초 미진입)
    else 대기열 미등록 + 입장 이력 있음 (토큰 만료)
        Note over QueueService, Redis: admitted 이력 존재 + 현재 토큰 없음 조합으로 만료 판단
        QueueService-->>QueueController: reEnterType=EXPIRED
        QueueController-->>Client: reEnterType=EXPIRED (재진입 필요)
    end
```

---

## 시나리오 3. 좌석 선점 (HOLD)

입장 토큰을 가진 사용자가 특정 좌석에 대해 HOLD를 생성하는 흐름과 동시성 제어 방식입니다.

- 입장 토큰 검증은 HoldService 내부에서 분산락 획득 전에 수행
- Redisson 분산락으로 동일 회차의 동일 좌석에 대한 동시 HOLD 요청 직렬화
- 락 내부에서 좌석 상태를 재검증하여 정합성 보장
- 락 키: `hold:seat:{showtimeId}:{seatId}` (같은 회차의 같은 좌석만 직렬화)

```mermaid
sequenceDiagram
    participant Client
    participant HoldController
    participant HoldService
    participant QueueService
    participant RedissonLock
    participant DB

    Client->>HoldController: POST /showtimes/{showtimeId}/hold (AccessToken, seatId, memberId)
    HoldController->>HoldService: hold(showtimeId, request) 호출

    HoldService->>QueueService: validateAdmissionToken(memberId)

    alt 입장 토큰 없음 또는 만료
        QueueService-->>HoldService: 예외 발생 (ADMISSION_TOKEN_NOT_FOUND)
        HoldService-->>HoldController: 예외 전파
        HoldController-->>Client: 403 Forbidden
    else 입장 토큰 유효
        HoldService->>RedissonLock: hold:seat:{showtimeId}:{seatId} 획득 시도 (waitTime 3s, leaseTime 5s)

        alt 락 획득 실패
            RedissonLock-->>HoldService: 예외 발생
            HoldService-->>HoldController: 예외 전파
            HoldController-->>Client: 409 Conflict (락 획득 실패)
        else 락 획득 성공
            HoldService->>DB: showtime, seat, showtimeSeat, member 존재 여부 검증

            alt 좌석 상태가 AVAILABLE이 아님 (이미 선점됨)
                HoldService-->>HoldController: 예외 전파 (NOT_AVAILABLE_FOR_HOLD)
                HoldController-->>Client: 409 Conflict
            else 좌석 AVAILABLE 확인
                HoldService->>DB: Hold 생성 (expiresAt = 현재 + 5분)
                HoldService->>DB: ShowtimeSeat 상태 AVAILABLE → HELD 변경
                HoldService->>RedissonLock: 락 해제
                HoldService-->>HoldController: HoldResponse 반환
                HoldController-->>Client: 201 Created (holdId 반환)
            end
        end
    end
```

---

## 시나리오 4. 예약 생성 및 결제

HOLD된 좌석을 기반으로 예약을 생성하고 결제를 수행하는 흐름입니다.
예약 생성과 결제는 분리된 단계로, 각각 독립 API 호출로 처리됩니다.

- reserve 시점: Reservation PENDING 생성, 좌석/HOLD 상태는 유지
- 결제 성공 시: Reservation CONFIRMED, 좌석 RESERVED, HOLD CONFIRMED
- 결제 실패 시: Reservation FAILED, HOLD EXPIRED, 좌석 AVAILABLE 복구
- idempotencyKey로 네트워크 재시도 시 결제 중복 방지 (Redis 기반)
- 결제 실패 시 forceFailure=true로 재시도 허용 (Redis 저장 안 함)

```mermaid
sequenceDiagram
    participant Client
    participant ReservationController
    participant ReservationService
    participant PaymentController
    participant PaymentService
    participant IdempotencyRedis
    participant MockPaymentClient
    participant DB

    Note over Client, DB: 4-1. 예약 생성

    Client->>ReservationController: POST /holds/{holdId}/reserve (AccessToken)
    ReservationController->>ReservationService: reserve(holdId) 호출

    alt HOLD가 존재하지 않음
        ReservationService-->>ReservationController: 예외 전파
        ReservationController-->>Client: 404 Not Found
    else HOLD 상태가 ACTIVE가 아님
        ReservationService-->>ReservationController: 예외 전파 (HOLD_NOT_ACTIVE)
        ReservationController-->>Client: 409 Conflict
    else HOLD가 만료됨
        ReservationService-->>ReservationController: 예외 전파 (HOLD_EXPIRED)
        ReservationController-->>Client: 409 Conflict
    else 좌석 상태가 HELD가 아님
        ReservationService-->>ReservationController: 예외 전파 (NOT_HELD)
        ReservationController-->>Client: 409 Conflict
    else 유효한 HOLD
        ReservationService->>DB: Reservation 생성 (status=PENDING)
        Note right of DB: 좌석/HOLD 상태는 결제 완료 시점까지 유지
        ReservationService-->>ReservationController: ReservationResponse 반환
        ReservationController-->>Client: 201 Created (reservationId 반환)
    end

    Note over Client, DB: 4-2. 결제 요청

    Client->>PaymentController: POST /payments (idempotencyKey, reservationId, amount, forceFailure)
    PaymentController->>PaymentService: pay(request) 호출

    alt idempotencyKey 누락
        PaymentController-->>Client: 400 Bad Request (IDEMPOTENCY_KEY_MISSING)
    else 동일 idempotencyKey 재요청 (중복)
        PaymentService->>IdempotencyRedis: GET idempotencyKey
        IdempotencyRedis-->>PaymentService: cached result
        PaymentService-->>PaymentController: 기존 결제 결과
        PaymentController-->>Client: 기존 결제 결과 반환 (DB 처리 없음)
    else 신규 결제 요청
        PaymentService->>DB: Reservation 존재 및 PENDING 상태 검증

        alt Reservation이 PENDING 상태가 아님
            PaymentService-->>PaymentController: 예외 전파 (PAYMENT_ALREADY_PROCESSED)
            PaymentController-->>Client: 409 Conflict
        else forceFailure=true (결제 실패 시뮬레이션)
            PaymentService->>DB: Payment FAIL 저장
            PaymentService->>DB: Reservation FAILED 전환
            PaymentService->>DB: HOLD EXPIRED 전환
            PaymentService->>DB: ShowtimeSeat AVAILABLE 복구
            Note right of DB: 실패 시 Redis 저장 안 함 (재시도 허용)
            PaymentService-->>PaymentController: 결제 실패 결과
            PaymentController-->>Client: 결제 실패 응답
        else forceFailure=false (정상 결제)
            PaymentService->>MockPaymentClient: 결제 요청
            MockPaymentClient-->>PaymentService: 결제 성공 응답
            PaymentService->>DB: Payment SUCCESS 저장
            PaymentService->>DB: Reservation CONFIRMED 전환
            PaymentService->>DB: ShowtimeSeat RESERVED 전환
            PaymentService->>DB: HOLD CONFIRMED 전환
            PaymentService->>IdempotencyRedis: SET idempotencyKey (결과 캐싱)
            PaymentService-->>PaymentController: 결제 성공 결과
            PaymentController-->>Client: 결제 성공 응답
        end
    end
```

---

## 시나리오 5-1. 예약 취소 (PENDING / CONFIRMED)

PENDING 또는 CONFIRMED 상태의 예약을 취소하고 좌석을 복구하는 흐름입니다.
환불이 필요한 경우 결제 환불 API는 별도 시나리오 5-2에서 처리합니다.

- 취소 허용 상태: PENDING, CONFIRMED (FAILED, CANCELLED는 불가)
- 취소는 단순 삭제가 아니라 CANCELLED 상태로 전이 (이력 보존)
- 예약 취소와 결제 환불은 분리된 API로 처리한다
- 결제 환불은 시나리오 5-2에서 다룬다

```mermaid
sequenceDiagram
    participant Client
    participant ReservationController
    participant ReservationService
    participant DB

    Client->>ReservationController: DELETE /reservations/{reservationId} (AccessToken)
    ReservationController->>ReservationService: cancel(reservationId) 호출

    alt Reservation이 존재하지 않음
        ReservationService-->>ReservationController: 예외 전파
        ReservationController-->>Client: 404 Not Found
    else 취소 불가 상태 (FAILED, CANCELLED)
        ReservationService-->>ReservationController: 예외 전파 (NOT_CONFIRMED)
        ReservationController-->>Client: 409 Conflict
    else PENDING 상태 취소
        ReservationService->>DB: ShowtimeSeat 상태 HELD → AVAILABLE 복구
        ReservationService->>DB: Reservation 상태 CANCELLED 전환
        ReservationService-->>ReservationController: 취소 완료
        ReservationController-->>Client: 200 OK
    else CONFIRMED 상태 취소
        ReservationService->>DB: ShowtimeSeat 상태 RESERVED → AVAILABLE 복구
        ReservationService->>DB: Reservation 상태 CANCELLED 전환
        Note right of DB: 결제 환불 처리 자체는 시나리오 5-2에서 별도로 수행
        ReservationService-->>ReservationController: 취소 완료
        ReservationController-->>Client: 200 OK
    end
```

---

## 시나리오 5-2. 결제 환불

CONFIRMED 상태의 결제 건에 대해 환불을 요청하고 Payment / Reservation / HOLD / 좌석 상태를 복구하는 흐름입니다.

- 소유권 검증을 상태 검증보다 먼저 수행하여 타인의 결제 상태 유추 방지
- 상태 전이 순서: Payment REFUNDED → Reservation CANCELLED → HOLD EXPIRED → 좌석 AVAILABLE
- 현재 구현은 단일 트랜잭션 내 상태 전이만 처리하며, 외부 PG 환불 연동은 미구현
- 외부 PG 환불 실패 시 데이터 불일치 위험 존재 → 보상 트랜잭션 도입 검토 필요 (TASK-032/033)

```mermaid
sequenceDiagram
    participant Client
    participant PaymentController
    participant PaymentService
    participant DB

    Client->>PaymentController: POST /payments/{paymentId}/refund (AccessToken)
    PaymentController->>PaymentService: refund(paymentId, loginMember) 호출

    alt Payment가 존재하지 않음
        PaymentService-->>PaymentController: 예외 전파
        PaymentController-->>Client: 404 Not Found
    else 소유권 불일치 (타인의 결제)
        PaymentService-->>PaymentController: 예외 전파 (REFUND_FORBIDDEN)
        PaymentController-->>Client: 403 Forbidden
    else SUCCESS 상태가 아님 (환불 불가)
        PaymentService-->>PaymentController: 예외 전파 (REFUND_NOT_ALLOWED)
        PaymentController-->>Client: 409 Conflict
    else 정상 환불 처리
        PaymentService->>DB: Payment 상태 REFUNDED 전환
        PaymentService->>DB: Reservation 상태 CANCELLED 전환
        PaymentService->>DB: HOLD 상태 EXPIRED 전환
        PaymentService->>DB: ShowtimeSeat 상태 AVAILABLE 복구
        Note right of DB: 현재 단일 트랜잭션 처리
        Note right of DB: 외부 PG 환불 실패 시 보상 트랜잭션 필요 (TASK-032/033)
        PaymentService-->>PaymentController: 환불 결과
        PaymentController-->>Client: 200 OK (환불 완료)
    end
```