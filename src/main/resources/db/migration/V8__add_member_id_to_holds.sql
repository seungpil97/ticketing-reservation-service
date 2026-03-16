ALTER TABLE holds
    ADD COLUMN member_id BIGINT NOT NULL COMMENT '회원 ID' AFTER showtime_seat_id,
    ADD CONSTRAINT fk_holds_member
        FOREIGN KEY (member_id) REFERENCES members(id);