# TASK-060 Document Migration Matrix

## 1. 문서 목적

이 문서는 TASK-060에서 A/B/C 기존 규칙·문서 684개 항목이 승인된 최종 프로세스 문서로 어떻게 추적·이동되는지를 기록하는 근거 문서다.

- Phase 2-2C의 684개 row ID, 12-column schema, 출처 구분, 원문 핵심 내용과 분류 집계를 보존한다.
- 원본 `대응 상태`, `제안 상태`, `사용자 승인 필요`, `자동 검증 후보`는 Phase 2-2C provenance로 유지한다.
- Phase 2-3A·2-3B·2-6 및 후속 승인으로 해소된 `목표 문서`만 최종 승인 범위에 맞게 정규화한다.
- 목표가 정규화된 row는 기존 `제안 이유`를 삭제하지 않고 최종 결정 근거를 뒤에 추가한다.
- B-02 `.claude/settings.json`과 B-08 `docs/reviews/TASK-031/AGENT-EVALUATION.md`는 실제 파일을 수정하지 않는다. 해당 source의 프로세스 의미 또는 provenance만 승인된 TASK-060 문서에서 추적한다.

## 2. 고정 원본

| 그룹 | 고정 원본 | mapping row | 원본 SHA-256 |
| --- | --- | ---: | --- |
| A | `TASK-060-PHASE-2-2C-MATRIX-A.md` | 378 | `0500ece08521181282c99eef6396ee3e2d974dd7bec2c3bbf15560c5165b9d90` |
| B | `TASK-060-PHASE-2-2B-MATRIX-B.md` | 227 | `169c0864a2fd8c55de6513659a6ee5fa21e27e22d5c199f162b176c5222d7efe` |
| C | `TASK-060-PHASE-2-2C-MATRIX-C.md` | 79 | `2b40f5d41e90be057dde9c078c6c831932b9e600a5c2c83b5dabef2b84d17153` |
| 합계 | — | 684 | — |

## 3. 최종 target 정규화

아래 변경은 12-column schema를 바꾸지 않고 `목표 문서`와 그 근거인 `제안 이유` 안에서만 수행했다.
proposal/mapping/validation 분류값은 변경하지 않았다.

| 기존 목표 | 최종 목표 | row 수 | 근거 |
| --- | --- | ---: | --- |
| `미정(CFL-01)` | `docs/process/GPT-REVIEW-CONTRACT.md` | 3 | 최종 결정 5에 따라 핵심·보조 입력 누락 기준을 GPT review SSOT로 확정 |
| `미정(CFL-02)` | `docs/process/PORTFOLIO-ROADMAP.md` | 2 | 최종 결정 1에 따라 전체 TASK 이력·활성 구간·상태 기준 책임을 Roadmap으로 확정 |
| `미정(CFL-04)` | `docs/process/PROJECT-RULES.md` | 109 | 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 |
| `미정(CFL-07)` | `docs/process/GPT-REVIEW-CONTRACT.md` | 3 | 최종 결정 7에 따라 공통 Finding schema와 Severity SSOT를 GPT-REVIEW-CONTRACT로 확정 |
| `미정(CFL-08)` | `docs/process/DEVELOPMENT-WORKFLOW.md` | 2 | 최종 결정 15 및 Phase 3-2B 승인 보완에 따라 learning timing과 merge gate 시점을 lifecycle SSOT에서 관리 |
| `미정(CFL-09)` | `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md` | 1 | 최종 결정 16 및 Phase 3-2B 승인 보완에 따라 learning 산출물·최소 추적 기준을 Learning Contract로 확정 |
| `미정(CFL-12)` | `docs/process/PROJECT-RULES.md` | 2 | 최종 결정 10에 따라 branch naming 형식을 Git metadata SSOT인 PROJECT-RULES로 확정 |
| `.claude/settings.json` | `docs/process/TASK-START-CHECKLIST.md` | 1 | B-02 설정 파일 자체는 비변경으로 유지하고 민감 경로 자료 요청 금지 원칙은 TASK-START-CHECKLIST에서 추적 |
| `docs/reviews/TASK-031/AGENT-EVALUATION.md` | `docs/process/DOCUMENT-MIGRATION-MATRIX.md` | 28 | B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 |

- target 정규화 row: **151**
- 승인 범위 밖 target: **0**
- unresolved `미정(CFL-XX)` target: **0**

## 4. 684-row Migration Matrix

