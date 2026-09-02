# 개발 Workflow

## 1. 문서 책임

이 문서는 TASK 시작부터 최종 완료까지의 lifecycle, 단계별 입력·산출물·승인 gate·중단 조건의 단일 기준이다.

상세 규칙은 다음 문서에 위임한다.

- 공통 프로젝트 원칙, Git 형식, 전체 테스트 명령: `docs/process/PROJECT-RULES.md`
- TASK 시작 입력과 안전한 자료 요청: `docs/process/TASK-START-CHECKLIST.md`
- GPT 독립 검토 입력·Finding·Severity: `docs/process/GPT-REVIEW-CONTRACT.md`
- TASK 상태와 다음 순서: `docs/process/PORTFOLIO-ROADMAP.md`
- 학습·면접 검증: `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md`

이 문서는 위 문서의 전문을 복사하지 않고 수행 시점과 gate를 연결한다.

## 2. 공통 gate

모든 단계에 다음 원칙을 적용한다.

- 현재 TASK, Phase, 허용 작업, 금지 작업과 baseline을 먼저 확인한다.
- 핵심 입력이 누락되면 다음 주요 단계로 진행하지 않는다.
- 사용자 승인 없이 다음 주요 Phase로 자동 진행하지 않는다.
- 승인 범위 밖 파일이나 기능을 현재 구현에 추가하지 않는다.
- 변경 가능한 저장소 사실은 현재 근거로 재검증한다.
- Critical Finding은 영향받는 단계의 진행을 차단한다.
- 실행하지 않은 테스트나 Git 작업을 실행했다고 기록하지 않는다.

## 3. 단계별 lifecycle

### 1. TASK 시작

**목적**
현재 작업의 식별자, 목표, 범위와 진행 가능한 Phase를 고정한다.

**입력**
- TASK 또는 승인된 작업 요청
- 현재 project state

**실행**
- `docs/process/TASK-START-CHECKLIST.md`에 따라 structured state를 확인한다.
- 특정 시작 magic phrase를 요구하지 않는다.

**산출물**
- TASK/Phase/allowed/prohibited/baseline/input/blocker 상태

**승인 gate**
- 사용자가 현재 TASK와 수행 Phase를 승인해야 한다.

**중단 조건**
- TASK 또는 범위 불명확
- baseline 불일치
- 진행을 막는 입력 누락

### 2. 입력자료 확인

**목적**
현재 Phase 수행에 필요한 핵심·보조 근거를 고정한다.

**입력**
- TASK 시작 상태
- 실제 저장소 경로와 승인 자료

**실행**
- 실제 존재 경로를 확인하고 필요한 최소 자료만 사용한다.
- 민감정보를 요청하거나 출력하지 않는다.

**산출물**
- 입력자료 목록
- 누락 자료
- blocker

**승인 gate**
- 핵심 입력이 모두 확보되어야 다음 단계 제안이 가능하다.

**중단 조건**
- 핵심 입력 하나 이상 누락
- 승인 자료와 현재 저장소 근거 충돌이 해소되지 않음

### 3. Issue / 계획

**목적**
Goal, Scope, 완료 조건과 검증 계획을 구현 전에 고정한다.

**입력**
- 승인된 TASK 목표
- 입력자료 검증 결과
- 관련 로드맵과 결정

**실행**
- Issue와 구현 계획을 작성·검토한다.
- Git metadata 형식은 `docs/process/PROJECT-RULES.md`를 참조한다.

**산출물**
- 승인 후보 Issue
- 구현 범위·계획
- Test plan

**승인 gate**
- Issue와 구현 계획을 사용자가 승인해야 설계·구현으로 진행한다.

**중단 조건**
- Scope와 DoD 불명확
- 승인 범위와 계획 충돌
- 핵심 입력 누락

### 4. 설계

**목적**
구현 전에 선택 구조와 위험, 테스트 전략을 검증한다.

**입력**
- 승인된 Issue와 계획
- 관련 코드·문서 근거

**실행**
- 필요한 설계 대안과 트레이드오프를 정리한다.
- TASK 범위를 넘어서는 개선은 분리한다.

**산출물**
- 승인 가능한 설계
- 위험과 검증 계획

**승인 gate**
- 현재 TASK에서 설계 승인이 필요한 경우 사용자 승인 후 구현한다.

**중단 조건**
- 핵심 기술 근거 부족
- 범위 확장 필요
- Critical 수준의 선행 위험

### 5. 구현

**목적**
승인된 설계를 허용 파일과 범위 안에서 반영한다.

**입력**
- 승인된 Issue·계획·설계
- 현재 코드와 문서

**실행**
- 승인된 논리 작업 단위로 변경한다.
- 비자명한 이유·제약에만 주석을 사용한다.
- 관련 테스트를 함께 작성할 수 있다.

**산출물**
- 승인 범위의 변경
- 구현 중 발견한 blocker와 범위 밖 제안

