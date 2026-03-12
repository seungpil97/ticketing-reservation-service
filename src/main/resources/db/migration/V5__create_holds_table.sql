CREATE TABLE holds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    showtime_seat_id BIGINT NOT NULL COMMENT '회차별 좌석 ID',
    expires_at DATETIME NOT NULL COMMENT 'HOLD 만료 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_holds_showtime_seat
        FOREIGN KEY (showtime_seat_id) REFERENCES showtime_seat(id)
) COMMENT='좌석 선점 정보';

CREATE INDEX idx_holds_showtime_seat_id
    ON holds (showtime_seat_id);