# Devlog

- [2026-02-28: W1 wrap-up](2026-02-28-w1-wrapup.md) — 공통 응답 포맷 + Member CRUD
- [2026-03-07: W2 wrap-up](2026-03-07-w2-wrapup.md) — Flyway + 티켓팅 조회 API + HOLD
  API + CI
- [2026-03-14: W3 wrap-up](2026-03-14-w3-wrapup.md) — 티켓팅 스키마 + HOLD API + 만료
  스케줄링
- [2026-03-20: W4 wrap-up](2026-03-20-w4-wrapup.md) — 비관적 락 → Redis 분산락 + N+1
  제거 + 예약 취소
- [2026-03-27: W5 wrap-up](2026-03-27-w5-wrapup.md) — Redis 캐시 + JWT 인증 +
  RefreshToken Rotation + 에러코드 리팩토링
- [2026-04-03: W6 wrap-up](2026-04-03-w6-wrapup.md) — 도메인형 패키지 +
  ERD/아키텍처/Swagger + 대기열 시스템
- [2026-04-10: W7 wrap-up](2026-04-10-w7-wrapup.md) — Mock 결제 + 멱등성 + 환불 + 소유권
  검증 + README 갱신

---

## 주요 기술 결정 기록

| TASK       | 결정                             | 이유                                                          |
|------------|--------------------------------|-------------------------------------------------------------|
| TASK-016   | 비관적 락 도입                       | 티켓팅 도메인 특성상 동시 선점 요청이 많아 DB 레벨 락으로 중복 방지                    |
| TASK-017   | fetch join으로 N+1 제거            | HOLD 만료 처리 시 루프에서 showtimeSeat 접근으로 N+1 발생                  |
| TASK-019-1 | Spring Boot 4.x → 3.4.3 다운그레이드 | Redisson 호환성 문제 발견, Spring Boot 3.x가 실무 표준                  |
| TASK-019   | Redis 분산락으로 전환                 | 비관적 락은 DB 커넥션 점유 → Redis 분산락으로 DB 부하 분산                     |
| TASK-020   | Redis 캐시 적용                    | 이벤트 목록은 변경 빈도 낮고 조회 빈도 높음 → TTL 10분 캐시로 DB 부하 감소            |
| TASK-021   | Spring Security + JWT          | Stateless 인증으로 서버 확장성 확보, AccessToken 15분 / RefreshToken 7일 |
| TASK-022-1 | RefreshToken Rotation          | 재발급마다 Redis 교체 → 탈취 감지 시 강제 로그아웃                            |
| TASK-023   | 에러코드 인터페이스 분리                  | 단일 enum 코드 충돌 해소 + 도메인별 독립 관리                               |
| TASK-023-1 | 도메인형 패키지 구조 전환                 | 레이어형은 도메인 추가 시 여러 패키지 동시 수정 필요 → 도메인형으로 응집도 향상              |
| TASK-029   | Redis Sorted Set 기반 대기열        | score = timestamp로 순번 보장, O(log N) 조회, 별도 MQ 없이 Redis 단일 처리 |
| TASK-030   | queue:active:events Set 관리     | SCAN 대신 Set으로 활성 이벤트 O(1) 조회, 운영 환경 성능 이슈 방지                |
| TASK-035   | 결제 완료 후 좌석 상태 전환               | 실제 PG 연동 시 콜백 기반 비동기 처리로 자연스럽게 전환 가능한 구조                    |
| TASK-036   | Idempotency Key 기반 멱등성         | 네트워크 재시도로 인한 중복 결제 방지, 동일 키 재요청 시 기존 결과 반환                  |
| TASK-021-1 | 소유권 검증 순서 설계                   | 상태 검증 전 소유권 검증으로 결제 상태 정보 노출 방지                             |