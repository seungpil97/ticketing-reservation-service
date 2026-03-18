-- 기존 중복 인덱스 제거
DROP INDEX IF EXISTS idx_showtime_seat_showtime_status ON showtime_seat;

-- 회차별 좌석 상태 조회 최적화
CREATE INDEX idx_showtime_seat_showtime_id_status
  ON showtime_seat (showtime_id, status);

-- 만료 HOLD 조회 최적화
CREATE INDEX idx_holds_status_expires_at
  ON holds (status, expires_at);