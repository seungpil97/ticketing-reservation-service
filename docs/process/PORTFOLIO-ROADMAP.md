# 포트폴리오 TASK 로드맵

## 문서 책임

이 문서는 다음 정보를 하나의 로드맵에서 관리한다.

- TASK 상태 기준
- 전체 TASK 이력
- 현재/활성 TASK
- 다음 TASK와 선행조건
- CS / interview / learning 연결

과거 로드맵의 TASK를 임의로 삭제하지 않는다.
과거 문서의 상태 표기는 현재 완료 근거가 아니며, 현재 근거가 확인되지 않은 항목은 `재확인필요`로 기록한다.
현재 확인 근거가 있는 상태가 과거 기록보다 우선한다.

## 상태 기준

- `완료`: 실제 완료 근거가 확인된 TASK
- `진행중`: 사용자 승인 후 실제 진행 중인 TASK
- `계획`: 사용자 승인된 후속 TASK
- `재확인필요`: 과거 자료에는 있으나 현재 완료/진행 근거가 확인되지 않은 항목

별도 상태 이력 열은 두지 않는다. 상세 변경 이력은 Issue, PR, commit 등 실제 근거에서 확인한다.

## 현재 확인 상태

| TASK | 내용 | 상태 | 확인 근거 |
| --- | --- | --- | --- |
| TASK-031 | Redis Sliding Window 기반 Rate Limiting 구현 | 완료 | Issue #96, PR #97, commit `f0aabf8` |
| TASK-059 | Claude Code 최소 구성 적용 및 위험 리뷰 Agent 검증 | 완료 | merge SHA `122816d91533befcc7a63c06cf83a7150e1fd05d` |
| TASK-060 | 프로젝트 프로세스 문서 정합성 복구 및 학습 계약 추가 | 진행중 | Issue #100, branch `chore/TASK-060-process-doc-consistency-recovery` |

## 다음 직접 진행 순서

사용자 승인 없이 다음 TASK를 자동으로 시작하지 않는다.

```text
TASK-032
→ TASK-033
→ TASK-033-1
→ TASK-028
→ TASK-043
```

| TASK | 내용 | 상태 | 선행 조건 |
| --- | --- | --- | --- |
| TASK-032 | 이벤트 기반 예약 처리 (Spring Events) | 계획 | TASK-060 완료 + 사용자 승인 |
| TASK-033 | Outbox Pattern 구현 | 계획 | TASK-032 완료 + 사용자 승인 |
| TASK-033-1 | Outbox 재처리 실패 시나리오 | 계획 | TASK-033 완료 + 사용자 승인 |
| TASK-028 | Redis/DB 장애 시나리오 구현 및 문서화 | 계획 | TASK-033-1 완료 + 사용자 승인 |
| TASK-043 | 장애 복구 전략 설계 + 코드 구현 | 계획 | TASK-028 완료 + 사용자 승인 |

## 전체 TASK 이력

A old roadmap의 고유 TASK 74개를 모두 보존한다.
A에서 과거에 `완료` 또는 `남은 로드맵`으로 분류됐더라도 현재 근거가 확인되지 않으면 이 문서에서는 `재확인필요`로 기록한다.
현재 근거가 확인된 TASK-031과 MASTER에서 완료가 확정된 TASK-059는 `완료`, 현재 수행 중인 TASK-060은 `진행중`, 승인된 직접 후속 5개는 `계획`으로 기록한다.

### Phase 1. 기반 구축

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-001 | 프로젝트 부트스트랩 + MariaDB 연결 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |
| TASK-002 | 공통 응답 포맷 + 글로벌 예외 처리 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 예외 처리 (Week 11) |
| TASK-007 | Flyway 마이그레이션 + 테스트/CI 환경 정리 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | DB (Week 4) |
| TASK-008 | Docker Compose 로컬 MariaDB 재현성 구성 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Docker (Week 14) |
| TASK-010 | Docker dev DB 연결 가이드 및 트러블슈팅 정리 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Docker (Week 14) |
| TASK-015 | 개발환경 안정화 - dev/test DB 분리 및 CI 환경 정리 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Docker/CI/CD (Week 14) |
| TASK-019-1 | Spring Boot 4.x → 3.4.3 다운그레이드 (Redisson 호환성) | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |

