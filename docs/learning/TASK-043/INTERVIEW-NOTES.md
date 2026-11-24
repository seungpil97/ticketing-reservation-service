# TASK-043 학습 기록 — 결제 커밋 후 멱등 결과 저장 실패 시 예약 소유자 결제 상태 조회 복구 경로

> **설명 완료 기록 — 사용자 설명 수령 확인 있음(TASK-043 MASTER 방, MASTER 인계), MASTER 정확성 검토 완료(WORK-24 수정본), merge 전 설명 중심 학습 gate 충족(계약 §15.3), 독립 이해도 검증 미실시·Level 미평가**

- 대상: Issue #123, 브랜치 `feat/TASK-043-payment-status-recovery-query` (PR 미생성)
- 설명 대상 기준: 기준 HEAD `030beeea2115280d4d11395b37f479101ba55300` + WORK-21에서 ADR 수정을 마친 미커밋 작업트리
  - 설명 대상 구현 커밋 SHA: `d609afa02e767c793e1a867fd9cde691e5e485ca` (WORK-26, 부모 `030beeea2115280d4d11395b37f479101ba55300`). 아래 8개 파일 해시는 이 커밋의 내용과 같다. 기준 HEAD와 미커밋 작업트리 표기는 설명·검토 시점의 기록으로 보존한다
  - 설명 대상 파일 SHA-256 (WORK-22 설명 시점과 같다)

    | 파일 | SHA-256 |
    |---|---|
    | `docs/architecture/adr/ADR-008-payment-status-recovery-query.md` | `35375e080a5c578d0a79b75ec54aa053ff55660cb55d44d68cb91695ed735f69` |
    | `docs/api/payment.md` | `d695443c12501a5a39096bf12f372b915765daa9442b379b6bcc3892de84483f` |
    | `src/main/java/com/pil97/ticketing/payment/api/PaymentController.java` | `93cf62390785406559d64995dd3704257f23bb4665e1a040a3c334a13310f0c7` |
    | `src/main/java/com/pil97/ticketing/payment/application/PaymentService.java` | `acaa7e2945b6ccbedb1be607b0cb80276e7eaa1083d7f527959cd068364920a1` |
    | `src/test/java/com/pil97/ticketing/payment/api/PaymentStatusQueryIntegrationTest.java` | `3f5d835a4f925d507299e0f97b7c5c44f0230db7e4efa4b85b83ae58048d4cd5` |
    | `src/test/java/com/pil97/ticketing/payment/api/PaymentStatusRecoveryQueryIntegrationTest.java` | `dbb7d0bff2f7b49867e0ceec48a1947b7edb95ca56eb2b02327f5a3b17e06d1a` |
    | `src/test/java/com/pil97/ticketing/payment/api/PaymentIdempotencySaveFailureIntegrationTest.java` (TASK-028 동결, 변경 없음) | `4a39f3e71678d7b073786736fc2b2096e928bd91c6e73ce6c9a7b4aa56a26602` |
    | `src/test/java/com/pil97/ticketing/payment/api/PaymentConnectionAcquisitionFailureIntegrationTest.java` (TASK-028 동결, 변경 없음) | `ad78358764a57044f91ddafbba12062fc88f19cd6b22c252b4a42997f2266f5c` |

- 학습 패킷 출처
  - 위 작업트리의 코드·테스트·문서: `PaymentController.java`, `PaymentService.java`, `IdempotencyRedisRepository.java`, `JwtAuthenticationFilter.java`, `TokenService.java`,
    `PaymentStatusQueryIntegrationTest.java`, `PaymentStatusRecoveryQueryIntegrationTest.java`,
    [ADR-008](../../architecture/adr/ADR-008-payment-status-recovery-query.md), [ADR-007](../../architecture/adr/ADR-007-payment-redis-db-failure.md), [`docs/api/payment.md`](../../api/payment.md)
  - 계약: Issue #123 본문, [FREEZE-RECORD](https://github.com/seungpil97/ticketing-reservation-service/issues/123#issuecomment-7055830274)
  - 실행 증거: WORK-13 Valid RED, WORK-14 GREEN, WORK-16 로컬 전체 회귀 (아래 §6). 원본 JUnit XML과 콘솔은 저장소 밖에 있다
  - 리뷰: WORK-19 Implementation 리뷰 패키지 `TASK-043-WORK-19-implementation-review.zip` (SHA-256 `71949fc4a006016796c80ff1cbfcb52b6f1c95a567d62cb2680dc942b54ccf0d`), WORK-20 GPT 독립 리뷰 및 WORK-21 MASTER 종료 판정 (MASTER 인계 기준, 아래 §8)
  - 설명 원본: WORK-22 전달 TXT `TASK-043-WORK-22-report.txt` (SHA-256 `525d7e2d179c80a29162b19149533a0cf08badc0c59c19adeecea8229e7c3b5a`)
