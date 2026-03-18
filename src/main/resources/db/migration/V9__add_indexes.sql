-- V9__add_indexes.sql

-- 회차별 좌석 상태 조회 최적화
-- 좌석 조회 API, 좌석 선점(HOLD) 시 AVAILABLE 여부 확인에 사용
CREATE INDEX idx_showtime_seat_showtime_id_status
  ON showtime_seat (showtime_id, status);

-- 만료 HOLD 조회 최적화
-- HoldExpirationService에서 주기적으로 ACTIVE 상태의 만료된 HOLD를 조회할 때 사용
CREATE INDEX idx_holds_status_expires_at
  ON holds (status, expires_at);