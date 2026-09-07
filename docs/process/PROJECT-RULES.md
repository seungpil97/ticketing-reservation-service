# 프로젝트 교차 작업 규칙

## 1. 문서 책임

이 문서는 다음 항목의 단일 기준이다.

- 모든 TASK에 공통으로 적용하는 프로젝트 원칙
- 파일·Git·외부 작업의 공통 승인 원칙
- Issue, branch, commit, PR, prefix, Assignee, Reviewer 형식
- 최종 전체 테스트 명령
- 현재 저장소 근거로 확인해야 하는 기술 최소 원칙과 상세 문서 인덱스

단계별 lifecycle과 승인 게이트 상세는 `docs/process/DEVELOPMENT-WORKFLOW.md`에서 관리한다.
GPT 입력·Finding·Severity 계약은 `docs/process/GPT-REVIEW-CONTRACT.md`에서 관리한다.

## 2. 근거와 승인

- 현재 TASK와 승인 범위 밖 파일이나 기능을 임의로 변경하지 않는다.
- 파일 생성·수정, Git 작업, 외부 작업은 현재 Phase에서 승인된 범위 안에서만 수행한다.
- 사용자 승인 없이 다음 주요 Phase나 다음 TASK로 자동 진행하지 않는다.
- 과거 문서와 현재 코드·설정·주제별 문서가 충돌하면 변경 가능한 현재 사실을 먼저 재검증한다.
- 기술 사실, 테스트 결과, 저장소 상태를 근거 없이 확정하지 않는다.
- 범위 밖 문제는 현재 TASK 구현과 분리해 보고한다.

## 3. Git metadata 단일 기준

### Issue

제목:

```text
TASK-XXX 작업 내용 요약
```

본문 섹션 순서:

```text
Goal
Background
Scope
Definition of Done
Test
Notes
```

Assignee:

```text
pil97
```

Label은 고정 집합을 만들지 않는다.
저장소에 실제 존재하는 label만 사용하고 신규 label 생성은 사용자 승인을 먼저 받는다.

### Branch

형식:

```text
{prefix}/TASK-XXX-english-kebab-case
```

TASK 뒤 설명은 영어 kebab-case만 사용한다.

### Commit / PR

형식:

```text
{prefix}(TASK-XXX): 변경 대상과 구체적인 결과
```

설명은 기본적으로 한국어를 사용하되 기술명과 경로는 영어를 사용할 수 있다.

자동 AI signature를 추가하지 않는다.

```text
Co-Authored-By
Generated-By
```

Reviewer:

```text
seungpil97
```

### 허용 prefix