- 학습 계약: `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md` (§10 18개 항목, §15.1). Issue #123 Notes: 고위험 TASK(§8 "인증·Redis·트랜잭션·동시성·결제·장애"), 면접 모드를 진행할 경우 목표 Level 4, merge 전 설명 중심 gate(§15.1 네 요건), 면접 모드는 사용자 요청 시에만

## 학습 상태

| 항목 | 상태 |
|---|---|
| 설명 제공 | **제공됨** (WORK-22, 2026-11-23 KST, Claude Code 답변 본문과 WORK-22 TXT). 이 문서는 AI 작성 설명 자료다 |
| 사용자 설명 수령 확인 | **확인됨** (TASK-043 MASTER 방, WORK-25 지시의 MASTER 인계). 설명을 들은 사용자의 질문 “이제는 결제를 다시 시도안해도 예약번호로 확인 가능하다는 말이야?” (실제 발언 중 발췌). 이해도 판정이 아니다 |
| MASTER 정확성 검토 | **완료**. WORK-23 문서 전문 대조(WORK-24 지시로 전달)에서 §3의 404 설명 정정 1건을 요청했고 WORK-24에서 반영했다. MASTER가 WORK-24 수정본을 diff로 복원해 SHA-256 일치(`f8e6854d8ca2e6a9e6936f119e76381929bc64c00381a58a050f06819f44fec9`)와 §3 설명(FAIL 200, `PAYMENT-001` 404, `RESERVATION-001` 404)을 확인하고 최종 검토 완료로 판정했다(WORK-25 지시로 전달) |
| 독립 이해도 검증 | **미실시** (면접 모드 미요청) |
| Level | **미평가** |
| merge 전 학습 gate | **충족** (계약 §15.3, 아래 §15.1 네 요건 충족. 면접 결과는 gate 조건 아님) |

### 세 가지 검토·검증의 구분

| 구분 | 수행 | 대상 | 이 문서에서의 의미 |
|---|---|---|---|
| GPT 독립 구현 리뷰 (WORK-20) | GPT | 코드·테스트·문서·실행 증거 (WORK-19 패키지) | 구현 Finding 판정. 학습 기록의 정확성 검토가 아니다 |
| MASTER 정확성 검토 (WORK-23 대조, WORK-24 수정본 최종 확인) | MASTER | 이 문서 | 기록의 사실 오류·과장·누락 검토. 사용자 이해도 판정이 아니다 |
| 사용자 독립 이해도 검증 | 사용자 (면접 모드) | 사용자의 설명 능력 | **미실시**. 면접 모드가 요청되지 않았다 |

### 설명·검토 기록

