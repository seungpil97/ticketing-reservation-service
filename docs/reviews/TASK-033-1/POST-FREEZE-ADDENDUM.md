# TASK-033-1 동결 이후 변경 보완 기록

> **현재 작성하는 보완 기록이다(WORK-53, 2026-11-16).** 과거 승인 문서가 아니며 소급 작성하지 않았다.
> 동결 계약(개정 3, `TEST-CONTRACT.md`·`AC-MAP.md`·`CHANGELOG.md`)과 동결 선택 집합 76건, 과거 RED/GREEN 기록은 수정하지 않는다.
> 판정·PASS·TASK 완료를 선언하지 않는다.

## 1. 기준과 출처

- 동결: WORK-21 FREEZE-RECORD. 계약 개정 3 기준이며, 동결 선택 집합은 76건(RED 71 + G 5)이다. WORK-22에서 같은 76건이 GREEN이 됐다.
- 지시 출처: 아래 "지시"는 사용자가 이 작업 세션에 붙여넣은 MASTER 지시문이다.
  - 사용자 직접 승인 발화와 승인 시각은 확보하지 못했다.
  - 원문 발췌 위치는 저장소 밖 검토 패킷이다.
    - WORK-50 `excerpts/`: U-WORK13~23·31·41~45
    - WORK-51 `excerpts-added/`: U-WORK28·29·32
- 증거 신뢰 수준
  - **실행 기록**: 해당 WORK에서 실제로 실행한 결과다. 보고서와 집계 사본이 남아 있다.
  - **재생본**: 원본이 소실돼 세션 기록에서 재생했다. 바이트 해시는 검증하지 못했다.
  - **관찰**: 단정이 아닌 관찰 출력이다.
- 해시는 SHA-256 앞 8자다. 현재 값 전체는 검토 패킷의 `state/` 해시 목록에 있다.

## 2. 변경별 기록

