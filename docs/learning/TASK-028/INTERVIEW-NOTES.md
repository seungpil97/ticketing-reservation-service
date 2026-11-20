# TASK-028 학습 기록 — 결제 경로 Redis 멱등성 결과 저장 실패·DB 커넥션 획득 실패

> **설명 완료 기록 — 사용자 설명 수령 확인 있음(2026-11-20 KST), MASTER 정확성 검토 완료(WORK-21 반영본, 보정 4건 확인), merge 전 설명 중심 학습 gate 충족(계약 §15.3), 독립 이해도 검증 미실시·Level 미평가, TASK 최종 완료(2026-11-20 KST 사용자 승인)**

- 대상: Issue #119, PR #120, 브랜치 `feat/TASK-028-payment-redis-db-failure-scenarios`
- 설명 대상 기준 SHA: `53e882ee532ed98aed1808ab4b74a0ebf18bbda0` (검토 HEAD = PR #120 head = CI run 44528202352 대상)
- 학습 패킷 출처
  - 위 SHA의 코드·테스트·문서: `PaymentService.java`, `IdempotencyRedisRepository.java`, `PaymentController.java`, `ReservationRepository.java`,
    `PaymentIdempotencySaveFailureIntegrationTest.java`, `PaymentConnectionAcquisitionFailureIntegrationTest.java`,
    [ADR-007](../../architecture/adr/ADR-007-payment-redis-db-failure.md), [`docs/api/payment.md`](../../api/payment.md)
  - 계약: Issue #119 본문, [FREEZE-RECORD](https://github.com/seungpil97/ticketing-reservation-service/issues/119#issuecomment-7003517818)
  - 실행 증거: WORK-8 RED, WORK-9 GREEN, WORK-10 로컬 전체 회귀, WORK-19 CI 조회 (아래 §6)
  - 리뷰: WORK-13 Claude 1차 검토, WORK-15 GPT 독립 구현 검토 (MASTER 전달 기준)
  - 설명 원본: WORK-20 전달 TXT `TASK-028-WORK-20-learning.txt` (SHA-256 `bf0a8e942eb0c6cdabe26cfce2dfaa063fdc8ab4b1687e4117c4d54f91c0698c`)
  - 보정: WORK-21 MASTER 지시에 담긴 MASTER 설명 정확성 검토 결과 (아래 "설명·검토 기록")
- 학습 계약: `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md` (§10 18개 항목, §15.1). Issue #119 §I: 고위험 TASK, 목표 Level 4, merge 전 설명 중심 gate, 면접 모드는 사용자 요청 시에만

## 학습 상태

| 항목 | 상태 |
|---|---|
| 설명 제공 | **제공됨** (WORK-20 TXT, 이 문서는 MASTER 보정 반영본). 이 문서는 AI 작성 설명 자료다 |
| 사용자 설명 수령 확인 | **확인됨** (2026-11-20 KST, 사용자 원문 "TASK-028 설명 받았어."). 이해도 판정이 아니다 |
| MASTER 정확성 검토 | **완료** (WORK-20 TXT 검토에서 보정 4건 → WORK-21 반영. WORK-21 반영본 SHA-256 `c83ce1d6669de9e67c95dddbbf11e2864d3416305ce9b633e11a8219ae1fb1fa` 대상 최종 판정: 정확성 검토 완료, 보정 4건 확인) |
| 독립 이해도 검증 | **미실시** (면접 모드 미요청) |
| Level | **미평가** |
| merge 전 학습 gate | **충족** (계약 §15.3, 아래 §15.1 네 요건 충족. 면접 결과는 gate 조건 아님) |
| TASK 최종 완료 | **완료** (2026-11-20 KST, TASK-028 MASTER 방 WORK-25 사용자 최종 완료 승인, MASTER 전달. 아래 "최종 완료 기록") |

### 세 가지 검토·검증의 구분

| 구분 | 수행 | 대상 | 이 문서에서의 의미 |
|---|---|---|---|
| GPT 독립 구현 검토 (WORK-15) | GPT | 코드·테스트·문서·실행 증거 | 구현 Finding 판정. 학습 기록의 정확성 검토가 아니다 |
| MASTER 설명 정확성 검토 (WORK-21 지시) | MASTER | WORK-20 설명 TXT | 설명 문장의 사실 오류·과장·누락 검토. 사용자 이해도 판정이 아니다 |
| 사용자 독립 이해도 검증 | 사용자 (면접 모드) | 사용자의 설명 능력 | **미실시**. 면접 모드가 요청되지 않았다 |

### 설명·검토 기록

| §15.1 요건 | 상태 | 근거 |
|---|---|---|
| 1. 필수 설명 | 충족 | WORK-20 TXT와 이 문서가 §4.1~§4.7, §5 분류, §8 고위험 항목, Finding 처분, AI·사용자 구분을 다룬다. 설명 작성자는 구현을 맡은 같은 세션(Claude Code)이다 |
| 2. 기록 (§10) | 충족 | 이 문서 18개 항목. 기준 SHA·학습 패킷 출처는 머리말, 오해·교정은 §17, 독립 재확인은 미실시 |
| 3. 사용자 설명 수령 확인 | 충족 | 2026-11-20(KST), WORK-22 마무리 요청 첫 줄의 사용자 원문 "TASK-028 설명 받았어." (사용자가 첫 줄을 자신의 수령 확인으로 명시). 수령 확인이며 이해도 판정이 아니다 |
| 4. MASTER 정확성 검토 | 충족 | WORK-20 TXT 검토 결과 보정 4건을 WORK-21에서 반영했다(아래). MASTER가 WORK-21 반영본(SHA-256 `c83ce1d6669de9e67c95dddbbf11e2864d3416305ce9b633e11a8219ae1fb1fa`)을 최종 코드·리뷰·테스트 증거와 대조해 "정확성 검토 완료, 보정 4건 확인"으로 판정했다(WORK-22 지시로 전달). 기록 정확성 검토이며 이해도 판정이 아니다. WORK-22에서 §6·§11 표현 2건을 MASTER 지시대로 정밀화했다 |

네 요건 충족에 따라 merge 전 설명 중심 학습 gate를 충족한 것으로 기록한다(계약 §15.3). 면접 모드 결과를 gate 조건에 포함하는 결정은 기록되지 않았다. 독립 이해도 검증 미실시, Level 미평가이며, 학습 PASS·Level 달성·이해도 검증 완료가 아니다. merge는 사용자 승인 후에만 수행한다.

WORK-21에서 반영한 MASTER 정확성 검토 보정 (AI 작성 설명의 오류·누락 정정이며 사용자 오해 교정이 아니다):

1. replay와 락 충돌 경로에도 이 결제 경로에서는 트랜잭션 시작과 DB 커넥션 획득이 먼저 일어난다. 이 경로들이 건너뛰는 것은 예약 조회와 신규 결제 처리다 (§3)
2. replay 조건을 "저장된 결과가 있고 요청 fingerprint가 일치"로 정정했다. SET 성공 후 락 DELETE만 실패한 경우도 결과 키를 읽을 수 있고 fingerprint가 일치해야 200이 가능하며, 이 조합은 미검증이다 (§3, §7)
3. 락 해제를 실패 시점에 따라 `processPayment`의 `finally`와 등록된 `afterCompletion`으로 나눠 썼다. 커밋 후 SET 직전 주입에는 추가 해제가 없다 (§3, §5)
4. S1의 결과 키 부재는 SET 직전 주입에서 관찰한 상태로 한정했다. S2의 "본문 진입 전 실패"는 풀 상태와 트랜잭션 시작 예외 스택을 근거로 들고, `find` spy 0회 단언에는 양성 대조가 없다는 한계를 유지했다 (§5, §6, §7)

### 최종 완료 기록

- 구현·병합: PR #120 merge (merge commit `c795d250a12ebfc52b5022a5fffe8d61c3b2d02e`, 2026-11-20 KST 기능 병합), 최종 기능 head `2c1fa8dfb1121c4e5823c386c67a6ef0241efcda`, Issue #119는 PR #120에 의해 자동 종료
- 검증: 승인 리뷰 5987530827 (seungpil97, commit `2c1fa8d`), 최종 기능 head CI run 44674357550 success (389 tests, 0 failures, 0 skipped, 동결 테스트 6개 포함)
- #119 Definition of Done 충족 근거
  - 동결·FREEZE-RECORD: Issue #119 코멘트 7003517818
  - Valid RED(AC-S1-1·AC-S1-4), GREEN(동결 6개), 최종 전체 테스트: §6 실행 증거 표
  - ADR-007·`docs/api/payment.md` 갱신, README 영향 없음: PR #120 (README는 변경 없음, 실행 방법·환경·외부 사용 방식 변경 없음)
  - Claude 1차 검토(WORK-13), GPT 독립 검토(WORK-15): §8
  - merge 전 설명 중심 학습 gate: 위 §15.1 표
- 조건부 회고·장애 기록 (`DEVELOPMENT-WORKFLOW.md` §12): 실제 incident·regression·data-loss·환경 장애가 없어 장애 기록은 작성하지 않는다. 기술 판단(201 유지, 500 유지·503 미매핑)은 [ADR-007](../../architecture/adr/ADR-007-payment-redis-db-failure.md), 실패 상황·검증 한계는 이 문서 §5·§7에 있다. devlog는 사용자 승인이 없어 작성하지 않는다
- 리뷰 처분: WORK-15 Low 5건 중 F-1~F-4는 후속 이관, F-5는 PR #120 본문의 FREEZE-RECORD·ADR-007 링크로 보완 (§9)
- 설명 중심 학습: §15.1 네 요건 충족으로 merge 전 gate 충족 (변경 없음)
- 독립 이해도 검증: 미실시 (면접 모드 미요청). Level: 미평가. 학습 PASS·Level 달성이 아니다
- 사용자 최종 완료 결정: 2026-11-20(KST), TASK-028 MASTER 방 WORK-25 지시 원문 "TASK-028의 최종 완료를 승인한다." 기능 병합일(2026-11-20, PR #120)과 구분해 기록한다
- 남은 한계: §7의 미검증 구간(실제 Redis 단절, 락 자연 TTL 만료, 실제 JWT 경로, SET 성공 후 DELETE만 실패 등)과 §10·§11의 후속 항목을 그대로 보존한다
- 다음 단계: TASK-043은 다음 후보일 뿐이며 착수 승인은 기록되지 않았다

## 표기

| 표기 | 의미 |
|---|---|
| [AI 작성 설명 자료] | 구현·실행 기록을 근거로 AI가 작성한 설명. 사용자의 답변이나 독립 판단이 아니다 |
| [미확인] | 근거가 없거나 아직 확인하지 않은 내용 |
| [미실시] | 진행하지 않은 과제 |

이 문서에는 사용자와 주고받은 질문·답변이 없다. 설명 수령 확인 원문("TASK-028 설명 받았어.")은 기록했지만 이해도 판정이 아니며, 사용자가 내용을 이해했다는 기록은 없다.

## 1. TASK 한 줄 요약

[AI 작성 설명 자료] 결제가 DB에 커밋된 뒤 Redis 멱등성 결과 저장이 실패해도 첫 응답 201을 유지하고 ERROR 로그를 남기게 했다.
결제 트랜잭션 시작 시 DB 커넥션을 얻지 못하면 기존 500 `COMMON-007`이 반환되고 아무것도 기록되지 않으며, 복구 후 같은 키로 정상 처리됨을 테스트로 고정했다.

## 2. 해결한 문제

[AI 작성 설명 자료]

- **S1 원래 동작**: 결제 성공 시 DB 커밋 후 `afterCommit` 콜백에서 Redis에 결과를 저장한다. 이 저장이 예외를 던지면 Spring은 그 예외를 커밋 처리(`AbstractPlatformTransactionManager.processCommit`)에서 `TransactionInterceptor`를 거쳐 호출자까지 올려 보낸다.
  `GlobalExceptionHandler`는 이를 처리되지 않은 예외로 보고 500 `COMMON-007`을 반환했다. DB 커밋은 되돌아가지 않으므로 "결제는 성공, 응답은 실패"가 됐다 (WORK-8 RED: 첫 응답 500, 스택에 `PaymentService$1.afterCommit` → `processCommit`).
- 클라이언트가 이를 실패로 믿으면 사용자에게 실패를 안내하거나 다른 키로 재시도할 수 있다. 실제 이중 결제는 DB 방어(§3)로 막히지만, 응답과 실제 상태가 어긋나면 고객 문의·환불 판단을 그르친다.
- **S2**: DB 커넥션 풀이 고갈됐을 때의 응답과 데이터 상태(부분 기록 여부, 복구 후 처리 가능 여부)가 테스트·문서로 고정돼 있지 않았다.

## 3. 핵심 처리 흐름

[AI 작성 설명 자료]

Redis 키 두 개를 먼저 구분한다.

- **결과 키** `idempotency:payment:{key}`: 처리 완료 기록. 요청 fingerprint와 첫 응답 본문을 담고 24시간 보관한다.
- **처리 중 락** `idempotency:payment:lock:{key}`: `SET NX`로 한 요청만 잡는다. TTL은 처음 잡은 시점부터 10초다.

```text
POST /payments (PaymentController.pay)
→ PaymentService.pay (@Transactional)
   → 메서드 본문 전에 트랜잭션 시작: JpaTransactionManager가 Hikari 풀에서 DB 커넥션 획득
     (replay·락 충돌 경로를 포함해 이 결제 경로의 모든 요청이 여기를 먼저 지난다)
   → IdempotencyRedisRepository.find
       ├ 저장된 결과가 있고 fingerprint 일치 → 저장된 응답 반환 (replay, 컨트롤러 200)
       │   예약 조회·신규 결제 처리를 건너뛴다
       ├ 저장된 결과가 있고 fingerprint 불일치 → 409 IDEMPOTENCY-002
       ├ (저장된 결과 역직렬화 실패 → WARN 로그 후 아래 락 단계로)
       ├ 결과 없음, 락 SET NX 실패 → 409 IDEMPOTENCY-001
       │   예약 조회·신규 결제 처리를 건너뛰고, 다른 요청의 락은 건드리지 않는다
       └ 결과 없음, 락 SET NX 성공 → processPayment
   → processPayment
       → 예약 비관적 락 (ReservationRepository.findByIdWithLock, PESSIMISTIC_WRITE)
       → validatePayable: 예약이 PENDING이 아니면 409 PAYMENT-002
       → 좌석·HOLD 잠금과 검증 → 결제 저장·SUCCESS → 이벤트 리스너가 같은 트랜잭션에서 예약 CONFIRMED·HOLD·좌석 전환·Outbox 행
       → registerAfterCommit: afterCommit(결과 저장)·afterCompletion(비커밋 시 락 해제) 등록
       → success = true
→ 커밋
   → afterCommit → IdempotencyRedisRepository.save: 결과 키 SET → 락 DELETE
→ 컨트롤러 201 (신규 처리)
```

**락 해제 위치 (실패 시점별)**

- `processPayment` 안에서 성공 표시 전에 예외가 나거나 forceFailure로 반환되면: `processPayment`의 `finally`가 락을 해제한다
- `registerAfterCommit` 등록 이후 트랜잭션이 커밋되지 못하고 끝나면: 등록된 `afterCompletion`(status ≠ COMMITTED)이 락을 해제한다
- 커밋에 성공하면: 해제는 `save()` 안의 락 DELETE 하나뿐이다. `finally`(success = true)와 `afterCompletion`(COMMITTED)은 해제하지 않는다
- 그래서 커밋 후 결과 SET에서 예외가 나면 DELETE까지 가지 못한다. 추가 해제 경로가 없어 락은 TTL까지 남는다

**S1 — 커밋 후 결과 SET 실패 (수정 후)**

1. 첫 요청: 트랜잭션 시작 → 락 획득 → 결제·예약·Outbox 커밋 → `afterCommit`의 결과 SET이 `DataAccessException`
2. `afterCommit`이 `DataAccessException`만 잡아 ERROR 로그(prefix·paymentId·reservationId·예외 타입)를 남기고 넘어간다 → 컨트롤러 201
3. SET 직전 주입 테스트에서 관찰한 Redis 상태: 결과 키 없음, 락 남음
4. 같은 키 재요청 (Redis 복구 후, 결과 키가 없으므로 `find`는 락 단계로 간다)
   - 락이 남아 있으면 409 `IDEMPOTENCY-001`
   - 락이 없으면 `processPayment` 진입 → 예약 잠금 후 CONFIRMED 확인 → 409 `PAYMENT-002` → `finally`에서 락 해제·롤백
   - 두 경우 모두 첫 결제의 200 replay는 받지 못한다. 저장된 결과가 없기 때문이다
   - 이 재요청 동작은 수정 전후가 같다. 수정이 바꾼 것은 첫 응답(500 → 201)과 ERROR 로그다

**락이 사라져도 두 번째 결제가 생기지 않는 이유**: 최종 방어는 Redis가 아니라 DB다. 예약 행을 `SELECT … FOR UPDATE`로 잠근 뒤 상태를 다시 검사한다.
첫 결제 커밋으로 예약이 CONFIRMED가 됐으므로, 이후 요청은 키가 같든 다르든 `PAYMENT-002`다. 동시 요청은 행 잠금에서 순서가 정해지고, 뒤 요청은 바뀐 상태를 보고 거절된다.
`payment.reservation_id`에는 UNIQUE 제약이 없으므로 이 방어는 결제 경로가 이 잠금과 검사를 거친다는 전제에 기댄다.

**S2 — 풀 고갈로 트랜잭션 시작 실패**

1. 풀이 모두 점유된 상태에서 요청 → 트랜잭션 시작 단계에서 커넥션 획득 timeout → `CannotCreateTransactionException`(원인 `SQLTransientConnectionException`) → 500 `COMMON-007`
2. 메서드 본문에 들어가지 않으므로 `find`(결과 키 조회·락 SET NX)도, 결제 저장·리스너도 실행되지 않는다. 결제 행·Outbox 행·Redis 키가 생기지 않는다
3. 커넥션 반환 후 같은 키 재요청: 남은 결과 키·락이 없고 예약은 PENDING 그대로다 → 정상 처리 → 201, 결제 1행·Outbox 1행

## 4. 기술 선택과 대안

[AI 작성 설명 자료]

| 선택 | 대안 | 이유와 비용 |
|---|---|---|
| `afterCommit`에서 `DataAccessException`만 catch | 예외 전체 catch, 그대로 전파(기존) | Redis 접근 오류만 "보조 기록 실패"로 본다. 전체 catch는 프로그래밍 오류를 숨긴다. 직렬화 오류는 `save()`가 이미 따로 처리한다 |
| 첫 응답 201 유지 | 500 유지, 별도 상태 코드 | 결제는 커밋된 사실이다. 결과 키는 재요청에 빠르게 답하기 위한 보조 기록이지 결제 성공의 근거가 아니다 |
| 추가 락 삭제·재시도 없음 | 실패 시 DELETE 재시도, 재시도 큐 | Redis 장애 중에는 같이 실패할 가능성이 높고 복잡도에 비해 이득이 작다. 비용: 락이 TTL(최대 10초)까지 남아 그동안 409 `IDEMPOTENCY-001` |
| 커넥션 획득 실패는 기존 500 유지 | 503(+Retry-After) 매핑 | Hikari는 풀 고갈과 DB 다운에 같은 예외를 쓴다. 예외만으로 일시 혼잡과 DB 장애를 구별할 수 없다 ([ADR-007](../../architecture/adr/ADR-007-payment-redis-db-failure.md) §2) |
| Redis 기반 멱등성 유지 | DB 기반 멱등성(결과를 같은 트랜잭션에 기록) | 근본적 대안이지만 스키마·흐름 변경이 커서 범위 밖이다. 비용: 결과 키 누락 시 200 replay 불가 |

## 5. 핵심 실패 상황

[AI 작성 설명 자료]

| 상황 | 동작 | 근거 |
|---|---|---|
| 커밋 후 결과 SET 실패 (SET 직전 주입) | 201, ERROR 로그 1건, 결과 키 없음·락 남음 (추가 해제 없음) | AC-S1-1~4 |
| 위 상태에서 같은 키, 락 있음 | 409 `IDEMPOTENCY-001`, 결제·Outbox 1행 유지 | AC-S1-5 |
| 위 상태에서 같은 키, 락 없음 | 409 `PAYMENT-002`, 이어서 다른 키도 409 `PAYMENT-002`, 결제 1행 유지 | AC-S1-6·7 |
| 결제 트랜잭션 시작 시 풀 고갈 | 500 `COMMON-007`, 응답에 내부 정보 없음, 결제·Outbox·Redis 무변경 | AC-S2-1·2 |
| 풀 회복 후 같은 키 | 201, 결제 1행, 새 결제에 연결된 Outbox 1행, 결과 키 생성 | AC-S2-3 |
| SET 성공 후 락 DELETE만 실패 | 저장된 결과를 읽을 수 있고 fingerprint가 일치하면 200 replay가 가능하다고 코드상 예상 | **미검증** (§7) |
| `processPayment` 중 예외 | `finally`에서 락 해제, 롤백, 결과 저장 없음 → 같은 키 재시도 가능 | 기존 동작 (이번 TASK 신규 검증 아님) |

fail-open과 fail-close: 결과 저장 실패는 응답에서 fail-open(성공 유지)이고, 커넥션 획득 실패는 fail-close(500, 무변경)다.
다중 인스턴스: 락·결과 키는 공유 Redis, 최종 방어는 공유 DB 행 잠금이므로 같은 판단이 나온다고 추론한다 [미확인: 다중 인스턴스 실행 검증 없음].
관측성: S1의 신호는 ERROR 로그 1건뿐이다. 메트릭·알림은 없다.

## 6. 테스트가 보장하는 것

[AI 작성 설명 자료] 두 테스트 모두 MockMvc HTTP부터 실제 DB·Redis까지 지나는 통합 테스트다. 하네스 조건(주입·환경 전제)이 맞지 않으면 `HarnessException`이 발생한다. `HarnessException`도 테스트 실행을 실패시키지만, 주입·환경 전제의 실패이므로 기능 assertion 실패에 의한 Valid RED와 구분한다.

**S1 `PaymentIdempotencySaveFailureIntegrationTest` (5개 메서드)**

- 주입: `StringRedisTemplate` spy에서 결과 키에 대한 `set` 1회만 `RedisConnectionFailureException`으로 실패시킨다 (SET 직전 주입). Redis 자체는 살아 있다
- 하네스: 주입 시점에 결제가 이미 커밋됐는지 별도 커넥션으로, 락이 있는지 Redis로 확인한다
- AC-S1-1: 첫 응답 201, `data.paymentId`·`status`가 DB의 유일한 결제 행(SUCCESS)과 일치
- AC-S1-2·3: 결제 SUCCESS·예약 CONFIRMED·좌석 RESERVED·HOLD CONFIRMED·Outbox 1행 PENDING, 결과 키 없음, 락 PTTL 0 초과 10000ms 이하
- AC-S1-4: `PaymentService` ERROR 1건(고정 문구·prefix·paymentId·reservationId·errorType), 멱등키 원문·응답 본문·금액 없음
- AC-S1-5: 락 TTL을 60초로 늘려(PEXPIRE) 자연 만료를 배제한 뒤 같은 키 → 409 `IDEMPOTENCY-001`
- AC-S1-6·7: 락을 직접 삭제한 뒤 같은 키 → 409 `PAYMENT-002`, 다른 키 → 409 `PAYMENT-002`, 결제·Outbox 1행 유지

**S2 `PaymentConnectionAcquisitionFailureIntegrationTest` (1개 메서드)**

- 격리 MariaDB 컨테이너와 전용 Hikari 풀(최대 2, 획득 timeout 250ms). 테스트 스레드가 커넥션 2개를 실제로 점유해 실제 풀 고갈을 만든다
- "본문 진입 전 실패"의 근거
  - 요청 시점 풀 상태 active=2, idle=0
  - `GlobalExceptionHandler` ERROR 1건의 최상위 예외가 `CannotCreateTransactionException`이고 원인 체인에 `SQLTransientConnectionException`이 있음 (트랜잭션 시작 예외 스택)
  - 보조: `IdempotencyRedisRepository` spy의 `find` 호출 0회. 단, spy가 호출을 실제로 기록하는지 보여주는 양성 대조가 없다 (F-1)
- AC-S2-1: 500, `success=false`, `COMMON-007`, path `/payments`, 응답 본문에 풀 이름·SQL·JDBC URL·DB 주소·원문 메시지 없음
- AC-S2-2: 점유 해제 후 결제 행 0건, 예약 PENDING·HOLD ACTIVE·좌석 HELD, Outbox ID 집합이 요청 전(B)과 같음(A == B), 결과 키·락 없음
- AC-S2-3: 같은 키 재요청 201, 결제 1행(응답 paymentId, SUCCESS), 예약 CONFIRMED, 새 Outbox ID(A′ − B) 정확히 1개(PAYMENT_SUCCEEDED, aggregate_id = 새 결제 ID, payload reservationId = 대상 예약), 결과 키 생성

**실행 증거 (각각 별개의 증거)**

| 실행 | 기준 | 결과 |
|---|---|---|
| WORK-8 RED | 커밋 `05e0244` (production 수정 전) | 동결 6개 중 2 실패 (AC-S1-1 201 기대·500 실제, AC-S1-4 로그 1건 기대·0건 실제), Gradle exit 1 |
| WORK-9 GREEN | `05e0244` + `PaymentService.java` 수정 (SHA-256 `92247130…7762`) | 동결 6개 통과, exit 0 |
| WORK-10 로컬 전체 회귀 | WORK-9와 같은 코드 | 389/389, 실패·오류·스킵 0, exit 0 (로컬 Colima, JUnit XML 53개) |
| WORK-19 CI | run 44528202352, head `53e882e` | 389 tests, 0 failures, 0 skipped (CI artifact HTML 리포트. errors는 HTML에 별도 항목이 없어 따로 확인하지 않았다), 동결 6개 포함 |

`53e882e`의 동결 테스트 2개와 `PaymentService.java`는 위 실행 파일과 SHA-256이 같고, 이후 커밋은 문서만 바꿨다.

## 7. 테스트하지 못한 것

[AI 작성 설명 자료]

- 실제 Redis 단절(프로세스 중단·네트워크 끊김): S1은 SET 1회에 예외를 주입했을 뿐이다. 실제 단절 시 예외 타입·대기 시간이 `DataAccessException` 범위에 드는지 [미확인]
- 결과 키 부재는 SET 직전 주입에서 관찰한 상태다. 다른 실패 시점에서의 Redis 상태는 관찰하지 않았다
- SET 성공 후 락 DELETE만 실패: 저장된 결과를 읽을 수 있고 fingerprint가 일치하면 200 replay가 가능하다고 예상하지만 미검증
- 락의 자연 TTL 만료: 락 있음·없음은 PEXPIRE와 직접 삭제로 만들었다. 10초 경과로 `IDEMPOTENCY-001`이 `PAYMENT-002`로 바뀌는 경계는 실행하지 않았다
- 실제 JWT 요청: 두 테스트 모두 테스트 인증 객체를 주입했다. Authorization 헤더 → `JwtAuthenticationFilter` 경로는 지나지 않았다
- S2 `find` 0회 단언의 양성 대조 (F-1)
- 직렬화 오류, `DataAccessException` 외 예외, DB 다운·네트워크 단절, 다중 인스턴스, `ReservationService`의 같은 구조

## 8. 리뷰에서 발견된 문제

[AI 작성 설명 자료]

- **Claude 1차 검토 (WORK-13)**: 구현을 맡은 같은 세션의 자체 점검이며 독립 검토가 아니다. Low 5건 (L-1~L-5)
- **GPT 독립 구현 검토 (WORK-15)**: 진행 가능, Critical 0 / High 0 / Medium 0 / Low 5 (F-1~F-5). MASTER 전달 기준으로 F-1·F-3은 Claude와 동의, F-2·F-4·F-5는 부분 동의다

| Finding | 내용 | Claude 대응 | GPT 판단 |
|---|---|---|---|
| F-1 | S2 `find` spy 0회 단언에 양성 대조 없음 | L-3 | 동의 |
| F-2 | 예외 발생 시 테스트 자원(appender·logger 등) 정리 순서 | L-4 | 부분 동의 |
| F-3 | `PaymentService` `afterCompletion`의 락 해제 주석이 실제와 다름 | L-1 | 동의 |
| F-4 | 신규 409(`IDEMPOTENCY-001`) 안내가 API 문서 오류 목록에 없음 | L-2 | 부분 동의 |
| F-5 | Issue·저장소에서 RED/GREEN 결과 추적성 부족 | L-5 | 부분 동의 |

[미확인] GPT 검토 원문은 이 기록 작업에 전달되지 않았다. 부분 동의의 세부 차이는 적지 않는다.

## 9. 최종 반영·미반영 결정

[AI 작성 설명 자료] MASTER 결정 (WORK-16 지시로 전달): 검토된 HEAD를 유지하고 Low 5건을 다음과 같이 처리한다.

- F-1·F-2: 다음 harness 개정 때 보완 (후속)
- F-3·F-4: 후속 정정. 기존 `PAYMENT-004` 표기 불일치는 #119 범위 밖
- F-5: PR #120 본문에 [FREEZE-RECORD](https://github.com/seungpil97/ticketing-reservation-service/issues/119#issuecomment-7003517818)와 [ADR-007 검증 결과](https://github.com/seungpil97/ticketing-reservation-service/blob/53e882ee532ed98aed1808ab4b74a0ebf18bbda0/docs/architecture/adr/ADR-007-payment-redis-db-failure.md) 링크를 넣어 보완
- 이 결정으로 코드·테스트는 바꾸지 않았다

## 10. 현재 한계

[AI 작성 설명 자료]

- 결과 키가 기록되지 않으면 같은 키 재요청은 200 replay를 받지 못한다(409 두 종류). 클라이언트가 결제 결과를 다시 알려면 다른 조회 경로가 필요하다
- 락이 남은 동안(첫 락 획득 후 최대 10초) 같은 키는 409 `IDEMPOTENCY-001`이다
- 중복 결제 방어가 예약 행 잠금과 상태 검사에 기대며, `payment.reservation_id` UNIQUE 제약은 없다
- S1의 운영 신호가 ERROR 로그뿐이다
- 커넥션 획득 실패를 500으로 응답하므로 클라이언트는 일시 혼잡과 서버 오류를 구별하지 못한다
- `ReservationService`는 같은 `afterCommit` 구조로 남아 있다

## 11. 다음 개선점

[AI 작성 설명 자료]

이번 리뷰에서 확인해 후속으로 이관한 항목 (원래 TASK 범위 밖으로 분류한 것이 아니다):

- F-1~F-4 처리 (harness 양성 대조·자원 정리, 주석 정정, API 오류 목록 보완)

Issue #119 §J 등에서 이번 TASK 범위 밖으로 둔 후속 후보:

- SET 성공 후 DELETE 실패, 직렬화 오류, `DataAccessException` 외 예외 검증
- 실제 Redis 단절 시 예외 타입 확인
- `ReservationService` 같은 구조 적용 여부
- DB 기반 멱등성 또는 결과 재기록 경로, `payment.reservation_id` UNIQUE
- 결과 저장 실패 메트릭·알림, 503 매핑은 DB 다운과 구분할 근거가 생길 때 재판단
- `docs/api/payment.md` `PAYMENT-004` 불일치

## 12. 사용자의 직접 판단과 기여

[AI 작성 설명 자료] 구현·테스트 작성·실행·문서·리뷰 패킷·푸시·PR 생성과 이 설명은 AI(Claude Code)가 수행했다. 독립 구현 검토는 GPT(WORK-15)가 수행했다.

MASTER 지시로 전달된 결정 (각 WORK 지시문 기준):

- Test Contract와 RED_NOT_APPLICABLE 판단(Issue #119 §F에 "MASTER 승인"으로 표기)
- 커넥션 획득 실패에 503을 매핑하지 않고 500 유지
- Low 5건 후속 처리와 검토된 HEAD 유지

이 결정들은 MASTER가 전달한 것이다. 사용자가 코드를 직접 검증했거나 개별 WORK를 명시적으로 승인했다는 기록은 아니다.
[미확인] 사용자 본인의 판단 원문은 이 저장소에 없다.

## 13. 30초 답변

[미실시: 면접 모드 미요청]

## 14. 90초 답변

[미실시: 면접 모드 미요청]

## 15. 심층 답변

[미실시: 면접 모드 미요청]

## 16. 예상 꼬리 질문과 답변

[미실시: 면접 모드 미요청]

예상 질문 목록만 둔다 (답변 없음):

- Redis 저장이 실패했는데 왜 성공 응답이 맞나요?
- 그 뒤 클라이언트는 결제 결과를 어떻게 다시 알 수 있나요?
- replay 요청도 DB 커넥션이 필요한가요?
- 락이 사라지면 왜 이중 결제가 생기지 않나요?
- UNIQUE 제약이 없는데 안전한가요?
- 풀 고갈인데 왜 503이 아니라 500인가요?
- 트랜잭션 시작 전에 실패했다는 것을 테스트는 어떻게 확인했나요?

## 17. 아직 약한 개념

[미확인] 사용자 오해를 교정한 기록이 없다. 독립 재확인(면접 모드)은 미실시이므로 약한 개념은 확인되지 않았다.
사용자 설명 수령 확인은 2026-11-20(KST)에 있었지만 이해도 판정이 아니다.
WORK-21의 보정 4건(트랜잭션 선행, replay 조건, 락 해제 위치, 관찰 범위·근거)은 MASTER가 찾은 AI 작성 설명의 오류·누락 정정이며 사용자 오해 교정이 아니다.

## 18. 재복습 날짜 또는 조건

기한: 늦어도 TASK-043 착수 전까지 학습 의무를 완료한다 (Issue #119 §I).
사용자 설명 수령 확인(2026-11-20 KST)과 MASTER 정확성 검토가 완료됐다. 구체적인 재복습 날짜는 정하지 않았다.