| §15.1 요건 | 상태 | 근거 |
|---|---|---|
| 1. 필수 설명 | 충족 (MASTER 확인, WORK-23 전문 대조) | WORK-22 설명과 이 문서가 §4.1~§4.7, §5 관점과 네 분류, §8 고위험 항목(정상·실패 흐름, 동시성, 정합성, 테스트 한계, 운영 관측성), Finding 반영 결과와 근거(§8·§9), AI 수행과 사용자 판단의 구분(§12)을 다룬다. 설명 작성자는 구현을 맡은 같은 세션(Claude Code)이다 |
| 2. 기록 (§10) | 충족 (WORK-23 작성, WORK-24 §3 문구 정정, MASTER 확인) | 이 문서 18개 항목. 설명 대상 기준·학습 패킷 출처는 머리말, 질문·오해·교정과 독립 재확인 여부는 §17. 설명 대상 구현 커밋 SHA는 `d609afa02e767c793e1a867fd9cde691e5e485ca`(WORK-26)이다 |
| 3. 사용자 설명 수령 확인 | 충족 | TASK-043 MASTER 방에서 사용자가 설명을 듣고 “이제는 결제를 다시 시도안해도 예약번호로 확인 가능하다는 말이야?”라고 질문했다(실제 발언 중 발췌, WORK-25 지시의 MASTER 인계). 작업 지시문 속 예시 문구나 보고서 첨부는 근거로 쓰지 않았다. 이 확인은 이해도 판정이 아니다 |
| 4. MASTER 정확성 검토 | 충족 (최종 검토 완료) | MASTER가 WORK-23 문서 전문을 최종 코드·리뷰·테스트 증거와 대조했다(WORK-24 지시로 전달). 필수 설명과 18개 항목 기록을 확인했고, 구현·실행 수치·리뷰 처분은 근거와 일치했으며, §3의 404 설명 한 곳의 문구 정정을 요청했다. WORK-24에서 그 문구만 정정했다. MASTER는 WORK-24 수정본을 diff로 복원해 SHA-256 일치와 §3의 FAIL 200, `PAYMENT-001` 404, `RESERVATION-001` 404 설명을 확인하고 최종 검토 완료로 판정했다(WORK-25 지시로 전달). 기록 정확성 검토이며 이해도 판정이 아니다 |

네 요건 충족에 따라 merge 전 설명 중심 학습 gate를 충족한 것으로 기록한다(계약 §15.3). 면접 모드 결과를 gate 조건에 포함하는 결정은 기록되지 않았다. 독립 이해도 검증 미실시, Level 미평가이며, 학습 PASS·Level 달성·이해도 검증 완료가 아니다. merge는 사용자 승인 후에만 수행한다.

## 표기

| 표기 | 의미 |
|---|---|
| [AI 작성 설명 자료] | 구현·실행 기록을 근거로 AI가 작성한 설명. 사용자의 답변이나 독립 판단이 아니다 |
| [미확인] | 근거가 없거나 아직 확인하지 않은 내용 |
| [미실시] | 진행하지 않은 과제 |

이 문서에 기록된 사용자 발언은 설명 수령 근거인 질문 1건(§17, MASTER 인계 발췌)뿐이다. 면접 모드 질의응답은 없고, 사용자가 내용을 이해했다는 판정도 없다.

## 1. TASK 한 줄 요약

[AI 작성 설명 자료] 결제가 DB에 커밋된 뒤 Redis 멱등 결과 저장이 실패하고 첫 201 응답까지 받지 못한 클라이언트를 위해, 인증된 예약 소유자가 예약 ID로 현재 결제 상태를 조회하는 `GET /reservations/{reservationId}/payment`를 추가했다.
`POST /payments`와 TASK-028 계약은 바꾸지 않았다.

## 2. 해결한 문제

[AI 작성 설명 자료]

- TASK-028 S1: 커밋 후 `afterCommit`에서 결과 키 SET이 실패해도 첫 응답은 201이다. 결과 키는 남지 않고, 락 키는 최초 획득 시각부터 최대 10초 남는다 (ADR-007 §1·§3).
- 클라이언트가 이 201을 받지 못하면 같은 `Idempotency-Key`로 다시 POST해도 첫 응답을 돌려받지 못한다.
  - 락 키가 남아 있으면 409 `IDEMPOTENCY-001`, 없으면 이미 CONFIRMED인 예약의 PENDING 가드에서 409 `PAYMENT-002`다.
  - 두 경우 모두 결제가 추가로 생기지는 않지만, 결제 ID와 상태도 알 수 없다.
- 결제 상태를 확인할 다른 API가 없었다 (ADR-007 §4). 사용자에게는 "결제됐는지 모름"만 남는다.

## 3. 핵심 처리 흐름

[AI 작성 설명 자료] 사례: 회원 A가 PENDING 예약 R에 대해 `Idempotency-Key: K`로 `POST /payments`를 보낸다.

**POST에서 결과가 불확실해지기까지 (기존 동작, 이번에 바꾸지 않음)**