| WORK | 변경 파일 | 성격 | 목적 | 지시 출처 | Before → After | 증거 (신뢰 수준) | 누락·미검증 |
|---|---|---|---|---|---|---|---|
| 22 | `OutboxMigrationIntegrationTest` H-3 | 하네스 변경 (단계 전환) | STUB 예외 기대를 실제 기록 결과 확인으로 바꿈 | U-WORK22 §3 "명시적 허용" | `33cf1ecb` → (WORK-22 After 기록 원본 누락) | WORK-22 regr1-mh 10/10 (재생본) | H3-CHANGE 원본 누락 |
| 23 | `OutboxRetryPolicyTest`, `OutboxRetryPolicy` | **기존 AC 누락 보완** (AC-1 `isExhausted` k ≤ 0 거부) | 계약 개정 3 AC-1에 있던 조건이 테스트·구현에서 빠져 있었음 | U-WORK23 ("MASTER가 명시적으로 허용한 동결 테스트 보완") | 테스트 `21f3b0a9` → `33efe9e7`, 구현 `7504746d` → `c0bdc1b1` | 신규 2건 RED 2/2 → Unit 27/27 (재생본) | – |
| 29 | `application-common.yml` (`org.hibernate.orm.jdbc.bind` `trace` → `"OFF"`), `docs/db/README.md` 설명 | **공통 설정 변경 (제한적)** | test 실행 출력에서 관찰된 payload 바인딩 값 출력을 기본으로 끔 | U-WORK29 ("application-common.yml에서 … "OFF"로 지정한다") | `d61af022` → `d9d2e550` | AC-14 격리 실행 2/2, payload 사례 bind 출력 없음 (실행 기록, **관찰**) | **자동 테스트 Valid RED→GREEN으로 기록하지 않음**. 전체 애플리케이션 출력의 비노출 보장이 아님. 운영 프로필·외부 설정은 미확인. Issue #115 Scope "제외: 기존 전역 로그"와의 관계는 미정리(수정안 별도) |
| 31 | `OutboxRequeueProcedureIntegrationTest` `ac13_lockTimeoutLeavesAllRowsUnchanged` | **회귀 단언** (세션 정리) | 프로시저 오류 뒤 같은 연결에 세션 변수·prepared statement가 남는지 확인 (기존 단언 유지) | U-WORK31 | `c2309c7c` → `521caa90` | Valid RED: 정리 단언 2개 실패 (실행 기록) | – |
| 32 | `R__outbox_requeue_failed.sql` | **세션 정리 보강** | 동적 SQL 구간 오류 시 statement 해제·세션 변수 NULL·원래 오류 RESIGNAL | U-WORK32 ("R__outbox_requeue_failed.sql 한 파일만 수정한다 …") | `04c67ac1` → `bf7ed077` | WORK-31 단언 GREEN (실행 기록). 같은 클래스의 AC-10b 하네스 예외는 WORK-33~45에서 다룸 | 잠금 timeout(1205) 외 오류 경로는 미검증 |
| 41 | 같은 클래스 AC-10b 관찰 방식, `PrimaryKeyLockProbe`·`PrimaryKeyLockProbeTest`·`PrimaryKeyLockProbeIntegrationTest` (신규) | **하네스 보완** | lock_data NULL에서도 대상 잠금을 입증하는 관찰 방식. 비즈니스 단언은 유지 | U-WORK41 | 테스트 `521caa90` → `c613c06b`. 신규 helper `91bafa50`, 단위 `c82a47da`, DB `3cfa7697` | RED 단위 5/13, DB 3/5 → 13/13, 5/5, final 24/24 (실행 기록) | – |
| 42 | `PrimaryKeyLockProbe`, `PrimaryKeyLockProbeIntegrationTest` | **하네스 보완** (기한·정리 실패 판정) | 기한 뒤 성공 반환 금지, close 실패를 성공으로 보지 않음 | U-WORK42 | helper `91bafa50` → `7379d1df`, DB `3cfa7697` → `5b2c5860` | RED 2/7 → 7/7 (실행 기록) | – |
| 43 | `OutboxBacklogQuery`, `OutboxEventRepository.existsByStatusIn`, `OutboxBacklogQueryCostIntegrationTest` (신규) | **반환 의미 유지 최적화 + 비기능 기준 추가** | 대상 상태가 없으면 존재 조회 1개로 빈 결과 반환. 결과 값은 같음 | U-WORK43 | `b607fdcb` → `2130806c`, `c2ec645c` → `655be3c6`, 신규 테스트 `df9ff457` | RED 2/5 → 5/5, AC-15 4/4 (실행 기록) | 실제 처리 시간·대량 데이터 성능 미측정 |
| 43·44 | `OutboxMigrationIntegrationTest` M-2 출력 라벨·주석 | **설명 정정** | `stub_call_out` → `procedure_call_out`, 낡은 stub 주석 정정 | U-WORK43, U-WORK44 | `fb326998` → `e604879a` → `c48c2e97` | WORK-43 Migration 10/10. WORK-44는 실행 없음 | – |
| 45 | `PrimaryKeyLockProbe`, `PrimaryKeyLockProbeIntegrationTest` | **하네스 보완** (observer 오류 뒤 정리 오류 보존, DIRECT 기한 확인) | 두 결함의 재현과 수정 | U-WORK45 | helper `7379d1df` → `927fdef3`, DB `5b2c5860` → `cda4c81e` | RED 2/2 → 9/9, final 24/13/9 (실행 기록) | – |
| 52 | `OutboxRelayService`, `OutboxBacklogIntegrationTest` | **기존 AC-15 시각 결함 수정** (G-1) | backlog 관측·간격 기준을 폴링 시작 시각에서 처리 후 시각과 실제 로그 호출 시각으로 분리 | WORK-52 MASTER 지시 (세션 기록, 이 문서에 발췌 없음) | `ccb82fb0` → `5f8a2155`, `3b14b9fa` → `d1d90347` | 신규 2건 RED 2/2 → GREEN 2/2, 관련 2개 클래스 11/11 (실행 기록) | snapshot 조회 지연 시나리오를 단정하는 테스트 없음 |
| 53 | `OutboxErrorExposureIntegrationTest` DisplayName 2개, `OutboxBacklogQuery` 주석, ADR-006, INTERVIEW-NOTES, 이 문서 | **설명 정정** (G-3·G-4) | 실제 단정 범위·시각 의미·검증 상태를 문서에 맞춤 | WORK-53 MASTER 지시 | 이번 작업 | 테스트·Gradle 미실행 | – |

## 3. 집계 (동결 선택 집합과 섞지 않음)