### Phase 2. 핵심 도메인 구현

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-003 | 회원 생성/조회 API 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Spring MVC (Week 12) |
| TASK-004 | 회원 수정/삭제 API 구현 및 예외 케이스 강화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 예외 처리 (Week 11) |
| TASK-006 | 회원 목록 페이징/정렬 적용 및 서비스 테스트 추가 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JPA (Week 6) |
| TASK-009 | 티켓팅 스키마 및 시드 데이터 추가 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | DB 인덱스 (Week 4) |
| TASK-011 | 티켓팅 조회 API 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JPA/N+1 (Week 6) |
| TASK-012 | 좌석 선점(HOLD) API 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션/락 (Week 4~5) |
| TASK-013 | 만료된 HOLD 해제 스케줄링 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션 (Week 5) |
| TASK-014 | 예약 확정 API 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션/락 (Week 4~5) |
| TASK-035 | Mock 결제 API + 예약 흐름 완성 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | @Transactional (Week 5) |
| TASK-036 | 결제 멱등성 보장 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션/Redis (Week 5, 8) |
| TASK-036-1 | 결제 실패/환불 처리 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | @Transactional 롤백 (Week 5) |
| TASK-018 | 예약 취소 API 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션 (Week 5) |

### Phase 2-1. 성능 & 동시성

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-016 | 동시성 제어 - 비관적 락을 이용한 좌석 선점 중복 방지 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Lock/MVCC (Week 4) |
| TASK-017 | 조회 성능 최적화 - N+1 문제 해결 및 인덱스 설계 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 인덱스/JPA N+1 (Week 4, 6) |
| TASK-019 | Redis 분산락으로 동시성 강화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Redis/분산락 (Week 8) |
| TASK-020 | Redis 캐시 적용 - 이벤트 목록 조회 성능 개선 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 캐시 전략 (Week 8) |
| TASK-029-1 | QueueConcurrencyTest Mockito stub 멀티스레드 불안정 버그 수정 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Java 동시성 (Week 10) |

### Phase 2-2. 인증 & 보안

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-021 | Spring Security + JWT 인증 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JWT/Security (Week 7) |
| TASK-022 | JWT RefreshToken 발급 및 Redis 블랙리스트 기반 로그아웃 구현 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JWT/Redis (Week 7, 8) |
| TASK-022-1 | RefreshToken Rotation 적용 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JWT RTR (Week 7) |
| TASK-022-2 | RefreshToken Rotation 흐름 README 문서화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JWT (Week 7) |
| TASK-021-1 | 환불 API 본인 소유권 검증 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Spring Security (Week 7) |

### Phase 2-3. 아키텍처 & 대기열

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-023 | 에러코드 도메인별 분류 리팩토링 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | SOLID/OCP (Week 12) |
| TASK-023-1 | 패키지 구조 레이어형 → 도메인형 리팩토링 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | SOLID (Week 12) |
| TASK-029 | 대기열 시스템 구현 - Redis Sorted Set 기반 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Redis 자료구조 (Week 8) |
| TASK-030 | 대기열 입장 토큰 설계 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | JWT/Redis (Week 7, 8) |
| TASK-030-1 | 대기열 이탈/만료 처리 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Redis TTL (Week 8) |
| TASK-057-1 | `queue:seq:{eventId}` 종료 정리 정책 반영 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Redis TTL / 정리 전략 (Week 8) |

### Phase 3. 문서화 & 설계 기반 확보

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-005 | API 스펙 문서 + 테스트 + devlog 정리 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 테스트 (Week 11) |
| TASK-024 | ERD + 아키텍처 다이어그램 + Swagger | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | DB 설계 (Week 4) |
| TASK-041 | README 전체 갱신 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |
| TASK-047 | 전체 예약 흐름 시나리오 문서화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션 흐름 (Week 5) |
| TASK-048 | 아키텍처 의사결정 통합 문서 (ADR) — Kafka 미도입/Mock 결제/Outbox/Soft Delete | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Kafka (Week 15), 트랜잭션 (Week 5) |
| TASK-049 | 목표 TPS 정의 + 트래픽 시나리오 상세 문서 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 숫자 기반 운영 감각 (전 주차) |
| TASK-050 | 서비스 전체 요약 (One Page) | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |
| TASK-057 | Redis Key Naming 지침 테이블 업데이트 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Redis (Week 8) |

