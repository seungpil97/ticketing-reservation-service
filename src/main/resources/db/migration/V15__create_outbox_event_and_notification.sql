-- 결제 성공 Outbox 이벤트 테이블 생성 (TASK-033)
-- 결제 트랜잭션에서 PENDING으로 저장하고, relay가 알림 생성과 같은 트랜잭션에서 PROCESSED로 표시한다
-- 원본 결제 삭제와 무관하게 이벤트 발생 기록으로 보존하므로 결제 FK를 두지 않는다 (소비는 payload만 사용)
CREATE TABLE outbox_event
(
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  event_type   VARCHAR(50) NOT NULL,
  aggregate_id BIGINT      NOT NULL,
  payload      TEXT        NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME NULL,
  PRIMARY KEY (id),
  -- 결제 1건당 같은 종류의 이벤트 1건
  CONSTRAINT uk_outbox_event_type_aggregate
    UNIQUE (event_type, aggregate_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- relay 후보 조회 인덱스 (status = ? AND id > ? ORDER BY id)
CREATE INDEX idx_outbox_event_status_id ON outbox_event (status, id);

-- 결제 완료 내부 알림 테이블 생성 (TASK-033)
-- 알림이 있는 결제·회원은 삭제할 수 없다 (RESTRICT)
CREATE TABLE notification
(
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  member_id       BIGINT       NOT NULL,
  payment_id      BIGINT       NOT NULL,
  outbox_event_id BIGINT       NOT NULL,
  type            VARCHAR(50)  NOT NULL,
  message         VARCHAR(255) NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 결제 1건당 같은 종류의 알림 1건
  CONSTRAINT uk_notification_type_payment
    UNIQUE (type, payment_id),
  CONSTRAINT fk_notification_payment
    FOREIGN KEY (payment_id) REFERENCES payment (id) ON DELETE RESTRICT,
  CONSTRAINT fk_notification_member
    FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