1. Redis 확인과 락 선점: 결과 키 `idempotency:payment:K`가 없다. 락 키 `idempotency:payment:lock:K`를 SETNX로 잡는다(TTL 10초, 값 `processing`).
2. DB 트랜잭션: 예약 R을 비관락으로 읽고 PENDING인지 확인한다(`validatePayable`). 좌석과 HOLD를 잠그고 결제 행을 SUCCESS로 저장한다.
   결제 성공 이벤트 리스너(`@EventListener` + `Propagation.MANDATORY`)가 같은 트랜잭션에서 예약을 CONFIRMED, 좌석을 RESERVED로 바꾸고 Outbox 1행을 기록한다.
3. **커밋 — 결제가 사실이 되는 시점.** 이후 "결제됨"의 근거는 DB다 (ADR-007).
4. **afterCommit 결과 SET 실패 — S1 시점.** `{fingerprint, 응답 본문}` SET이 Redis 오류로 실패한다. ERROR 로그만 남기고 201을 그대로 반환한다.
   락 삭제는 SET 다음 줄이라 실행되지 않아, 락 키가 TTL 동안 남는다.
5. **응답 유실 — 사용자에게 결과가 불확실해지는 시점.** 201이 A에게 도착하지 않는다. DB에는 SUCCESS가 있지만 Redis 결과 키는 없다.

**같은 키 재요청의 두 종류 409**

- replay(200)는 결과 키가 있을 때만 일어나므로 일어나지 않는다.
- 락이 남아 있으면 SETNX 실패로 409 `IDEMPOTENCY-001`이다.
- 락이 사라졌으면 결제 처리에 들어가지만 예약 R이 CONFIRMED라 PENDING 가드에서 409 `PAYMENT-002`다.
- 남은 락 키의 값은 `processing`뿐이라 fingerprint, 응답 본문, K와 예약·결제의 연결을 복원할 수 없다.

**신규 GET (`PaymentService.getPaymentOfReservation`)**

1. `reservationRepository.findById`로 예약을 찾는다(잠금 없음).
2. 예약 회원 ID가 로그인 회원 ID와 같은지 확인한다. 예약이 없거나 소유자가 아니면 같은 404 `RESERVATION-001`이다.
3. 소유자일 때만 `paymentRepository.findByReservationId`로 결제를 찾는다. 결제 행이 없으면 404 `PAYMENT-001`이다.
4. `PaymentResponse.of(payment)`로 `paymentId`·`status`·`paidAt`·`refundedAt`을 200으로 반환한다.

- A는 결제 요청 때 이미 R을 알고 있다. GET은 K나 이전 POST 결과를 입력으로 받지 않으므로, 409를 먼저 받거나 POST를 재시도할 필요 없이 응답을 못 받은 즉시 조회할 수 있다.
- 결과는 첫 응답의 재전송이 아니라 조회 시점의 DB 상태다. paidAt은 DB `paid_at`(DATETIME, 초 단위)이라 첫 201의 paidAt과 초 미만 자릿수가 다를 수 있다.
- **소유권을 먼저 보는 이유:** 결제를 먼저 찾으면 남의 예약에 대해 "결제 있음/없음"이 응답 차이로 드러난다. 없는 예약과 남의 예약을 같은 404로 만들어 예약 존재 여부도 숨긴다.
  판정 기준이 결제 요청자가 아니라 예약 소유자인 이유는 `POST /payments`가 로그인 회원을 받지 않아 DB에 결제 요청자가 없기 때문이다.
- **FAIL 행은 200:** 예약 소유자가 조회했고 FAIL 결제 행이 있으면 조회는 성공하므로 200이다. FAIL은 "결제 시도가 실패로 기록돼 있다"는 사실이다.
  소유권 확인 후 결제 행이 없으면 404 `PAYMENT-001`이다. 예약이 없거나 다른 회원의 예약이면 결제 조회 전에 404 `RESERVATION-001`을 반환한다.
- **결제 행 부재의 404 `PAYMENT-001`:** "조회 시점에 기록된 결제가 없다"는 뜻일 뿐이다. 아직 커밋되지 않은 결제가 진행 중일 수 있으므로 실패 확정도, 즉시 재결제 허용도 아니다.
- **환불 뒤:** 현재 상태를 반환하므로 `REFUNDED`와 `refundedAt`이 나오고 paidAt은 환불 전 값 그대로다.