| 구분 | 건수 | 근거 |
|---|---|---|
| 동결 선택 집합 | 76 | WORK-21 FREEZE-RECORD (재생본), WORK-22 GREEN |
| 동결 후 추가 (WORK-51까지) | 29 | WORK-23 +2, WORK-41 +18, WORK-42 +2, WORK-43 +5, WORK-45 +2 |
| WORK-52 추가 | 2 | 신규 회귀 2건 |
| **누적 추가** | **31** | 29 + 2 |
| 마지막 전체 회귀 | 381 | WORK-47, **WORK-52 수정 전 소스**. exit 0, 381/381, 공유 DB `R__` 재적용 성공. 종료 단계 Netty ERROR 1건은 원인·영향 미확정 |
| 현재 예상 전체 수 | 383 | 381 + 2. 다른 증감이 없다는 전제의 **예상이며 실행 결과가 아니다** |

- WORK-23의 +2는 WORK-27(354건) 이전에 추가돼 354와 381 안에 포함돼 있다.
- 기존 메서드의 단언·관찰 방식만 바뀐 경우(WORK-22 H-3, WORK-31, WORK-41 AC-10b)는 건수에 넣지 않았다.

## 4. 검토 상태 (2026-11-16, 판정 아님)

- **Claude 1차 검토 (WORK-51)**
  - Risk Reviewer 결과: Critical 0, High 0, Medium 3, Low 5
  - Medium 3건: 동결 후 production 변경 기록 누락, bind 설정과 Issue 범위 충돌, 문서 정정 후 validator 미실행
- **GPT 독립 검토 REVIEW-1**
  - 권고 "수정 후 진행", 항목 G-1~G-4 (MASTER 전달 기준, 검토 원문은 이 저장소에 없다)
  - 아래 대응은 모두 독립 재검토를 기다린다. Finding 최종 종료를 선언하지 않는다.

  | 항목 | 내용 | 대응 | 남은 것 |
  |---|---|---|---|
  | G-1 | backlog 시각 결함 | WORK-52에서 수정, 관련 검증 완료 (신규 2건 RED→GREEN, 관련 2개 클래스 11/11) | 독립 재검토, 수정본 전체 회귀 |
  | G-2 | 후속 변경의 계약상 지위와 Issue 범위 추적 | 이 문서로 변경별 기록을 보완했다(§2). Issue #115 수정안을 저장소 밖에 준비했다 | Issue 원격 반영, 최종 종료 판단 |
  | G-3 | 현재 문서의 검증 상태 최신화 | WORK-53에서 ADR-006·INTERVIEW-NOTES 갱신 | 재검토 |
  | G-4 | AC-14 DisplayName 표현 | WORK-53에서 실제 단정 범위로 정정 (단언 불변) | 재검토 |
- **완료되지 않은 것**
  - WORK-52·53 수정본의 전체 회귀
  - GPT 재검토
  - CI
  - 학습 gate: 사용자 설명 수령 확인, MASTER 정확성 검토
- **validator와 self-test**
  - 마지막 PASS는 WORK-45다.
  - 스크립트 기준으로 이 문서, ADR-006, INTERVIEW-NOTES, Java 소스는 검사 입력이 아니다.
  - 검사 입력은 필수 12개 파일, `docs/db/README.md`, `docs/architecture`·`docs/api` 디렉터리 존재다.

## 5. 확보 상태와 미검증

| 항목 | 상태 | 출처 |
|---|---|---|
| 계획 본문 | 확보 | WORK-15 Claude 보고 (세션 기록 발췌 `A-WORK15`) |
| 계획 보완 | 확보 | WORK-16 MASTER 지시 (`U-WORK16`) |
| 구현 진입 | 확보 | WORK-22 MASTER 지시 (`U-WORK22`) |
| 사용자 직접 승인 발화·시각 | **미확보** | – |
| 동결 원문을 보존하고 동결 이후 변경을 이 별도 addendum으로 관리하는 방식 | 정해짐 | WORK-53 MASTER 지시 |
| 과거 각 변경 당시(WORK-23~52)의 계약 반영 결정 원문 | 기록 없음 | 각 변경 지시는 개별 변경만 허용했다. 이는 위의 현재 기록 방식 결정(WORK-53)과 다른 것이다 |

미검증·소실:

- WORK-21~23 원본 로그·XML, H3-CHANGE, red-green-table, WORK-27 원본은 소실된 상태다. 복구하지 않았다.
- WORK-52 이후 수정본의 전체 회귀 결과가 없다(현재 예상 383건은 실행 결과가 아니다).

## 6. 후속 기록 (WORK-54~62, 2026-11-17 추가)

> §1~§5는 WORK-54 정정이 반영된 본문을 그대로 보존한다. 보존 기준 SHA-256: `00115fe95ef6aaf2632c09c1b0c4718e1222dcc8a79b456a2809898164b7293d`.
> 해당 절의 미완료·예상 건수 표기는 당시 시점 기록이며, 이후 상태는 이 후속 기록에 명시한다.
> 이 절도 판정·PASS·TASK 완료를 선언하지 않는다.

