# TASK-031 Claude Agent와 GPT 독립 리뷰 비교 평가

## 1. 평가 목적

- 최소 구성의 `ticketing-risk-reviewer`가 실제 위험 리뷰에 도움이 되는지 평가한다.
- GPT 독립 리뷰를 대체할 수 있는지가 아니라 1차 보조 리뷰어로 유지할 가치가 있는지 판단한다.
- Claude Agent와 GPT가 동일한 고정 입력과 동일한 공통 출력 계약을 사용한 비교임을 명시한다.

## 2. 평가 대상과 Git 기준선

| 항목 | 고정 기준 |
| --- | --- |
| 전체 TASK | TASK-059 |
| 검토 대상 | TASK-031 Redis 슬라이딩 윈도우 기반 Rate Limiting |
| Issue | #96 `TASK-031 Redis 슬라이딩 윈도우 기반 Rate Limiting 구현` |
| PR | #97 `feat(TASK-031): Redis 슬라이딩 윈도우 기반 Rate Limiting 구현` |
| 변경 전 SHA | `f522ea3b65dfb02bdd1574279c4f107b6407de94` |
| 완료 SHA | `f0aabf857bb91686b0ba74c0dde7f238525cba22` |
| 검토 파일 | Rate Limiting 관련 파일 11개 |
| 문맥 파일 | 인증·예외 처리·테스트 환경 관련 파일 9개 |
| 제외 파일 | PR #97에서 변경됐지만 이번 비교 범위에서 제외한 Queue 파일 3개 |

## 3. 고정 리뷰 패킷

| 항목 | 검증 결과 |
| --- | --- |
| 전체 패킷 SHA-256 | `f6695f94d8644e1feb98dc17ab6ba923f405ed3b3fa30100db99bb289c6acf08` |
| 리뷰 경계 SHA-256 | `1e6d1f16554789bec8fcdfb7b6300be3298ea19ad3187a19045edbb3e620590a` |
| 전체 줄 수 | 3,148줄 |
| Git 검증 | 리뷰 diff, 전체 코드 스냅샷, 문맥 스냅샷과 실제 Git byte 대조 완료 |
| 입력 동일성 | Claude Agent와 GPT가 동일한 `REVIEW_PACKET_START`부터 `REVIEW_PACKET_END`까지의 경계 byte를 사용 |
| 이전 패킷 | 완료 SHA와 byte 불일치가 확인된 기존 패킷은 폐기 |

## 4. 실행 방식과 공정성

### Claude Agent

- Claude Code 버전: `2.1.222`
- 실행 모드: `safe-mode`
- 승인된 Agent 본문을 `system-prompt-file`로 제공
- 고정 패킷을 표준 입력으로 전달
- 제공 도구: 빈 목록
- 실제 `tool_use`: 0회
- permission denial: 0회
- MCP 서버 및 MCP 오류: 0개
- 외부 `CLAUDE.md`와 자동 메모리 비활성화
- Agent 전체 SHA-256: `297b585dc2add9ee7aba01076a63dd312497cc948a88ee9588d07f8405572d19`
- Agent 시스템 프롬프트 본문 SHA-256: `226fa6c0ee4fa2853474d6a80f2b73228abd185047006d4d756b4f2f5e3d5691`
- Agent 등록·인식 자체는 Phase 2-5B-1에서 별도로 확인했다.
- 이번 실험은 승인된 `ticketing-risk-reviewer` 본문의 리뷰 품질을 비교한 것이며 Agent 등록 기능 자체를 검증한 실행이 아니다.

### GPT

- 이전 대화와 분리된 새 ChatGPT 채팅을 사용했다.
- 고정 리뷰 경계 파일만 첨부했다.
- 추가 힌트, Claude Agent 결과, MASTER 대화, 이전 TASK-031 평가와 예상 Finding을 제공하지 않았다.
- 패킷에 포함된 공통 출력 형식을 그대로 사용하도록 지시했다.