**인증 경로와 조회 서비스의 Redis 의존 차이**

- 서비스 메서드는 DB만 읽는다(`@Transactional(readOnly = true)`). Redis 멱등 결과 키와 락에는 접근하지 않는다(`IdempotencyRedisRepository`·`StringRedisTemplate` 호출 없음).
- 유효한 Bearer 토큰 요청은 서비스 호출 전에 `JwtAuthenticationFilter`를 거친다. 토큰이 있으면 Redis 블랙리스트 확인(`TokenService.isBlacklisted`)과 회원 DB 조회(`memberRepository.findById`)를 한다.
- 따라서 "조회 API 전체가 Redis와 무관하다"고 말할 수 없다. Redis에 접근하지 않는 범위는 `PaymentService.getPaymentOfReservation`으로 한정된다 (ADR-008 Decision §1, §4).

## 4. 기술 선택과 대안

[AI 작성 설명 자료]

| 항목 | 선택: 예약 소유자의 현재 상태 DB 조회 | 대안: 같은 키 재요청에 첫 응답 재현 (DB 기반 멱등성) |
|---|---|---|
| 결과 | 조회 시점의 결제 상태 | 첫 요청과 같은 응답(200 replay) |
| 저장 정보 | 기존 `payment` 행과 `reservations.member_id` | 멱등키·fingerprint·요청자(·응답)를 결제 트랜잭션에서 DB에 기록 |
| 스키마 | 변경 없음 | migration, 멱등키 UNIQUE 설계 |
| 환불 뒤 | `REFUNDED` (현재 상태와 일치) | 첫 응답(`SUCCESS`) 재현에 응답 스냅샷 별도 보관 필요 |
| 기존 POST 계약 | 영향 없음, TASK-028 동결 테스트 6개를 회귀 기준으로 사용 | S1 재요청 기대값이 바뀌어 동결 계약 변경 필요 |
| 접근 통제 | 예약 소유자 기준 | 멱등키가 회원 단위가 아니고 POST가 요청자를 받지 않아 재설계 필요 |

- 선택의 비용: 첫 응답 재현이 아니므로 클라이언트가 "조회 결과 = 현재 상태"로 해석해야 한다.
- 다른 회원 요청에 403 또는 별도 오류 코드(`PAYMENT-007` 제안)를 쓰지 않고, 없는 예약과 같은 404 `RESERVATION-001`을 쓴다 (WORK-5 결정, §12).

## 5. 핵심 실패 상황

[AI 작성 설명 자료]

| 상황 | 동작 | 근거 |
|---|---|---|
| 커밋 후 결과 SET 실패(S1) 뒤 조회 | 200, 첫 201과 같은 paymentId·`SUCCESS`, paidAt = DB `paid_at` | AC-1 (확인된 사실) |
| S1 뒤 락 존재·부재 상태에서 조회 | 둘 다 같은 200, 결과 키 없음·락 키 존재 여부 유지, PTTL 연장 없음 | AC-2a·2b. 락 상태는 테스트가 PEXPIRE·DELETE로 조성(H-Q2) |
| 다른 회원·없는 예약 | 같은 404 `RESERVATION-001`, 결제 필드 없음, 두 응답 구별 불가 | AC-3·AC-5 |
| 결제 없는 자기 예약 | 404 `PAYMENT-001` | AC-4 |
| 환불된 결제 / 실패한 결제 | 200 `REFUNDED`(paidAt 유지) / 200 `FAIL`(시각 null) | AC-6 / AC-7 |
| 인증 정보 없음 | 401 `AUTH-003` (보안 entry point) | AC-8 |
| 진행 중 POST와 동시 조회 | 커밋 전이면 404 `PAYMENT-001`로 예상 | 미검증 (Issue §G) |
| 한 예약의 결제 행 2개 이상 | `findByReservationId` 단건 조회 실패. `reservation_id` UNIQUE 없음. 현재 흐름에서는 생기지 않는다고 추론 | 미검증 |
| 실제 Redis 전체 단절 중 실제 토큰 요청 | 필터의 블랙리스트 확인 단계에서 영향 가능. 어떤 응답인지 확인하지 않음 | 미검증 (추가 확인 필요) |
| DB 장애 중 조회 | 확인하지 않음 | 미검증 |

