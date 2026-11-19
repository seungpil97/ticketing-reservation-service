-- TASK-033-1 M-3 repeatable migration 재적용 확인용 테스트 전용 probe (v1)
-- production 프로시저와 무관하다: 본문이 바뀌면 Flyway가 다시 적용하는지만 확인한다
DROP PROCEDURE IF EXISTS m3_reapply_probe;

DELIMITER $$
CREATE PROCEDURE m3_reapply_probe(OUT p_version INT)
BEGIN
  SET p_version = 1;
END$$
DELIMITER ;