### Phase 4-1. 신뢰성 & 요청 보호

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-058 | CONFIRMED 상태 예약 취소 정책화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션 (Week 5) |
| TASK-046 | 예약/결제/HOLD/좌석 상태 전이 및 동시 결제 정합성 보강 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Lock/MVCC, 트랜잭션 (Week 4~5) |
| TASK-052 | Idempotency Key 기반 예약 생성 API 중복 요청 방지 확장 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 트랜잭션/Redis (Week 5, 8) |
| TASK-031 | Redis Sliding Window 기반 Rate Limiting 구현 | 완료 | B current: Issue #96, PR #97, commit `f0aabf8` | Redis/동시성 (Week 8, 10) |

### Phase 4-2. 이벤트 처리 & 장애 신뢰성

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-032 | 이벤트 기반 예약 처리 (Spring Events) | 계획 | MASTER 승인 직접 순서; TASK-060 완료 + 사용자 승인 | 이벤트 기반 설계/Kafka 비교 (Week 15) |
| TASK-033 | Outbox Pattern 구현 | 계획 | MASTER 승인 직접 순서; TASK-032 완료 + 사용자 승인 | Kafka/트랜잭션 (Week 15, 5) |
| TASK-033-1 | Outbox 재처리 실패 시나리오 | 계획 | MASTER 승인 직접 순서; TASK-033 완료 + 사용자 승인 | 트랜잭션/재시도 (Week 5) |
| TASK-028 | Redis/DB 장애 시나리오 구현 및 문서화 | 계획 | MASTER 승인 직접 순서; TASK-033-1 완료 + 사용자 승인 | 커넥션 풀/Redis (Week 5, 8) |
| TASK-043 | 장애 복구 전략 설계 + 코드 구현 | 계획 | MASTER 승인 직접 순서; TASK-028 완료 + 사용자 승인 | 장애 흐름 전반 |
| TASK-034 | Circuit Breaker 적용 (Resilience4j) | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요; A old roadmap에서 선택 TASK로 기록 | 장애 격리 (Week 15) |

### Phase 5. Admin API 구현

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-039 | Admin API — 이벤트 생성/수정, 좌석 관리, 예약 현황, 강제 취소 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Spring MVC (Week 12) |
| TASK-054 | Admin 권한 분리 (RBAC) | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Spring Security (Week 7) |
| TASK-039-1 | 비즈니스 규칙 문서화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | — |

### Phase 6. 전체 플로우 검증

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-045 | 테스트 전략 문서화 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 테스트 전략 (Week 11) |
| TASK-025 | Testcontainers 통합 테스트 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 테스트 전략 (Week 11) |
| TASK-025-1 | 동시성 테스트 — 대기열 포함 전체 예약 플로우 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Java 동시성 (Week 10) |
| TASK-025-2 | E2E 통합 시나리오 테스트 — 로그인 → 대기열 → 선점 → 예약 → 결제 → 취소/환불 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 테스트 전략 (Week 11) |
| TASK-055 | Failover 테스트 시나리오 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 커넥션 풀/Redis 장애 (Week 5, 8) |

### Phase 7. 성능 측정 & 관찰가능성

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-044 | 비즈니스 이벤트 로그 설계 — MDC traceId | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 운영 감각 전반 |
| TASK-026 | MDC Logging + Actuator + 요청 추적 로그 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 운영 감각 전반 |
| TASK-027 | Prometheus + Grafana — TPS/에러율/응답시간/Redis/DB | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 숫자 기반 운영 감각 |
| TASK-027-1 | Grafana 알람 룰 정의 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 운영 감각 전반 |
| TASK-024-1 | Redis 캐시 성능 측정 Before/After | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 캐시 전략 (Week 8) |
| TASK-042 | 동시성 제어 + 인덱스 성능 측정 Before/After | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 인덱스/락 (Week 4) |
| TASK-056 | Slow Query 추적 + 인덱스 튜닝 로그 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 인덱스/실행계획 (Week 4, 13) |
| TASK-037 | k6 부하 테스트 — TASK-049 목표 TPS 기준 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | TPS/QPS 숫자 감각 |
| TASK-037-1 | k6 결과 분석 문서 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | 운영 감각 전반 |