## 6. 테스트가 보장하는 것

[AI 작성 설명 자료] 테스트 계약은 Issue #123 `## Test`, 동결 기록은 [FREEZE-RECORD](https://github.com/seungpil97/ticketing-reservation-service/issues/123#issuecomment-7055830274)다.
동결 테스트는 4개 클래스 15개다: 신규 `PaymentStatusRecoveryQueryIntegrationTest`(AC-1, AC-2a, AC-2b) 3개, 신규 `PaymentStatusQueryIntegrationTest`(AC-3~8) 6개, TASK-028 회귀(AC-R) 6개.

| 실행 | 결과 | 확인한 범위 |
|---|---|---|
| Valid RED (WORK-13, 2026-11-21, 구현 전) | 15개 중 신규 8개 실패(AC-1, AC-2a, AC-2b, AC-3~7), 7개 통과(AC-8, AC-R 6개) | 실패 원인이 대상 GET 매핑 부재였다는 것 |
| GREEN (WORK-14, 2026-11-23, 구현 후) | 같은 15개 통과 (실패·오류·건너뜀 0) | 테스트와 Issue를 바꾸지 않고 구현만으로 계약을 충족했다는 것. 동결 파일 해시는 FREEZE-RECORD와 같다 |
| 전체 회귀 (WORK-16, 2026-11-23) | 표준 `./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test`, exit 0, 55개 클래스 398건, 실패·오류·건너뜀 0 | 이번 변경이 저장소의 다른 테스트를 깨지 않았다는 것. 로컬 실행이며 CI 결과는 아직 없다 |

Valid RED 8개의 판정 근거:

- 각 테스트의 사전 조건(H-Q1, H-Q2 포함)이 충족된 뒤 GET 상태 단언에서 실패했다. 실제 값은 HTTP 500이었다.
- 테스트별 진단에서 처리기 `ResourceHttpRequestHandler`와 `NoResourceFoundException`을 직접 확인했다. 즉 대상 GET 매핑이 없었다.
- 이 500이 `COMMON-007`이라는 것은 직접 관찰값이 아니다. 근거는 `GlobalExceptionHandler`의 `Exception` 처리기 매핑(`CommonErrorCode.INTERNAL_ERROR`)과 Issue #123 §F의 기준 HEAD 사전 관찰(WORK-9)이다.

H-Q1(별도 커넥션 SUCCESS 확인)이 보장하는 것:

- 결과 키 SET 실패 주입 시점에, 트랜잭션과 무관한 별도 autocommit JDBC 커넥션에서 대상 결제 행이 SUCCESS로 보였다. "Redis 실패보다 커밋이 먼저 끝났다"는 S1 전제가 실제로 성립했다.
- 주입 정확히 1회, 결과 키 부재, 첫 POST 201까지 모두 맞아야 AC-1·2 판정이 유효하다. 하나라도 어긋나면 `HarnessException`으로 판정에서 제외한다.

## 7. 테스트하지 못한 것

[AI 작성 설명 자료]

- 유효한 Bearer 토큰으로 실제 인증 경로(블랙리스트 확인·회원 조회)를 거친 조회
  - 동결 테스트는 `SecurityMockMvcRequestPostProcessors.authentication(...)`으로 인증된 `Member`를 넣고 `Authorization` 헤더를 보내지 않는다. 코드상 필터는 토큰 없음 분기로 지나간다.
  - AC-8의 401은 인증 정보가 전혀 없는 요청에 대한 보안 entry point 응답만 확인했다.
- 실제 Redis 연결 단절·timeout, DB 장애 중 조회 (장애 주입은 첫 POST 결과 키 SET 1회 실패뿐이고 조회 시점 Redis는 정상이었다)
- 진행 중인 POST와 동시 조회
- 한 예약에 결제 행이 2개 이상인 경우
- 락 TTL 자연 만료 (AC-1은 락 상태를 비교하지 않고, 락 비교는 AC-2에서 테스트가 조성한 상태로 수행)
- CI 실행 (아직 없음)