**승인 gate**
- 허용 범위를 변경해야 하면 구현을 멈추고 사용자 승인을 받는다.

**중단 조건**
- 승인 외 파일 변경 필요
- 현재 근거와 승인 설계의 실질 충돌
- Critical 위험

### 6. Claude 1차 검토

**목적**
GPT 독립 검토 전에 구현 범위와 명백한 결함을 자체 점검한다.

**입력**
- 실제 변경
- 승인 Issue·계획
- 관련 테스트·문서

**실행**
- 범위, 누락, 테스트 영향, 문서 영향과 명백한 위험을 확인한다.
- 코드 위험 검토가 필요하면 프로젝트 Risk Reviewer Agent를 사용할 수 있다.

**산출물**
- 1차 검토 결과
- 미해결 Finding
- GPT 전달에 필요한 근거

**승인 gate**
- Critical이 없고 독립 검토에 필요한 핵심 입력이 준비되어야 한다.

**중단 조건**
- Critical 발견
- 변경 범위 불일치
- GPT 핵심 입력 준비 불가

### 7. GPT 독립 검토

**목적**
구현자가 아닌 독립 검토자가 승인 요구사항과 실제 변경을 검증한다.

**입력**
- `docs/process/GPT-REVIEW-CONTRACT.md`의 Implementation 핵심 입력

**실행**
- 공통 Finding schema와 Severity를 사용한다.
- Agent 결과가 있어도 먼저 독립적으로 검토한다.

**산출물**
- 전체 Finding
- Final recommendation
- User decision required

**승인 gate**
- 사용자가 Finding과 수정·위험 수용 여부를 결정한다.

**중단 조건**
- 핵심 입력 누락
- Critical 미해결
- 사용자의 판단이 필요한 blocker 미결정

### 8. 테스트

**목적**
승인된 변경이 요구사항과 회귀 위험을 만족하는지 검증한다.

**입력**
- 실제 변경
- Test plan
- 검토 Finding

**실행**
- 관련 테스트가 있으면 먼저 실행한다.
- 관련 테스트가 존재하지 않으면 해당 없음으로 기록할 수 있다.
- 환경 또는 기술적 이유로 관련 테스트를 실행할 수 없으면 이유와 남은 위험을 기록한다.
- 동작 변경은 자동 테스트를 기본으로 한다.
- 수동 검증은 자동 테스트로 충분하지 않을 때 조건부 수행한다.
- 최종 단계에는 `docs/process/PROJECT-RULES.md`의 전체 테스트 명령을 사용한다.

**산출물**
- 실제 실행 결과
- 실행한 test evidence는 `docs/process/PROJECT-RULES.md`의 공통 test evidence 원칙에 따라 기록한다.
- 실패 원인
- 미실행 사유와 남은 위험

**승인 gate**
- 실패와 미실행 위험을 사용자가 판단할 수 있어야 한다.

**중단 조건**
- 필수 검증 실패
- Critical에 연결된 재검증 실패
- 테스트 결과를 확정할 근거 부족

### 9. Git 검증

**목적**
커밋 전에 변경 범위와 저장소 상태를 검증한다.

**입력**
- 최종 변경
- 테스트 결과
- 미해결 Finding과 사용자 결정

**실행**
- changed files, diff, 상태와 승인 범위를 비교한다.
- README 영향 여부와 문서 링크 영향을 확인한다.
- Git 형식은 `docs/process/PROJECT-RULES.md`를 참조한다.

**산출물**
- commit 가능 여부에 대한 검증 결과

**승인 gate**
- commit은 별도 승인된 단계에서만 수행한다.

**중단 조건**
- 승인 범위 밖 변경
- unresolved Critical
- 필요한 사용자 결정 미완료

### 10. PR

**목적**
승인된 변경과 검증 근거를 리뷰 가능한 형태로 제출한다.

**입력**
- 승인된 commit
- 최종 diff와 검증 결과
- 미해결 Finding과 사용자 결정

**실행**
- PR 형식은 `docs/process/PROJECT-RULES.md`를 참조한다.
- 구현·테스트·위험 정보를 사실 그대로 기록한다.

**산출물**
- 리뷰 가능한 PR

**승인 gate**
- PR 생성과 reviewer 지정은 승인된 단계에서만 수행한다.

**중단 조건**
- unresolved Critical
- 승인되지 않은 변경 포함
- 검증 근거 부족

### 11. merge

**목적**
검토·승인된 변경만 기준 branch에 반영한다.

**입력**
- PR 리뷰 결과
- 최종 검증
- 학습 gate 상태

**실행**
- 현재 TASK 계획에서 merge 전 learning gate가 승인됐는지 확인한다.
- merge 전 learning gate가 없으면 정상 merge 흐름을 진행하고, 일반 TASK learning은 merge 후 수행할 수 있다.
- merge 전 learning gate가 있고 아직 통과하지 않았다면 단계 13 Learning을 선행 수행한다.
- 단계 13 Learning이 PASS하면 단계 11 merge gate로 복귀하며, merge는 사용자 승인 후에만 수행한다.
- 이 조건부 transition은 사용자 승인 없는 자동 단계 진행을 의미하지 않는다.
- 일반 TASK learning은 merge 후 수행할 수 있지만 TASK 최종 완료 전, 다음 핵심 구현 TASK 시작 전에는 통과해야 한다.

