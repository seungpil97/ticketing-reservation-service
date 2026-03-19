-- reservations 테이블에 status 컬럼 추가
-- 예약 취소 기능 구현에 따라 CONFIRMED / CANCELLED 상태 관리 필요
ALTER TABLE reservations
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';