### Phase 8. 배포 & 마무리

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-038-1 | 배포 아키텍처 의사결정 문서 — Blue-Green 전략 설계 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Docker/CI/CD (Week 14) |
| TASK-038 | Docker + AWS 배포 + GitHub Actions CI/CD | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | Docker/CI/CD (Week 14) |
| TASK-037-2 | AWS 실서버 k6 재측정 — 로컬 결과와 비교 분석 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요 | TPS/QPS 숫자 감각 |
| TASK-040 | Next.js 데모 | 재확인필요 | A old roadmap 기록만 확인됨; 현재 완료/진행 근거 재검증 필요; A old roadmap에서 선택 TASK로 기록 | — |

### 프로세스 보강

| TASK | 내용 | 상태 | 현재 근거 / 조건 | CS 연관 개념 |
| --- | --- | --- | --- | --- |
| TASK-059 | Claude Code 최소 구성 적용 및 위험 리뷰 Agent 검증 | 완료 | merge SHA `122816d91533befcc7a63c06cf83a7150e1fd05d` | — |
| TASK-060 | 프로젝트 프로세스 문서 정합성 복구 및 학습 계약 추가 | 진행중 | Issue #100, branch `chore/TASK-060-process-doc-consistency-recovery` | 프로세스 / AI 협업 / 검증 |

## CS 주차 ↔ TASK ↔ interview 연결

| CS 주차 | 연관 TASK | 면접 연결 포인트 |
| --- | --- | --- |
| Week 4 (인덱스/락/MVCC) | TASK-016, TASK-017, TASK-019, TASK-046, TASK-042, TASK-056 | 좌석 선점 동시성 제어, 동시 결제 중복 방지, 인덱스 설계 이유, 실행계획 Before/After |
| Week 5 (@Transactional/커넥션 풀) | TASK-012, TASK-014, TASK-035, TASK-036, TASK-046, TASK-052, TASK-058 | 예약/결제 트랜잭션 경계, 상태 전이 가드, 멱등성 저장 시점, 롤백 전략 |
| Week 6 (JPA/N+1) | TASK-011, TASK-017, TASK-025 | N+1 해결, 낙관적/비관적 락 선택 이유 |
| Week 7 (JWT/Security) | TASK-021, TASK-022, TASK-022-1, TASK-054 | 필터 체인, RTR, RBAC |
| Week 8 (Redis/캐시) | TASK-019, TASK-020, TASK-029, TASK-030, TASK-052, TASK-031, TASK-024-1 | 분산락, 캐시 전략, 대기열, 멱등성, Sliding Window, TTL |
| Week 10 (Java 동시성) | TASK-016, TASK-019, TASK-025-1, TASK-029-1, TASK-031 | 동시성 테스트, Redis Lua 원자성, ReentrantLock vs 분산락 |
| Week 11 (예외/테스트) | TASK-002, TASK-004, TASK-025, TASK-025-2, TASK-045 | 예외 처리 전략, 테스트 피라미드 |
| Week 12 (SOLID/패턴/MVC) | TASK-023, TASK-023-1, TASK-031, TASK-039 | 패키지 리팩토링 이유, AOP 기반 횡단 관심사 분리, OCP |
| Week 13 (DB 심화) | TASK-017, TASK-042, TASK-056 | 커버링 인덱스, 슬로우 쿼리 분석 |
| Week 14 (Docker/CI/CD) | TASK-008, TASK-015, TASK-038, TASK-038-1 | 배포 실패 경험, 롤백 전략 |
| Week 15 (Kafka/MSA) | TASK-032, TASK-033, TASK-048 | Kafka 미도입 이유, Spring Events 한계, Outbox 선택 이유 |

## Learning 연결

TASK별 학습·면접 검증의 상세 기준은 `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md`를 따른다.
대표/고위험 여부와 learning deadline은 TASK 계획 단계에서 승인하며, 학습 산출물 경로와 pass 기준도 해당 계약을 단일 기준으로 사용한다.

## TASK coverage 기준

- A old roadmap 고유 TASK: 74
- B current roadmap 고유 TASK: 7
- 이 후보의 전체 TASK 이력 고유 TASK: 76
- A old roadmap에서 누락된 TASK: 없음
- A 기준 신규 TASK: TASK-059, TASK-060
