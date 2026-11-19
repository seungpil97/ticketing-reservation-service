-- FAILED Outbox 수동 재처리 프로시저 (TASK-033-1)
-- 호출: CALL outbox_requeue_failed('[id, ...]', @requeued);
-- - 입력: JSON 배열 1~100건, 각 원소는 양의 signed BIGINT 범위의 JSON 정수. 중복 거부
--   JSON_TYPE만으로는 지수 표기(1e3)와 BIGINT 초과 정수를 거르지 못하므로 원래 lexeme 형식·자릿수·범위를 먼저 확인한다
-- - 입력 위반은 SQLSTATE 45000이며, 모든 입력 검증을 UPDATE 전에 끝내므로 어떤 행도 바꾸지 않는다
-- - 반영: 요청 시점에 FAILED인 행만 단일 UPDATE로 PENDING·실패 0·다음 시도 시각 NULL·상태 버전 +1로 전이한다.
--   last_error_*·last_failed_at은 유지한다. 단일 문장이므로 도중 오류(잠금 timeout 등)는 전부 반영되지 않는다
-- - p_requeued: FAILED 조건에 일치해 PENDING으로 전이된 행 수 (UPDATE 직후 ROW_COUNT)
-- - 내부에서 START TRANSACTION·COMMIT하지 않는다: 호출자의 autocommit 또는 호출자 트랜잭션이 커밋을 맡는다
-- - SQL SECURITY DEFINER: 실행 계정에는 이 프로시저의 EXECUTE만 필요하다
--   (DEFINER 계정은 outbox_event 조회·갱신 권한, migration 계정은 CREATE ROUTINE·ALTER ROUTINE 권한이 필요하다)
DROP PROCEDURE IF EXISTS outbox_requeue_failed;

DELIMITER $$
CREATE PROCEDURE outbox_requeue_failed(IN p_ids JSON, OUT p_requeued INT)
  SQL SECURITY DEFINER
BEGIN
  DECLARE v_count INT;
  DECLARE v_index INT DEFAULT 0;
  DECLARE v_element LONGTEXT;
  DECLARE v_text LONGTEXT;
  DECLARE v_ids LONGTEXT DEFAULT '';

  SET p_requeued = 0;

  IF p_ids IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'outbox_requeue_failed: ids must not be null';
  END IF;
  IF JSON_VALID(p_ids) = 0 OR JSON_TYPE(p_ids) <> 'ARRAY' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'outbox_requeue_failed: ids must be a JSON array';
  END IF;
  SET v_count = JSON_LENGTH(p_ids);
  IF v_count < 1 OR v_count > 100 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'outbox_requeue_failed: ids must contain 1 to 100 elements';
  END IF;

  WHILE v_index < v_count DO
    SET v_element = JSON_EXTRACT(p_ids, CONCAT('$[', v_index, ']'));
    -- 원래 JSON 타입: 정수만 (문자열·boolean·null·소수·객체·배열 거부)
    IF v_element IS NULL OR JSON_TYPE(v_element) <> 'INTEGER' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'outbox_requeue_failed: each id must be a JSON integer';
    END IF;
    -- 원래 lexeme: 앞자리 0·부호·지수 없는 양의 십진수, signed BIGINT 최대 이하 (변환 전에 문자열로 판정)
    SET v_text = JSON_UNQUOTE(v_element);
    IF v_text NOT REGEXP '^[1-9][0-9]{0,18}$'
       OR (CHAR_LENGTH(v_text) = 19 AND v_text > '9223372036854775807') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'outbox_requeue_failed: each id must be a positive signed BIGINT';
    END IF;
    -- 정규화된 십진 표기이므로 텍스트 동일 = 변환 후 ID 동일
    IF FIND_IN_SET(v_text, v_ids) > 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'outbox_requeue_failed: duplicate id';
    END IF;
    SET v_ids = IF(v_ids = '', v_text, CONCAT(v_ids, ',', v_text));
    SET v_index = v_index + 1;
  END WHILE;

  -- 동적 SQL 구간: 도중 오류(잠금 timeout 등)에도 호출 연결에 세션 변수·prepared statement를 남기지 않는다
  -- 이번 호출이 PREPARE에 성공한 경우에만 해제하고, 원래 오류는 RESIGNAL로 그대로 전달한다 (트랜잭션 제어 없음)
  BEGIN
    DECLARE v_prepared BOOLEAN DEFAULT FALSE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
      IF v_prepared THEN
        DEALLOCATE PREPARE outbox_requeue_failed_stmt;
      END IF;
      SET @outbox_requeue_failed_sql = NULL;
      RESIGNAL;
    END;

    -- 검증된 숫자 목록만 문장에 넣는다 (기본키 IN 목록 - 대상 행만 잠근다)
    SET @outbox_requeue_failed_sql = CONCAT(
      'UPDATE outbox_event SET status = ''PENDING'', failed_attempts = 0, next_attempt_at = NULL, ',
      'state_version = state_version + 1 WHERE id IN (', v_ids, ') AND status = ''FAILED''');
    PREPARE outbox_requeue_failed_stmt FROM @outbox_requeue_failed_sql;
    SET v_prepared = TRUE;
    EXECUTE outbox_requeue_failed_stmt;
    SET p_requeued = ROW_COUNT();
    DEALLOCATE PREPARE outbox_requeue_failed_stmt;
    SET v_prepared = FALSE;
    SET @outbox_requeue_failed_sql = NULL;
  END;
END$$
DELIMITER ;
