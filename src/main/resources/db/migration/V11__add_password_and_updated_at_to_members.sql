-- members 테이블에 password, updated_at 컬럼 추가
ALTER TABLE members
  ADD COLUMN password VARCHAR(100) NOT NULL DEFAULT '',
  ADD COLUMN updated_at DATETIME NULL;

-- 기존 데이터 updated_at 현재 시각으로 초기화
UPDATE members SET updated_at = NOW();