**산출물**
- merge 가능 여부 또는 merge 결과

**승인 gate**
- merge는 사용자 승인 후에만 수행한다.

**중단 조건**
- unresolved Critical
- 필수 review 미완료
- 현재 TASK 계획에서 merge 전 필수로 승인된 learning gate 미통과
- 사용자 승인 없음

### 12. 조건부 회고 / 장애 기록

**목적**
실제 가치가 있는 실패·의사결정·장애를 보존한다.

**입력**
- TASK 수행 중 사건과 검증 기록

**실행**
- 실제 incident, regression, data-loss risk, environment failure가 있었으면 장애 기록 필요성을 판정한다.
- 중요한 기술 판단, 의미 있는 실패·재검증, 포트폴리오·면접 가치가 있고 사용자가 승인하면 devlog를 작성한다.
- 모든 TASK에 devlog나 장애 문서를 강제하지 않는다.

**산출물**
- 작성 필요 여부와 근거
- 승인된 경우에만 해당 기록

**승인 gate**
- devlog 작성은 사용자 별도 승인 대상이다.

**중단 조건**
- 해당 기록이 필요하지 않다는 판단 자체는 TASK 진행을 차단하지 않는다.
- 기록이 필요하다고 판정됐지만 필요한 사실 근거 또는 사용자 결정이 부족하면 해당 기록 단계에서 중단한다.

### 13. Learning / interview

**목적**
TASK 결과를 사용자가 설명하고 방어할 수 있는 상태인지 검증한다.

**입력**
- 최종 구현과 검증 근거
- `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md`

**실행**
- 대표·고위험 여부와 learning deadline은 TASK 계획 단계에서 승인한다.
- 계획에서 merge 전 learning gate가 승인된 TASK만 merge 전에 학습을 통과해야 한다.
- 일반 TASK는 merge 후 학습을 수행할 수 있지만 TASK 최종 완료 전, 다음 핵심 구현 TASK 시작 전에는 통과해야 한다.
- 학습 결과에는 pass 여부, 핵심 오해, 사용자와 AI 기여 구분을 남긴다.
- 대표·고위험 TASK는 승인된 학습 계약의 산출물 기준을 따른다.

**산출물**
- 학습 검증 결과
- 필요 시 승인된 학습 산출물

**승인 gate**
- 학습 통과 조건과 기한은 TASK 계획 단계에서 승인한다.

**중단 조건**
- 해당 TASK에 필요한 학습 gate 미통과

### 14. TASK 최종 완료

**목적**
개발·검토·검증·Git·학습 의무가 모두 충족됐는지 확인한다.

**입력**
- merge 또는 승인된 종료 상태
- 테스트와 review 결과
- 학습 결과
- 조건부 회고·장애 기록 판단

**실행**
- DoD와 남은 위험을 확인한다.
- TASK 상태 변경은 `docs/process/PORTFOLIO-ROADMAP.md` 책임에 따라 관리한다.

**산출물**
- TASK 최종 완료 판정
- 다음 단계 후보

**승인 gate**
- 사용자가 TASK 최종 완료와 다음 작업 시작 여부를 결정한다.

**중단 조건**
- DoD 미충족
- 필요한 review 또는 검증 미완료
- 해당 TASK의 learning 의무 미완료
- 필요한 사용자 최종 결정 없음

## 4. Critical 공통 흐름

Critical이 확인되면 현재 영향 단계에서 다음 순서로 처리한다.

1. 진행 중단
2. Evidence와 Risk 확인
3. 재현 조건과 Required action 확인
4. 수정 선택지와 영향 보고
5. 사용자 승인
6. 승인된 수정
7. 재검증

해결 또는 명시적 위험 수용 전에는 commit, push, PR, merge로 진행하지 않는다.

## 5. 문서와 테스트 영향

- 모든 TASK에서 문서와 테스트 영향을 확인한다.
- README는 TASK 승인 범위 안에서 실제 수정 필요가 있을 때만 수정한다.
- 실행 방법, 환경, 외부 사용 방식 변경은 대표적인 README 수정 필요 사례이며 유일한 조건이 아니다.
- README 영향이 없으면 불필요하게 수정하지 않는다.
- 영향이 없으면 그 이유를 기록할 수 있어야 한다.
- 전체 테스트 명령은 이 문서에 복제하지 않고 `docs/process/PROJECT-RULES.md`를 참조한다.

## 6. 다음 단계 금지

현재 Phase 결과를 보고한 뒤 사용자의 승인 없이 다음 Phase나 다음 TASK를 자동 실행하지 않는다.
