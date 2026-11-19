-- 결제 성공 Outbox 재시도·최종 실패 상태 컬럼 추가 (TASK-033-1)
-- 기존 행은 기본값으로 채워진다: 기록된 실패 0회, 다음 시도 시각 NULL(즉시 대상), 상태 버전 0
-- 오류 원문은 저장하지 않는다: 오류 분류 코드·예외 클래스명·SQLState·vendor code만 둔다
ALTER TABLE outbox_event
  ADD COLUMN failed_attempts  INT          NOT NULL DEFAULT 0,
  ADD COLUMN next_attempt_at  DATETIME     NULL,
  ADD COLUMN last_failed_at   DATETIME     NULL,
  ADD COLUMN last_error_code  VARCHAR(40)  NULL,
  ADD COLUMN last_error_type  VARCHAR(100) NULL,
  ADD COLUMN last_sql_state   CHAR(5)      NULL,
  ADD COLUMN last_vendor_code INT          NULL,
  ADD COLUMN state_version    BIGINT       NOT NULL DEFAULT 0;
