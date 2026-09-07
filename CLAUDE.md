# Claude Code 프로젝트 지침

## 문서 목적

이 문서는 `ticketing-reservation` 프로젝트에서 Claude Code가 작업을 시작할 때 읽는 짧은 진입 계약이다.
상세 규칙을 중복하지 않고 현재 TASK와 Phase의 승인 범위, 역할, 중단 원칙과 기준 문서만 연결한다.

## 작업 원칙

- 모든 프로젝트 작업은 TASK 단위로 진행한다.
- 작업 전 현재 TASK, Phase, 허용 작업, 금지 작업, baseline과 필수 입력자료를 확인한다.
- 확인되지 않은 저장소 상태, 기술 사실, 테스트 결과를 추정하지 않는다.
- 사용자 승인 없이 다음 주요 Phase나 다음 TASK로 자동 진행하지 않는다.
- 승인 범위 밖 문제는 현재 작업에 섞지 않고 별도 제안으로 보고한다.

## 역할

- Claude Code: 사용자가 승인한 범위의 구현과 검증을 수행한다.
- GPT: 제공된 근거를 독립적으로 검토한다.
- 사용자: 범위, 구현, 위험 수용, Git 작업과 다음 단계의 최종 승인자다.

## Critical

Critical Finding이 확인되면 영향받는 Phase를 중단한다.
해결하거나 사용자가 위험을 명시적으로 수용하기 전에는 해당 흐름을 계속하지 않는다.
상세 처리 절차는 `docs/process/GPT-REVIEW-CONTRACT.md`와 `docs/process/DEVELOPMENT-WORKFLOW.md`를 따른다.

## 기준 문서

- 공통 프로젝트 원칙과 Git 형식: `docs/process/PROJECT-RULES.md`
- TASK 상태와 순서: `docs/process/PORTFOLIO-ROADMAP.md`
- TASK 시작 입력과 안전한 자료 요청: `docs/process/TASK-START-CHECKLIST.md`
- GPT 독립 검토 계약: `docs/process/GPT-REVIEW-CONTRACT.md`
- 개발 lifecycle과 단계별 승인 게이트: `docs/process/DEVELOPMENT-WORKFLOW.md`
- TASK 학습·면접 검증 계약: `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md`

상세 기술 규칙, 리뷰 schema, 단계별 workflow와 학습 절차 전문은 위 기준 문서에서 관리한다.