## 5. 결과 요약

| 항목 | Claude Agent | GPT |
| --- | --- | --- |
| 리뷰 SHA-256 | `e5e15343224906ee42564a7469b5845a2e42f9da2af4e91c083e05b831c668df` | `24ba8df30416f5d702be259d8f016f9c6960108d50c7328c0386fbfbac14b88a` |
| 줄 수 | 92 | 100 |
| Critical | 0 | 0 |
| Warning | 2 | 3 |
| Info | 2 | 2 |
| 근거 부족 | 5 | 3 |
| 최종 권고 | 진행 가능 | 수정 후 진행 |

폐기된 GPT 저장 결과 SHA-256 `9ee510fa129bd0ece58f0dcce9e6ab627462037bc8d536bf05f62ae8f4fac2d7`은 최종 비교 결과로 사용하지 않았다.

## 6. Finding 대응표

| 주제 | Claude Agent | GPT | 비교 판정 | 최종 심각도 | 최종 판단 근거 |
| --- | --- | --- | --- | --- | --- |
| AOP·Controller·HTTP 429 자동 테스트 부재 | Warning Finding 1 | Warning Finding 3 | 공통 발견 | Warning | Controller 어노테이션 적용부터 HTTP 429 응답까지 연결된 자동 검증이 없어 실제 AOP 적용 누락과 예외 응답 변환 문제를 놓칠 수 있다. 근거는 충분하다. |
| PROJECT-RULES DoD와 완료 diff 불일치 | Warning Finding 2 | Info Finding 4 | 공통 발견 | Info | 런타임 결함보다는 Issue 완료 조건과 실제 변경 이력 사이의 문서·추적성 문제다. 근거는 충분하지만 운영 위험 수준은 낮다. |
| 슬라이딩 윈도우 경계와 부분 만료 테스트 | Info Finding 4 | Warning Finding 2 | 부분 공통 | Warning | GPT는 경계 정책뿐 아니라 기존 통합 테스트가 Key TTL 만료만으로 통과할 수 있는 거짓 양성 구조까지 발견했다. 정확한 부분 만료와 경계 시각 검증이 필요하다. |
| Redis `execute` null 반환 시 조용한 fail-open | 미발견 | Warning Finding 1 | GPT 단독 발견 | Warning | fail-open 자체는 정책과 일치하지만 예외 경로와 달리 장애 로그가 없어 운영 관측성과 트러블슈팅 가능성이 낮다. |
| Issue 적용 경로와 실제 Controller 경로 불일치 | 미발견 | Info Finding 5 | GPT 단독 발견 | Info | 런타임 결함보다는 역사적 요구사항과 실제 구현 엔드포인트 사이의 추적성 문제다. |
| 잘못된 어노테이션 파라미터가 500을 만드는 경로 | Info Finding 3 | 미발견 | Claude 단독 발견 | Info | 기술적으로 가능한 경로지만 현재 세 Controller의 값은 모두 유효한 컴파일 타임 상수이므로 실제 발생 가능성이 낮다. |

## 7. Claude Agent 평가

### 장점

- AOP·Controller·HTTP 429 통합 테스트 공백을 발견했다.
- Issue DoD와 실제 완료 diff의 불일치를 발견했다.
- 슬라이딩 윈도우 경계 정책 문제를 발견했다.
- 고정 패킷의 검토 범위와 공통 출력 계약을 준수했다.
- 근거 없는 Critical을 만들지 않았다.
- 명확한 TASK 범위 밖 리팩터링 제안을 만들지 않았다.

### 한계

- Redis `execute` null 반환의 운영 관측성 문제를 발견하지 못했다.
- 부분 만료 테스트의 거짓 양성 가능성을 발견하지 못했다.
- Issue의 적용 엔드포인트와 실제 Controller 경로 불일치를 발견하지 못했다.
- 문서 정합성 문제를 Warning으로 평가해 실제 런타임 위험보다 다소 높게 분류했다.
- 실제 발생 가능성이 낮은 어노테이션 파라미터 오류 경로를 확정 Finding으로 보고했다.

