CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '예약 ID',
    hold_id BIGINT NOT NULL COMMENT '선점 ID',
    showtime_id BIGINT NOT NULL COMMENT '회차 ID',
    seat_id BIGINT NOT NULL COMMENT '좌석 ID',
    member_id BIGINT NOT NULL COMMENT '회원 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_hold_id
        UNIQUE (hold_id),
    CONSTRAINT fk_reservations_hold
        FOREIGN KEY (hold_id) REFERENCES holds(id),
    CONSTRAINT fk_reservations_showtime
        FOREIGN KEY (showtime_id) REFERENCES showtime(id),
    CONSTRAINT fk_reservations_seat
        FOREIGN KEY (seat_id) REFERENCES seat(id),
    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id) REFERENCES members(id)
) COMMENT='예약 정보';