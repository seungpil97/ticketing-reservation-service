# Devlog

- [2026-02-28: W1 wrap-up](2026-02-28-w1-wrapup.md)
- [2026-03-07: W2 wrap-up](2026-03-07-w2-wrapup.md)
- [2026-03-14: W3 wrap-up](2026-03-14-w3-wrapup.md)
- [2026-03-20: W4 wrap-up](2026-03-20-w4-wrapup.md)

---

## 주요 기술 결정 기록

| TASK | 결정 | 이유 |
|---|---|---|
| TASK-016 | 비관적 락 도입 | 티켓팅 도메인 특성상 동시 선점 요청이 많아 DB 레벨 락으로 중복 방지 |
| TASK-017 | fetch join으로 N+1 제거 | HOLD 만료 처리 시 루프에서 showtimeSeat 접근으로 N+1 발생 |
| TASK-019-1 | Spring Boot 4.x → 3.4.3 다운그레이드 | Redisson 호환성 문제 발견, Spring Boot 3.x가 실무 표준 |
| TASK-019 | Redis 분산락으로 전환 | 비관적 락은 DB 커넥션 점유 → Redis 분산락으로 DB 부하 분산 |