## 8. 리뷰에서 발견된 문제

[AI 작성 설명 자료] 출처: WORK-20 GPT 독립 리뷰 및 WORK-21 MASTER 종료 판정 (MASTER 인계 기준)

- **Claude Agent 1차 검토**: 기록 미확인. 이 기록 작업에서 확보된 별도 검토 기록이 없다.
- **GPT 독립 구현 리뷰 (WORK-20, WORK-19 패키지 대상)**: Implementation 권고 "수정 후 진행". Critical 0 / High 0 / Medium 0 / Low 2 (F-1·F-2)

| Finding | 내용 | 반영 (WORK-21, ADR-008) |
|---|---|---|
| F-1 (Low) | ADR-008 Decision §1이 Redis 멱등 키에 접근하지 않는 범위를 넓게 서술했다 | 범위를 `PaymentService.getPaymentOfReservation`으로 한정했다. 실제 Bearer 인증의 Redis 블랙리스트 조회는 별도 경로로 적고 §4에 연결했다 |
| F-2 (Low) | ADR-008 §5 Valid RED 행이 `COMMON-007`을 직접 관찰값처럼 서술했다 | WORK-13에서 관찰한 HTTP 500·처리기·예외와, `COMMON-007`의 근거인 예외 매핑·WORK-9 사전 관찰을 구분했다 |

- 두 건 모두 WORK-21에서 ADR-008에 반영했고 MASTER가 종료했다. 미해결 GPT Finding은 0건이다.
- [미확인] GPT 리뷰 원문은 이 기록 작업에 전달되지 않았다. 위 내용은 MASTER 인계 기준이다.

## 9. 최종 반영·미반영 결정

[AI 작성 설명 자료]