| WORK | 내용 | 저장소 변경 | 증거 (신뢰 수준) | 한계 |
|---|---|---|---|---|
| 54 | 이 문서의 Finding 대응·증거 확보 상태 정정: §2 WORK-53 행 표기(G-3·G-4), §4 REVIEW-1 대응 표(G-1~G-4·남은 것), §5 확보 상태 표. 재검토 보충 자료 작성 | 이 문서 | 정정 전후 diff와 보충 자료 (저장소 밖 보존, 실행 기록) | – |
| 55~56 | GPT 재검토 입력 전달: 텍스트 검토 자료, 계획·지시 원문 보충 | 없음 | 저장소 밖 전달 파일 (실행 기록) | – |
| 57 | Issue #115 Scope·Notes 정정을 원격에 반영 (WORK-53 수정안 문구 그대로) | 없음 (원격 Issue) | 반영 후 본문 SHA-256 `90886834`, 수정안 외 diff 없음, 제목·라벨·담당자·상태 불변 (실행 기록) | 근거는 MASTER 인계로 확인된 사용자 승인이다 |
| 58 | 수정본 전체 회귀 사전 확인 | 없음 | 공유 DB 집계·Flyway checksum 대조, 읽기 전용 (실행 기록) | – |
| 59 | 수정본 전체 회귀 1회 | 없음 | exit 0, 383/383, 51개 클래스 (실행 기록) | 아래 참고 |
| 60 | 회귀 환경 관찰 영향 확인 | 없음 | 소스·보존 로그 대조 (관찰·추론 구분) | 원인 미확정 |
| 61 | 현행 규칙 확인·문서 최신화안 작성 | 없음 | 저장소 밖 수정안 | – |
| 62 | 이 절 추가, ADR-006·INTERVIEW-NOTES 검증 상태 최신화 | 이 문서, ADR-006, INTERVIEW-NOTES | 적용 전후 diff (저장소 밖 보존) | 테스트·validator 미실행 |

| 구분 | 건수 | 근거 |
|---|---|---|
| 수정 전 소스 전체 회귀 | 381 | WORK-47 (§3 그대로) |
| **수정본 전체 회귀** | **383** | WORK-59. 실패·오류·건너뜀 0. WORK-47 대비 `OutboxBacklogIntegrationTest` 4→6(+2), 그 밖의 클래스별 수 동일 |

- 공유 테스트 DB: 새 migration·`R__` 재적용 없음. `outbox_event` PENDING 99→110, notification·payment 0. 집계·스키마만 확인했고 기존 행 각각의 불변은 주장하지 않는다.
- 프로필: 프로필 미지정 Controller 슬라이스 3개 클래스 28건은 기본 프로필 `dev`로 실행됐다. 383건 전체를 test 프로필 결과로 쓰지 않는다.
  WORK-60 관찰에서 현재 TASK 수정 근거는 확인되지 않았지만, 외부 접속의 전면 부재와 JVM 전역 로깅 영향은 입증하지 않았다.
- Netty: WORK-47 종료 단계 ERROR는 WORK-59 1회에서 관찰되지 않았다. 원인·영향은 미확정이며 후속 조사 항목으로 분류한다(Issue 미생성).
- 검토 상태: GPT 재검토 REVIEW-2에서 G-1~G-4 종료와 검토 입력 누락 해소 (MASTER 인계 기준, 원문은 이 저장소에 없다). 변경 파일 전체의 신규 검토나 TASK 완료로 확대하지 않는다.
- 승인 기록의 한계: §5의 "사용자 직접 승인 발화·시각 미확보"는 그 표가 다루는 과거 기록(계획·구현 진입 등)의 한계다. WORK-57 반영의 사용자 승인은 MASTER 인계에서 확인된 사실로 기록한다.
- 완료되지 않은 것: CI, Git·PR, 학습 gate(Issue #115 계획: merge 전 설명 중심 gate)
  - 필수 설명: 사용자 전달 전 초안
  - 기록: INTERVIEW-NOTES를 WORK-62에서 최신화했으나 MASTER 정확성 검토 전
  - 사용자 설명 수령 확인: 미완료
  - MASTER 정확성 검토: 미완료
- validator·self-test: WORK-54~62에서는 실행하지 않았다.