| 번호 | 출처 ID | 원문 장·절 | 원문 핵심 내용 | 현재 B 대응 위치 | 대응 상태 | 제안 상태 | 목표 문서 | 제안 이유 | 잃을 수 있는 정보·위험 | 사용자 승인 필요 | 자동 검증 후보 |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | A-01 | 서문 | 프로젝트·레포와 전체 로드맵 성격 | B-05 `문서 책임` | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 전체 이력과 검증 구간의 범위를 결정해야 함 (관련 #479, #480, #481) | 전체 이력 소실 또는 미검증 정보 혼입 | 예 | 수동 검토 |
| 2 | A-01 | 완료된 TASK / Phase 1 | TASK-001·002·007·008·010·015·019-1 완료 목록과 CS 연결 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 3 | A-01 | 완료된 TASK / Phase 2 | 회원·티켓팅·HOLD·예약·결제·환불 완료 목록 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 4 | A-01 | 완료된 TASK / Phase 2-1 | 락·N+1·Redis·캐시·동시성 완료 목록 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 5 | A-01 | 완료된 TASK / Phase 2-2 | JWT·로그아웃·RTR·소유권 검증 완료 목록 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 6 | A-01 | 완료된 TASK / Phase 2-3 | 에러코드·패키지·대기열·토큰·정리 정책 완료 목록 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 7 | A-01 | 완료된 TASK / Phase 3 | API·ERD·README·ADR·TPS·Redis 지침 완료 목록 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 8 | A-01 | 완료된 TASK / Phase 4-1 | 취소 정책·정합성·멱등성·Rate Limit 완료 목록 | B-05는 TASK-031만 확인 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거 확인 후 반영 범위를 결정해야 함 | 과거 이력 소실 또는 검증되지 않은 완료 상태 복원 | 예 | 자동 검증: TASK ID·상태·근거 필드 |
| 9 | A-01 | 진행 중인 TASK | 진행 중 표가 비어 있음 | B-05 `개발 프로세스 보강`은 TASK-059 진행 중 | 충돌 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 고정 main SHA가 TASK-059 squash merge commit이므로 현재 상태와 불일치 (관련 #486) / 최종 결정 2: TASK-059=완료, 근거 SHA `122816d91533befcc7a63c06cf83a7150e1fd05d` | 완료 TASK를 진행 중으로 오인해 후속 순서 왜곡 | 예 | 자동 검증: merge 근거와 로드맵 상태 대조 |
| 10 | A-01 | 남은 로드맵 / Phase 4-2 | TASK-032·033·033-1·028·043·034와 기간·CS 연결 | B-05 후속 순서에 034·기간·CS 연결 없음 | 부분 대응 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 선택 TASK·기간·CS 연결의 현행 유효성은 승인 필요 | 과거 계획 고정 또는 선택 TASK 소실 | 예 | 수동 검토 |
| 11 | A-01 | 남은 로드맵 / Phase 5 | Admin API·RBAC·비즈니스 규칙 계획 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 계획 유효성을 사용자 승인으로 재판정해야 함 | 향후 계획 소실 또는 오래된 계획 복원 | 예 | 수동 검토 |
| 12 | A-01 | 남은 로드맵 / Phase 6 | 테스트 전략·Testcontainers·동시성·E2E·Failover 계획 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 계획 유효성을 사용자 승인으로 재판정해야 함 | 향후 계획 소실 또는 오래된 계획 복원 | 예 | 수동 검토 |
| 13 | A-01 | 남은 로드맵 / Phase 7 | 로그·모니터링·성능 측정·k6 계획 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 계획 유효성을 사용자 승인으로 재판정해야 함 | 향후 계획 소실 또는 오래된 계획 복원 | 예 | 수동 검토 |
| 14 | A-01 | 남은 로드맵 / Phase 8 | 배포 ADR·AWS·CI/CD·재측정·Next.js 계획 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 계획 유효성을 사용자 승인으로 재판정해야 함 | 향후 계획 소실 또는 오래된 계획 복원 | 예 | 수동 검토 |
| 15 | A-01 | 다음 작업 순서 | TASK-032→033→033-1→028→043 | B-05 `후속 진행 순서` | 완전 대응 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 동일 순서와 승인 조건이 존재 (관련 #487) | 중복 기록 시 순서 드리프트 | 아니오 | 자동 검증: 순서 목록 일치 |
| 16 | A-01 | CS 주차 ↔ TASK 연결 맵 | CS 주차·TASK·면접 포인트 연결 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 로드맵에 유지할지 학습 문서로 이동할지 결정 필요 | 면접 연결 정보 소실 또는 로드맵 비대화 | 예 | 수동 검토 |
| 17 | A-01 | TASK 학습 종료 기준 | 문제·한계·대안·선택·위험·테스트·확장 한계 7문항 | B-05 동일 절, C-01 상세 계약 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 상세 기준을 학습 계약에 두고 로드맵은 참조하는 후보 (관련 #490, #608, #618, #680, #681, #682, #683, #684) | 중복본 기준 불일치 | 예 | 자동 검증: 7개 질문 존재 |
| 18 | A-02 | 서문 / 문서 책임 | 프로젝트 전용 작업 절차와 진입 규칙 | B-01 `문서 목적`, B-04 `문서 책임` | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 진입 문서와 상세 workflow 책임을 분리해야 함 (관련 #43, #315, #379, #435) | 절차 기준 분산 | 예 | 수동 검토 |
| 19 | A-02 | 서문 / 기준 문서 분리 | 공통 원칙·기술 규칙·로드맵·체크리스트의 기준을 분리 | B-01 `기준 문서`, B-04 `문서 참조 순서` | 부분 대응 | 병합 | CLAUDE.md | 짧은 진입점에서 기준 문서 역할만 안내 | 기준 우선순위 혼선 | 예 | 자동 검증: 참조 경로 존재 |
| 20 | A-02 | 1. 프로젝트 개요 / 목적 | 금융·결제 백엔드 이직용 포트폴리오 | B-01 `프로젝트 목적` | 완전 대응 | 유지 | CLAUDE.md | 현재 목적이 충분히 존재 | 목적 문구 중복 | 아니오 | 수동 검토 |
| 21 | A-02 | 1. 프로젝트 개요 / 학습 목표 | 정합성·트랜잭션·동시성·Redis·결제 실패·테스트 근거 | B-01 `프로젝트 목적` 일부 | 부분 대응 | 병합 | CLAUDE.md | 진입 문서에는 압축된 목표만 유지 | 세부 학습 목표 소실 또는 CLAUDE 비대화 | 예 | 수동 검토 |
| 22 | A-02 | 1. 프로젝트 개요 / 협업 흐름 | 두 계정으로 Issue→branch→PR→review→merge 재현 | B-04 `프로젝트 작업 형식` | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 계정은 PROJECT-RULES, 단계는 workflow로 분리 | 계정·단계 중복 | 예 | 자동 검증: 계정·Reviewer 값 일치 |
| 23 | A-02 | 2. 기술 스택 | Java 17·Spring Boot 3.4.3·JPA·MariaDB·Redis·Flyway·Docker·Gradle·Actions | B-04는 README 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | TASK-060 목표 문서 중 기술 스택 단일 위치를 결정해야 함 (관련 #135, #435, #560) / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 버전 드리프트 또는 중복 | 예 | 수동 검토 |
| 24 | A-02 | 2. 미확인 정보 금지 | 확인되지 않은 버전·도구를 추측해 추가하지 않음 | B-01 `범위 통제`, B-04 `TASK 범위 통제` | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 교차 사실성 규칙으로 단일화 | 추측 정보 혼입 | 예 | 자동 검증: 금지 문구 존재 |
| 25 | A-02 | 3. 환경 정보 / 포트·DB | dev·test·CI DB와 Redis 포트·DB명 | B-04는 README 참조 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 설정과 대조할 단일 위치가 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 환경값 노후화 | 예 | 수동 검토 |
| 26 | A-02 | 3. 환경 정보 / dev DB명 설명 | ticketing_flyway는 실제 이름이라는 설명 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 설정 확인 후 유지 여부 판단 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 오타로 오인하거나 오래된 값 복원 | 예 | 수동 검토 |
| 27 | A-02 | 3. 환경 정보 / direnv | 환경변수를 direnv로 자동 로드 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 환경 사실 확인이 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 로컬 환경 가정 오류 | 예 | 수동 검토 |
| 28 | A-02 | 3. 환경 정보 / 민감정보 | 비밀번호·토큰·API Key·개인정보 기록 금지 | B-04 `민감정보 처리` | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 더 상세한 규칙 존재 | 중복 규칙 드리프트 | 아니오 | 자동 검증: 금지 문자열 |
| 29 | A-02 | 3. 환경 정보 / 실제 설정 우선 | 환경값은 설정·Compose 확인 후 판단 | B-04 `문서 참조 순서`, B-06 유형별 자료 | 부분 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 환경 관련 TASK의 입력 원칙으로 통합 | 추정 환경값 사용 | 예 | 수동 검토 |
| 30 | A-02 | 4. 필수 참조 문서 / 로드맵 | PORTFOLIO-ROADMAP의 TASK 순서·상태 책임 | B-01 `기준 문서` | 완전 대응 | 유지 | CLAUDE.md | 현재 참조가 존재 | 경로 드리프트 | 아니오 | 자동 검증: 문서 링크 |
| 31 | A-02 | 4. 필수 참조 문서 / 기술 규칙 | PROJECT-RULES의 상세 기술 규칙 책임 | B-04 책임과 충돌 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 상세 기술 기준 단일 위치 결정 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 기술 기준 공백 | 예 | 수동 검토 |
| 32 | A-02 | 4. 필수 참조 문서 / 시작 체크리스트 | TASK 유형별 시작 자료 책임 | B-01·B-06 | 완전 대응 | 유지 | CLAUDE.md | 현재 참조가 존재 | 경로 드리프트 | 아니오 | 자동 검증: 문서 링크 |
| 33 | A-02 | 4. 필수 참조 문서 / GPT 지침 | GPT 리뷰 별도 기준 | B-01·B-07 | 완전 대응 | 유지 | CLAUDE.md | 현재 계약 문서 존재 | 경로 드리프트 | 아니오 | 자동 검증: 문서 링크 |
| 34 | A-02 | 4. 필수 참조 문서 / docs | API·DB·ADR·시퀀스·개발 기록 | B-04 `문서 참조 순서` 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 패킷에 확인된 경로만 참조 후보로 기록 | 깨진 경로 또는 누락된 영역 | 예 | 자동 검증: 문서 링크 |
| 35 | A-02 | 4. 필수 참조 문서 / Incident Log | 장애·버그·재발 방지 기록, 없으면 생성 제안 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 장애 발생 TASK에 조건부 기록 여부 결정 (관련 #128) | 장애 지식 소실 또는 불필요 문서 생성 | 예 | 수동 검토 |
| 36 | A-02 | 4. 충돌 처리 | 중복·충돌 시 임의 선택 금지, 위치·차이 보고 | B-04 `문서 충돌 처리` | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 목적과 내용이 충분히 존재 | 중복 문구 | 아니오 | 자동 검증: 충돌 처리 필수 장 |
| 37 | A-02 | 5. 작업 시작 / 로드맵 확인 | 현재 위치와 선행 TASK 확인 | B-06 `1. TASK와 로드맵 확인` | 완전 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | workflow의 첫 단계로 단일화 | 선행 조건 누락 | 예 | 자동 검증: workflow 단계 존재 |
| 38 | A-02 | 5. 작업 시작 / 현재 단계 | 오늘 TASK와 Dev Loop 단계 확인 | B-01·B-06 | 완전 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 단계 확인 절차를 단일화 | 허용 범위 오판 | 예 | 자동 검증: 현재 단계 필드 |
| 39 | A-02 | 5. 작업 시작 / 자료 요청 | 체크리스트 기준 필요한 파일·명령 결과 요청 | B-06 | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 자료 기준의 단일 위치 | 고정 경로 재도입 | 예 | 자동 검증: 필수 schema 필드 |
| 40 | A-02 | 5. 작업 시작 / 실제 확인 | 관련 코드·설정·테스트·문서를 실제 확인 | B-01 `작업 시작 규칙` | 완전 대응 | 유지 | CLAUDE.md | 현재 진입 원칙에 존재 | 자료를 받기만 하고 미확인 | 아니오 | 수동 검토 |
| 41 | A-02 | 5. 작업 시작 / 확인 완료 문구 | 고정 문구 출력 후 Issue·설계 시작 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 고정 문구보다 자료 확인 결과를 구조화하는 후보 | 형식 강제로 실제 확인이 가려질 수 있음 | 예 | 수동 검토 |
| 42 | A-02 | 5. 작업 시작 / 자료 부족 | 자료 부족 시 추측 진행 금지 | B-01·B-06·B-07 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 공통 사실성·중단 원칙으로 단일화 | 추정 구현 | 예 | 자동 검증: 금지 문구 존재 |
| 43 | A-02 | 6. AI 개발 흐름 / 단계 목록 | 0~11 TASK 선정·Issue·리뷰·설계·구현·검증·Git·회고 | B-01·B-06·B-07에 분산 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 전체 lifecycle의 단일 기준 필요 (관련 #18, #315, #379, #435) (관련 #547, #548, #549, #550, #551, #552) | 단계 누락·자동 진행 | 예 | 자동 검증: 필수 단계 집합 |
| 44 | A-02 | 6. AI 개발 흐름 / 단계 보고 | 현재 단계·가능 여부·차단 문제·다음 단계 보고 | B-01·B-07 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 현재 단계와 승인 범위를 구조화해야 함 | 잘못된 다음 단계 안내 | 예 | 자동 검증: 필수 보고 필드 |
| 45 | A-02 | 7. 아키텍처 / 패키지 | 도메인형 패키지 구조 | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 46 | A-02 | 7. 아키텍처 / 호출 방향 | api→application→domain | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 47 | A-02 | 7. 아키텍처 / Controller | 요청·응답 변환만 담당 | B-04는 주제별 문서 참조만 제공 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 48 | A-02 | 7. 아키텍처 / Service | 비즈니스 로직·트랜잭션 관리 | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 49 | A-02 | 7. 아키텍처 / Repository | 인터페이스는 domain 패키지 | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 50 | A-02 | 7. 아키텍처 / Infra | Redis·외부 연동 구현체는 infra | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 51 | A-02 | 7. 아키텍처 / Entity 반환 | Entity를 API 응답으로 직접 반환 금지 | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 52 | A-02 | 7. 아키텍처 / 공통 응답 | ApiResponse<T>·ErrorResponse 사용 | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 53 | A-02 | 7. 아키텍처 / 비즈니스 예외 | BusinessException·도메인 ErrorCode 사용 | B-04는 주제별 문서 참조만 제공 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | A-03 상세 규칙과 실제 구조를 대조한 뒤 위치 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 기술 규칙 강제 또는 기준 공백 | 예 | 수동 검토 |
| 54 | A-02 | 7. 새 구조 도입 제한 | 새 패턴을 임의 도입하지 않고 기존 코드·규칙 우선 | B-04 `TASK 범위 통제` | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 범위 통제 규칙으로 단일화 | 임의 아키텍처 확장 | 예 | 자동 검증: 금지 문구 존재 |
| 55 | A-02 | 8. 금지 / Controller→Repository | Controller의 Repository 직접 호출 금지 | B 그룹 기술 본문 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 필수 장·절 |
| 56 | A-02 | 8. 금지 / Transactional 위치 | Controller·Repository의 @Transactional 금지 | B-03·B-07은 검토 범위만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 금지 패턴 |
| 57 | A-02 | 8. 금지 / Service Entity 반환 | Service의 Entity 직접 반환 금지 | B 그룹 기술 본문 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 필수 장·절 |
| 58 | A-02 | 8. 금지 / RuntimeException | 비즈니스 예외에 RuntimeException 직접 사용 금지 | B 그룹 기술 본문 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 필수 장·절 |
| 59 | A-02 | 8. 금지 / 통합 테스트 Transactional | 실제 commit·lock을 숨기는 @Transactional 금지 | B 그룹 기술 본문 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 필수 장·절 |
| 60 | A-02 | 8. 금지 / Redis Key | 문서 없는 Key 패턴 임의 추가 금지 | B-06은 Key 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 금지 패턴 |
| 61 | A-02 | 8. 금지 / Flyway | 기존 migration 파일 수정 금지 | B-06은 수정 여부 자료 요구 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 금지 패턴 |
| 62 | A-02 | 8. 금지 / 범위 밖 추가 | 요청하지 않은 기능·API·샘플·의존성 추가 금지 | B-04 `TASK 범위 통제` | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 금지 패턴 |
| 63 | A-02 | 8. 금지 / 테스트 허위 보고 | 실행하지 않고 성공 보고 금지 | B-04 `테스트 결과 보고` | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 금지 패턴 |
| 64 | A-02 | 8. 금지 / Critical 상태 진행 | 정합성·동시성·핵심 테스트 문제에서 다음 단계 금지 | B-04·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 교차 작업 규칙과 상세 기술 규칙의 위치를 분리해야 함 | 금지 기준 소실 또는 중복 | 예 | 자동 검증: 금지 패턴 |
| 65 | A-02 | 9. 전체 테스트 명령 | `./gradlew clean test -Dspring.profiles.active=test` | B-04는 옵션이 추가된 명령 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 현재 단일 테스트 명령을 결정해야 함 | 서로 다른 검증 환경 | 예 | 자동 검증: 전체 테스트 명령 단일성 |
| 66 | A-02 | 9. 기본 Git 확인 | status·diff stat·diff name-only | B-04·B-06 일부 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 단계별 읽기 전용 확인 명령으로 배치 | 불필요한 출력 또는 상태 누락 | 예 | 자동 검증: 허용 명령 목록 |
| 67 | A-02 | 9. 관련 테스트 우선 | 관련 테스트 후 가능한 경우 전체 테스트 | B-04는 실행 결과 보고 중심 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 변경 유형별 테스트 순서를 정의해야 함 | 전체 테스트만으로 국소 실패 원인 불명확 | 예 | 수동 검토 |
| 68 | A-02 | 9. 의존 환경 확인 | MariaDB·Redis 필요 여부를 먼저 확인 | B-04 `테스트 결과 보고` | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 규칙에 존재 | 중복 | 아니오 | 자동 검증: 필수 보고 필드 |
| 69 | A-02 | 9. 테스트 수 보고 | 확인 가능할 때 성공·실패·스킵 수 보고, 추측 금지 | B-04 실제 결과 보고 | 부분 대응 | 병합 | docs/process/PROJECT-RULES.md | 결과 보고 schema로 통합 | 추측된 테스트 수 | 예 | 자동 검증: 결과 필드 |
| 70 | A-02 | 9. 실행 명령 실제 설정 우선 | 앱·Compose·Redis 명령은 저장소 설정 확인 후 사용 | B-04·B-06 일반 원칙 | 부분 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 환경 자료 확인 규칙으로 통합 | 잘못된 실행 명령 | 예 | 수동 검토 |
| 71 | A-02 | 10. Issue 제목 | `TASK-XXX 작업 내용 요약` | B-04 `프로젝트 작업 형식` | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 존재 (관련 #286, #440) | 중복본 드리프트 | 아니오 | 자동 검증: 정규식 형식 |
| 72 | A-02 | 10. Issue 본문 | Goal→Background→Scope→DoD→Test→Notes | B-04 동일 순서 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 존재 (관련 #285, #441) | 중복본 드리프트 | 아니오 | 자동 검증: 필수 장·절 |
| 73 | A-02 | 10. Assignee | pil97 | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 값 존재 | 계정 드리프트 | 아니오 | 자동 검증: 필수 값 |
| 74 | A-02 | 10. 라벨 목록 | feature·security·db·infra·chore·test·documentation·bug | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 라벨 유효성 확인 후 단일 기준화 | 오래된 라벨 강제 또는 분류 정보 소실 | 예 | 수동 검토 |
| 75 | A-02 | 10. Issue 검토 / 기술 선택 | 왜 이 방식인지 설명 가능해야 함 | B-07 ISSUE_REVIEW·C-01 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | Issue 검증 질문으로 통합 | 학습과 리뷰 목적 혼동 | 예 | 수동 검토 |
| 76 | A-02 | 10. Issue 검토 / 실무·포트폴리오 | 실무 문제와 포트폴리오 포인트 확인 | B-07 직접 문구 없음 | 대응 없음 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | Issue 품질 기준으로 유지 여부 결정 | 범위 밖 과장 유도 | 예 | 수동 검토 |
| 77 | A-02 | 10. Issue 검토 / 검증 가능성 | DoD·Test가 검증 가능한지 확인 | B-07 ISSUE_REVIEW | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 목적이 존재 | 중복 | 아니오 | 자동 검증: ISSUE_REVIEW 필수 필드 |
| 78 | A-02 | 10. Issue 검토 / 면접 방어 | 선택 이유와 한계를 설명할 수 있는지 확인 | C-01 상세 계약 | 부분 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | Issue 리뷰와 학습 완료를 구분 | Issue 단계 과도한 부담 | 예 | 수동 검토 |
| 79 | A-02 | 11. 구현 전 출력 / 요구사항 | 요구사항 이해 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 80 | A-02 | 11. 구현 전 출력 / 현재 구조 | 현재 구조와 관련 코드 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 81 | A-02 | 11. 구현 전 출력 / 핵심 문제 | 핵심 문제 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 82 | A-02 | 11. 구현 전 출력 / 변경 파일 | 생성·수정 파일 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 83 | A-02 | 11. 구현 전 출력 / 계층별 변경 | 계층별 변경 내용 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 84 | A-02 | 11. 구현 전 출력 / 영향 | 기존 기능 영향 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 85 | A-02 | 11. 구현 전 출력 / 대안 | 설계 대안과 트레이드오프 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 86 | A-02 | 11. 구현 전 출력 / 추천 | 추천안과 이유 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 87 | A-02 | 11. 구현 전 출력 / 테스트 | 테스트 계획 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 88 | A-02 | 11. 구현 전 출력 / 완료 | 완료 기준 | B-01은 설계 승인만 규정 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 설계 승인 패킷의 필드로 유지 여부 결정 | 승인 근거 누락 또는 과도한 형식 | 예 | 자동 검증: 필수 schema 필드 |
| 89 | A-02 | 11. 사용자 승인 후 구현 | 10개 항목 제시 후 사용자 승인 | B-01·B-04 승인 게이트 | 완전 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | workflow 단일 게이트로 통합 | 승인 없는 구현 | 예 | 자동 검증: 승인 상태 필드 |
| 90 | A-02 | 11. 작업 단위 | 사용자가 원하면 한 파일씩, 강결합 파일은 승인 범위에서 묶음 | A-04는 항상 한 파일씩 | 충돌 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 논리 작업 단위 기준으로 조정 (관련 #254, #272) | 문맥 단절 또는 사용자 검토권 약화 | 예 | 수동 검토 |
| 91 | A-02 | 12. 작업자 관점 설명 | 파일 역할·변경 이유·핵심 구현·계층 영향 설명 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 변경 설명의 필수 필드 여부 결정 | 설명 부족 또는 반복 | 예 | 수동 검토 |
| 92 | A-02 | 12. 개선 제안 | 대안이 있을 때만 현재·대안·트레이드오프·추천 | B-01 범위 밖 제안 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 현재 TASK 결함과 선택 개선을 분리 | 범위 확장 | 예 | 수동 검토 |
| 93 | A-02 | 12. 생략 코드 금지 | 적용 불가능한 생략 코드·`나머지는 동일` 금지 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 실제 적용 가능한 산출물 원칙으로 재표현 | 불완전 코드 제공 | 예 | 수동 검토 |
| 94 | A-02 | 13. 리뷰 항목 순서 | 요구사항→정합성→트랜잭션→동시성→보안→예외→성능→레이어→테스트→복잡도 | B-03·B-07 일부 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 검토 범주와 우선순위로 통합 | 범주 드리프트 | 예 | 자동 검증: 검토 범주 집합 |
| 95 | A-02 | 13. Severity | Critical·Warning·Info 정의 | B-04·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 텍스트 enum 단일 기준 (관련 #337, #473, #573) | 심각도 불일치 | 예 | 자동 검증: 허용 enum |
| 96 | A-02 | 13. 중요 문제 우선 | 한 번에 가장 중요한 문제 3개 먼저 제시 | B는 중요 위험 우선만 규정 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 상위 3개 요약과 전체 Finding 보존으로 조정 (관련 #340) | 4번째 이후 문제 은폐 | 예 | 자동 검증: 요약과 전체 Finding 개수 대조 |
| 97 | A-02 | 13. Critical 우선 | Critical이 있으면 차단 사유·수정 포인트 우선 | B-04·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 우선 처리 규칙 (관련 #339, #474) | Critical 지연 | 예 | 자동 검증: Critical 선행 배치 |
| 98 | A-02 | 14. REVIEW_REQUEST 형식 | TASK·단계·요약·첨부·참조 | B-07 단계별 필수 자료 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 단계별 입력 schema와 통합 (관련 #234, #260, #261, #262, #263, #573) | 이중 포맷·입력 누락 | 예 | 자동 검증: 필수 schema 필드 |
| 99 | A-02 | 14. Critical 처리 | Critical 즉시 수정 | B-04는 사용자 판단 대기, B-07은 권고 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 자동 수정 대신 사용자 승인 후 조치로 변경 | GPT 권고의 자동 실행 | 예 | 자동 검증: 사용자 승인 문구 |
| 100 | A-02 | 14. Warning 처리 | 사용자와 수정 여부 결정 | B-04·B-07 역할과 일치 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 최종 결정권 보존 | 중복 | 아니오 | 자동 검증: 사용자 결정 필드 |
| 101 | A-02 | 14. Info 처리 | 참고 사항으로 처리 | B-04·B-07 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 enum과 일치 | 중복 | 아니오 | 자동 검증: 허용 enum |
| 102 | A-02 | 14. 진행 금지 후 재검토 | 수정 후 재검토 | B-07 권고·공통 원칙 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 재검토는 승인된 workflow 단계로 연결 | 자동 재실행 | 예 | 자동 검증: 재검토 상태 전이 |
| 103 | A-02 | 15. DoD 체크 | Issue 완료 조건 충족 확인 | B-04 Git·B-07 PR_REVIEW | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 커밋 게이트 필드로 통합 (관련 #250, #278) | 미완료 커밋 | 예 | 자동 검증: DoD 체크 필드 |
| 104 | A-02 | 15. 관련 테스트 | 관련 테스트 통과 확인 | B-04 테스트 결과 보고 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 커밋 게이트에 실제 결과 연결 | 미검증 커밋 | 예 | 자동 검증: 테스트 결과 필드 |
| 105 | A-02 | 15. 전체 테스트 | 가능한 경우 전체 테스트 통과 | B-04 전체 테스트 명령 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 미실행 사유를 허용하는 조건부 게이트로 명확화 | 불필요한 차단 또는 검증 공백 | 예 | 자동 검증: 전체 테스트 결과 또는 미실행 사유 |
| 106 | A-02 | 15. 수동 테스트 | 필요한 API 시나리오 확인 | B-07 PR_REVIEW는 결과 또는 미실행 사유 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 변경 유형에 맞는 수동 검증으로 조정 (관련 #252, #280) | 비API TASK에 불필요한 강제 | 예 | 자동 검증: 수동 검증 결과 또는 사유 |
| 107 | A-02 | 15. README·문서 | 변경 시 관련 문서 업데이트, 불필요 시 사유 | B-04 문서 참조·충돌 처리 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 영향받는 기준 문서로 일반화 (관련 #253, #281) | README 과잉 수정 또는 문서 누락 | 예 | 자동 검증: 문서 영향 결과 또는 사유 |
| 108 | A-02 | 15. 미완료 시 커밋 금지 | 미완료 항목이 있으면 커밋 명령 미제공 | B-04 Git 승인 | 완전 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 커밋 상태 전이로 통합 | 미완료 커밋 | 예 | 자동 검증: 게이트 상태 |
| 109 | A-02 | 16. 이슈 단위 브랜치 | 브랜치는 이슈 단위로 생성 | B-04 작업 형식 | 부분 대응 | 병합 | docs/process/PROJECT-RULES.md | TASK·Issue 추적 단일 기준 | Issue 없는 브랜치 | 예 | 자동 검증: 브랜치-TASK 형식 |
| 110 | A-02 | 16. 브랜치 이름 | `{prefix}/TASK-XXX-간단요약` | B-04는 english-kebab-case | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 요약 언어·형식을 결정해야 함 / 최종 결정 10에 따라 branch naming 형식을 Git metadata SSOT인 PROJECT-RULES로 확정 | 브랜치 regex 불일치 | 예 | 자동 검증: 정규식 형식 |
| 111 | A-02 | 16. prefix | feat·fix·refactor·test·docs·chore·build | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 집합 존재 | 중복 | 아니오 | 자동 검증: 허용 enum |
| 112 | A-02 | 16. 서브태스크 | TASK-XXX-N 형식을 브랜치에도 사용 | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 서브태스크 표준을 명시할지 결정 | 추적성 소실 | 예 | 자동 검증: 정규식 형식 |
| 113 | A-02 | 16. 브랜치 삭제 | merge 후 로컬·원격 브랜치 삭제 | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | merge 후 정리 단계로 둘지 결정 | 불필요 브랜치 누적 또는 조사 이력 소실 | 예 | 자동 검증: 원격·로컬 브랜치 존재 |
| 114 | A-02 | 16. 기준 브랜치 | 기준 브랜치는 main | B-04·B-06 암묵적 | 부분 대응 | 유지 | docs/process/PROJECT-RULES.md | 기준점 명시 유지 | 기준 브랜치 오판 | 아니오 | 자동 검증: 필수 값 |
| 115 | A-02 | 17. 커밋 제목 | `{prefix}(TASK-XXX): 변경 대상과 결과` | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 존재 (관련 #224, #443) | 중복 | 아니오 | 자동 검증: 정규식 형식 |
| 116 | A-02 | 17. TASK 스코프 필수 | 모든 커밋에 TASK 번호, 없으면 로드맵 등록 | B-04 제목 형식은 TASK 포함 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 예외 없는 강제와 TASK 등록 절차를 분리 판단 | 잡무 커밋 추적성 또는 불필요 TASK 생성 | 예 | 자동 검증: 커밋 regex |
| 117 | A-02 | 17. 서브태스크 스코프 | TASK-XXX-N 허용 | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 서브태스크 형식 명시 여부 결정 | 추적성 불일치 | 예 | 자동 검증: 커밋 regex |
| 118 | A-02 | 17. squash PR suffix | `(#PR번호)` suffix 허용 | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | squash merge 결과와 정규식 호환 필요 | 정상 merge commit 오탐 | 예 | 자동 검증: 커밋 regex |
| 119 | A-02 | 17. 커밋 본문 | Why·What·Note 선택 사용 | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 복잡한 변경에 조건부 사용 후보 | 형식 과잉 또는 이유 소실 | 예 | 수동 검토 |
| 120 | A-02 | 17. 커밋 언어 | 기본 한국어 | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 프로젝트 표준 여부 결정 | 다국어 일관성 저하 | 예 | 수동 검토 |
| 121 | A-02 | 17. AI 서명 금지 | Co-Authored-By 등 AI 서명 금지 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 커밋 메타데이터 정책으로 유지 여부 결정 | 원치 않는 서명 또는 출처 정보 소실 | 예 | 자동 검증: 금지 문자열 |
| 122 | A-02 | 17. 논리적 커밋 단위 | 커밋을 논리 작업 단위로 분리 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 품질 원칙이나 자동 판정은 어려움 | 거대 커밋 또는 과도한 분할 | 예 | 수동 검토 |
| 123 | A-02 | 18. PR 제목 | `{prefix}(TASK-XXX): 요약` | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 존재 | 중복 | 아니오 | 자동 검증: 정규식 형식 |
| 124 | A-02 | 18. PR 본문 | What·Why·How·Test·Notes·closes | B-04는 본문 순서 미상세 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 상세 template 위치를 결정해야 함 | PR 근거 소실 또는 중복 | 예 | 자동 검증: 필수 장·절 |
| 125 | A-02 | 18. PR 제공 세트 | 제목·라벨·본문·DoD·Test·작업자·리뷰어 코멘트 | B-04는 일부 메타만 존재 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | PR 생성 단계 산출물로 분리 (관련 #251, #294) | 불필요한 코멘트 강제 또는 정보 누락 | 예 | 자동 검증: 필수 schema 필드 |
| 126 | A-02 | 18. 작업 계정·Reviewer | pil97·seungpil97 | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 값 존재 | 계정 드리프트 | 아니오 | 자동 검증: 필수 값 |
| 127 | A-02 | 18. hotfix도 PR | hotfix도 PR 경유, 긴급 대응은 협의 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 긴급 예외와 승인 경로를 명확히 해야 함 | 직접 반영 또는 긴급 대응 지연 | 예 | 수동 검토 |
| 128 | A-02 | 19. 장애 기록 | 장애·버그·예측 밖 동작을 Incident Log에 사실 기반 기록 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 장애 발생 시 조건부 기록으로 검토 (관련 #35) | 장애 지식 소실 또는 모든 TASK 문서화 | 예 | 자동 검증: 조건 충족 시 필수 장·절 |
| 129 | A-02 | 20. devlog 회고 | PR merge 후 docs/devlog에 구현·문제·면접 질문 기록 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 대표·고위험·학습 TASK 조건부 여부 결정 (관련 #283) | 문서 폭증 또는 회고 소실 | 예 | 수동 검토 |
| 130 | A-02 | 21. TASK 완료 보고 | 변경·검증·요구사항 대조·남은 위험 보고 | B-04·B-07 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 완료 보고 schema로 재정의 | 완료 근거 누락 | 예 | 자동 검증: 필수 schema 필드 |
| 131 | A-02 | 21. 다음 채팅 시작 문구 | 완료 시 `[TASK-XXX 이슈 작성해줘]` 강제 | B-01은 다음 TASK 자동 진행 금지 | 충돌 | 제외 | docs/process/DEVELOPMENT-WORKFLOW.md | 요청 시 제공으로 대체 후보 (관련 #284) | 채팅 전환 편의 소실 | 예 | 검증 불필요 |
| 132 | A-02 | 22. 문서 변경 트리거 | Redis·패키지·테스트·응답·리뷰·로드맵·Git 형식 변경 시 관련 기준 수정 | B-04 직접 목록 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현재 목표 문서 책임에 맞춰 트리거 재작성 | 문서 드리프트 | 예 | 자동 검증: 변경 파일과 동반 문서 |
| 133 | A-02 | 22. 문서별 책임 | CLAUDE=진입, PROJECT-RULES=기술, ROADMAP=순서 | B-01·B-04와 일부 충돌 | 충돌 | 수정 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 최종 문서 책임 승인 후 기록 | 책임 중복·공백 | 예 | 수동 검토 |
| 134 | A-02 | 22. 최종 수정 날짜 | 문서 수정 시 상단 날짜 갱신 | B 문서에는 날짜 형식 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | Git 이력과 날짜 표기 중 어느 기준을 쓸지 결정 | 날짜 노후화·불필요 수동 작업 | 예 | 자동 검증: 날짜 필드 존재 |
| 135 | A-03 | 서문 / 공통 기준 | Claude와 GPT가 공통 참조하는 프로젝트 규칙 | B-04는 교차 작업만 책임 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 상세 기술 기준의 단일 위치를 결정해야 함 (관련 #23, #435, #560) / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 기술 기준 공백 또는 중복 | 예 | 수동 검토 |
| 136 | A-03 | 서문 / 변경 단일화 | 규칙 변경 시 이 파일만 수정하고 프롬프트는 참조 | B-01·B-04 참조 구조 일부 | 부분 대응 | 수정 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 최종 단일 기준과 참조 구조를 결정 기록 | 복제 문구 드리프트 | 예 | 자동 검증: 중복 규칙 문구 |
| 137 | A-03 | 프로젝트 개요 / 목적 | Spring Boot 티켓팅 포트폴리오 | B-01 목적 | 완전 대응 | 유지 | CLAUDE.md | 현재 목적 존재 | 중복 | 아니오 | 수동 검토 |
| 138 | A-03 | 프로젝트 개요 / 레포 | 저장소 식별자 | B 그룹 직접 동일 문구 없음 | 부분 대응 | 수정 | CLAUDE.md | 진입 문서에 저장소명만 유지할지 결정 | 원격 식별 혼선 | 예 | 수동 검토 |
| 139 | A-03 | 프로젝트 개요 / 기술 스택 | Java·Spring·JPA·MariaDB·Redis 등 | B-04 README 참조 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 스택 단일 위치 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 버전 드리프트 | 예 | 수동 검토 |
| 140 | A-03 | 프로젝트 개요 / 계정 흐름 | 두 계정의 Issue→branch→PR→review→merge | B-04 계정·Reviewer | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 단계와 계정을 분리해 참조 | 중복 | 예 | 자동 검증: 계정 값 일치 |
| 141 | A-03 | 프로젝트 개요 / 로드맵 기준 | PORTFOLIO-ROADMAP 기준 | B-01·B-06 | 완전 대응 | 유지 | CLAUDE.md | 현재 참조 존재 | 경로 드리프트 | 아니오 | 자동 검증: 문서 링크 |
| 142 | A-03 | 환경 설정 | dev·test·CI DB와 Redis 값 | B-04 README 참조 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 설정과 대조할 단일 위치 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 환경값 노후화 | 예 | 수동 검토 |
| 143 | A-03 | 패키지 구조 / 루트 | `com.pil97.ticketing` 도메인형 구조 | B-04 아키텍처 문서 참조 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 코드 확인 전 자동 복원 금지 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 구조 강제 또는 기준 공백 | 예 | 수동 검토 |
| 144 | A-03 | 패키지 규칙 / 새 도메인 | 새 도메인은 api·application·domain 구조 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 단일 기준화 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 패키지 규칙 공백 또는 과거 구조 복원 | 예 | 수동 검토 |
| 145 | A-03 | 패키지 규칙 / Repository | Repository 인터페이스는 domain | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 단일 기준화 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 패키지 규칙 공백 또는 과거 구조 복원 | 예 | 자동 검증: 패키지 경로 규칙 |
| 146 | A-03 | 패키지 규칙 / 외부 연동 | 외부 연동 구현체는 infra | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 단일 기준화 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 패키지 규칙 공백 또는 과거 구조 복원 | 예 | 수동 검토 |
| 147 | A-03 | 패키지 규칙 / common | 응답·예외·에러코드는 common | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 단일 기준화 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 패키지 규칙 공백 또는 과거 구조 복원 | 예 | 수동 검토 |
| 148 | A-03 | 레이어 규칙 / 호출 방향 | api→application→domain 단방향 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 레이어 위반 판정 공백 | 예 | 자동 검증: 정적 패키지·의존성 규칙 |
| 149 | A-03 | 레이어 규칙 / Controller→Repository | 직접 호출 금지 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 레이어 위반 판정 공백 | 예 | 자동 검증: 정적 패키지·의존성 규칙 |
| 150 | A-03 | 레이어 규칙 / Controller 역할 | 요청·응답 변환만 담당 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 레이어 위반 판정 공백 | 예 | 자동 검증: 정적 패키지·의존성 규칙 |
| 151 | A-03 | 레이어 규칙 / Service 반환 | Entity 직접 반환 금지 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 레이어 위반 판정 공백 | 예 | 자동 검증: 정적 패키지·의존성 규칙 |
| 152 | A-03 | 레이어 규칙 / Repository 위치 | domain 패키지 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 레이어 위반 판정 공백 | 예 | 자동 검증: 정적 패키지·의존성 규칙 |
| 153 | A-03 | 레이어 규칙 / 외부 연동 위치 | Redis·외부 API 구현체는 infra | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 실제 구조와 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 레이어 위반 판정 공백 | 예 | 자동 검증: 정적 패키지·의존성 규칙 |
| 154 | A-03 | 레이어 위반 / 직접 주입 | Controller가 Repository 직접 주입·호출하면 위반 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 정적 검사가 가능한 위반 규칙 후보 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 오탐 또는 위반 누락 | 예 | 자동 검증: 정적 코드 패턴 |
| 155 | A-03 | 레이어 위반 / 조건 분기 | Controller의 다수 비즈니스 분기는 위반 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 정적 검사가 가능한 위반 규칙 후보 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 오탐 또는 위반 누락 | 예 | 자동 검증: 정적 코드 패턴 |
| 156 | A-03 | 레이어 위반 / Entity 반환 | Service 반환 타입이 Entity면 위반 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 정적 검사가 가능한 위반 규칙 후보 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 오탐 또는 위반 누락 | 예 | 자동 검증: 정적 코드 패턴 |
| 157 | A-03 | 레이어 위반 / 구현 위치 | Repository 구현 성격 코드가 api·application이면 위반 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 정적 검사가 가능한 위반 규칙 후보 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 오탐 또는 위반 누락 | 예 | 자동 검증: 정적 코드 패턴 |
| 158 | A-03 | 트랜잭션 규칙 / 선언 위치 | @Transactional은 Service에서만 | B-03·B-07은 트랜잭션을 검토 대상으로만 지정 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 구체 기준 위치 결정 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 트랜잭션 판단 공백 | 예 | 자동 검증: @Transactional 위치 |
| 159 | A-03 | 트랜잭션 규칙 / 조회 | 조회 전용은 readOnly=true | B-03·B-07은 트랜잭션을 검토 대상으로만 지정 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 구체 기준 위치 결정 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 트랜잭션 판단 공백 | 예 | 수동 검토 |
| 160 | A-03 | 트랜잭션 규칙 / 금지 위치 | Controller·Repository 선언 금지 | B-03·B-07은 트랜잭션을 검토 대상으로만 지정 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 구체 기준 위치 결정 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 트랜잭션 판단 공백 | 예 | 자동 검증: @Transactional 위치 |
| 161 | A-03 | 트랜잭션 규칙 / 경계 | 유스케이스 상태 변경을 하나의 Service 경계에서 관리 | B-03·B-07은 트랜잭션을 검토 대상으로만 지정 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 구체 기준 위치 결정 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 트랜잭션 판단 공백 | 예 | 수동 검토 |
| 162 | A-03 | 트랜잭션 규칙 / 상태 변경 | Entity 메서드·도메인 규칙으로 표현하고 Service는 오케스트레이션 | B-03·B-07은 트랜잭션을 검토 대상으로만 지정 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 구체 기준 위치 결정 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 트랜잭션 판단 공백 | 예 | 수동 검토 |
| 163 | A-03 | 트랜잭션 위반 / Controller·Repository | 금지 레이어 선언은 위반 | B-03·B-07에 구체 판정 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 정적·수동 검토 기준으로 구분 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 위험 누락 또는 정적 오탐 | 예 | 자동 검증: @Transactional 위치 |
| 164 | A-03 | 트랜잭션 위반 / readOnly | 조회 Service readOnly 누락은 Warning | B-03·B-07에 구체 판정 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 정적·수동 검토 기준으로 구분 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 위험 누락 또는 정적 오탐 | 예 | 자동 검증: @Transactional 위치 |
| 165 | A-03 | 트랜잭션 위반 / 경계 분산 | 여러 Service로 분산되면 Warning | B-03·B-07에 구체 판정 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 정적·수동 검토 기준으로 구분 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 위험 누락 또는 정적 오탐 | 예 | 수동 검토 |
| 166 | A-03 | 트랜잭션 위반 / 경계 밖 변경 | 경계 밖 Entity 변경 기대는 위반 | B-03·B-07에 구체 판정 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 정적·수동 검토 기준으로 구분 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 위험 누락 또는 정적 오탐 | 예 | 수동 검토 |
| 167 | A-03 | DTO / Request 이름 | {동사}{도메인}Request | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 168 | A-03 | DTO / Response 이름 | {도메인}{형태}Response | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 169 | A-03 | DTO / 페이지 이름 | {도메인}PageResponse 또는 목적형 이름 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 170 | A-03 | DTO / 위치 | api/dto 또는 api 하위 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 171 | A-03 | DTO 위반 / Entity 반환 | Controller Entity 직접 반환은 위반 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 172 | A-03 | DTO 위반 / 이름 | 규칙에서 크게 벗어나면 Warning | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 173 | A-03 | DTO 위반 / domain 위치 | Request·Response DTO가 domain이면 Warning | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 DTO 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과도한 네이밍 강제 또는 일관성 소실 | 예 | 자동 검증: 클래스명·패키지 정규식 |
| 174 | A-03 | 예외 / 비즈니스 예외 | BusinessException과 ErrorCode 사용 | B-04 민감정보 외 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 자동 검증: 예외 타입 사용 |
| 175 | A-03 | 예외 / 공통 처리 | GlobalExceptionHandler에서 처리 | B-04 민감정보 외 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 자동 검증: Handler 클래스·어노테이션 |
| 176 | A-03 | 예외 / Controller try-catch | 직접 처리 금지 | B-04 민감정보 외 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 자동 검증: Controller try/catch 패턴 |
| 177 | A-03 | 예외 / 등록 순서 | 새 예외는 ErrorCode에 먼저 등록 | B-04 민감정보 외 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 수동 검토 |
| 178 | A-03 | 예외 / 민감정보 | 비밀번호·토큰 원문을 메시지에 포함 금지 | B-04 민감정보 외 직접 대응 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 자동 검증: 금지 문자열 |
| 179 | A-03 | 예외 위반 / RuntimeException | 직접 RuntimeException 사용은 위반 | B-04 민감정보 외 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 자동 검증: 금지 코드 패턴 |
| 180 | A-03 | 예외 위반 / ErrorCode 없음 | ErrorCode 없이 BusinessException 사용은 위반 | B-04 민감정보 외 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 예외 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 예외 일관성·보안 기준 소실 | 예 | 수동 검토 |
| 181 | A-03 | 응답 / 성공 | ApiResponse<T> 사용 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 자동 검증: Controller 반환 타입 |
| 182 | A-03 | 응답 / 에러 | ErrorResponse 사용 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 자동 검증: 예외 응답 타입 |
| 183 | A-03 | 응답 / 검증 실패 | 필드 단위 오류 정보 포함 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 수동 검토 |
| 184 | A-03 | 응답 / 외형 | 도메인별 차이가 있어도 공통 외형 유지 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 수동 검토 |
| 185 | A-03 | 응답 위반 / 성공 형식 | ApiResponse 미준수는 Warning | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 자동 검증: 반환 타입 |
| 186 | A-03 | 응답 위반 / 제각각 에러 | Handler 밖 개별 에러 생성은 위반 | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 수동 검토 |
| 187 | A-03 | 응답 위반 / 필드 정보 | 검증 실패에 필드 정보 없으면 Warning | B 그룹 기술 본문 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현재 응답 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 응답 타입 강제 또는 공통성 소실 | 예 | 수동 검토 |
| 188 | A-03 | 테스트 전략 / 단위 | application, JUnit5+Mockito, Service 로직 | B-06·B-07은 자료만 요구 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현행 테스트 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 테스트 유형 책임 공백 | 예 | 수동 검토 |
| 189 | A-03 | 테스트 전략 / 슬라이스 | api, @WebMvcTest, 요청·응답 | B-06·B-07은 자료만 요구 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현행 테스트 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 테스트 유형 책임 공백 | 예 | 수동 검토 |
| 190 | A-03 | 테스트 전략 / 통합 | Testcontainers+SpringBootTest, MariaDB·Redis | B-06·B-07은 자료만 요구 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현행 테스트 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 테스트 유형 책임 공백 | 예 | 수동 검토 |
| 191 | A-03 | 테스트 전략 / 동시성 | ExecutorService+CountDownLatch, 동시 요청 | B-06·B-07은 자료만 요구 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현행 테스트 구조와 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 테스트 유형 책임 공백 | 예 | 수동 검토 |
| 192 | A-03 | 테스트 규칙 / 프로파일 | test 프로파일 명시 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 자동 검증: 테스트 명령 문자열 |
| 193 | A-03 | 테스트 규칙 / DB | ticketing_test 사용 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 자동 검증: 설정 값 |
| 194 | A-03 | 테스트 규칙 / Mockito | MockitoExtension 사용 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 자동 검증: 어노테이션 |
| 195 | A-03 | 테스트 규칙 / 슬라이스 범위 | 웹 계층만 검증 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 수동 검토 |
| 196 | A-03 | 테스트 규칙 / 통합 Transactional | 통합 테스트 @Transactional 금지 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 자동 검증: 금지 어노테이션 |
| 197 | A-03 | 테스트 규칙 / 초기화 | @Sql 또는 teardown으로 초기화 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 수동 검토 |
| 198 | A-03 | 테스트 규칙 / 동시성 우선 | 좌석·예약·대기열에 동시성 테스트 우선 고려 | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 수동 검토 |
| 199 | A-03 | 테스트 위반 / 성공만 | 핵심 실패 케이스 없으면 Warning | B-04 결과 보고·B-06 자료 요구 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 기준과 실행 보고를 분리해야 함 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 거짓 양성·테스트 공백 | 예 | 수동 검토 |
| 200 | A-03 | Redis Key / RefreshToken | refresh:{memberId}, 7일 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 201 | A-03 | Redis Key / AccessToken blacklist | blacklist:{accessToken}, 잔여 만료 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 202 | A-03 | Redis Key / 이벤트 목록 캐시 | events:list, 10분 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 203 | A-03 | Redis Key / 좌석 선점 락 | hold:seat:{showtimeId}:{seatId}, 락 TTL | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 204 | A-03 | Redis Key / 활성 이벤트 | queue:active:events, 종료 정리 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 205 | A-03 | Redis Key / 대기열 사용자 | queue:event:{eventId}, 종료 정리 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 206 | A-03 | Redis Key / 입장 허용 사용자 | queue:admitted:members:{eventId}, 종료 정리 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 207 | A-03 | Redis Key / 대기열 시퀀스 | queue:seq:{eventId}, 종료 정리 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 208 | A-03 | Redis Key / 입장 토큰 | token:user:{memberId}, 입장 후 만료 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 209 | A-03 | Redis Key / Rate Limit | rate:limit:{memberId}:{api}, 60초 | B-06은 Key·TTL 자료 요청만 존재 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 코드·현행 문서 대조 후 유지 여부 결정 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 과거 Key 복원 또는 TTL 정보 소실 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 210 | A-03 | Redis 네이밍 / 구분자 | :만 사용 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 Key 정책 확인 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | Key 충돌·문서 드리프트 | 예 | 자동 검증: Key 정규식 |
| 211 | A-03 | Redis 네이밍 / 언더바 | _ 사용 금지 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 Key 정책 확인 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | Key 충돌·문서 드리프트 | 예 | 자동 검증: 금지 문자열 |
| 212 | A-03 | Redis 네이밍 / 형식 | {목적}:{도메인}:{식별자} 또는 명확한 고정 Key | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 Key 정책 확인 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | Key 충돌·문서 드리프트 | 예 | 수동 검토 |
| 213 | A-03 | Redis 네이밍 / 신규 Key | 새 Key를 문서 표에 반영 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 Key 정책 확인 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | Key 충돌·문서 드리프트 | 예 | 자동 검증: 코드 Key와 문서 표 대조 |
| 214 | A-03 | Redis 위반 / showtimeId | 좌석 락 Key에 showtimeId 없으면 Warning | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 Key 정책 확인 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | Key 충돌·문서 드리프트 | 예 | 자동 검증: Key 패턴 |
| 215 | A-03 | 로그 / traceId | 요청 단위 고유 ID | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 자동 검증: MDC key 문자열 |
| 216 | A-03 | 로그 / memberId | 인증 사용자 ID | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 자동 검증: MDC key 문자열 |
| 217 | A-03 | 로그 / reservationId | 예약 ID | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 자동 검증: MDC key 문자열 |
| 218 | A-03 | 로그 / eventId | 이벤트 ID | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 자동 검증: MDC key 문자열 |
| 219 | A-03 | 로그 레벨 / INFO | 주요 비즈니스 이벤트 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 수동 검토 |
| 220 | A-03 | 로그 레벨 / WARN | 재시도 가능·예상 실패 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 수동 검토 |
| 221 | A-03 | 로그 레벨 / ERROR | 복구 불가·처리 누락 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 수동 검토 |
| 222 | A-03 | 로그 레벨 / DEBUG | 개발 환경 전용 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 수동 검토 |
| 223 | A-03 | 로그 형식 | traceId와 key=value 예시 | B 그룹 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 로깅 구현과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 미구현 규칙 강제 또는 관측성 기준 소실 | 예 | 수동 검토 |
| 224 | A-03 | 커밋 메시지 형식 / 제목 | prefix(TASK): 요약 | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 (관련 #115, #443) | 중복 | 아니오 | 자동 검증: 정규식 형식 |
| 225 | A-03 | 커밋 메시지 형식 / 본문 | Why·What·Note | B-04 직접 대응 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 조건부 본문 기준 여부 결정 | 형식 과잉 또는 이유 소실 | 예 | 수동 검토 |
| 226 | A-03 | 커밋 prefix | feat·fix·refactor·chore·docs·test | B-04에는 build도 포함 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 현재 집합과 통일 | build 분류 누락 | 예 | 자동 검증: 허용 enum |
| 227 | A-03 | 커밋 예시 | TASK-031 Rate Limit 예시 | B 그룹 직접 대응 없음 | 대응 없음 | 제외 | docs/process/PROJECT-RULES.md | 특정 TASK 예시는 기준 노후화 가능 | 구체 예시의 이해 도움 소실 | 예 | 검증 불필요 |
| 228 | A-03 | 리뷰 / 로드맵·선행 | 로드맵과 선행 TASK 우선 확인 | B-05·B-06 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 단계별 필수 자료로 통합 | 불필요한 로드맵 요구 | 예 | 자동 검증: 로드맵 필드 |
| 229 | A-03 | 리뷰 / 설계 예외 | 설계·사전 조사 목적은 선행 미완료 예외 허용 | A-05에도 존재, B 직접 없음 | 대응 없음 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 구현과 검토 예외를 분리 | 선행 미완료 구현으로 오해 | 예 | 자동 검증: review-only 상태 |
| 230 | A-03 | 리뷰 / 위험 우선 | 정합성·동시성·트랜잭션·테스트 우선 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 우선순위 | 중복 | 예 | 자동 검증: 범주·순서 |
| 231 | A-03 | 리뷰 / 문제 없는 부분 축약 | 문제 없는 부분은 길게 설명하지 않음 | B-07 중요 위험 우선 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 근거·한계는 보존하면서 중복만 줄여야 함 | 검토 범위 설명 소실 | 예 | 수동 검토 |
| 232 | A-03 | 리뷰 / 최대 3개 | 핵심 문제 최대 3개 우선 | B 직접 제한 없음 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 상위 3개 요약+전체 Finding으로 조정 | 결함 누락 | 예 | 자동 검증: 요약·전체 개수 대조 |
| 233 | A-03 | 리뷰 / Critical 차단 | Critical이면 차단 사유 우선 | B-04·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 우선 처리 | 중복 | 예 | 자동 검증: Critical 선행 |
| 234 | A-03 | Claude→GPT 형식 | REVIEW_REQUEST 필드 | B-07 필수 자료 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 단계별 입력 schema로 통합 (관련 #98, #260, #261, #262, #263, #573) | 이중 포맷 | 예 | 자동 검증: 필수 schema 필드 |
| 235 | A-03 | GPT→Claude 형식 | GPT_REVIEW 필드와 진행 판단 | B-07 Finding·권고 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 권고와 사용자 승인을 명확히 분리 | GPT 권고 자동 실행 | 예 | 자동 검증: 사용자 승인 문구 |
| 236 | A-03 | 자료 요청 / 우선 대상 | Issue·PR·파일·코드·테스트·구조·트랜잭션·로그·로드맵 | B-06·B-07 | 부분 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 단계별 자료 matrix로 통합 | 자료 과다·누락 | 예 | 자동 검증: 필수 schema 필드 |
| 237 | A-03 | 자료 요청 / 명령 예시 | tree·find·cat·git diff 예시 | B-06 일부 Git 명령 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 확인된 경로에만 조건부 명령 제공 | 민감·없는 경로 탐색 | 예 | 자동 검증: 금지 경로·허용 명령 |
| 238 | A-03 | 자료 요청 / 파일 직접 요청 | 필요 파일 전체를 직접 요청 | B-06 관련 경로 요구 | 부분 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 명령 출력 대신 파일 제공 방식 유지 | 파일 과다 요청 | 예 | 수동 검토 |
| 239 | A-03 | 문서 수정 트리거 | Redis·패키지·테스트·응답·리뷰·로드맵·커밋 변경 시 갱신 | B-04 직접 목록 없음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 최종 문서 책임에 맞춰 재작성 | 문서 드리프트 | 예 | 자동 검증: 변경 파일-문서 대조 |
| 240 | A-03 | 문서 불일치 판정 | 공통 규칙 변경 후 문서 미수정은 리뷰 기준 불일치 | B-04 충돌 처리 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 자동 검증 가능한 동반 변경으로 전환 후보 | 규칙·구현 불일치 | 예 | 자동 검증: 변경 파일-문서 대조 |
| 241 | A-04 | 서문 / 문서 책임 | Claude 작업 절차만 정의, 기술 규칙은 PROJECT-RULES 참조 | B-01 `문서 목적` | 부분 대응 | 병합 | CLAUDE.md | 짧은 진입점과 workflow 참조로 재구성 | 절차·기술 규칙 중복 | 예 | 자동 검증: 문서 링크 |
| 242 | A-04 | 채팅 제목 추천 | `[TASK-XXX] 작업 내용 요약` | B 그룹 직접 대응 없음 | 대응 없음 | 제외 | docs/process/DEVELOPMENT-WORKFLOW.md | 안전·검증·추적의 필수 규칙이라는 근거가 없음 | 채팅 식별 편의 소실 | 예 | 검증 불필요 |
| 243 | A-04 | 프로젝트 개요 / 목적 | 티켓팅 백엔드 포트폴리오 | B-01 | 완전 대응 | 병합 | CLAUDE.md | 중복 목적을 진입 문서로 단일화 | 중복 | 예 | 수동 검토 |
| 244 | A-04 | 프로젝트 개요 / 스택 | Java·Spring·DB·Redis 등 | B-04 README 참조 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 기술 스택 단일 위치 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 값 드리프트 | 예 | 수동 검토 |
| 245 | A-04 | 프로젝트 개요 / 계정 흐름 | 두 계정 협업 lifecycle | B-04 계정·Reviewer | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 단계는 workflow, 계정은 PROJECT-RULES 참조 | 중복 | 예 | 자동 검증: 계정 값 |
| 246 | A-04 | 환경 설정 | DB·Redis·direnv 값 | B-04 README 참조 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 실제 설정과 대조 필요 / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 환경값 노후화 | 예 | 수동 검토 |
| 247 | A-04 | TASK 시작 / 로드맵 | 로드맵 적절성·선행 완료 확인 | B-06 | 완전 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | workflow 첫 단계로 단일화 | 선행 확인 누락 | 예 | 자동 검증: workflow 단계 |
| 248 | A-04 | TASK 시작 / 파일 요청 | 체크리스트 기반 자료 요청, 파일 없이 Issue 금지 | B-06 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 필수·보조 자료 구분과 연결 | 과도한 중단 또는 자료 부족 Issue | 예 | 자동 검증: 필수 자료 상태 |
| 249 | A-04 | TASK 시작 / 확인 문구 | 확인 후 고정 문구로 Issue 시작 | B 직접 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 문구가 아니라 확인 결과·승인 상태로 전환 | 형식이 실제 검증을 대체 | 예 | 자동 검증: 승인 상태 필드 |
| 250 | A-04 | 작업 규칙 / DoD 확인 | 코드 작업 전 DoD·Test 확인 | B-04·B-07 일부 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 단계별 완료 게이트로 통합 (관련 #103, #278) | 검증 없는 Git 작업 | 예 | 자동 검증: DoD·Test 필드 |
| 251 | A-04 | 작업 규칙 / PR 산출물 | PR 제목·라벨·본문·DoD·Test·작업자·리뷰어 코멘트 세트 | B-04 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | PR 단계 필수·선택 산출물로 재분류 (관련 #125, #294) | PR 근거 누락 또는 코멘트 과잉 | 예 | 자동 검증: PR schema |
| 252 | A-04 | 작업 규칙 / 수동 테스트 | 항상 수동 테스트 확인 | B-07은 결과 또는 미실행 사유 | 충돌 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 변경 유형에 맞는 수동 검증으로 조정 (관련 #106, #280) | 비API TASK에 불필요한 강제 | 예 | 자동 검증: 결과 또는 미실행 사유 |
| 253 | A-04 | 작업 규칙 / README 갱신 | PR 후 README·관련 문서 최신화 | B-04 문서 참조·충돌 처리 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 영향 문서 갱신 또는 불필요 사유로 일반화 (관련 #107, #281) | README 과잉 수정 또는 문서 누락 | 예 | 자동 검증: 문서 영향 결과 |
| 254 | A-04 | 작업 규칙 / 파일 하나씩 | 파일 하나씩 순서대로 제공 | A-02는 사용자 요청 시에만 한 파일씩 | 충돌 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 승인된 논리 작업 단위별 제공으로 조정 (관련 #90, #272) | 강결합 변경의 문맥 단절 | 예 | 수동 검토 |
| 255 | A-04 | 작업 규칙 / 코드 주석 | 각 코드에 설명 주석 추가 | B 직접 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 모든 코드에 강제하지 않고 이유가 드러나지 않는 동시성·트랜잭션·도메인 결정에 이유 중심 주석 (관련 #270) | 주석 노후화 또는 설계 이유 소실 | 예 | 수동 검토 |
| 256 | A-04 | 작업 규칙 / 범위 밖 개선 | 코드를 보면 Issue 밖 개선 포인트도 먼저 말함 | B-01·B-04는 범위 밖 제안으로 분리 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 먼저가 아니라 `[범위 밖 제안]`으로 별도 보고 | 현재 TASK 위험보다 선택 개선이 앞설 수 있음 | 예 | 자동 검증: 범위 밖 표식 |
| 257 | A-04 | 작업 규칙 / 주석 이모지 금지 | 코드 주석에 이모지 금지 | B 직접 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 코드 스타일 정책으로 유지 필요성 결정 | 불필요 형식 강제 또는 스타일 혼선 | 예 | 자동 검증: 금지 문자 |
| 258 | A-04 | 작업 규칙 / Assignee | Issue Assignee pil97 | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 값 존재 | 중복 | 아니오 | 자동 검증: 필수 값 |
| 259 | A-04 | 작업 규칙 / 커밋 단위 | 논리 단위와 prefix 형식 | B-04 제목 형식 | 부분 대응 | 병합 | docs/process/PROJECT-RULES.md | 형식은 PROJECT-RULES, 분할 판단은 workflow | 거대 커밋 또는 과도한 분할 | 예 | 수동 검토 |
| 260 | A-04 | GPT 전달 요약 / 현재 단계 | 현재 단계 필드 | B-07 단계별 리뷰 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 리뷰 요청 schema로 통합 (관련 #98, #234, #261, #262, #263, #573) | 단계 오판 | 예 | 자동 검증: 필수 schema 필드 |
| 261 | A-04 | GPT 전달 요약 / 작업·파일·설계 | 작업 내용·변경 파일·핵심 설계 | B-07 필수 자료 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 단계별 입력 필드로 통합 (관련 #98, #234, #260, #262, #263, #573) | 리뷰 맥락 부족 | 예 | 자동 검증: 필수 schema 필드 |
| 262 | A-04 | GPT 전달 요약 / 남은 리스크 | 트랜잭션·동시성·예외·테스트 누락 중심 | B-07·B-03 검토 범위 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 미해결 위험 필드로 통합 (관련 #98, #234, #260, #261, #263, #573) | 자기평가 편향 | 예 | 자동 검증: 필수 schema 필드 |
| 263 | A-04 | GPT 전달 요약 / 질문 최대 3개 | 확인 질문 최대 3개 | B 직접 제한 없음 | 대응 없음 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 핵심 질문 우선 원칙으로 바꾸되 필요한 질문은 보존 (관련 #98, #234, #260, #261, #262, #573) | 필수 질문 누락 | 예 | 수동 검토 |
| 264 | A-04 | GPT 전달 요약 / 요약 없는 요청 금지 | 작업 요약 없이 리뷰 요청 금지 | B-07 필수 자료 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 필수 자료 schema 충족으로 대체 | 불완전 리뷰 | 예 | 자동 검증: 필수 schema 필드 |
| 265 | A-04 | GPT 결과 / Critical | Critical 먼저 수정 후 진행 | B-04 사용자 판단·B-07 권고 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 사용자 승인 후 수정·재검토로 변경 | GPT가 자동 명령자로 오인 | 예 | 자동 검증: 승인 상태 |
| 266 | A-04 | GPT 결과 / Warning | 수정 여부를 사용자에게 확인 | B-04·B-07 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 최종 승인권과 일치 | 중복 | 아니오 | 자동 검증: 사용자 결정 필드 |
| 267 | A-04 | GPT 결과 / Info | 참고만 | B-04·B-07 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 enum과 일치 | 중복 | 아니오 | 자동 검증: 허용 enum |
| 268 | A-04 | GPT 결과 / 진행 금지 | 수정 후 재검토 요청 | B-07 최종 권고 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 사용자 승인 후 재검토 상태로 전이 | 자동 재검토 | 예 | 자동 검증: 상태 전이 |
| 269 | A-04 | 코드 제공 / 작업자 설명 | 파일 역할을 한 줄 설명 | B 직접 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 변경 단위 설명으로 일반화 | 설명 부족 또는 반복 | 예 | 수동 검토 |
| 270 | A-04 | 코드 제공 / 인라인 주석 | 주요 설명 주석을 코드에 포함 | B 직접 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 이유 중심 주석으로 통일. 관련 A-04 코드 주석 행 (관련 #255) | 주석 노후화 또는 핵심 이유 소실 | 예 | 수동 검토 |
| 271 | A-04 | 코드 제공 / 개선 제안 | 대안이 있을 때만 현재·대안·트레이드오프·추천 | B-01 범위 밖 제안 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 현재 TASK 결함과 선택 개선 분리 | 범위 확장 | 예 | 자동 검증: 범위 밖 표식 |
| 272 | A-04 | 코드 제공 / 파일 순차 | 항상 한 파일씩 | A-02 조건부와 충돌 | 충돌 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 논리 작업 단위 기준으로 통일 (관련 #90, #254) | 문맥 단절 | 예 | 수동 검토 |
| 273 | A-04 | 전체 코드 리뷰 / 아키텍처 | PROJECT-RULES 위반 확인 | B-03·B-07 일부 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범주와 학습 기준을 분리 | 리뷰 범위 혼합 또는 누락 | 예 | 수동 검토 |
| 274 | A-04 | 전체 코드 리뷰 / 범위 밖 개선 | Issue 밖 개선 포인트 | B-03·B-07 일부 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범주와 학습 기준을 분리 | 리뷰 범위 혼합 또는 누락 | 예 | 수동 검토 |
| 275 | A-04 | 전체 코드 리뷰 / 면접 | 선택 이유 설명 가능성 | B-03·B-07 일부 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범주와 학습 기준을 분리 | 리뷰 범위 혼합 또는 누락 | 예 | 수동 검토 |
| 276 | A-04 | 전체 코드 리뷰 / 예외 | 예외 처리 누락 | B-03·B-07 일부 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범주와 학습 기준을 분리 | 리뷰 범위 혼합 또는 누락 | 예 | 수동 검토 |
| 277 | A-04 | 전체 코드 리뷰 / 테스트 | 테스트 커버리지 누락 | B-03·B-07 일부 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범주와 학습 기준을 분리 | 리뷰 범위 혼합 또는 누락 | 예 | 수동 검토 |
| 278 | A-04 | 커밋 전 / DoD | 완료·미완료 체크, 미완료 시 커밋 금지 | B-04 Git 승인 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 커밋 게이트로 통합 (관련 #103, #250) | 미완료 커밋 | 예 | 자동 검증: DoD 상태 |
| 279 | A-04 | 커밋 전 / Test | 완료·미완료 체크, 미완료 시 커밋 금지 | B-04 테스트 보고 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 실제 결과와 연결 | 미검증 커밋 | 예 | 자동 검증: 테스트 결과 |
| 280 | A-04 | 커밋 전 / Postman | Postman 시나리오를 항상 완료 표시 | B-07은 수동 결과 또는 사유 | 충돌 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | API 변경 등 해당 TASK에만 수동 검증 (관련 #106, #252) | 비HTTP TASK 과잉 차단 | 예 | 자동 검증: 결과 또는 미실행 사유 |
| 281 | A-04 | 커밋 전 / README | 변경 유형별 README 갱신 또는 불필요 사유 | B 직접 상세 없음 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 영향 문서 전체로 일반화 (관련 #107, #253) | README 과잉 수정 또는 문서 누락 | 예 | 자동 검증: 문서 영향 결과 |
| 282 | A-04 | 커밋 전 / 명령 제공 차단 | 모든 체크 완료 전 커밋 명령 미제공 | B-04 Git 승인 | 완전 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 게이트 상태로 통합 | 승인 없는 커밋 | 예 | 자동 검증: 게이트 상태 |
| 283 | A-04 | 회고 작성 | 모든 PR merge 후 docs/devlog에 회고 | B 직접 없음 | 대응 없음 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 대표·고위험·사건 발생 TASK에 조건부 적용 검토 (관련 #129) | 문서 폭증 또는 회고 누락 | 예 | 수동 검토 |
| 284 | A-04 | TASK 완료 문구 | 다음 채팅 시작 문구 강제 | B-01 다음 TASK 자동 진행 금지 | 충돌 | 제외 | docs/process/DEVELOPMENT-WORKFLOW.md | 요청 시 제공으로 대체 후보 (관련 #131) | 채팅 전환 편의 소실 | 예 | 검증 불필요 |
| 285 | A-04 | Issue 본문 형식 | Goal·Background·Scope·DoD·Test·Notes | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 (관련 #72, #441) | 중복 | 아니오 | 자동 검증: 필수 장·절 |
| 286 | A-04 | Issue 제목·Assignee | TASK 제목·pil97 | B-04 동일 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 값 존재 (관련 #71, #440) | 중복 | 아니오 | 자동 검증: 정규식·필수 값 |
| 287 | A-04 | Issue 라벨 | 8개 라벨, 복수 허용 | B 직접 없음 | 대응 없음 | 수정 | docs/process/PROJECT-RULES.md | 현행 라벨 확인 필요 | 오래된 라벨 강제 | 예 | 수동 검토 |
| 288 | A-04 | 면접관 Issue 검토 / 선택 이유 | 기술 선택 이유 명확성 | B-07 ISSUE_REVIEW·C-01 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | Issue 검증 질문으로 통합 | 학습과 리뷰 혼동 | 예 | 수동 검토 |
| 289 | A-04 | 면접관 Issue 검토 / 실무·포트폴리오 | 실무 맥락·포트폴리오 포인트 | B 직접 없음 | 대응 없음 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 과장 없이 검증 가능한 맥락으로 제한 | 범위 확대·과장 | 예 | 수동 검토 |
| 290 | A-04 | 면접관 Issue 검토 / DoD | 구체적·검증 가능성 | B-07 ISSUE_REVIEW | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 계약에 존재 | 중복 | 아니오 | 자동 검증: DoD·Test 필드 |
| 291 | A-04 | 면접관 Issue 검토 / 설명 가능성 | 왜 구현했는지 답변 가능 | C-01 | 부분 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 계약으로 이동 | Issue 단계 과도한 부담 | 예 | 수동 검토 |
| 292 | A-04 | PR 본문 형식 | What·Why·How·Test·Notes·closes | B-04 일부 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 상세 template 단일 위치 결정 | PR 근거 누락 또는 중복 | 예 | 자동 검증: 필수 장·절 |
| 293 | A-04 | PR 테스트 상세 | 전체 테스트·추가 테스트·Postman 시나리오 | B-07 PR_REVIEW 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 변경 유형별 검증 결과로 일반화 | 비API TASK 과잉 요구 | 예 | 자동 검증: 결과 또는 미실행 사유 |
| 294 | A-04 | PR 메타 | Title·Label·작업자 코멘트·Reviewer 코멘트 | B-04 Title·Reviewer 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 필수·선택 산출물을 분리 (관련 #125, #251) | 코멘트 과잉 또는 추적성 누락 | 예 | 자동 검증: PR schema |
| 295 | A-04 | 로드맵 관리 | 다음 TASK·선후·우선순위 전 로드맵 확인 | B-05·B-06 | 완전 대응 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 현재 목적 존재 | 중복 | 아니오 | 자동 검증: 문서 링크 |
| 296 | A-05 | Role / 시니어·PM | GPT를 10년차 백엔드 시니어이자 PM으로 정의 | B-07은 독립 검토자 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | PM 권한과 독립 검토 권한 중 기준 결정 필요 (관련 #553) | 사용자 승인권 침해 또는 진행 관리 기능 소실 | 예 | 수동 검토 |
| 297 | A-05 | Role / 검증자 | 개발 흐름 판단·문제 탐지·자료 요청·다음 단계 안내 | B-07 독립 검토와 일부 대응 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 결과와 단계 선택지 보고로 한정 후보 | GPT가 프로세스를 자동 통제 | 예 | 수동 검토 |
| 298 | A-05 | Strict / 다른 채팅 금지 | 다른 채팅·프로젝트 맥락 절대 참조 금지 | B-03은 고정 패킷 모드에서만 외부 금지 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 고정 패킷과 일반 검토 모드를 분리 (관련 #414, #415, #416) | 승인된 문맥 차단 또는 독립성 훼손 | 예 | 자동 검증: 검토 모드 필드 |
| 299 | A-05 | Strict / 현재 자료만 | 현재 채팅 제공 정보·첨부만 판단 | B-03 고정 패킷 모드와 대응 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 고정 패킷에서는 유지, 일반 모드는 승인 자료로 한정 | 외부 정보 혼입 또는 자료 부족 | 예 | 자동 검증: 입력 경계 |
| 300 | A-05 | Strict / 칭찬·장황함 금지 | 불필요한 칭찬·추상·장황한 배경 금지 | B-07 중요 위험 우선 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 스타일보다 근거·중복 금지 중심으로 재표현 | 필요한 설명까지 축약 | 예 | 수동 검토 |
| 301 | A-05 | Strict / 사실 기반 | 사실만 답하고 정보 부족 시 추측 금지 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 근거 원칙 | 중복 | 예 | 자동 검증: 근거 필드 |
| 302 | A-05 | Strict / 문제 없는 부분 축약 | 문제 없는 부분을 길게 설명하지 않음 | B-07 중요 위험 우선 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위·한계는 남기고 반복만 줄임 | 검토 범위가 불명확해짐 | 예 | 수동 검토 |
| 303 | A-05 | Strict / 전체 코드 재작성 금지 | 요청하지 않은 전체 코드 재작성 금지 | B-03·B-07 구현 금지 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 비수정 역할에 포함 | 중복 | 아니오 | 자동 검증: 수정·코드 생성 금지 |
| 304 | A-05 | Strict / 리뷰어 역할 | 구현자가 아니라 리뷰어·검증자에 집중 | B-07 동일 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 역할 존재 | 중복 | 아니오 | 자동 검증: 권한 문구 |
| 305 | A-05 | Strict / 자료 부족 | 정보 부족 시 자료부터 요청 | B-07 필수 자료 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 단계별 중단 기준으로 통합 | 추정 리뷰 | 예 | 자동 검증: 필수 자료 상태 |
| 306 | A-05 | Roadmap / 우선 확인 | 작업 전 로드맵·순서·선행·다음 단계 확인 | B-05·B-06 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 리뷰 단계에 필요한 로드맵 필드만 요구 | 모든 리뷰의 과도한 로드맵 요구 | 예 | 자동 검증: 로드맵 필드 |
| 307 | A-05 | Roadmap / 순서 위반 | 순서 어긋남 지적, 선행 미완료 시 진행 금지 | B-05 상태 기준·B-07 권고 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 구현 진행과 검토 진행을 분리 | 검토 자체까지 불필요하게 차단 | 예 | 수동 검토 |
| 308 | A-05 | Roadmap / 예외 | 설계·사전 조사·Issue 초안·분리 판단·선후 점검은 예외 | B 직접 대응 없음 | 대응 없음 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | review-only 예외 상태로 구조화 | 예외가 구현 승인으로 오해 | 예 | 자동 검증: review-only 상태 |
| 309 | A-05 | Roadmap / 예외 표기 | 예외 이유와 초안·검토 단계임을 명시 | B-01 승인 범위 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 범위 상태 필드로 통합 | 임시 판단의 확정 표현 | 예 | 자동 검증: 단계·범위 필드 |
| 310 | A-05 | STOP / 설계 결함 | 설계 결함 시 다음 단계 금지 | B-07 최종 권고 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 근거 있는 차단 조건으로 통합 | 과도한 차단 또는 결함 진행 | 예 | 수동 검토 |
| 311 | A-05 | STOP / 정합성·트랜잭션·동시성 | 핵심 정합성 위험 시 중단 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 Critical 범주 | 중복 | 예 | 자동 검증: Critical 범주 |
| 312 | A-05 | STOP / 장애 가능성 | 장애 가능성 높으면 중단 | B-04 Critical 운영 장애 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 근거 충분한 운영 장애로 한정 | 막연한 가능성으로 과도한 차단 | 예 | 수동 검토 |
| 313 | A-05 | STOP / 테스트 부재 | 테스트 부재·검증 부족 시 중단 | B-07 단계별 필수 테스트 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 핵심 요구에 필요한 테스트 부재로 한정 | 문서 TASK까지 과도하게 차단 | 예 | 수동 검토 |
| 314 | A-05 | STOP / 입력 부족 | 필수 입력 부족 시 중단 | B-07과 임계값 충돌 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 핵심·보조 자료 기준 결정 필요 / 최종 결정 5에 따라 핵심·보조 입력 누락 기준을 GPT review SSOT로 확정 | 불완전 리뷰 또는 과도한 중단 | 예 | 자동 검증: 필수 자료 상태 |
| 315 | A-05 | AI Dev Loop / 단계 | 0~10 TASK 선정·Issue·리뷰·설계·구현·테스트·Git·PR | B-06·B-07에 분산 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 전체 lifecycle 단일 기준 (관련 #18, #43, #379, #435) (관련 #547, #548, #549, #550, #551, #552) | GPT가 workflow 소유자로 보일 위험 | 예 | 자동 검증: 필수 단계 집합 |
| 316 | A-05 | AI Dev Loop / 상태 보고 | 현재 위치·진행 가능 여부·다음 단계 명시 | B-01·B-07 일부 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 승인된 단계와 선택지 보고로 정리 | 자동 다음 단계 유도 | 예 | 자동 검증: 현재 단계 필드 |
| 317 | A-05 | Review Mode / 단계 enum | ISSUE·CODE·DEEP_DIVE·PR | B-07 동일 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 네 단계 존재 (관련 #565, #567, #569, #571) | 중복 | 아니오 | 자동 검증: 허용 enum |
| 318 | A-05 | Review Mode / 첫 줄 | 응답 첫 줄에 모드 표기 | B-07 직접 규정 없음 | 대응 없음 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 구조화 필드 또는 heading으로 유지 여부 결정 | 단계 식별성 소실 또는 형식 강제 | 예 | 자동 검증: 모드 heading |
| 319 | A-05 | Review Depth / QUICK·DEEP | 상황별 깊이 선택 | B-07 단계 자체로 깊이 구분 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 모드와 깊이 중복을 해소 | 과도하거나 얕은 리뷰 | 예 | 수동 검토 |
| 320 | A-05 | Review Depth / 커밋 전 | 커밋 전 반드시 DEEP_DIVE | B-07 DEEP_DIVE 목적 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | TASK 위험도와 변경 유형에 따른 적용 여부 결정 | 모든 TASK 과잉 검토 또는 위험 누락 | 예 | 수동 검토 |
| 321 | A-05 | Deep-Dive / 트랜잭션 | 트랜잭션 흐름 추적 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 322 | A-05 | Deep-Dive / 동시성 | 충돌 시나리오 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 323 | A-05 | Deep-Dive / 정합성 | 데이터 정합성 깨짐 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 324 | A-05 | Deep-Dive / 장애 | 장애 시나리오 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 325 | A-05 | Deep-Dive / 경계 | 경계 조건 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 326 | A-05 | Deep-Dive / 롤백·재시도·중복 | 롤백·재시도·중복 요청 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 327 | A-05 | Deep-Dive / 테스트 방어 | 테스트가 위험을 실제로 막는지 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | DEEP_DIVE 공통 위험 schema로 통합 | 범주 드리프트 | 예 | 자동 검증: 위험 범주·필수 필드 |
| 328 | A-05 | Review 기준 / 트랜잭션 | 트랜잭션 | B-03·B-04·B-07 일부 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 자동 검증: 허용 범주 |
| 329 | A-05 | Review 기준 / 동시성 | 동시성 | B-03·B-04·B-07 일부 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 자동 검증: 허용 범주 |
| 330 | A-05 | Review 기준 / 정합성 | 데이터 정합성 | B-03·B-04·B-07 일부 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 자동 검증: 허용 범주 |
| 331 | A-05 | Review 기준 / 성능 | 성능 | B-03·B-04·B-07 일부 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 수동 검토 |
| 332 | A-05 | Review 기준 / 장애 | 장애 가능성 | B-03·B-04·B-07 일부 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 자동 검증: 허용 범주 |
| 333 | A-05 | Review 기준 / 유지보수성 | 유지보수성 | B-03·B-04·B-07 일부 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 수동 검토 |
| 334 | A-05 | Review 기준 / 확장성 | 확장성 | B-03·B-04·B-07 일부 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 수동 검토 |
| 335 | A-05 | Review 기준 / 테스트 | 테스트 적절성 | B-03·B-04·B-07 일부 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위와 범위 밖 제안을 구분 | 무제한 범위 확장 또는 누락 | 예 | 자동 검증: 허용 범주 |
| 336 | A-05 | Review 기준 / 문제만 설명 | 문제 있는 부분만 말함 | B-07 중요 위험 우선 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 범위·한계는 보존하고 문제 중심으로 작성 | 검토 한계 소실 | 예 | 수동 검토 |
| 337 | A-05 | Severity | Critical·Warning·Info 정의 | B-04·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 텍스트 enum 단일 기준 (관련 #95, #473, #573) | 심각도 불일치 | 예 | 자동 검증: 허용 enum |
| 338 | A-05 | 최종 검토 권고 | 진행 금지·수정 후 진행·진행 가능 기준 | B-07 동일 3개 권고 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 최종 권고 enum 단일화 | 권고가 명령으로 오해 | 예 | 자동 검증: 허용 enum |
| 339 | A-05 | Critical 우선 처리 | Critical 1개라도 있으면 먼저 제시하고 Warning·Info 최소화 | B-04·B-07 중요 위험 우선 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | Critical 우선은 유지하되 나머지 Finding을 숨기지 않도록 조정 (관련 #97, #474) | 다른 근거 있는 문제 누락 | 예 | 자동 검증: Critical 선행·전체 Finding 보존 |
| 340 | A-05 | 문제 최대 3개 | 가장 중요한 문제 3개까지만 먼저 제시 | B 직접 제한 없음 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 상위 3개 요약과 전체 Finding 보존 (관련 #96) | 4번째 이후 결함 누락 | 예 | 자동 검증: 요약·전체 개수 대조 |
| 341 | A-05 | 선택 개선 | 낮은 우선순위는 선택 개선으로 짧게 | B-01 범위 밖 제안 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 범위 밖·Info와 분리 | 결함이 선택 개선으로 축소 | 예 | 수동 검토 |
| 342 | A-05 | 문제 정렬 | 차단→정합성→테스트→성능·구조→기타 | B-04·B-07 일부 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 우선순위 단일화 | 중복 | 예 | 자동 검증: 정렬 우선순위 |
| 343 | A-05 | Code Policy / 부분 코드 | 필요 시 부분 코드만 제시 | B-07 구현 금지 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토자가 직접 구현하지 않고 수정 포인트만 제시 | 역할 침범 | 예 | 자동 검증: 전체 파일 코드 금지 |
| 344 | A-05 | Code Policy / 구조 존중 | 기존 구조 존중·범위 밖 리팩터링 금지 | B-04 범위 통제 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 교차 범위 규칙으로 단일화 | 범위 확장 | 예 | 자동 검증: 범위 밖 표식 |
| 345 | A-05 | Code Policy / 검증+제안 | 구현자가 아니라 검증·개선 제안만 | B-07 동일 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 권한 존재 | 중복 | 아니오 | 자동 검증: 권한 문구 |
| 346 | A-05 | ISSUE_REVIEW 필수 자료 | Issue 본문·목표·범위·DoD·Test·선행·로드맵 | B-07 ISSUE_REVIEW | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 단계별 schema와 통합 | 자료 중복·누락 | 예 | 자동 검증: 필수 schema 필드 |
| 347 | A-05 | CODE_REVIEW 필수 자료 | PR/요약·변경 파일·핵심 코드·테스트·구조·트랜잭션·로드맵 | B-07 CODE_REVIEW | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 단계별 schema와 통합 | 자료 중복·누락 | 예 | 자동 검증: 필수 schema 필드 |
| 348 | A-05 | PR_REVIEW 필수 자료 | PR·변경 요약·테스트·체크·diff·로드맵 | B-07 PR_REVIEW | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 단계별 schema와 통합 | 자료 중복·누락 | 예 | 자동 검증: 필수 schema 필드 |
| 349 | A-05 | 리뷰 중단 임계값 | 필수 자료 2개 이상 없으면 중단 | B-07은 하나라도 누락 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 핵심·보조 또는 임계값 결정 필요 (관련 #558) / 최종 결정 5에 따라 핵심·보조 입력 누락 기준을 GPT review SSOT로 확정 | 불완전 리뷰 또는 과도한 중단 | 예 | 자동 검증: 필수 자료 상태 |
| 350 | A-05 | 자료 요청 / 명령 | tree·find·cat·git diff를 구체적으로 요청 | B-06 일부 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 경로·민감정보 제한을 적용 | 없는 경로·민감 탐색 | 예 | 자동 검증: 허용 명령·금지 경로 |
| 351 | A-05 | 자료 요청 / 파일 | 원문 파일 전체 직접 요청 | B-06 관련 파일 경로 | 부분 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 필요 범위 파일 요청 방식으로 통합 | 과도한 파일 수집 | 예 | 수동 검토 |
| 352 | A-05 | 자료 요청 / 사용자 입력 | PR·Issue·요구사항·API·결과·로그·구조를 요청 | B-06·B-07 | 부분 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 단계별 입력 필드로 통합 | 자료 과다·민감 로그 노출 | 예 | 자동 검증: 필수 schema·금지 문자열 |
| 353 | A-05 | Input 부족 / 전체 목록 | 코드·Issue·PR·Entity·구조·트랜잭션·API·테스트·요구사항·로드맵이 없으면 진행 금지 | B-07 단계별 자료 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 모든 항목을 모든 단계에 강제하지 않고 단계별 핵심 자료로 분리 | 과도한 자료 요구 또는 핵심 자료 누락 | 예 | 자동 검증: 단계별 필수 자료 matrix |
| 354 | A-05 | Output / 현재 단계 | Dev Loop·로드맵·선행 조건 | B-07 직접 고정 형식 없음 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 단계·범위 필드로 간소화 | 단계 맥락 소실 | 예 | 자동 검증: 필수 heading |
| 355 | A-05 | Output / 문제 | Severity·문제·이유 | B-07 Finding | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | Finding schema로 통합 | 근거 부족 | 예 | 자동 검증: 필수 schema 필드 |
| 356 | A-05 | Output / 수정안 | 기존 위치·현재 내용·수정 내용 | B-07 위치·조치 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 근거와 필요한 조치 필드로 통합 | 수정 포인트 모호 | 예 | 자동 검증: 필수 schema 필드 |
| 357 | A-05 | Output / 추가 자료 | 자료·방식·이유·확인 후 단계 | B-07 공통 원칙 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 검증 불가 절의 schema로 통합 | 자료 요청 이유 불명확 | 예 | 자동 검증: 필수 schema 필드 |
| 358 | A-05 | Output / 결론 | 3개 권고와 다음 단계 | B-07 최종 권고 | 완전 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 다음 단계는 사용자 선택지로 표현 | 자동 단계 진행 오해 | 예 | 자동 검증: 권고 enum·사용자 결정 |
| 359 | A-05 | 응답 길이 상한 | 기본 12줄·항목당 3문장 | B 직접 대응 없음 | 대응 없음 | 제외 | docs/process/GPT-REVIEW-CONTRACT.md | 고정 길이가 근거·한계·전체 Finding을 숨길 수 있음 | 간결성 원칙 약화 | 예 | 검증 불필요 |
| 360 | A-05 | 수정안 제시 | 위치·현재·수정 중심, 전체 재작성보다 교체 포인트 | B-07 Finding | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 구체 위치와 조치 필드로 통합 | 수정안 모호 | 예 | 자동 검증: 필수 schema 필드 |
| 361 | A-05 | Review Style | 직설·숨은 위험 우선·애매하면 자료 요청·통과는 짧게 | B-07 공통 원칙 일부 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 톤보다 근거·우선순위·중복 금지로 재표현 | 공격적 표현 또는 장황함 | 예 | 수동 검토 |
| 362 | A-05 | 시작 규칙 / 순서 | 로드맵→TASK→단계→Issue→ISSUE_REVIEW | B-06·B-07 | 부분 대응 | 수정 | docs/process/DEVELOPMENT-WORKFLOW.md | 이미 제공된 정보를 재질문하지 않는 상태 기반 흐름으로 변경 | 반복 질문·무조건 Issue 유도 | 예 | 자동 검증: 단계 상태 |
| 363 | A-05 | 시작 규칙 / 고정 첫 질문 | 오늘 TASK와 로드맵·Issue·코드 자료를 한꺼번에 질문 | B 직접 없음 | 대응 없음 | 제외 | docs/process/DEVELOPMENT-WORKFLOW.md | 현재 제공 정보와 단계에 맞춘 자료 요청으로 대체 | 질문 편의 소실 | 예 | 검증 불필요 |
| 364 | A-05 | 이모지 심각도 | 🔥·⚠️·ℹ️ 표기 | B-03·B-07은 텍스트 enum | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 저장·검증 값은 텍스트, 표시는 선택 | 자동 파싱 불안정 | 예 | 자동 검증: 허용 enum |
| 365 | A-06 | 서문 / 목적 | TASK 유형에 맞는 자료 요청 가이드 | B-06 `목적` | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 단일 체크리스트로 통합 | 중복 | 예 | 자동 검증: 문서 책임 |
| 366 | A-06 | 서문 / 필수 자료 없으면 중단 | 필수 항목 없으면 Issue 시작 금지 | B-06 `자료 확인 후 흐름` | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 필수·보조 자료 정의와 연결 | 과도한 중단 또는 자료 부족 Issue | 예 | 자동 검증: 필수 자료 상태 |
| 367 | A-06 | 공통 / 패키지 구조 | 모든 TASK에서 main 패키지 find 요청 | B-06은 관련 경로만 요구 | 충돌 | 수정 | docs/process/TASK-START-CHECKLIST.md | 구조 영향 TASK에만 실제 경로 기반으로 요청 | 불필요한 대량 출력 또는 구조 정보 부족 | 예 | 자동 검증: TASK 유형별 요청 |
| 368 | A-06 | 공통 / ErrorCode | 모든 TASK에서 ErrorCode.java 요청 | B-06은 고정 파일 강제 금지 | 충돌 | 제외 | docs/process/TASK-START-CHECKLIST.md | 예외·응답 영향 TASK에만 요청하는 규칙으로 대체 | 예외 영향 누락 가능 | 예 | 자동 검증: 예외 관련 TASK 조건 |
| 369 | A-06 | 공통 / branch | 현재 브랜치 확인 | B-06 동일 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 명령 존재 | 중복 | 아니오 | 자동 검증: 허용 명령 |
| 370 | A-06 | 공통 / 최근 commit | 최근 5개 commit 확인 | B-06 동일 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 명령 존재 | 중복 | 아니오 | 자동 검증: 허용 명령 |
| 371 | A-06 | 신규 도메인 / 유사 구현 | 유사 Service·Controller·Entity 고정 경로 요청 | B-06 신규 기능 자료 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 확인된 유사 구현 경로를 요청 | 고정 경로 실패 | 예 | 자동 검증: 경로 존재 |
| 372 | A-06 | 기존 도메인 / 대상 파일 | Service·Entity 고정 경로 요청 | B-06은 호출 관계·테스트·실패 근거 포함 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 변경 영향 자료까지 포함 | Controller·Repository 영향 누락 | 예 | 자동 검증: 경로 존재·필수 schema |
| 373 | A-06 | Redis TASK | infra 관련 파일 요청 | B-06은 Key·TTL·Lua·장애·테스트 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 Redis 흐름 전체를 요청 | 한 파일로 판단 불가 | 예 | 자동 검증: Redis 필수 schema |
| 374 | A-06 | 테스트 TASK | test tree·특정 테스트 요청 | B-06 실행 명령·설정·결과 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 경로와 실행 근거를 함께 요청 | 고정 깊이로 누락 | 예 | 자동 검증: 경로·결과 필드 |
| 375 | A-06 | DB TASK | migration 목록·최신 SQL 요청 | B-06 Entity·제약·인덱스·호환성 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 현재 스키마·migration 영향 전체를 요청 | 잘못된 glob·기존 migration 수정 위험 | 예 | 자동 검증: migration 파일 목록·불변성 |
| 376 | A-06 | 보안 TASK | auth Service·domain 고정 파일 요청 | B-06 Security 설정·흐름·권한·테스트 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 인증 흐름 중심으로 요청 | 필터·설정 누락 | 예 | 자동 검증: 보안 필수 schema |
| 377 | A-06 | GPT 결과 전달 형식 | 기존 GPT_REVIEW 필드 전달 | B-07 Finding 형식 | 부분 대응 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 승인된 이전 Finding만 구조화 입력으로 사용 (관련 #573) | 과거 권고 자동 실행 | 예 | 자동 검증: Finding schema·승인 상태 |
| 378 | A-06 | Claude 자료 요청 형식 | TASK 번호와 실행 명령 목록을 사용자에게 요청 | B-06 자료 확인 흐름 | 부분 대응 | 수정 | docs/process/TASK-START-CHECKLIST.md | 실제 경로·민감정보 제한이 반영된 요청 형식으로 변경 (관련 #491, #492) | 불필요 명령·민감 파일 탐색 | 예 | 자동 검증: 허용 명령·금지 경로 |
| 379 | B-01 | 문서 목적 / 짧은 진입점 | 역할·승인·참조만 정의하고 상세 규칙 복사 금지 | 현재 B 고유 | 완전 대응 | 유지 | CLAUDE.md | 현재 단일 책임 유지 (관련 #18, #43, #315, #435) | 상세 규칙 재복제로 비대화 | 아니오 | 자동 검증: 필수 장·절·문서 링크 |
| 380 | B-01 | 문서 목적 / 200줄 목표 | 200줄 이하는 품질 목표이지 기술 제한 아님 | 현재 B 고유 | 완전 대응 | 유지 | CLAUDE.md | 가독성 목표와 로드 제한 오해 방지 | 고정 줄 수가 내용 누락을 유도할 수 있음 | 아니오 | 자동 검증: 줄 수 경고 |
| 381 | B-01 | 프로젝트 목적 / 구현 목표 | Spring Boot 티켓팅 포트폴리오 | A 개요들과 대응 | 완전 대응 | 병합 | CLAUDE.md | 중복 목적 문구 단일화 | 목적 드리프트 | 예 | 수동 검토 |
| 382 | B-01 | 프로젝트 목적 / 설명 가능성 | 트랜잭션·동시성·정합성·Redis·장애·테스트 근거 설명 | A-02 학습 목표·C-01 | 부분 대응 | 병합 | CLAUDE.md | 짧은 목표만 진입 문서에 유지 | 학습 계약과 중복 | 예 | 수동 검토 |
| 383 | B-01 | 프로젝트 목적 / 근거 우선 | 기존 코드·문서 우선, 미확인 상태 추측 금지 | B-04·B-06·B-07 | 완전 대응 | 병합 | CLAUDE.md | 진입 원칙 요약으로 유지 | 중복 드리프트 | 예 | 자동 검증: 금지 문구 |
| 384 | B-01 | 역할 / Claude | 승인 범위의 작성·실행 담당 | B-04 승인 게이트 | 완전 대응 | 병합 | CLAUDE.md | 역할 요약 유지 | 권한 범위 불일치 | 예 | 자동 검증: 역할 문구 |
| 385 | B-01 | 역할 / GPT | 독립 검토 담당 | B-07 역할 | 완전 대응 | 병합 | CLAUDE.md | 상세 계약 참조 | GPT 역할 드리프트 | 예 | 자동 검증: 역할 문구 |
| 386 | B-01 | 역할 / 사용자 | 범위·설계·구현·Git·최종 진행 승인 | B-04·B-07 | 완전 대응 | 병합 | CLAUDE.md | 최종 승인자 요약 유지 | 승인권 혼선 | 예 | 자동 검증: 승인자 문구 |
| 387 | B-01 | 역할 / 검토 결과 성격 | GPT·Agent 결과는 권고·검증 자료 | B-07·B-08 | 완전 대응 | 병합 | CLAUDE.md | 권고와 명령 분리 | 자동 실행 오해 | 예 | 자동 검증: 권고 문구 |
| 388 | B-01 | 작업 시작 / TASK·Issue·단계 | 현재 TASK·Issue·승인 단계·허용 범위 확인 | B-04·B-06 일부 | 부분 대응 | 병합 | CLAUDE.md | 진입 요약은 유지하고 상세 workflow 참조 | 시작 게이트 드리프트 | 예 | 자동 검증: 필수 장·절 |
| 389 | B-01 | 작업 시작 / 기존 자료 | 기존 코드·기준 문서 우선 확인 | B-04·B-06 일부 | 부분 대응 | 병합 | CLAUDE.md | 진입 요약은 유지하고 상세 workflow 참조 | 시작 게이트 드리프트 | 예 | 자동 검증: 필수 장·절 |
| 390 | B-01 | 작업 시작 / 입력 부족 | 자료 부족 시 구현하지 않고 보고 | B-04·B-06 일부 | 부분 대응 | 병합 | CLAUDE.md | 진입 요약은 유지하고 상세 workflow 참조 | 시작 게이트 드리프트 | 예 | 자동 검증: 필수 장·절 |
| 391 | B-01 | 작업 시작 / 설계 승인 | 필요 단계에서 승인 후 작업 | B-04·B-06 일부 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약은 유지하고 상세 workflow 참조 | 시작 게이트 드리프트 | 예 | 자동 검증: 필수 장·절 |
| 392 | B-01 | 작업 시작 / 허용 파일·명령 | 현재 단계에 승인된 것만 사용 | B-04·B-06 일부 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약은 유지하고 상세 workflow 참조 | 시작 게이트 드리프트 | 예 | 자동 검증: 필수 장·절 |
| 393 | B-01 | 승인 게이트 / 구현 | 승인 전 구현 금지 | B-04와 중복 | 완전 대응 | 병합 | CLAUDE.md | CLAUDE에는 요약, B-04·workflow에 상세 | 중복 드리프트 | 예 | 자동 검증: 금지 동사·승인 상태 |
| 394 | B-01 | 승인 게이트 / Git | branch·commit·push·PR·merge 각각 승인 단계에서만 | B-04와 중복 | 완전 대응 | 병합 | CLAUDE.md | CLAUDE에는 요약, B-04·workflow에 상세 | 중복 드리프트 | 예 | 자동 검증: 금지 동사·승인 상태 |
| 395 | B-01 | 승인 게이트 / 테스트 표현 | 미실행 테스트를 실행·통과로 표현 금지 | B-04와 중복 | 완전 대응 | 병합 | CLAUDE.md | CLAUDE에는 요약, B-04·workflow에 상세 | 중복 드리프트 | 예 | 자동 검증: 금지 동사·승인 상태 |
| 396 | B-01 | 승인 게이트 / 자동 진행 | 다음 단계·TASK 자동 진행 금지 | B-04와 중복 | 완전 대응 | 병합 | CLAUDE.md | CLAUDE에는 요약, B-04·workflow에 상세 | 중복 드리프트 | 예 | 자동 검증: 금지 동사·승인 상태 |
| 397 | B-01 | 범위 통제 / TASK 밖 변경 | 범위 밖 파일·기능 임의 변경 금지 | B-04와 일부 중복 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약과 상세 규칙 분리 | 범위 밖 변경·오류 은폐 | 예 | 자동 검증: 금지 문자열·표식 |
| 398 | B-01 | 범위 통제 / 범위 밖 제안 | 구현하지 않고 `[범위 밖 제안]`으로 보고 | B-04와 일부 중복 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약과 상세 규칙 분리 | 범위 밖 변경·오류 은폐 | 예 | 자동 검증: 금지 문자열·표식 |
| 399 | B-01 | 범위 통제 / 문서 충돌 | 중복·충돌 문서를 임의 선택·통합 금지 | B-04와 일부 중복 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약과 상세 규칙 분리 | 범위 밖 변경·오류 은폐 | 예 | 자동 검증: 금지 문자열·표식 |
| 400 | B-01 | 범위 통제 / 추측 | 미확인 상태·버전·규칙·테스트 결과를 사실화 금지 | B-04와 일부 중복 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약과 상세 규칙 분리 | 범위 밖 변경·오류 은폐 | 예 | 자동 검증: 금지 문자열·표식 |
| 401 | B-01 | 범위 통제 / 오류 | 오류 우회 없이 명령·오류 보고 | B-04와 일부 중복 | 완전 대응 | 병합 | CLAUDE.md | 진입 요약과 상세 규칙 분리 | 범위 밖 변경·오류 은폐 | 예 | 자동 검증: 금지 문자열·표식 |
| 402 | B-01 | 검증 보고 / Git 상태 | 변경 전후 Git 상태 확인 | B-04·B-07 | 부분 대응 | 병합 | CLAUDE.md | 진입 원칙과 참조를 유지 | 증거 과장·민감정보 노출 | 예 | 자동 검증: 필수 보고 필드·금지 문자열 |
| 403 | B-01 | 검증 보고 / 실제 출력 | 명령·실제 출력·변경 파일·미확인 구분 | B-04·B-07 | 부분 대응 | 병합 | CLAUDE.md | 진입 원칙과 참조를 유지 | 증거 과장·민감정보 노출 | 예 | 자동 검증: 필수 보고 필드·금지 문자열 |
| 404 | B-01 | 검증 보고 / 민감정보 | 민감정보·로컬 절대 경로 기록 금지 | B-04·B-07 | 완전 대응 | 병합 | CLAUDE.md | 진입 원칙과 참조를 유지 | 증거 과장·민감정보 노출 | 예 | 자동 검증: 필수 보고 필드·금지 문자열 |
| 405 | B-01 | 검증 보고 / 위험 리뷰 | 필요 시 Agent·GPT 계약 사용 | B-04·B-07 | 부분 대응 | 유지 | CLAUDE.md | 진입 원칙과 참조를 유지 | 증거 과장·민감정보 노출 | 예 | 자동 검증: 필수 보고 필드·금지 문자열 |
| 406 | B-01 | 기준 문서 / 교차 규칙 | PROJECT-RULES 참조 | 현재 B 고유 | 완전 대응 | 유지 | CLAUDE.md | 진입점의 핵심 책임 | 깨진 경로 | 아니오 | 자동 검증: 문서 링크 `docs/process/PROJECT-RULES.md` |
| 407 | B-01 | 기준 문서 / 로드맵 | PORTFOLIO-ROADMAP 참조 | 현재 B 고유 | 완전 대응 | 유지 | CLAUDE.md | 진입점의 핵심 책임 | 깨진 경로 | 아니오 | 자동 검증: 문서 링크 `docs/process/PORTFOLIO-ROADMAP.md` |
| 408 | B-01 | 기준 문서 / 시작 자료 | TASK-START-CHECKLIST 참조 | 현재 B 고유 | 완전 대응 | 유지 | CLAUDE.md | 진입점의 핵심 책임 | 깨진 경로 | 아니오 | 자동 검증: 문서 링크 `docs/process/TASK-START-CHECKLIST.md` |
| 409 | B-01 | 기준 문서 / GPT 계약 | GPT-REVIEW-CONTRACT 참조 | 현재 B 고유 | 완전 대응 | 유지 | CLAUDE.md | 진입점의 핵심 책임 | 깨진 경로 | 아니오 | 자동 검증: 문서 링크 `docs/process/GPT-REVIEW-CONTRACT.md` |
| 410 | B-02 | permissions.deny | Read(./.env)·Read(./.envrc) 거부 | B-04·B-06 민감정보 규칙 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 실행 가능한 최소 차단 (관련 #463, #543) / B-02 설정 파일 자체는 비변경으로 유지하고 민감 경로 자료 요청 금지 원칙은 TASK-START-CHECKLIST에서 추적 | JSON 오류 또는 deny 누락 | 아니오 | 자동 검증: JSON 문법·deny 값 |
| 411 | B-03 | frontmatter / name·description | Agent 식별자와 사용 목적 | B-08 Agent SHA·평가 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 등록·호출 책임 | 이름 변경 시 참조 깨짐 | 아니오 | 자동 검증: frontmatter 필드 |
| 412 | B-03 | frontmatter / tools | Read·Grep·Glob만 허용 | B-08 권한 제한 판정 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | 읽기 전용 권한 유지 | 권한 확대·평가 근거 훼손 | 아니오 | 자동 검증: 허용 도구 목록 |
| 413 | B-03 | 역할 | 승인 TASK 위험 검토, 작성·수정·명령·테스트 실행 금지 | B-07 역할과 유사 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 고유 권한 경계 | GPT·Claude 역할 혼동 | 아니오 | 자동 검증: 금지 동사 |
| 414 | B-03 | 일반 검토 모드 / 입력 | PROJECT-RULES·승인 요구·승인 코드·테스트·문서 확인 | B-07 단계별 필수 자료 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 일반 모드 입력 경계 (관련 #298, #415, #416) | 필요 자료 부족 | 아니오 | 자동 검증: 필수 schema 필드 |
| 415 | B-03 | 일반 검토 모드 / 범위 | 승인 범위 밖 파일 미열람·미검토 | B-04 범위 통제 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 특화 권한 제한 (관련 #298, #414, #416) | 범위 초과 읽기 | 아니오 | 자동 검증: 허용 파일 목록 |
| 416 | B-03 | 고정 패킷 모드 / 입력 경계 | REVIEW_PACKET 경계 내부만 사용 | B-08 고정 패킷 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | 재현 가능한 독립 검토 (관련 #298, #414, #415) | 외부 정보 혼입 | 아니오 | 자동 검증: 패킷 경계·SHA |
| 417 | B-03 | 고정 패킷 모드 / 기준 | 패킷 내부 PROJECT-RULES 스냅샷 사용, 외부 파일 미열람 | B-08 동일 입력 원칙 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | 사후 기준 고정 | 현재 규칙과 혼동 | 아니오 | 자동 검증: 외부 파일 미사용·패킷 필드 |
| 418 | B-03 | 고정 패킷 모드 / 자료 부족 | 패킷 밖 필요 정보는 확정 Finding이 아닌 근거 부족 | B-07 근거 부족 원칙 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | 추정 Finding 방지 | 실제 위험 누락 가능 | 아니오 | 자동 검증: 근거 충분 여부 필드 |
| 419 | B-03 | 검토 범위 | 트랜잭션·동시성·상태·정합성·멱등성·장애·롤백·재시도·중복·실패 테스트 | B-07 DEEP_DIVE | 완전 대응 | 병합 | .claude/agents/ticketing-risk-reviewer.md | 공통 범주 schema를 참조하는 후보 | Agent·GPT 범주 드리프트 | 예 | 자동 검증: 검토 범주 집합 |
| 420 | B-03 | 검토 원칙 / 직접 근거 | 제공 코드·문서의 직접 사실만 사용 | B-07 일부 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: Finding 근거 필드 |
| 421 | B-03 | 검토 원칙 / 위치 | 상대 경로와 가능하면 스냅샷 줄 번호 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: 절대 경로 금지·위치 필드 |
| 422 | B-03 | 검토 원칙 / 미실행 | 테스트를 실행한 것처럼 표현 금지 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: 실행 출처 필드 |
| 423 | B-03 | 검토 원칙 / 테스트 방어 | 테스트 결과와 위험 방어 여부 구분 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 수동 검토 |
| 424 | B-03 | 검토 원칙 / 근거 부족 | 부족 항목을 Finding 수에서 제외 | B-07 일부 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: 근거 충분 여부 |
| 425 | B-03 | 검토 원칙 / 범위 밖 | TASK 밖 개선을 결함과 분리 | B-07 일부 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: TASK 범위 필드 |
| 426 | B-03 | 검토 원칙 / 스타일 | 스타일 취향을 결함으로 분류 금지 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 수동 검토 |
| 427 | B-03 | 검토 원칙 / 중복 | 동일 원인을 여러 Finding으로 중복 금지 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 수동 검토 |
| 428 | B-03 | 검토 원칙 / 비수정 | 전체 재작성·직접 수정 금지 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: 금지 동사 |
| 429 | B-03 | 검토 원칙 / 무Finding | 문제 없으면 범위·한계 기록 | B-07 일부 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 품질 기준 유지 | 근거 없는 Finding 또는 정보 손실 | 아니오 | 자동 검증: 검토 한계 필드 |
| 430 | B-03 | 출력 / Finding | 심각도·범주·위치·조건·위험·근거·조치·근거 충분·범위 | B-07 Finding과 유사 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 schema와 Agent 부가 필드 경계 결정 (관련 #573) / 최종 결정 7에 따라 공통 Finding schema와 Severity SSOT를 GPT-REVIEW-CONTRACT로 확정 | 자동 비교 불가 또는 Agent 정보 소실 | 예 | 자동 검증: 필수 schema 필드 |
| 431 | B-03 | 출력 / 근거 부족 | 항목·부족 자료·확정 불가 이유 | B-07 공통 원칙만 존재 | 부분 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | Agent 고유 한계 보고 | 추정 Finding | 아니오 | 자동 검증: 필수 schema 필드 |
| 432 | B-03 | 출력 / 검토 한계 | 미확인 파일·실행 결과·범위 밖 파일 | B-07 직접 형식 없음 | 대응 없음 | 유지 | .claude/agents/ticketing-risk-reviewer.md | 검토 범위 투명성 | 완전 검토로 오해 | 아니오 | 자동 검증: 필수 장·절 |
| 433 | B-03 | 출력 / 요약 | Critical·Warning·Info·근거 부족·범위 밖 수 | B-08 비교에 사용 | 완전 대응 | 유지 | .claude/agents/ticketing-risk-reviewer.md | 정량 비교 가능 | 합계 불일치 | 아니오 | 자동 검증: Finding 합계 |
| 434 | B-03 | 출력 / 최종 권고 | 진행 가능·수정 후·진행 금지와 근거 | B-07 동일 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 권고 enum 후보 / 최종 결정 7에 따라 공통 Finding schema와 Severity SSOT를 GPT-REVIEW-CONTRACT로 확정 | 권고 형식 드리프트 | 예 | 자동 검증: 허용 enum |
| 435 | B-04 | 문서 책임 | 승인·범위·검증·민감정보·Git만 정의 | A-03 상세 기술 기준 선언과 충돌 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 상세 기술 규칙 위치 결정 후 책임 재서술 (관련 #18, #43, #315, #379) (관련 #23, #135, #560) / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 기술 기준 공백 또는 문서 비대화 | 예 | 수동 검토 |
| 436 | B-04 | 참조 순서 / Issue·승인 | 현재 Issue와 승인 단계 우선 | B-01·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 고유 우선순위 | 승인 범위 오판 | 아니오 | 자동 검증: 참조 순서 |
| 437 | B-04 | 참조 순서 / 코드·테스트 | 직접 관련 코드·테스트 우선 | B-01·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 근거 우선 원칙 | 추측 판단 | 아니오 | 자동 검증: 참조 순서 |
| 438 | B-04 | 참조 순서 / 주제별 문서 | README·architecture·ADR·API·DB·performance 경로 | 패킷은 경로 존재를 검증하지 않음 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 참조 후보로만 유지하고 존재 검사 필요 | 깨진 링크 | 예 | 자동 검증: 문서 링크 |
| 439 | B-04 | 참조 순서 / 교차 규칙·process | 이 문서와 나머지 process 문서 순서 | B-01 기준 문서 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 우선순위 명시 | 순환 참조 | 아니오 | 자동 검증: 문서 링크·순환 |
| 440 | B-04 | 작업 형식 / Issue 제목 | TASK-XXX 작업 내용 요약 | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 (관련 #71, #286) | 중복 드리프트 | 아니오 | 자동 검증: 정규식 |
| 441 | B-04 | 작업 형식 / Issue 본문 | Goal→Background→Scope→DoD→Test→Notes | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 (관련 #72, #285) | 중복 드리프트 | 아니오 | 자동 검증: 필수 장·절 |
| 442 | B-04 | 작업 형식 / branch | {prefix}/TASK-XXX-english-kebab-case | A-02·A-04 유사 규칙 | 충돌 | 수정 | docs/process/PROJECT-RULES.md | 간단요약과 영문 kebab 충돌 결정 필요 / 최종 결정 10에 따라 branch naming 형식을 Git metadata SSOT인 PROJECT-RULES로 확정 | 중복 드리프트 | 예 | 자동 검증: 정규식 |
| 443 | B-04 | 작업 형식 / commit | {prefix}(TASK-XXX): 대상과 결과 | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 (관련 #115, #224) | 중복 드리프트 | 아니오 | 자동 검증: 정규식 |
| 444 | B-04 | 작업 형식 / PR 제목 | {prefix}(TASK-XXX): 요약 | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 | 중복 드리프트 | 아니오 | 자동 검증: 정규식 |
| 445 | B-04 | 작업 형식 / prefix | feat·fix·refactor·test·docs·chore·build | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 | 중복 드리프트 | 아니오 | 자동 검증: 허용 enum |
| 446 | B-04 | 작업 형식 / Assignee | pil97 | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 | 중복 드리프트 | 아니오 | 자동 검증: 필수 값 |
| 447 | B-04 | 작업 형식 / 계정·Reviewer | pil97·seungpil97 | A-02·A-04 유사 규칙 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 단일 기준 유지 | 중복 드리프트 | 아니오 | 자동 검증: 필수 값 |
| 448 | B-04 | 작업 형식 / 승인 Issue 재확인 | TASK 번호·제목·계정·Reviewer를 승인 Issue에서 재확인 | A 직접 동일 없음 | 대응 없음 | 유지 | docs/process/PROJECT-RULES.md | 현재 B 고유 안전 강화 | 오래된 기본값 사용 | 아니오 | 자동 검증: Issue 메타와 문서 값 대조 |
| 449 | B-04 | 승인 게이트 / 현재 단계 | 승인된 단계만 수행 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 단일 기준 후보 | CLAUDE와 드리프트 | 예 | 자동 검증: 승인 상태·금지 동사 |
| 450 | B-04 | 승인 게이트 / 설계 | 승인 전에 구현 금지 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 단일 기준 후보 | CLAUDE와 드리프트 | 예 | 자동 검증: 승인 상태·금지 동사 |
| 451 | B-04 | 승인 게이트 / 쓰기 작업 | 파일·테스트·branch·commit·push·PR·merge는 승인 범위에서만 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 단일 기준 후보 | CLAUDE와 드리프트 | 예 | 자동 검증: 승인 상태·금지 동사 |
| 452 | B-04 | 승인 게이트 / 검토 결과 | GPT·Agent는 권고, 사용자가 최종 결정 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 단일 기준 후보 | CLAUDE와 드리프트 | 예 | 자동 검증: 승인 상태·금지 동사 |
| 453 | B-04 | 승인 게이트 / 자동 진행 | 다음 단계·TASK 자동 시작 금지 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 단일 기준 후보 | CLAUDE와 드리프트 | 예 | 자동 검증: 승인 상태·금지 동사 |
| 454 | B-04 | 범위 / 허용 파일 | Issue·승인 지시 파일·동작만 변경 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 범위 기준 유지 | 범위 밖 변경 | 예 | 자동 검증: 허용 파일 목록 |
| 455 | B-04 | 범위 / 범위 밖 제안 | 수정하지 않고 `[범위 밖 제안]` | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 범위 기준 유지 | 범위 밖 변경 | 예 | 자동 검증: 표식 |
| 456 | B-04 | 범위 / 불필요 추가 | 리팩터링·의존성·설정·샘플·자동화 임의 추가 금지 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 범위 기준 유지 | 범위 밖 변경 | 예 | 자동 검증: 허용 파일·의존성 diff |
| 457 | B-04 | 범위 / 추측 | 미확인 코드·규칙을 문서에 반영 금지 | B-01과 중복 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | 상세 범위 기준 유지 | 범위 밖 변경 | 예 | 수동 검토 |
| 458 | B-04 | 테스트 보고 / 환경 | DB·Redis·환경·프로필 확인 | A-02 일부 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 main 기준 규칙 유지 | 검증 결과 왜곡 또는 명령 드리프트 | 아니오 | 자동 검증: 필수 보고 필드 |
| 459 | B-04 | 테스트 보고 / 실제 결과 | 실행한 명령과 성공·실패·스킵만 보고 | A-02 일부 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 main 기준 규칙 유지 | 검증 결과 왜곡 또는 명령 드리프트 | 아니오 | 자동 검증: 결과 schema |
| 460 | B-04 | 테스트 보고 / 미실행 | 미실행 테스트 통과 표현 금지 | A-02 일부 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 main 기준 규칙 유지 | 검증 결과 왜곡 또는 명령 드리프트 | 아니오 | 자동 검증: 실행 출처 필드 |
| 461 | B-04 | 테스트 보고 / 전체 명령 | 현재 CI 기준 Gradle 명령 | A-02 일부 | 충돌 | 유지 | docs/process/PROJECT-RULES.md | 현재 main 기준 규칙 유지 | 검증 결과 왜곡 또는 명령 드리프트 | 아니오 | 자동 검증: 명령 문자열 단일성 |
| 462 | B-04 | 테스트 보고 / 오류 우회 | 오류를 우회해 성공처럼 만들지 않음 | A-02 일부 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 main 기준 규칙 유지 | 검증 결과 왜곡 또는 명령 드리프트 | 아니오 | 수동 검토 |
| 463 | B-04 | 민감정보 / 출력 금지 | .env·.envrc·토큰·비밀번호·API Key·인증 헤더 값 금지 | B-02·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 가장 상세한 안전 규칙 (관련 #410, #543) | 민감정보 노출 또는 근거 과삭제 | 아니오 | 자동 검증: 금지 문자열 |
| 464 | B-04 | 민감정보 / 존재 여부 | 필요 시 존재 여부만 확인 | B-02·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 가장 상세한 안전 규칙 | 민감정보 노출 또는 근거 과삭제 | 아니오 | 수동 검토 |
| 465 | B-04 | 민감정보 / 원격 URL | 토큰 포함 가능 URL 전체 출력 금지 | B-02·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 가장 상세한 안전 규칙 | 민감정보 노출 또는 근거 과삭제 | 아니오 | 자동 검증: URL 패턴 |
| 466 | B-04 | 민감정보 / 저장 전 비식별 | 인증정보·환경값·토큰 URL·절대 경로·세션 ID 제거 | B-02·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 가장 상세한 안전 규칙 | 민감정보 노출 또는 근거 과삭제 | 아니오 | 자동 검증: 금지 문자열·절대 경로 |
| 467 | B-04 | 민감정보 / 검증 근거 유지 | 비식별 후 성공·실패·파일 경로 유지 | B-02·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 현재 가장 상세한 안전 규칙 | 민감정보 노출 또는 근거 과삭제 | 아니오 | 수동 검토 |
| 468 | B-04 | Git / 읽기 조회 | 승인 범위에서 읽기 전용 상태·이력 조회 | B-01·B-06 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | Git 상세 단일 기준 | 사용자 변경 손상 | 예 | 자동 검증: 허용 명령 |
| 469 | B-04 | Git / 쓰기 승인 | branch·commit·push·PR·merge 별도 승인 | B-01·B-06 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | Git 상세 단일 기준 | 사용자 변경 손상 | 예 | 자동 검증: 승인 상태 |
| 470 | B-04 | Git / status | 작업 전후 git status --short | B-01·B-06 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | Git 상세 단일 기준 | 사용자 변경 손상 | 예 | 자동 검증: 필수 명령 |
| 471 | B-04 | Git / 커밋 가능 보고 | 테스트·DoD 미확인 시 커밋 가능으로 보고 금지 | B-01·B-06 | 완전 대응 | 병합 | docs/process/PROJECT-RULES.md | Git 상세 단일 기준 | 사용자 변경 손상 | 예 | 자동 검증: 게이트 상태 |
| 472 | B-04 | Git / 위험 명령 | reset·force push 등 손상 명령 임의 사용 금지 | B-01·B-06 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | Git 상세 단일 기준 | 사용자 변경 손상 | 아니오 | 자동 검증: 금지 명령 |
| 473 | B-04 | 위험 / Severity | Critical·Warning·Info 정의 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 검토 계약의 단일 enum 후보 (관련 #95, #337, #573) | 중복 드리프트 | 예 | 자동 검증: 허용 enum |
| 474 | B-04 | 위험 / Critical 처리 | 근거와 위험 먼저 보고하고 사용자 판단 대기 | B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 자동 수정 금지와 결합 (관련 #97, #339) | Critical 자동 실행 또는 지연 | 예 | 자동 검증: 사용자 결정 필드 |
| 475 | B-04 | 위험 / 근거 부족 | 확정 문제와 분리 | B-03·B-07 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | Finding schema로 통합 | 추정 문제 | 예 | 자동 검증: 근거 충분 여부 |
| 476 | B-04 | 문서 충돌 / 임의 우선순위 금지 | 충돌 시 새 우선순위 생성 금지 | B-01 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | TASK-060 보호 규칙 | 자동 통합 | 아니오 | 자동 검증: 필수 장·절 |
| 477 | B-04 | 문서 충돌 / 보고 | 경로·문구·코드 차이를 보고 | B-01 일부 | 부분 대응 | 유지 | docs/process/PROJECT-RULES.md | 충돌 근거 schema | 충돌 원인 추적 불가 | 아니오 | 자동 검증: 필수 schema 필드 |
| 478 | B-04 | 문서 충돌 / 승인 전 삭제 금지 | 승인 전 통합·삭제 금지 | B-01 | 완전 대응 | 유지 | docs/process/PROJECT-RULES.md | 기존 정보 보호 | 정보 손실 | 아니오 | 자동 검증: 승인 상태 |
| 479 | B-05 | 문서 책임 / 검증 구간 | 현재 저장소·승인 기록으로 확인된 구간만 기록 | A-01 전체 로드맵과 충돌 | 충돌 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 전체 이력과 활성 구간 구조 결정 (관련 #1, #480, #481) / 최종 결정 1에 따라 전체 TASK 이력·활성 구간·상태 기준 책임을 Roadmap으로 확정 | 전체 이력 소실 또는 미검증 복원 | 예 | 수동 검토 |
| 480 | B-05 | 문서 책임 / 외부 로드맵 미복사 | 전체 외부 로드맵을 복사하지 않음 | A-01 전체 목록 | 충돌 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | A 원문을 근거로 한 이력 보존 범위 결정 (관련 #1, #479, #481) / 최종 결정 1에 따라 전체 TASK 이력·활성 구간·상태 기준 책임을 Roadmap으로 확정 | 검증되지 않은 계획 혼입 또는 정보 소실 | 예 | 수동 검토 |
| 481 | B-05 | 문서 책임 / 범위 고지 | TASK-031·059와 후속 중심, 나머지는 근거 후 추가 | A-01과 충돌 | 충돌 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 범위 선언을 최종 구조에 맞춰 갱신 (관련 #1, #479, #480) | 문서가 전체인지 일부인지 오인 | 예 | 자동 검증: 범위 선언 |
| 482 | B-05 | 상태 기준 / 완료 | Issue·PR·commit·검증 결과로 완료 근거 | A에는 명시적 증거 기준 없음 | 대응 없음 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 현재 B 고유 강화 | 증거 없는 상태 기록 | 아니오 | 자동 검증: 상태별 근거 필드 |
| 483 | B-05 | 상태 기준 / 진행 중 | Issue·작업 branch 확인, 완료 검증 전 | A에는 명시적 증거 기준 없음 | 대응 없음 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 현재 B 고유 강화 | 증거 없는 상태 기록 | 아니오 | 자동 검증: 상태별 근거 필드 |
| 484 | B-05 | 상태 기준 / 계획 | 선행 완료와 사용자 승인 후 예정 | A에는 명시적 증거 기준 없음 | 대응 없음 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 현재 B 고유 강화 | 증거 없는 상태 기록 | 아니오 | 자동 검증: 상태별 근거 필드 |
| 485 | B-05 | 확인 완료 / TASK-031 | Issue #96·PR #97·commit 근거 | A-01 TASK-031 | 완전 대응 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 완료 근거가 추가됨 | 근거 삭제 | 아니오 | 자동 검증: TASK ID·근거 |
| 486 | B-05 | 개발 프로세스 / TASK-059 상태 | TASK-059를 진행 중으로 기록 | 고정 main SHA는 TASK-059 squash merge, branch 삭제 확인 | 충돌 | 수정 | docs/process/PORTFOLIO-ROADMAP.md | 현재 main의 완료 근거와 로드맵 상태가 불일치 (관련 #9) / 최종 결정 2: TASK-059=완료, 근거 SHA `122816d91533befcc7a63c06cf83a7150e1fd05d` | 완료 TASK를 진행 중으로 오인해 후속 순서 왜곡 | 예 | 자동 검증: merge 근거와 로드맵 상태 대조 |
| 487 | B-05 | 후속 순서 / 코드블록 | 032→033→033-1→028→043 | A-01 동일 | 완전 대응 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 동일 순서 존재 (관련 #15) | 순서 드리프트 | 아니오 | 자동 검증: 순서 목록 |
| 488 | B-05 | 후속 순서 / 선행 조건 표 | 각 TASK의 선행 완료·사용자 승인 조건 | A-01에는 기간·CS 중심 | 부분 대응 | 유지 | docs/process/PORTFOLIO-ROADMAP.md | 현재 승인 통제 강화 | 선행 조건 누락 | 아니오 | 자동 검증: 선행 조건 필드 |
| 489 | B-05 | 후속 자동 시작 금지 | 완료 판정·다음 착수는 사용자 승인 | B-01·B-04 | 완전 대응 | 병합 | docs/process/PORTFOLIO-ROADMAP.md | 로드맵 특화 표현 유지 | 다음 TASK 자동 진행 | 예 | 자동 검증: 승인 조건 |
| 490 | B-05 | TASK 학습 종료 기준 | 7개 설명 질문 | A-01·C-01 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 상세 계약 단일화, 로드맵은 참조 (관련 #17, #608, #618, #680, #681, #682, #683, #684) | 기준 드리프트 | 예 | 자동 검증: 7개 질문 |
| 491 | B-06 | 목적 / 최소 자료 | Issue·구현 범위 전 최소 자료 확인 | A-06 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 단일 책임 (관련 #378, #492) | 자료 부족 | 아니오 | 자동 검증: 문서 책임 |
| 492 | B-06 | 목적 / 고정 파일 금지 | 존재하지 않는 특정 파일을 모든 TASK에 강제하지 않음 | A-06과 충돌 | 충돌 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 원칙 (관련 #378, #491) | TASK별 핵심 파일 누락 가능 | 아니오 | 자동 검증: 고정 파일 강제 문구 |
| 493 | B-06 | 1. TASK 번호·목적 | TASK 번호와 작업 목적 | A-02·A-04·A-05 일부 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 시작 필드 유지 | 단계·번호 오판 | 아니오 | 자동 검증: 필수 schema 필드 |
| 494 | B-06 | 1. 로드맵 상태 | 현재 상태와 선행 조건 | A-02·A-04·A-05 일부 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 시작 필드 유지 | 단계·번호 오판 | 아니오 | 자동 검증: 필수 schema 필드 |
| 495 | B-06 | 1. Issue 존재 | 기존 GitHub Issue 여부 | A-02·A-04·A-05 일부 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 시작 필드 유지 | 단계·번호 오판 | 아니오 | 자동 검증: 필수 schema 필드 |
| 496 | B-06 | 1. 현재 단계 | Issue·설계·구현·리뷰·Git 중 단계 | A-02·A-04·A-05 일부 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 시작 필드 유지 | 단계·번호 오판 | 아니오 | 자동 검증: 필수 schema 필드 |
| 497 | B-06 | 2. 저장소 root | git rev-parse --show-toplevel | A-06 일부 | 대응 없음 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 자동 검증: 허용 명령 |
| 498 | B-06 | 2. 현재 branch | git branch --show-current | A-06 일부 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 자동 검증: 허용 명령 |
| 499 | B-06 | 2. 작업 트리 | git status --short | A-06 일부 | 대응 없음 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 자동 검증: 허용 명령 |
| 500 | B-06 | 2. 최근 commit | git log --oneline -5 | A-06 일부 | 완전 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 자동 검증: 허용 명령 |
| 501 | B-06 | 2. 원격 URL 금지 | 원격 URL 전체 출력 요청 금지 | A-06 일부 | 대응 없음 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 자동 검증: 금지 명령·URL |
| 502 | B-06 | 2. 오염 처리 | 변경이 있으면 기존 변경과 TASK 관계 확인 | A-06 일부 | 대응 없음 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 수동 검토 |
| 503 | B-06 | 2. branch 승인 | 승인 없이 생성·전환 금지 | A-06 일부 | 대응 없음 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 안전 강화 항목 | 민감 URL 노출·사용자 변경 손상 | 아니오 | 자동 검증: 승인 상태 |
| 504 | B-06 | 3. 요구사항 | Issue 또는 승인된 요구사항 | A-05·A-06 일부 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 파일명보다 목적 기반 공통 자료 | 구체 코드 부족 가능 | 아니오 | 자동 검증: 필수 schema 필드 |
| 505 | B-06 | 3. 목표·범위 | 목표와 구현 범위 | A-05·A-06 일부 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 파일명보다 목적 기반 공통 자료 | 구체 코드 부족 가능 | 아니오 | 자동 검증: 필수 schema 필드 |
| 506 | B-06 | 3. 완료 조건 | Definition of Done | A-05·A-06 일부 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 파일명보다 목적 기반 공통 자료 | 구체 코드 부족 가능 | 아니오 | 자동 검증: 필수 schema 필드 |
| 507 | B-06 | 3. 테스트 계획 | Test 계획 | A-05·A-06 일부 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 파일명보다 목적 기반 공통 자료 | 구체 코드 부족 가능 | 아니오 | 자동 검증: 필수 schema 필드 |
| 508 | B-06 | 3. 영향 기능 | 영향받을 기능 | A-05·A-06 일부 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 파일명보다 목적 기반 공통 자료 | 구체 코드 부족 가능 | 아니오 | 자동 검증: 필수 schema 필드 |
| 509 | B-06 | 3. 관련 경로 | 현재 구조를 확인할 파일 경로 | A-05·A-06 일부 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 파일명보다 목적 기반 공통 자료 | 구체 코드 부족 가능 | 아니오 | 자동 검증: 필수 schema 필드 |
| 510 | B-06 | 4. 기존 기능 / 대상 코드 | 수정 대상 코드 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 511 | B-06 | 4. 기존 기능 / 호출 관계 | 호출하는·호출받는 코드 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 512 | B-06 | 4. 기존 기능 / 테스트 | 관련 테스트 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 513 | B-06 | 4. 기존 기능 / 변경 근거 | 현재 실패·변경 필요 동작 근거 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 514 | B-06 | 4. 신규 / 유사 구현 | 가장 가까운 기존 구현 실제 경로 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 515 | B-06 | 4. 신규 / API·입출력 | 새 API 또는 입출력 요구 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 필수 schema |
| 516 | B-06 | 4. 신규 / 상태·데이터 | 상태 전이와 저장 데이터 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 517 | B-06 | 4. 신규 / 예외·권한 | 예외와 권한 요구 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 518 | B-06 | 4. 신규 / 테스트 | 테스트 시나리오 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 필수 schema |
| 519 | B-06 | 4. 트랜잭션 / 경계 | 시작·종료 위치 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 520 | B-06 | 4. 트랜잭션 / 상태·Repository | 상태 변경 코드와 Repository 호출 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 521 | B-06 | 4. 트랜잭션 / 락·원자성 | 락 또는 원자 연산 코드 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 522 | B-06 | 4. 트랜잭션 / 동시 시나리오 | 중복·동시 요청 시나리오 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 523 | B-06 | 4. 트랜잭션 / 실패 테스트 | 실패·롤백·재시도 테스트 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로·결과 필드 |
| 524 | B-06 | 4. Redis / 접근 코드 | 실제 Redis 접근 코드 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 525 | B-06 | 4. Redis / Key | Key 생성 위치 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 526 | B-06 | 4. Redis / TTL | TTL과 정리 방식 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 527 | B-06 | 4. Redis / Lua | Lua·원자 연산 위치 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 528 | B-06 | 4. Redis / 장애 | Redis 장애 시 동작 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 529 | B-06 | 4. Redis / 테스트 | 단위·통합 테스트 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로·결과 필드 |
| 530 | B-06 | 4. DB / Entity·Repository | 현재 Entity·Repository | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 531 | B-06 | 4. DB / migration | 스키마·최신 migration 목록 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 파일 목록 |
| 532 | B-06 | 4. DB / 인덱스·제약 | 인덱스와 제약조건 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 533 | B-06 | 4. DB / 기존 migration 수정 | 기존 migration 수정 여부 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 변경 파일 목록 |
| 534 | B-06 | 4. DB / 호환성 | 롤백·호환성 영향 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 535 | B-06 | 4. 보안 / 설정·흐름 | Security 설정과 인증 흐름 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 536 | B-06 | 4. 보안 / 권한 | 권한 검증 위치 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 537 | B-06 | 4. 보안 / 민감정보 | 민감정보 처리 방식 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 수동 검토 |
| 538 | B-06 | 4. 보안 / 실패·테스트 | 실패 응답과 보안 테스트 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로·결과 필드 |
| 539 | B-06 | 4. 테스트·인프라·문서 / 명령·설정 | 실행 명령과 설정 파일 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로·명령 |
| 540 | B-06 | 4. 테스트·인프라·문서 / CI·Docker | CI 또는 Docker 설정 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 541 | B-06 | 4. 테스트·인프라·문서 / 결과 | 기존 테스트와 실행 결과 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 결과 schema |
| 542 | B-06 | 4. 테스트·인프라·문서 / 문서 경로 | 변경 대상 문서 실제 경로 | A-06보다 확장 | 부분 대응 | 유지 | docs/process/TASK-START-CHECKLIST.md | 현재 목적 기반 자료 요구 유지 | 자료 과다 또는 판단 근거 부족 | 아니오 | 자동 검증: 경로 존재 |
| 543 | B-06 | 5. 비밀값 요청 금지 | .env·.envrc·비밀번호·토큰·API Key·인증 헤더 값 금지 | B-04와 중복 | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 요청 단계 요약, 상세는 B-04 참조 (관련 #410, #463) | 중복 드리프트 | 예 | 자동 검증: 금지 문자열 |
| 544 | B-06 | 5. 제한된 정보 | 존재 여부·변수명·비식별 실패 상태만 요청 | B-04와 중복 | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 요청 단계 요약, 상세는 B-04 참조 | 중복 드리프트 | 예 | 수동 검토 |
| 545 | B-06 | 5. 원격 URL | 토큰 가능 URL 전체 출력 금지 | B-04와 중복 | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 요청 단계 요약, 상세는 B-04 참조 | 중복 드리프트 | 예 | 자동 검증: URL 패턴 |
| 546 | B-06 | 5. 절대 경로·세션 | 저장소 문서 기록 금지 | B-04와 중복 | 완전 대응 | 병합 | docs/process/TASK-START-CHECKLIST.md | 요청 단계 요약, 상세는 B-04 참조 | 중복 드리프트 | 예 | 자동 검증: 절대 경로·세션 패턴 |
| 547 | B-06 | 6. 자료 판정 | 필요 자료가 모두 확인됐는지 판정 | A-02·A-04 workflow와 중복 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 체크리스트는 자료 완료 신호, 흐름은 workflow로 이동 후보 (관련 #43, #315, #548, #549, #550, #551, #552) | 책임 중복 | 예 | 자동 검증: workflow 단계·승인 상태 |
| 548 | B-06 | 6. 부족 시 중단 | 부족하면 Issue·구현 시작 금지 | A-02·A-04 workflow와 중복 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 체크리스트는 자료 완료 신호, 흐름은 workflow로 이동 후보 (관련 #43, #315, #547, #549, #550, #551, #552) | 책임 중복 | 예 | 자동 검증: workflow 단계·승인 상태 |
| 549 | B-06 | 6. Issue 초안 | 확인되면 Issue 초안 | A-02·A-04 workflow와 중복 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 체크리스트는 자료 완료 신호, 흐름은 workflow로 이동 후보 (관련 #43, #315, #547, #548, #550, #551, #552) | 책임 중복 | 예 | 자동 검증: workflow 단계·승인 상태 |
| 550 | B-06 | 6. ISSUE_REVIEW | Issue 검토 진행 | A-02·A-04 workflow와 중복 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 체크리스트는 자료 완료 신호, 흐름은 workflow로 이동 후보 (관련 #43, #315, #547, #548, #549, #551, #552) | 책임 중복 | 예 | 자동 검증: workflow 단계·승인 상태 |
| 551 | B-06 | 6. 구현 승인 | Issue·설계 승인 후 구현 | A-02·A-04 workflow와 중복 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 체크리스트는 자료 완료 신호, 흐름은 workflow로 이동 후보 (관련 #43, #315, #547, #548, #549, #550, #552) | 책임 중복 | 예 | 자동 검증: workflow 단계·승인 상태 |
| 552 | B-06 | 6. 자동 진행 금지 | 다음 단계는 승인 없이 진행 금지 | A-02·A-04 workflow와 중복 | 부분 대응 | 병합 | docs/process/DEVELOPMENT-WORKFLOW.md | 체크리스트는 자료 완료 신호, 흐름은 workflow로 이동 후보 (관련 #43, #315, #547, #548, #549, #550, #551) | 책임 중복 | 예 | 자동 검증: workflow 단계·승인 상태 |
| 553 | B-07 | 역할 / GPT | 독립 검토자, 구현·수정·명령·Git 금지 | A-05 PM 역할과 충돌 | 충돌 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 독립성 보존 (관련 #296) | 역할 혼입 | 아니오 | 자동 검증: 권한 문구 |
| 554 | B-07 | 역할 / Claude | 승인 범위 작성·실행 | B-01 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 역할 관계 요약 | 중복 | 예 | 자동 검증: 역할 문구 |
| 555 | B-07 | 역할 / 사용자 | 최종 결정·승인 | B-01·B-04 | 완전 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 권고와 명령 분리 | 승인권 혼선 | 예 | 자동 검증: 승인자 문구 |
| 556 | B-07 | 권고 성격 | 3개 권고는 최종 명령이 아님 | B-01·B-04 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 독립 검토 계약 핵심 | 자동 실행 오해 | 아니오 | 자동 검증: 권고 문구 |
| 557 | B-07 | 공통 / 필요한 자료만 | 현재 단계 자료만 요구 | A-05 Input 목록과 충돌 가능 | 부분 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 과도한 자료 요청 방지 | 핵심 자료 누락 | 아니오 | 수동 검토 |
| 558 | B-07 | 공통 / 하나라도 누락 | 필수 자료 하나라도 없으면 리뷰 중단 | A-05 2개 이상과 충돌 | 충돌 | 수정 | docs/process/GPT-REVIEW-CONTRACT.md | 핵심·보조 임계값 결정 필요 (관련 #349) / 최종 결정 5에 따라 핵심·보조 입력 누락 기준을 GPT review SSOT로 확정 | 과도한 중단 또는 불완전 리뷰 | 예 | 자동 검증: 필수 자료 상태 |
| 559 | B-07 | 공통 / 추측 금지 | 미제공 코드·설정·결과·운영 상태 추측 금지 | B-03 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 독립 검토 핵심 | 추정 Finding | 아니오 | 자동 검증: 근거 필드 |
| 560 | B-07 | 공통 / 기술 기준 | PROJECT-RULES 참조 | B-04 책임 충돌 가능 | 부분 대응 | 수정 | docs/process/PROJECT-RULES.md | 상세 기술 기준 위치 결정 필요 (관련 #23, #135, #435) / 최종 결정 3 및 Phase 2-6 기술 결정 10개에 따라 현재 근거 기반 기술 최소 원칙·인덱스 책임을 PROJECT-RULES로 확정 | 기준 공백 | 예 | 자동 검증: 문서 링크 |
| 561 | B-07 | 공통 / 중요 위험 | 가장 중요한 위험·차단 문제 우선 | B-04 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 우선순위 유지 | 낮은 위험 선행 | 아니오 | 자동 검증: Critical 선행 |
| 562 | B-07 | 공통 / 범위 밖 | 범위 밖 개선을 결함과 분리 | B-03·B-04 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 범위 분리 | 범위 확장 | 아니오 | 자동 검증: TASK 범위 필드 |
| 563 | B-07 | 공통 / 독립성 | Agent 결과 보기 전 독립 검토 | B-08 GPT 실행 방식 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 비교 공정성 | 결과 오염 | 아니오 | 자동 검증: 입력 패킷에 Agent 결과 부재 |
| 564 | B-07 | 공통 / 자동 진행 금지 | 검토 결과로 구현·Git 자동 진행 금지 | B-01·B-04 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 권고 한계 | 자동 실행 | 아니오 | 자동 검증: 금지 동사 |
| 565 | B-07 | ISSUE_REVIEW / 목적 | 요구사항·범위·DoD·Test 검증 가능성 | A-05 유사 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 (관련 #317, #567, #569, #571) | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 566 | B-07 | ISSUE_REVIEW / 필수 자료 | 제목·본문·Goal·Background·Scope·DoD·Test·로드맵·관련 경로 | A-05 유사 | 부분 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 567 | B-07 | CODE_REVIEW / 목적 | 승인 요구와 변경 코드 일치·위험 | A-05 유사 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 (관련 #317, #565, #569, #571) | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 568 | B-07 | CODE_REVIEW / 필수 자료 | 승인 Issue·설계·파일·diff·테스트 코드·실행 결과·해당 위험 설명 | A-05 유사 | 부분 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 569 | B-07 | DEEP_DIVE / 목적 | 커밋 전 정합성·장애·경계 집중 검증 | A-05 유사 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 (관련 #317, #565, #567, #571) | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 570 | B-07 | DEEP_DIVE / 필수 자료 | 요구·최종 diff·호출 흐름·경계·동시·장애·실패 테스트 결과 | A-05 유사 | 부분 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 571 | B-07 | PR_REVIEW / 목적 | PR의 범위·DoD·검증 결과 반영 | A-05 유사 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 (관련 #317, #565, #567, #569) | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 572 | B-07 | PR_REVIEW / 필수 자료 | PR·Issue·diff·DoD·테스트·수동 결과·미해결 위험 | A-05 유사 | 부분 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 구조화된 계약 유지 | 필수 자료 누락 또는 과도한 요구 | 아니오 | 자동 검증: 필수 장·절·schema |
| 573 | B-07 | Finding 형식 | 심각도·위치·조건·위험·근거·조치·근거 충분·범위 | B-03과 유사 | 부분 대응 | 병합 | docs/process/GPT-REVIEW-CONTRACT.md | 공통 schema 경계 결정 (관련 #95, #337, #473) (관련 #98, #234, #260, #261, #262, #263) (관련 #377) (관련 #430) / 최종 결정 7에 따라 공통 Finding schema와 Severity SSOT를 GPT-REVIEW-CONTRACT로 확정 | 자동 비교 불가 또는 Agent 정보 소실 | 예 | 자동 검증: 필수 schema 필드 |
| 574 | B-07 | 최종 권고 / 진행 가능 | 차단 근거 있는 문제 없음 | A-05 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 enum | 중복 | 아니오 | 자동 검증: 허용 enum |
| 575 | B-07 | 최종 권고 / 수정 후 | 현재 단계 전 보강 필요 | A-05 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 enum | 중복 | 아니오 | 자동 검증: 허용 enum |
| 576 | B-07 | 최종 권고 / 진행 금지 | 정합성·트랜잭션·동시성·보안·핵심 테스트·필수 자료 문제 | A-05 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 현재 enum | 중복 | 아니오 | 자동 검증: 허용 enum |
| 577 | B-07 | 최종 권고 / 사용자 결정 | 권고 근거와 사용자 결정 항목 명시 | B-01·B-04 | 완전 대응 | 유지 | docs/process/GPT-REVIEW-CONTRACT.md | 최종 승인자 보존 | 자동 실행 오해 | 아니오 | 자동 검증: 사용자 결정 필드 |
| 578 | B-08 | 1. 평가 목적 / 보조 가치 | Agent가 1차 보조 리뷰어로 가치 있는지 평가 | 현재 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 평가 목적 보존 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 단일 표본 일반화 | 아니오 | 자동 검증: 필수 장·절 |
| 579 | B-08 | 1. 평가 목적 / 비대체 | GPT 대체가 아니라 보조 역할 | B-01·B-07 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 역사적 결론 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | Agent 권한 확대 오용 | 아니오 | 자동 검증: 역할 문구 |
| 580 | B-08 | 1. 평가 목적 / 동일 입력 | 동일 고정 입력·공통 출력 계약 비교 | B-03·B-07 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 공정성 기준 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 입력 차이로 비교 무효 | 아니오 | 자동 검증: 패킷 SHA·schema |
| 581 | B-08 | 2. 평가 기준선 | TASK·Issue·PR·변경 전·완료 SHA·검토·문맥·제외 파일 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 재현성 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 범위·SHA 소실 | 아니오 | 자동 검증: 필수 schema·SHA |
| 582 | B-08 | 3. 패킷 SHA·줄 수 | 전체·경계 SHA와 3,148줄 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 입력 식별 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 패킷 오인 | 아니오 | 자동 검증: SHA·줄 수 |
| 583 | B-08 | 3. Git byte 검증 | diff·코드·문맥과 Git byte 대조 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 입력 진위 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 잘못된 스냅샷 평가 | 아니오 | 자동 검증: SHA·byte 대조 |
| 584 | B-08 | 3. 입력 동일성 | Agent와 GPT가 동일 경계 byte 사용 | B-03 고정 모드 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 비교 공정성 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 입력 차이 | 아니오 | 자동 검증: 경계 SHA 일치 |
| 585 | B-08 | 3. 이전 패킷 폐기 | 완료 SHA 불일치 패킷 폐기 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 오류 이력 투명성 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 폐기 결과 재사용 | 아니오 | 자동 검증: 폐기 상태 |
| 586 | B-08 | 4. Agent 실행 환경 | 버전·safe-mode·system prompt·도구 없음·외부 메모리 비활성 | B-03 frontmatter 일부 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 재현 조건 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 환경 차이 | 아니오 | 자동 검증: 실행 메타 필드 |
| 587 | B-08 | 4. Agent SHA | 전체·본문 SHA | B-03 파일 SHA | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 평가 대상 식별 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 다른 Agent 버전과 혼동 | 아니오 | 자동 검증: SHA 일치 |
| 588 | B-08 | 4. 등록 검증 범위 | 등록 기능 자체가 아닌 리뷰 품질 비교 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 검증 범위 제한 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 기능 검증으로 과장 | 아니오 | 수동 검토 |
| 589 | B-08 | 4. GPT 독립 실행 | 새 채팅·고정 경계만·힌트·Agent 결과 미제공 | B-07 독립성 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 공정성 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 검토 오염 | 아니오 | 자동 검증: 입력 패킷 항목 |
| 590 | B-08 | 5. 결과 수치 | 두 리뷰 SHA·줄 수·Finding 수·권고 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 비교 결과 식별 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 합계 불일치 | 아니오 | 자동 검증: 합계·SHA |
| 591 | B-08 | 5. 폐기 GPT 결과 | 폐기 SHA를 최종 비교에 미사용 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 결과 선택 투명성 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 폐기본 혼입 | 아니오 | 자동 검증: 폐기 상태 |
| 592 | B-08 | 6. Finding 대응표 | 공통·단독·부분 공통·최종 심각도·근거 | B-03·B-07 schema | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 비교 핵심 산출물 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 대응 누락 | 아니오 | 자동 검증: Finding 합계·ID |
| 593 | B-08 | 7. Agent 장점 | 탐지 성과·계약 준수·과잉 Critical 없음 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 균형 평가 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 장점 소실 | 아니오 | 수동 검토 |
| 594 | B-08 | 7. Agent 한계 | null·부분 만료·경로 불일치 미탐지·심각도 오분류 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 권한 확대 방지 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 한계 삭제로 과장 | 아니오 | 수동 검토 |
| 595 | B-08 | 8. 최종 판정 / 조건부 유지 | Agent 1차·GPT 2차·사용자 승인 | B-01·B-03·B-07 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 역사적 운영 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 단일 표본 영구 정책화 | 아니오 | 자동 검증: 역할 값 일치 |
| 596 | B-08 | 8. 최종 판정 / 권한 제한 | 자동 수정·Git 금지, Read·Grep·Glob 유지 | B-03 frontmatter | 완전 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 평가 결론과 실행 설정 연결 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 권한 확대 | 아니오 | 자동 검증: 도구 목록 |
| 597 | B-08 | 8. 최종 판정 / 확대 보류 | 다중 Agent·자동화 확대 미승인 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 단일 표본 한계 반영 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 과잉 자동화 | 아니오 | 수동 검토 |
| 598 | B-08 | 9. 코드·테스트 권고 | AOP·429·null 로그·부분 만료·경계 테스트 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 미완료 후속 권고 보존 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 완료 사실로 오인 | 아니오 | 자동 검증: 권고·완료 상태 구분 |
| 599 | B-08 | 9. 문서 권고 | DoD·엔드포인트 차이 정정·기록 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 추적성 권고 보존 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 완료 사실로 오인 | 아니오 | 자동 검증: 권고 상태 |
| 600 | B-08 | 9. 수정 금지 범위 | 평가 문서 생성 단계에서는 코드·기존 문서 미수정 | B-01·B-04 | 완전 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 평가 범위 보존 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 역사 기록과 실제 변경 혼동 | 아니오 | 자동 검증: 변경 파일 목록 |
| 601 | B-08 | 10. 민감 파일 검증 한계 | deny 인식·공식 형식·CLI 한계·비밀값 미노출·판정 불가 | B-02 설정 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 불확실성 보존 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 차단 완전 검증으로 과장 | 아니오 | 자동 검증: 한계 장 존재 |
| 602 | B-08 | 10. 추가 한계 | Issue·PR byte 미대조·로그 미제공·단일 표본·비결정성 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 결론 범위 제한 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 과장된 일반화 | 아니오 | 자동 검증: 한계 장 존재 |
| 603 | B-08 | 11. 결론 / 역할 | Agent 유지·GPT 독립 리뷰 필요 | B-01·B-07 | 부분 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 평가 결론 보존 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 프로세스 규칙과 역사 결론 혼동 | 아니오 | 수동 검토 |
| 604 | B-08 | 11. 결론 / 권한 확대 근거 없음 | Agent 권한 확대 미승인 | B-03 도구 제한 | 완전 대응 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 권한 제한 근거 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 과잉 권한 | 아니오 | 자동 검증: 도구 목록 |
| 605 | B-08 | 11. 결론 / 추가 표본 | 2~3개 TASK에서 재평가 | 역사 기록 고유 | 대응 없음 | 유지 | docs/process/DOCUMENT-MIGRATION-MATRIX.md | 단일 표본 한계 대응 / B-08은 수정 금지 역사 기록으로 유지하며 TASK-060에서는 정책 원문으로 재작성하지 않고 본 Matrix에서 provenance만 추적 | 재평가 누락 | 아니오 | 수동 검토 |
| 606 | C-01 | 서문 / 적용 대상 | 완료된 각 TASK에 적용 | B-05 학습 종료 기준 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 신규 계약의 적용 범위 | 모든 TASK에 동일 부담 | 예 | 수동 검토 |
| 607 | C-01 | 서문 / 목적 | 자기 말로 기술 선택·실패·테스트 한계·기여를 면접 방어 | B-01 설명 가능성·B-05 7문항 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 기존 요약을 구체화 | 기능 완료와 학습 완료 혼동 | 예 | 수동 검토 |
| 608 | C-01 | 1. 기본 / 구현과 학습 분리 | 구현 완료만으로 학습 종료 아님 | B-05 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 완료 상태 신설 (관련 #17, #490, #618, #680, #681, #682, #683, #684) | 완료 상태 복잡화 | 예 | 자동 검증: 완료 상태 enum |
| 609 | C-01 | 1. 기본 / 적용 시점 | 최종 코드·리뷰 확정 뒤 학습 시작 | B-07 리뷰 완료 이후와 연결 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 검토 전 불완전 코드 학습 방지 | Git 흐름 지연 | 예 | 자동 검증: 상태 전이 |
| 610 | C-01 | 1. 기본 / 읽기와 설명 구분 | 설명을 읽는 것과 직접 설명 능력 구분 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 이해도 검증 핵심 | 평가 시간 증가 | 예 | 수동 검토 |
| 611 | C-01 | 1. 기본 / 선답 금지 | 사용자 답변 전 모범 답안 비공개 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 암기 대신 회상 검증 | 학습 초반 어려움 | 예 | 자동 검증: 질문-답안 순서 |
| 612 | C-01 | 1. 기본 / 재답변 | 오류·누락만 보완한 뒤 같은 질문 재답변 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 오개념 교정 확인 | 반복 피로 | 예 | 자동 검증: 질문 상태 전이 |
| 613 | C-01 | 1. 기본 / 근거 구분 | 확인 사실·합리적 추론·추가 확인 구분 | B-03·B-07 근거 원칙 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 답변의 사실성 | 분류 주관성 | 예 | 자동 검증: 분류 enum |
| 614 | C-01 | 1. 기본 / 자료 밖 금지 | 프로젝트 자료 없는 내용을 사실화 금지 | B-03·B-07 | 완전 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 모드에도 근거 원칙 적용 | 추정 성과 과장 | 예 | 자동 검증: 근거 필드 |
| 615 | C-01 | 1. 기본 / AI와 사용자 기여 | AI 수행과 사용자 판단 분리 | B-08 평가 방식 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 면접 과장 방지 | 기여 구분 기록 부담 | 예 | 자동 검증: 기여 필드 |
| 616 | C-01 | 1. 기본 / 오개념 차단 | 치명적 오개념이 남으면 다음 단계 금지 | B-07 진행 금지와 유사 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 게이트 핵심 | 치명적 기준 주관성 | 예 | 수동 검토 |
| 617 | C-01 | 1. 기본 / 최종 답변 | 30초·90초·심층 답변을 사용자가 직접 완성 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 면접 산출물 정의 | 모든 TASK에 과도한 산출물 | 예 | 자동 검증: 필수 산출물 |
| 618 | C-01 | 2. 적용 흐름 | TASK 선택→계획→구현→테스트→Agent→GPT→Finding 결정→수정→학습→통과→Git | B-01·B-04·B-07과 부분 대응 | 부분 대응 | 신규 | docs/process/DEVELOPMENT-WORKFLOW.md | 학습 통과의 Git 게이트 위치 결정 필요 (관련 #17, #490, #608, #680, #681, #682, #683, #684) / 최종 결정 15 및 Phase 3-2B 승인 보완에 따라 learning timing과 merge gate 시점을 lifecycle SSOT에서 관리 | 개발 지연 또는 미학습 반영 | 예 | 자동 검증: workflow 단계 집합 |
| 619 | C-01 | 2. 기존 commit·push TASK 예외 | 이미 Git 작업된 TASK는 PR 전·merge 직후 학습 가능 | B 직접 없음 | 대응 없음 | 신규 | docs/process/DEVELOPMENT-WORKFLOW.md | 과거 TASK와 신규 TASK의 예외 경로 필요 / 최종 결정 15 및 Phase 3-2B 승인 보완에 따라 learning timing과 merge gate 시점을 lifecycle SSOT에서 관리 | 예외가 일반 흐름을 무력화 | 예 | 자동 검증: 예외 상태 전이 |
| 620 | C-01 | 3. 패킷 파일명 | 권장 파일명 TASK-XXX-LEARNING-PACKET.md | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 재현 가능한 식별 형식 | 이름 강제의 유연성 저하 | 예 | 자동 검증: 정규식 형식 |
| 621 | C-01 | 3. 입력 / Issue | Goal·Background·Scope·DoD·Test·Notes | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 필수 schema |
| 622 | C-01 | 3. 입력 / SHA | 변경 전·후 기준 SHA | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: SHA 형식 |
| 623 | C-01 | 3. 입력 / 변경 | 최종 변경 파일·핵심 diff | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 파일 목록·diff 필드 |
| 624 | C-01 | 3. 입력 / 주변 코드 | 요청 흐름에 필요한 주변 코드 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 수동 검토 |
| 625 | C-01 | 3. 입력 / 테스트 | 실행 테스트와 결과 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 결과 schema |
| 626 | C-01 | 3. 입력 / Agent 리뷰 | Claude Agent 결과 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 리뷰 섹션 |
| 627 | C-01 | 3. 입력 / GPT 리뷰 | GPT 독립 리뷰 결과 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 리뷰 섹션 |
| 628 | C-01 | 3. 입력 / Finding 결정 | 반영·미반영 Finding과 근거 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 결정 필드 |
| 629 | C-01 | 3. 입력 / 한계 | 확인된 현재 한계 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 수동 검토 |
| 630 | C-01 | 3. 입력 / 후속 TASK | 분리된 후속 항목 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: TASK ID 형식 |
| 631 | C-01 | 3. 입력 / 사용자 결정 | 사용자가 직접 결정한 내용 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 결정 필드 |
| 632 | C-01 | 3. 입력 / 계약 전문 | 이 계약의 전문 | B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷 필드 후보 | 패킷 비대화·민감정보 혼입 | 예 | 자동 검증: 문서 링크·SHA |
| 633 | C-01 | 3. 독립 리뷰 패킷 | 다른 결과·예상 Finding을 숨기고 결함 탐지 | B-03·B-08 | 완전 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 패킷과 정보 공개 범위 구분 | 리뷰 독립성 훼손 | 예 | 자동 검증: 패킷 유형·금지 섹션 |
| 634 | C-01 | 3. 학습 패킷 | 최종 코드·두 리뷰·판단까지 포함 | B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 전체 작업 이해 목적 | 검토 전 결과 노출과 혼동 | 예 | 자동 검증: 패킷 유형·필수 섹션 |
| 635 | C-01 | 4.1 문제 | 비즈니스·기술 문제, 방치 위험, 도메인 중요성 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 636 | C-01 | 4.2 해결 구조 | 요청 흐름·계층·컴포넌트·상태 저장·트랜잭션·외부 경계 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 637 | C-01 | 4.3 기술 선택 | 선택 이유·대안·장점·비용·프로젝트 적합성 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 638 | C-01 | 4.4 실패·운영 | 동시·중복·재시도·롤백·외부 장애·timeout·null·경계·다중 인스턴스·관측성·복구 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 639 | C-01 | 4.5 테스트·검증 | 테스트별 위험·역할·보장·비보장·거짓 양성·수동 구간 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 640 | C-01 | 4.6 리뷰 결과 | 공통·단독·심각도 차이·반영·미반영·근거 부족 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 641 | C-01 | 4.7 한계·개선 | 현재 한계·운영 전 검증·후속 TASK·재설계·복잡도 가치 | B-03·B-05·B-07·B-08 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명 단계의 독립 항목 | 일반 TASK에 과도한 깊이 | 예 | 수동 검토 |
| 642 | C-01 | 5. 놓치기 쉬운 관점 / 위험 목록 | 규칙·상태·동시성·트랜잭션·멱등성·부분 실패·관측성·보안·성능·문서·AI 미검증 | B-03 검토 범위 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 리뷰 위험을 학습 관점으로 전환 | 범위 무제한 확장 | 예 | 수동 검토 |
| 643 | C-01 | 5. 관점 분류 | 확인 사실·합리적 추론·추가 확인·TASK 범위 밖 | B-03 근거 충분·범위 필드 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 설명의 근거 수준 명시 | 분류 주관성 | 예 | 자동 검증: 허용 enum |
| 644 | C-01 | 6. 이해도 진행 / 한 질문 | 한 번에 질문 하나 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 집중된 회상 검증 | 진행 시간 증가 | 예 | 자동 검증: 동시 질문 수 |
| 645 | C-01 | 6. 이해도 진행 / 선답·평가 | 답 전 모범 답안 금지, 정확·부족 구분 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 암기 방지 | 초기 학습 난이도 | 예 | 자동 검증: 질문-답안 순서·평가 필드 |
| 646 | C-01 | 6. 이해도 진행 / 보완·재답변 | 부족 개념만 설명하고 같은·변형 질문 재답변 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 교정 확인 | 반복 피로 | 예 | 자동 검증: 상태 전이 |
| 647 | C-01 | 6. 이해도 진행 / 모름 | 정답 대신 단계별 힌트 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 사고 과정 지원 | 힌트 단계 기준 모호 | 예 | 수동 검토 |
| 648 | C-01 | 6. 이해도 진행 / 자기 말 | 복사 문장보다 자기 표현 유도 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 실제 면접 능력 검증 | 판정 주관성 | 예 | 수동 검토 |
| 649 | C-01 | 6. 필수 이해 질문 | 문제·흐름·원리·선택·대안·실패·동시·테스트·리뷰·한계·기여 등 14개 | B-05 7문항 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 기존 질문을 상세화 | 일반 TASK 과도한 질문 | 예 | 자동 검증: 질문 집합 |
| 650 | C-01 | 7. 평가 / 정확성·문제 이해 | 기술 오류와 해결 문제·위험 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가 rubric 구성 | 평가 주관성·항목 과다 | 예 | 수동 검토 |
| 651 | C-01 | 7. 평가 / 인과·원리 | 문제·선택·결과 연결과 흐름 원리 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가 rubric 구성 | 평가 주관성·항목 과다 | 예 | 수동 검토 |
| 652 | C-01 | 7. 평가 / 선택·대안 | 선택 근거와 대안 장단점 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가 rubric 구성 | 평가 주관성·항목 과다 | 예 | 수동 검토 |
| 653 | C-01 | 7. 평가 / 실패·테스트 | 장애·동시성·경계와 테스트 위험 연결 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가 rubric 구성 | 평가 주관성·항목 과다 | 예 | 수동 검토 |
| 654 | C-01 | 7. 평가 / 한계·기여 | 비보장 범위와 AI·사용자 기여 구분 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가 rubric 구성 | 평가 주관성·항목 과다 | 예 | 수동 검토 |
| 655 | C-01 | 7. 평가 / 전달·과장 | 면접 전달력과 검증되지 않은 성과 금지 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가 rubric 구성 | 평가 주관성·항목 과다 | 예 | 수동 검토 |
| 656 | C-01 | 7. 평가 등급 | 통과·부분 통과·재학습 필요·근거 부족 | B-07 권고와 다른 학습 enum | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 전용 상태 정의 | 검토 권고와 혼동 | 예 | 자동 검증: 허용 enum |
| 657 | C-01 | 8. 일반 CRUD·문서 | 문제·흐름·선택·기본 테스트·한계, Level 3 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | TASK 중요도별 깊이 차등화 | 등급 선정 기준 주관성 | 예 | 수동 검토 |
| 658 | C-01 | 8. 고위험 TASK | 인증·Redis·트랜잭션·동시성·결제·장애, Level 4 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | TASK 중요도별 깊이 차등화 | 등급 선정 기준 주관성 | 예 | 수동 검토 |
| 659 | C-01 | 8. 대표 포트폴리오 | 대안·트레이드오프·운영 확장·재설계·측정, Level 5 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | TASK 중요도별 깊이 차등화 | 등급 선정 기준 주관성 | 예 | 수동 검토 |
| 660 | C-01 | 8. AI 프로세스·자동화 | 자동화 이유·제한 권한·동일 입력·채택 근거·효과 측정, Level 4+ | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | TASK 중요도별 깊이 차등화 | 등급 선정 기준 주관성 | 예 | 수동 검토 |
| 661 | C-01 | 9.1 기본 면접 | 구현·필요·기여·어려움·테스트 | A-04 면접 검토 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 이해도 통과 후 기본 전달력 검증 | 학습·면접 완료 혼동 | 예 | 자동 검증: 질문 집합 |
| 662 | C-01 | 9.2 기술 심화 | 기술 선택·대안·동시성·장애·한계·운영 추가 | B-05 학습 질문 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 심층 설명 검증 | 일반 TASK 과도한 깊이 | 예 | 자동 검증: 질문 집합 |
| 663 | C-01 | 9.3 압박 질문 | 최선 여부·테스트 한계·다중 서버·부분 성공·AI 기여·재설계 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 꼬리 질문 대응 | 사용자 부담 증가 | 예 | 자동 검증: 질문 집합 |
| 664 | C-01 | 9. 질문 방식 | 면접 질문도 한 번에 하나 | 6장과 중복 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 공통 질문 진행 규칙 참조 | 중복 드리프트 | 예 | 자동 검증: 동시 질문 수 |
| 665 | C-01 | 10. 산출물 경로 | docs/learning/TASK-XXX/INTERVIEW-NOTES.md 권장 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 저장소 필수 범위 결정 필요 (관련 #666) / 최종 결정 16 및 Phase 3-2B 승인 보완에 따라 learning 산출물·최소 추적 기준을 Learning Contract로 확정 | 문서 폭증 또는 추적성 소실 | 예 | 자동 검증: 경로 정규식 |
| 666 | C-01 | 10. 산출물 내용 | 요약·문제·흐름·선택·실패·테스트·리뷰·판단·한계·기여·답변·복습 등 18항목 | A-02 devlog·완료 보고 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 결과 schema 후보 (관련 #665) | 모든 TASK에 과도한 문서 | 예 | 자동 검증: 필수 장·절 |
| 667 | C-01 | 11. 통과 / 오개념 | 치명적 오개념 0 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 최소 안전 기준 | 치명적 판단 주관성 | 예 | 수동 검토 |
| 668 | C-01 | 11. 통과 / 80% | 핵심 질문 80% 이상 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 정량 통과 기준 | 질문 수·재답변 계산 방식 모호 | 예 | 자동 검증: 점수 계산 |
| 669 | C-01 | 11. 통과 / 설명 연결 | 문제→선택→구현→검증→한계 연결 | B-05 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 통합 이해 검증 | 정성 평가 주관성 | 예 | 수동 검토 |
| 670 | C-01 | 11. 통과 / 무코드·대안·실패·테스트·기여 | 코드 없이 흐름, 대안, 실패, 보장·비보장, 기여 설명 | B-05 일부 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 핵심 이해 기준 | 일반 TASK 과도한 깊이 | 예 | 수동 검토 |
| 671 | C-01 | 11. 통과 / 30초·90초 | 두 답변을 자료 없이 말함 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 면접 전달력 기준 | 모든 TASK 반복 비용 | 예 | 수동 검토 |
| 672 | C-01 | 11. 미통과 처리 | 약한 개념에 기록하고 재질문 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 미완료 학습 추적 | 재학습 기록 부담 | 예 | 자동 검증: 약한 개념·상태 필드 |
| 673 | C-01 | 12. 학습 채팅 시작 프롬프트 | 패킷·계약만 근거로 설명→한 질문→평가→최종 답변까지 진행하는 재사용 프롬프트 | B 직접 없음 | 대응 없음 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 재현 가능한 학습 시작 절차 (관련 #674, #675, #676, #677, #678, #679) | 계약 변경 시 프롬프트 드리프트 | 예 | 자동 검증: 계약 필수 단계와 프롬프트 대조 |
| 674 | C-01 | 13. 금지 / 자료 밖 사실 | 자료 없는 구현 사실 추가 금지 | B-03·B-07 | 완전 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 학습 모드 근거 원칙 (관련 #673, #675, #676, #677, #678, #679) | 추정 성과 | 예 | 자동 검증: 근거 필드 |
| 675 | C-01 | 13. 금지 / 선답·다중 질문 | 선답·한 번에 많은 질문 금지 | 6장과 대응 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 진행 규칙 단일화 (관련 #673, #674, #676, #677, #678, #679) | 암기·인지 과부하 | 예 | 자동 검증: 질문 순서·개수 |
| 676 | C-01 | 13. 금지 / 암기·자동 통과 | 암기를 이해로 판정하거나 미이해를 자동 통과 금지 | 7·11장과 대응 | 부분 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 평가·통과 규칙과 연결 (관련 #673, #674, #675, #677, #678, #679) | 허위 학습 완료 | 예 | 수동 검토 |
| 677 | C-01 | 13. 금지 / AI 기여 과장 | AI 작업을 사용자 직접 구현으로 표현 금지 | 1·7장과 대응 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 기여 구분 단일화 (관련 #673, #674, #675, #676, #678, #679) | 면접 과장 | 예 | 자동 검증: 기여 필드 |
| 678 | C-01 | 13. 금지 / 테스트 안전 단정 | 테스트 통과만으로 운영 안전성 단정 금지 | 4.5·7장과 대응 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 보장·비보장 구분 강화 (관련 #673, #674, #675, #676, #677, #679) | 과도한 안전 주장 | 예 | 수동 검토 |
| 679 | C-01 | 13. 금지 / 면접 과장 | 검증되지 않은 성과 추가 금지 | 7장과 대응 | 완전 대응 | 병합 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 과장 방지 단일화 (관련 #673, #674, #675, #676, #677, #678) | 신뢰성 훼손 | 예 | 자동 검증: 근거·성과 필드 |
| 680 | C-01 | 14. 기능 완료 | 코드·테스트·문서가 승인 범위 충족 | B-04·B-07 | 부분 대응 | 신규 | docs/process/DEVELOPMENT-WORKFLOW.md | 완료 상태 모델의 첫 단계 (관련 #17, #490, #608, #618, #681, #682, #683, #684) | 상태 정의 중복 | 예 | 자동 검증: 완료 상태 enum |
| 681 | C-01 | 14. 리뷰 완료 | Agent·GPT 검토와 Finding 판단 완료 | B-07·B-08 | 부분 대응 | 신규 | docs/process/DEVELOPMENT-WORKFLOW.md | 기능 완료와 분리 (관련 #17, #490, #608, #618, #680, #682, #683, #684) | 리뷰 권고만으로 완료 오인 | 예 | 자동 검증: Finding 결정 상태 |
| 682 | C-01 | 14. 학습 완료 | 사용자가 문제·선택·원리·실패·테스트·한계 설명 | B-05 일부 | 부분 대응 | 신규 | docs/process/DEVELOPMENT-WORKFLOW.md | 학습 상태 분리 (관련 #17, #490, #608, #618, #680, #681, #683, #684) | 정성 판정 주관성 | 예 | 수동 검토 |
| 683 | C-01 | 14. 프로젝트 완료 | 기능·리뷰·학습 결과 모두 추적 가능 | B 직접 없음 | 대응 없음 | 신규 | docs/process/DEVELOPMENT-WORKFLOW.md | 상위 완료 정의 (관련 #17, #490, #608, #618, #680, #681, #682, #684) | 모든 TASK 학습 의무로 오해 | 예 | 자동 검증: 세 상태 존재 |
| 684 | C-01 | 14. 위험도별 깊이 | 모든 TASK 동일 깊이 강제 금지, 핵심 TASK는 이해 검증 필수 | 8장과 대응 | 부분 대응 | 신규 | docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md | 비용과 중요도 균형 (관련 #17, #490, #608, #618, #680, #681, #682, #683) | 분류 기준 주관성 | 예 | 수동 검토 |

## 5. CFL-01~20 최종 결정 연결

| CFL | 연결 결정 | 최종 책임 문서 | 최종 해소 의미 |
| --- | ---: | --- | --- |
| CFL-01 | 결정 5 | `docs/process/GPT-REVIEW-CONTRACT.md` | 핵심 하나 누락 시 review 중단, 보조 누락은 limitation 명시 |
| CFL-02 | 결정 1 | `docs/process/PORTFOLIO-ROADMAP.md` | 한 Roadmap에서 전체 이력과 활성 구간을 분리 관리 |
| CFL-03 | 결정 18 | `docs/process/DEVELOPMENT-WORKFLOW.md` | CLAUDE 요약·PROJECT-RULES 공통 원칙·WORKFLOW 상세 gate로 책임 분리 |
| CFL-04 | 결정 3 | `docs/process/PROJECT-RULES.md` | 현재 근거 우선, PROJECT-RULES에 기술 최소 원칙·인덱스 |
| CFL-05 | 결정 9 | `docs/process/PROJECT-RULES.md` | Issue/Branch/Commit/PR 형식은 PROJECT-RULES 단일 기준 |
| CFL-06 | 결정 6 | `docs/process/TASK-START-CHECKLIST.md` | TASK 목적·유형·실제 경로 확인 후 최소 안전 자료만 요청 |
| CFL-07 | 결정 7 | `docs/process/GPT-REVIEW-CONTRACT.md` | 공통 Finding schema는 GPT 계약, Agent/GPT extras 분리 |
| CFL-08 | 결정 15 | `docs/process/DEVELOPMENT-WORKFLOW.md` | 계획에서 merge 전 learning gate 승인 시에만 merge 전 통과; 일반 TASK는 merge 후 가능 |
| CFL-09 | 결정 16 | `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md` | 모든 TASK 최소 학습 추적, 대표·고위험 INTERVIEW-NOTES 필수, 일반 조건부 |
| CFL-10 | 결정 8 | `docs/process/GPT-REVIEW-CONTRACT.md` | GPT는 비실행 독립 검토자, 사용자가 최종 결정 |
| CFL-11 | 결정 19 | `docs/process/PROJECT-RULES.md` | 전체 테스트 명령은 PROJECT-RULES SSOT |
| CFL-12 | 결정 10 | `docs/process/PROJECT-RULES.md` | branch 설명은 영어 kebab-case |
| CFL-13 | 결정 14 | `docs/process/GPT-REVIEW-CONTRACT.md` | Severity=Critical/High/Medium/Low, 전체 Finding 보존 |
| CFL-14 | 결정 2 | `docs/process/PORTFOLIO-ROADMAP.md` | TASK-059 완료 + merge SHA 근거 |
| CFL-15 | 결정 11 | `docs/process/PROJECT-RULES.md` | 승인된 논리 작업 단위 기본, 한 파일씩은 요청 시 |
| CFL-16 | 결정 12 | `docs/process/DEVELOPMENT-WORKFLOW.md` | 수동 검증은 자동 테스트가 충분하지 않을 때 조건부 |
| CFL-17 | 결정 20 | `docs/process/GPT-REVIEW-CONTRACT.md` | Critical 보고→영향 phase 중단→사용자 승인→수정→재검증 |
| CFL-18 | 결정 17 | `docs/process/GPT-REVIEW-CONTRACT.md` | fixed packet과 일반 운영 문맥 분리 |
| CFL-19 | 결정 21 | `docs/process/PROJECT-RULES.md` | prefix는 feat/fix/refactor/test/docs/chore/build 정확히 7개 |
| CFL-20 | 결정 22 | `docs/process/GPT-REVIEW-CONTRACT.md` | GPT review stage별 핵심 입력 matrix를 GPT 계약에서 관리 |

## 6. 핵심 최종 결정 반영 인덱스

| 검증 항목 | 최종 근거 | Matrix가 추적하는 최종 의미 | 최종 책임 문서 |
| --- | --- | --- | --- |
| TASK-059 상태 | 결정 2 | TASK-059=완료; merge SHA `122816d91533befcc7a63c06cf83a7150e1fd05d` | `docs/process/PORTFOLIO-ROADMAP.md` |
| Roadmap 책임 | 결정 1 | 상태 기준·전체 이력·활성 TASK·다음 순서·CS/학습 연결 | `docs/process/PORTFOLIO-ROADMAP.md` |
| 기술 규칙 책임 | 결정 3 + Phase 2-6 | 현재 코드/설정/주제 문서 우선; 10개 기술 최소 원칙·인덱스 | `docs/process/PROJECT-RULES.md` |
| approval gate | 결정 18 | CLAUDE는 핵심 원칙, PROJECT-RULES는 공통 승인, WORKFLOW는 단계별 gate | `docs/process/DEVELOPMENT-WORKFLOW.md` |
| Git metadata | 결정 9 | Issue/Branch/Commit/PR/Assignee/Reviewer 형식의 SSOT | `docs/process/PROJECT-RULES.md` |
| branch naming | 결정 10 | `{prefix}/TASK-XXX-english-kebab-case` | `docs/process/PROJECT-RULES.md` |
| Finding schema | 결정 7 | 공통 9개 필드 + Agent/GPT extras 분리 | `docs/process/GPT-REVIEW-CONTRACT.md` |
| learning timing | 결정 15 + Phase 3-2B | 계획에서 승인된 merge 전 gate만 선행; 일반은 merge 후 가능하되 TASK 최종 완료·다음 핵심 구현 전 통과 | `docs/process/DEVELOPMENT-WORKFLOW.md` |
| learning output | 결정 16 + Phase 3-2B | 모든 TASK pass/핵심 오해/사용자·AI 기여 추적; 대표·고위험 notes 필수, 일반 조건부 | `docs/process/TASK-LEARNING-INTERVIEW-CONTRACT.md` |
| GPT role | 결정 8 | 독립 검토자; 구현·파일 수정·Git·자동 진행 금지 | `docs/process/GPT-REVIEW-CONTRACT.md` |
| full test | 결정 19 | `./gradlew clean test --no-daemon --stacktrace -Dspring.profiles.active=test` | `docs/process/PROJECT-RULES.md` |
| review output | 결정 14 | Critical/High/Medium/Low; blocker 우선; 전체 Finding 생략 금지; Finding 없음 명시 | `docs/process/GPT-REVIEW-CONTRACT.md` |
| Critical | 결정 20 | 영향 phase 중단; 사용자 승인 전 자동 해결 금지; 해결/수용 전 commit/push/PR/merge 금지 | `docs/process/GPT-REVIEW-CONTRACT.md` |
| fixed packet | 결정 17 | 고정 패킷은 패킷만 사용; 일반 운영은 승인 과거 결정 사용 가능하되 mutable fact 재검증 | `docs/process/GPT-REVIEW-CONTRACT.md` |
| prefix 7개 | 결정 21 | feat / fix / refactor / test / docs / chore / build 외 사용하지 않음 | `docs/process/PROJECT-RULES.md` |
| GPT stage input matrix | 결정 22 | Plan/Issue·Implementation·Git/PR·Fixed packet 단계별 핵심 입력 | `docs/process/GPT-REVIEW-CONTRACT.md` |

### 기술 규칙 추가 확인

- Package/Layer: Controller는 API boundary이며 use-case/service에 위임하고 repository를 직접 호출하지 않는다.
- Package/Layer: `api → application → domain`을 모든 코드의 절대 규칙으로 복원하지 않는다.
- DTO: 단일 `{verb}{domain}Request` naming을 강제하지 않는다.
- Test: Testcontainers, Mockito, 고정 concurrency/thread 수, Postman을 모든 TASK의 필수 규칙으로 복원하지 않는다.
- Redis: 과거 Key table과 `events:list`를 현재 사실로 복원하지 않는다.
- Logging: 미구현 MDC field를 필수 규칙으로 복원하지 않는다.
- Error/Common Response: 모든 오류가 `GlobalExceptionHandler`만 거쳐야 한다는 절대 규칙을 두지 않는다.

## 7. Phase 2-4B 기술 근거 선정 provenance

기술 규칙 판정은 기본 승인 경로만이 아니라 최종 Phase 2-4B의 **전체 선정 mapping**을 근거로 한다.

| 항목 | 보존 값 |
| --- | ---: |
| 전체 선정 mapping 행 (header 제외) | 114 |
| unique 선택 파일 | 98 |
| `@Transactional` 동적 발견 파일 | 8 |
| 로그 사용 동적 발견 파일 | 12 |

선정 provenance에는 `approved-basic`, DTO의 `candidate-path-name`, Transaction/로그의 `content-path-discovery`, 설정·CI 등 `cross-reference`가 포함된다.
이 문서는 해당 114개 기술 증거 row를 684개 migration row로 재작성하지 않으며, 기술 규칙의 판정 provenance가 기본 선정에 한정되지 않았음을 보존한다.

## 8. 무결성 요약

| 항목 | 결과 |
| --- | ---: |
| header column 수 | 12 |
| data row 수 | 684 |
| A row | 378 |
| B row | 227 |
| C row | 79 |
| missing ID | 0 |
| duplicate ID | 0 |
| 12-column 위반 row | 0 |
| 승인 범위 밖 target | 0 |
| broken related reference | 0 |
| duplicate related reference | 0 |
| CFL 전체 | 20 |
| CFL 누락 | 0 |
| CFL→결정 중복 연결 | 0 |

### proposal 집계

| 제안 상태 | row |
| --- | ---: |
| 유지 | 197 |
| 병합 | 150 |
| 수정 | 257 |
| 제외 | 7 |
| 신규 | 73 |
| 합계 | 684 |

### mapping 집계

| 대응 상태 | row |
| --- | ---: |
| 완전 대응 | 195 |
| 부분 대응 | 307 |
| 대응 없음 | 147 |
| 충돌 | 35 |
| 합계 | 684 |

### validation 집계

| 검증 분류 | row |
| --- | ---: |
| 자동 | 495 |
| 수동 | 183 |
| 없음 | 6 |
| 합계 | 684 |

## 9. 범위

이 Matrix는 validator와 self-test의 구체 구현을 정의하지 않는다.
validator 관련 row의 검증 의도는 provenance로 보존하되 실제 shell 구현은 후속 승인 Phase에서 작성한다.
