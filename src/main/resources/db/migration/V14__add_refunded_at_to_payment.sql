-- payment 테이블에 환불 완료 시각 컬럼 추가
-- 환불 전 null, 환불 완료 시 refund() 호출 시각으로 채워진다
ALTER TABLE payment
  ADD COLUMN refunded_at DATETIME NULL AFTER paid_at;