## 8. 최종 판정

**Claude Agent 유지 판정: 조건부 유지**

역할을 다음과 같이 고정한다.

- Claude Agent: 빠른 1차 위험 검토
- GPT: 독립적인 2차 검토와 누락 확인
- 사용자: 두 결과를 비교한 뒤 최종 승인

Claude Agent는 유용한 보조 리뷰어다. 다만 이번 표본에서는 GPT보다 탐지 범위가 좁았으며 GPT 독립 리뷰를 대체하지 않는다.

자동 수정·commit·push·PR 권한은 부여하지 않는다. Agent frontmatter의 `Read, Grep, Glob` 제한은 유지한다. 다중 Agent 구성이나 자동화 확대는 이번 단일 평가 결과만으로 승인하지 않는다.

## 9. TASK-031 후속 보완 권고

### 코드·테스트

1. Hold·Reservation·Payment의 AOP 적용과 HTTP 429 응답을 연결해 검증하는 통합 테스트를 추가한다.
2. Redis `execute`가 null을 반환할 때 장애 로그를 남기고 해당 경로의 단위 테스트를 추가한다.
3. 전체 Key TTL 만료가 아니라 일부 요청만 윈도우에서 제거되는 상황과 정확한 경계 시각을 검증하는 슬라이딩 윈도우 테스트를 추가한다.

### 문서

4. Issue DoD의 `PROJECT-RULES` 완료 기록과 실제 완료 diff의 불일치를 정정하거나 사유를 기록한다.
5. Issue에 기록된 적용 엔드포인트와 실제 Controller 경로 차이를 기록한다.

이번 평가 문서 생성 단계에서는 위 코드·테스트·기존 문서를 수정하지 않는다.

## 10. 검증 한계

### Claude Code 민감 파일 차단 검증 한계

- Claude Code 버전: `2.1.222`
- 프로젝트 `permissions.deny`에서 `Read(./.env)`, `Read(./.envrc)` 인식을 확인했다.
- 공식 문서의 권장 민감 파일 차단 형식과 일치했다.
- JSON 및 stream-json 기본 실행은 정상 동작했다.
- headless 환경에서 차단 대상 Read 요청 시 CLI가 종료 코드 1로 선종료되어 `permission_denials` JSON은 수집하지 못했다.
- 테스트 sentinel 및 실제 프로젝트 비밀값 노출은 없었다.
- 실제 프로젝트 Git 상태와 승인 기반 파일 변경은 없었다.
- 자동화된 deny 증거 수집은 판정 불가로 기록한다.

### 추가 한계

- Issue #96과 PR #97 본문은 Phase 2-6A에서 승인한 역사적 고정 텍스트이며 로컬 Git 객체와 byte 대조하지 못했다.
- 원본 로컬 테스트 로그와 CI HTML 리포트는 제공되지 않았다.
- 이번 비교는 완료된 TASK-031 하나만 사용한 단일 표본이다.
- `safe-mode`에서도 조직 관리 정책으로 강제된 Hook이 있다면 예외적으로 실행될 수 있으나 해당 환경이라는 증거는 없다.
- 모델 결과는 비결정적이므로 같은 입력을 다시 실행하면 문구나 Finding 수가 달라질 수 있다.

## 11. 결론

- `ticketing-risk-reviewer` 최소 구성은 유지 가치가 있다.
- GPT와 병렬 또는 순차 독립 검토가 필요하다.
- Agent 권한 확대의 근거는 확보하지 못했다.
- 다음 실제 TASK부터 `1차 Agent → 2차 GPT → 사용자 승인` 흐름을 적용한다.
- 최소 2~3개의 추가 TASK 표본에서 탐지 범위, 중복률, 근거 정확성과 심각도 분류를 재평가한다.
