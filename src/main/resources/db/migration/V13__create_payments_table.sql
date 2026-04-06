-- payment 테이블 생성
-- 결제 상태: PENDING(결제 시작), SUCCESS(성공), FAIL(실패)
CREATE TABLE payment
(
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT      NOT NULL,
  amount         INT         NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  paid_at        DATETIME NULL,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_payment_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 예약 ID 기반 결제 조회 인덱스
CREATE INDEX idx_payment_reservation_id ON payment (reservation_id);