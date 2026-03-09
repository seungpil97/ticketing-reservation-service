CREATE TABLE event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '공연명',
    venue VARCHAR(100) NOT NULL COMMENT '공연장명',
    status VARCHAR(30) NOT NULL COMMENT '공연 상태(예: ON_SALE, CLOSED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id)
) COMMENT='공연 기본 정보';

CREATE TABLE showtime (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL COMMENT '공연 ID',
    show_at DATETIME NOT NULL COMMENT '공연 시작 일시',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_showtime_event FOREIGN KEY (event_id) REFERENCES event(id)
) COMMENT='공연 회차 정보';

CREATE TABLE seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seat_number VARCHAR(20) NOT NULL COMMENT '좌석 표시 번호(A1, A2 등)',
    grade VARCHAR(20) NOT NULL COMMENT '좌석 등급(VIP, R, S)',
    row_label VARCHAR(10) NOT NULL COMMENT '좌석 열 구분(A, B 등)',
    seat_no INT NOT NULL COMMENT '좌석 번호(1, 2, 3 등)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_seat_number UNIQUE (seat_number)
) COMMENT='좌석 마스터 정보';

CREATE TABLE seat_grade_price (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL COMMENT '공연 ID',
    grade VARCHAR(20) NOT NULL COMMENT '좌석 등급(VIP, R, S)',
    price INT NOT NULL COMMENT '등급별 가격',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_seat_grade_price_event FOREIGN KEY (event_id) REFERENCES event(id),
    CONSTRAINT uk_event_grade UNIQUE (event_id, grade)
) COMMENT='공연별 좌석 등급 가격 정보';

CREATE TABLE showtime_seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    showtime_id BIGINT NOT NULL COMMENT '공연 회차 ID',
    seat_id BIGINT NOT NULL COMMENT '좌석 ID',
    status VARCHAR(20) NOT NULL COMMENT '좌석 상태(AVAILABLE, HELD, RESERVED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_showtime_seat_showtime FOREIGN KEY (showtime_id) REFERENCES showtime(id),
    CONSTRAINT fk_showtime_seat_seat FOREIGN KEY (seat_id) REFERENCES seat(id),
    CONSTRAINT uk_showtime_seat UNIQUE (showtime_id, seat_id)
) COMMENT='회차별 좌석 상태 정보';

CREATE INDEX idx_showtime_seat_showtime_status
    ON showtime_seat (showtime_id, status);