- 반영: F-1·F-2를 WORK-21에서 ADR-008에 반영했다. 코드·테스트는 바꾸지 않았다.
- 리뷰 전 문서 보완(WORK-18, MASTER 지시): `docs/api/payment.md`의 "모든 경우" 표현, ADR-008 Context의 멱등키·fingerprint 구분, 인증 설명(principal 주입만으로 필터 미실행을 단정하지 않음, 401 검증과 장애 미검증의 구분)
- 미반영(이번 범위 밖, Finding이 아님):
  - `docs/architecture/README.md` ADR 색인에 ADR-006·007·008 미등재 (Issue #123 [제외]의 별도 후보)
  - README API 목록에 신규 GET이 없다. README 영향 여부 결정은 아직 기록되지 않았다 [미확인]
  - 기존 `docs/api/payment.md`의 오류 예시 `"details": []`(실제 null)와 `PAYMENT-003` 표기 불일치

## 10. 현재 한계

[AI 작성 설명 자료]

- 상태 조회이며 멱등 응답 재현이 아니다. 환불 뒤에는 `REFUNDED`가 조회되고, paidAt 정밀도가 첫 응답과 다를 수 있다.
- 404 `PAYMENT-001`은 진행 중인 결제를 실패와 구별하지 못한다. 클라이언트가 즉시 재결제 판단에 쓰면 안 된다.
- 중복 결제 방어는 예약 비관락과 PENDING 가드에 기대며 `payment.reservation_id` UNIQUE 제약이 없다.
- 실제 토큰 요청은 Redis 블랙리스트 확인에 의존하므로, Redis 전체 장애 시 이 복구 조회가 동작한다고 보장하지 않는다.
- 조회 API에 전용 로그·메트릭·알림이 없다. S1의 운영 신호는 POST 쪽 ERROR 로그뿐이다.

## 11. 다음 개선점

[AI 작성 설명 자료]

운영 전에 필요한 추가 검증:

- 유효한 Bearer 토큰으로 조회하는 통합 테스트
- Redis 단절 시 `JwtAuthenticationFilter`의 동작(fail-open/fail-close) 확인

별도 조사 후보 (결함으로 확정하지 않음, Issue #123 [별도 조사 후보]):

- `POST /payments`의 요청자·예약 소유자 대조
- 회원 단위 멱등키 범위

범위 밖 후속 후보 (Issue #123 [제외], ADR-008 §6):

- DB 기반 멱등성(첫 응답 재현), 결과 재기록, `payment.reservation_id` UNIQUE
- 커넥션 획득 실패의 503 매핑, Circuit Breaker(TASK-034), Failover 테스트(TASK-055)
- `ReservationService`의 같은 afterCommit 구조
- README API 목록과 ADR 색인 정비, 기존 `payment.md`의 `"details": []`·`PAYMENT-003` 표기 정정

## 12. 사용자의 직접 판단과 기여

[AI 작성 설명 자료] 조사, Issue 초안, 테스트·구현·문서 작성, 실행, 증거 정리, 리뷰 패키지 작성과 이 설명·기록은 AI(Claude Code)가 수행했다. 독립 구현 리뷰는 GPT(WORK-20)가 수행했다.

MASTER 지시로 전달된 결정 (각 WORK 지시문 기준):

- WORK-5: 복구 방식으로 "예약 소유자의 현재 상태 조회"를 선택. 다른 회원과 없는 예약을 같은 404로 처리하고 403·`PAYMENT-007` 제안을 삭제
- WORK-11: paidAt은 DB `paid_at`과 정확히 일치하도록 단언하고 1초 미만 허용 오차를 삭제. AC-4·5·8의 고정 키 목록 단언 삭제
- WORK-18: 문서 표현 3건 보완
- WORK-20·21: GPT 독립 리뷰 F-1·F-2 반영과 종료 판정

이 결정들은 MASTER가 전달한 것이다. 사용자가 코드를 줄 단위로 직접 검증했다는 기록은 없다.
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

- 결제는 성공했는데 왜 같은 키로 다시 보내면 409가 나오나요? 두 409는 어떻게 다른가요?
- 왜 409를 받은 뒤가 아니라 바로 조회해도 되나요?
- 왜 결제보다 예약 소유권을 먼저 확인하나요? 403이 아니라 404인 이유는요?
- FAIL은 왜 404가 아니라 200인가요?
- `PAYMENT-001`을 받으면 다시 결제해도 되나요?
- 이 조회는 Redis와 무관한가요? Redis가 완전히 죽으면 어떻게 되나요?
- 별도 커넥션으로 SUCCESS를 확인한 것이 무엇을 증명하나요?
- 첫 응답을 그대로 돌려주는 방식(DB 기반 멱등성)은 왜 고르지 않았나요?

## 17. 아직 약한 개념

설명 후 질문 (TASK-043 MASTER 방, WORK-25 지시의 MASTER 인계):

- 사용자 질문(실제 발언 중 발췌): “이제는 결제를 다시 시도안해도 예약번호로 확인 가능하다는 말이야?”
- MASTER 보충 설명: 기존의 확인 공백은 Redis 결과 저장 실패에 더해 첫 201 응답까지 놓쳤을 때 생긴다.
- 이 질문과 보충 설명을 사용자 오해로 판정한 기록은 없다. 사용자의 이후 답변이나 독립 재확인 기록도 없다.

독립 재확인(면접 모드)은 미실시이므로 약한 개념은 확인되지 않았다.

아래는 AI 작성 문서의 오류·과장 정정이며 사용자 오해 교정이 아니다:

- WORK-17 보고 TXT: `PAYMENT-003`과 "201·status FAIL"의 관계를 AC-7 전제로 쓴 표현을 "코드상 예외 없이 201과 status FAIL, 테스트로 직접 단언하지 않음"으로 정정
- WORK-18 (MASTER 지시): "모든 경우에 조회" 표현, 멱등키·fingerprint 구분, "JWT 필터를 거치지 않았다"는 단정
- WORK-21 (GPT F-1·F-2): Redis 미접근 범위의 주어, `COMMON-007` 근거 구분

## 18. 재복습 날짜 또는 조건

기한: TASK-043 최종 완료 전, 그리고 다음 핵심 구현 TASK 착수 전 (Issue #123 Notes, 계약 §2).
사용자 설명 수령 확인(TASK-043 MASTER 방)과 MASTER 정확성 검토(WORK-24 수정본 최종 확인)가 완료됐다.
설명 대상 구현 커밋 SHA `d609afa02e767c793e1a867fd9cde691e5e485ca`를 WORK-26에서 기록했다. 구체적인 재복습 날짜는 정하지 않았다.
