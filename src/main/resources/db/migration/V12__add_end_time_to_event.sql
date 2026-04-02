ALTER TABLE event
  ADD COLUMN end_time DATETIME NULL COMMENT '이벤트 종료 시각 - 대기열 자동 종료 판단 기준';

-- 기존 시드 이벤트 end_time 설정
-- id=1(ON_SALE): 향후 종료 예정으로 미래 시각 설정
UPDATE event
SET end_time = '2099-12-31 23:00:00'
WHERE id = 1;