정확히 다음 값만 허용한다.

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`
- `build`

그 외 prefix는 현재 규칙에서 사용하지 않는다.
prefix 집합 자체를 변경하려면 `PROJECT-RULES.md` 변경을 별도 승인받아야 한다.

### Hotfix

정상적인 Issue → branch → PR 흐름을 기본으로 한다.
hotfix라는 이유만으로 승인 게이트나 검증을 자동 우회하지 않는다.

우회가 필요하면 먼저 다음을 보고하고 사용자 승인을 받는다.

- 우회 이유
- 위험
- 가능한 검증
- 정상 흐름을 유지하는 대안

## 4. 테스트와 문서 영향

모든 TASK에서 테스트와 문서 영향을 확인한다.

- 동작 변경 시 관련 자동 테스트 작성·실행을 기본으로 한다.
- 관련 테스트가 있으면 먼저 실행한다.
- 실제로 실행한 각 test는 실행 결과와 함께 실제로 실행한 exact full command를 기록한다.
- 관련 테스트가 존재하지 않으면 해당 없음으로 기록할 수 있다.
- 환경 또는 기술적 이유로 관련 테스트를 실행할 수 없으면 이유와 남은 위험을 기록한다.
- 실행하지 못한 테스트는 실행했다고 기록하지 않는다.
- 테스트를 실행하지 못하면 이유와 남은 위험을 기록한다.
- 수동 검증은 자동 테스트만으로 충분하지 않을 때 조건부로 수행한다.
- Postman을 모든 TASK의 필수 절차로 강제하지 않는다.
- README는 TASK 승인 범위 안에서 실제 수정 필요가 있을 때만 수정한다.
- 실행 방법, 환경, 외부 사용 방식 변경은 대표적인 README 수정 필요 사례이며 유일한 조건이 아니다.
- README 영향이 없으면 불필요하게 수정하지 않는다.
- README 영향이 없으면 그 판단 근거를 기록할 수 있어야 한다.

### 최종 전체 테스트 명령

최종 전체 테스트의 단일 명령은 다음과 같다.

```bash
./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test
```

실행 시점과 실패 처리 절차는 `docs/process/DEVELOPMENT-WORKFLOW.md`를 따른다.

## 5. 기술 최소 원칙

기술 규칙은 과거 문서보다 현재 코드·설정·주제별 문서를 우선 확인한다.
과거 값이나 구조를 현재 사실처럼 자동 복원하지 않는다.

### 5.1 Java / Spring Boot / Gradle

- 현재 `build.gradle`, `settings.gradle`, Gradle wrapper 선언을 우선 확인한다.
- 과거 버전 값을 현재 버전으로 자동 복구하지 않는다.
- 버전이나 toolchain을 문서에 적을 때는 현재 선언 근거를 다시 확인한다.

### 5.2 Package / Layer

- 현재 도메인 중심 package 구조를 기준으로 변경 영향을 확인한다.
- Controller는 API boundary를 담당하고 use-case/service에 위임한다.
- Controller에서 repository를 직접 호출하지 않는다.
- `api → application → domain`을 모든 코드에 적용되는 절대 의존 규칙으로 선언하지 않는다.

상세 구조는 현재 `docs/architecture/`와 실제 package를 함께 확인한다.

### 5.3 Transaction

- 트랜잭션은 service/use-case 경계를 기본 검토 지점으로 삼는다.
- 트랜잭션 경계를 명확히 하고 변경 시 관련 service와 테스트를 함께 확인한다.
- read-only 조회 트랜잭션은 우선 고려할 수 있지만 모든 조회에 절대 강제하지 않는다.
- Controller를 트랜잭션 경계로 사용하지 않는다.
- 동시성·lock 검증에서 테스트의 트랜잭션이 실제 commit/lock 동작을 가리지 않는지 확인한다.

### 5.4 DTO

- API Request와 Response 역할을 분리한다.
- Entity를 API 응답으로 직접 노출하지 않는다.
- 내부 query/use-case result DTO를 허용한다.
- DTO 이름은 현재 도메인과 기존 코드의 역할 표현을 우선한다.
- 단일 `{verb}{domain}Request` naming 규칙을 모든 DTO에 강제하지 않는다.

### 5.5 Error / Common Response

- 현재 `ErrorCode`, `BusinessException`, `ApiResponse`, `ErrorResponse` 계약을 기준으로 변경 영향을 확인한다.
- 현재 `GlobalExceptionHandler`를 공통 오류 처리 구조의 일부로 사용한다.
- Controller에 반복적인 예외 변환용 `try/catch`를 추가하지 않는다.
- 오류 응답에 민감정보를 포함하지 않는다.
- Security/JWT Filter처럼 `GlobalExceptionHandler` 밖의 framework boundary에서도 필요한 경우 동일한 응답 계약을 구성할 수 있다.
- 모든 오류가 반드시 `GlobalExceptionHandler` 하나만 거쳐야 한다는 절대 규칙을 두지 않는다.

### 5.6 Test

- 변경과 가장 관련된 테스트가 있으면 먼저 실행한다.
- 최종 검증에서는 이 문서의 전체 테스트 명령을 기준으로 한다.
- 환경 prerequisite를 확인하고, 미실행 결과를 통과로 기록하지 않는다.
- 테스트 실행 불가 시 이유와 남은 위험을 기록한다.
- transaction test가 commit 또는 lock 동작을 숨기지 않는지 주의한다.
- Testcontainers, Mockito, 고정 thread 수, Postman을 모든 TASK의 필수 기준으로 강제하지 않는다.

### 5.7 Redis Key / TTL

- Redis Key, TTL, cleanup 정책은 현재 implementation과 관련 문서를 먼저 확인한다.
- 과거 Key 표나 TTL 값을 현재 사실로 추정하지 않는다.
- Key, TTL, cleanup 변경 시 구현과 관련 문서를 함께 갱신한다.
- 생성부터 만료·정리까지 lifecycle을 확인한다.
- 과거 `events:list` 값을 현재 Key 표로 복원하지 않는다.

### 5.8 Logging

- 민감정보를 로그에 기록하지 않는다.
- 로그 수준은 사건의 성격과 운영 필요성에 맞게 사용한다.
- 실제로 구현되지 않은 MDC 필드를 필수 logging schema로 선언하지 않는다.
- 관측성 구조를 새로 도입하거나 확대할 때는 별도 TASK에서 구현과 문서를 함께 변경한다.

### 5.9 DB / Flyway

DB와 Flyway 상세 기준은 `docs/db/README.md`를 우선한다.

- 기존 migration 파일을 수정하지 않는다.
- schema 변경은 새로운 migration으로 추가한다.
- DB 설정과 migration 상태는 현재 저장소 근거를 다시 확인한다.
- 과거 DB 환경값을 현재 사실처럼 복원하지 않는다.

### 5.10 Profile / Environment

- 실제 `application` 설정, Compose, CI, 현재 문서를 우선 확인한다.
- `.env`, `.envrc`, `.direnv` 본문을 요청하거나 출력하지 않는다.
- secret 값을 문서나 로그에 기록하지 않는다.
- 과거 환경값을 현재 값으로 확정하지 않는다.
- dev/test JWT static default는 별도 security Finding 후보로 취급하며 현재 TASK에서 임의 수정하지 않는다.

- 민감 키 assignment의 false-positive 예외는 normalized 전체 RHS가 `${NAME}` 또는 `<NAME>`인 structured placeholder인 경우로만 제한한다.
- `placeholder`, `dummy`, `example`, `changeme`, `sample`, `test`, `fake` 같은 일반 literal은 broad allowlist에 넣지 않으며 그 외 non-empty RHS는 보수적으로 민감 후보로 본다.

## 6. 코드와 파일 제공

- 승인된 논리 작업 단위로 변경을 제공한다.
- 강하게 연결된 파일은 같은 승인 단위 안에서 함께 다룰 수 있다.
- 사용자가 요청한 경우에만 한 파일씩 제공한다.
- 승인 범위를 넘어 파일을 임의로 묶지 않는다.
- 코드 주석은 코드 자체로 드러나지 않는 이유와 제약에 사용한다.
- 오래되거나 코드와 불일치하는 주석은 수정하거나 제거한다.

## 7. 상세 문서 인덱스

- 개발 lifecycle과 단계별 승인 게이트: `docs/process/DEVELOPMENT-WORKFLOW.md`
- TASK 상태와 순서: `docs/process/PORTFOLIO-ROADMAP.md`
- TASK 시작 입력: `docs/process/TASK-START-CHECKLIST.md`
- GPT 독립 검토: `docs/process/GPT-REVIEW-CONTRACT.md`
- TASK 학습·면접 검증: `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md`
- DB/Flyway 상세: `docs/db/README.md`
- 아키텍처 기준: `docs/architecture/`
- API 문서: `docs/api/`
