# Test-First AI Development Contract

## 1. 문서 책임

이 문서는 신규 Production behavior의 Requirement를 executable Test Contract로 고정하고 RED → Production Implementation → GREEN으로 진행하는 Test-First semantics의 authoritative SSOT다.

개발 lifecycle, review, regression, Git gate는 `docs/process/DEVELOPMENT-WORKFLOW.md`를 따른다.
공통 test evidence와 최종 전체 테스트 명령은 `docs/process/PROJECT-RULES.md`를 따른다.

## 2. 기본 흐름

신규 Production behavior는 구현보다 Test Contract를 먼저 준비한다.

```text
Requirement
→ Happy / Unhappy / Boundary
→ Acceptance Criteria
→ Test Level
→ Test Contract
→ RED
→ Production Implementation
→ GREEN
→ 필요한 Integration / Acceptance GREEN
→ Claude Review
→ GPT Independent Review
→ Refactor
→ Full Regression
→ Evidence
→ Git
```

기존 MASTER / WORK / Review / Validator / Regression / Git / Learning 구조는 보존한다.

## 3. Test Contract

Production behavior 구현 전에 최소 다음을 명시한다.

- Requirement
- Happy Path
- Unhappy Path
- Boundary
- Acceptance Criteria
- Test Level
- RED policy

Acceptance Criteria는 검증 가능한 behavior로 작성하고 executable test에 연결한다.

Test Level은 위험과 behavior에 따라 Unit / Integration / Acceptance 중 선택한다.
Integration 또는 Acceptance test를 모든 TASK에 전역 강제하지 않는다.

## 4. RED 정책

### RED_REQUIRED

신규 Production behavior의 기본값은 `RED_REQUIRED`다.

`RED_REQUIRED`에서는 Production Implementation 전에 승인된 Test Contract의 Valid RED를 확인한다.

### RED_NOT_APPLICABLE

`RED_NOT_APPLICABLE`은 예외이며 최소 다음 세 조건이 모두 필요하다.

- 명시적 사유
- 위험 평가
- MASTER 승인

하나라도 없으면 Production Implementation 진입 근거로 사용할 수 없다.

## 5. Valid RED

RED != 아무 실패

Valid RED는 승인된 Test Contract가 요구하는 behavior가 아직 구현되지 않았기 때문에 발생한 예상된 실패다.

다음은 Valid RED가 아니다.

- dependency/setup failure
- compile/test infrastructure failure
- unrelated regression failure
- broken test 자체의 오류
- 단순 외부 infrastructure 미가동으로 발생한 실패
- Harness 자체 오류

Validator는 failure semantics를 실행 결과 수준에서 추론하지 않는다.
Valid RED의 의미 판단은 MASTER / Review 책임이며 validator는 structural contract drift만 방어한다.

## 6. Production Implementation과 GREEN

`RED_REQUIRED`의 Production Implementation 진입 조건은 다음과 같다.

1. Requirement와 Acceptance Criteria 승인
2. Test Level과 executable Test Contract 고정
3. Valid RED 확인

`RED_NOT_APPLICABLE`이면 명시적 사유, 위험 평가, MASTER 승인 근거를 구현 전에 확인한다.

Production Implementation 후에는 동일 Test Contract가 GREEN이어야 한다.
계약상 필요한 Integration / Acceptance test가 있으면 해당 수준도 GREEN이어야 한다.

GREEN은 단순 command 성공이 아니라 승인된 Test Contract의 behavior가 통과한 상태를 의미한다.

## 7. Test Level과 Mock Boundary

### Unit

도메인 규칙, 상태 전이, 계산, 분기처럼 좁은 behavior를 결정적으로 검증한다.

### Integration

DB, Redis, transaction, framework wiring처럼 실제 경계와의 결합이 핵심 위험일 때 사용한다.

### Acceptance

사용자가 관찰하는 주요 흐름 또는 여러 component를 연결한 Acceptance Criteria 검증이 필요할 때 사용한다.

### Mock Boundary

도메인 behavior 자체는 가능한 실제 객체로 검증하고 외부 또는 느린 경계는 Test Contract 목적에 맞게 Mock할 수 있다.

Mock PASS != 실제 Infrastructure Evidence

Mock 기반 GREEN을 실제 DB, Redis, network 또는 external infrastructure 검증으로 기록하지 않는다.

## 8. Frozen Acceptance Contract

중요 기능은 다음 순서를 사용한다.

```text
Requirement
→ Acceptance Criteria
→ Executable Test
→ Contract Freeze
→ RED
→ Production Implementation
→ 동일 Contract GREEN
```

Production 구현에 맞추기 위해 테스트를 임의로 약화하지 않는다.

Contract mismatch/change가 발견되면 다음 순서를 따른다.

```text
Mismatch 발견
→ Requirement 재비교
→ MASTER 보고/승인
→ Before / After Contract 구분
→ 필요 시 새 RED
→ 변경 Contract 기준 GREEN
→ Evidence
```

변경 전 Contract와 변경 후 Contract를 구분하지 않은 채 기존 실패를 삭제하거나 기대값을 완화하지 않는다.

## 9. Evidence 책임

최소 다음 evidence 책임을 구분한다.

- TEST_CONTRACT_EVIDENCE
- RED_EVIDENCE
- PRODUCTION_CHANGE_EVIDENCE
- GREEN_EVIDENCE
- REGRESSION_EVIDENCE

실제 command와 결과, 공통 test evidence 형식은 `docs/process/PROJECT-RULES.md`에 위임한다.
실행하지 않은 결과를 PASS로 기록하지 않는다.

## 10. A-5 Harness

A-5 Harness 적용은 SELECTIVE다.

A-5 Harness는 Requirement → Test 검증을 보조할 수 있지만 모든 Repository TASK에 전역 적용하지 않는다.

A-5 Harness PASS != Repository RED/GREEN Evidence

Harness PASS를 Repository executable Test Contract의 RED 또는 GREEN을 대신하는 evidence로 사용하지 않는다.
