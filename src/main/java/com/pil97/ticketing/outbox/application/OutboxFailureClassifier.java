package com.pil97.ticketing.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pil97.ticketing.outbox.domain.OutboxErrorCode;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Outbox 처리 실패 분류 (TASK-033-1)
 * - 예외 타입과 숫자 코드만 본다: getMessage()·getLocalizedMessage()·toString()을 호출하지 않는다
 * - type: 원인 체인에서 허용 목록(Jackson 처리 예외 계열, java.sql 예외 계열) 중 가장 깊은 예외의 단순명, 없으면 OTHER
 * - sqlState: 가장 깊은 SQLException의 값이 5자리 영대문자·숫자일 때만, vendorCode는 그 error code
 * - code: Jackson 처리 예외 PAYLOAD_INVALID, vendor 1062 DUPLICATE_KEY·1452 FOREIGN_KEY·1205 LOCK_WAIT_TIMEOUT·1213 DEADLOCK,
 *   그 밖 SQLException DB_ACCESS, 그 밖 UNKNOWN
 */
@Component
public class OutboxFailureClassifier {

  private static final String OTHER = "OTHER";
  private static final Pattern SQL_STATE = Pattern.compile("^[0-9A-Z]{5}$");
  private static final int MAX_DEPTH = 32;

  /**
   * 저장 가능한 실패 정보 - 오류 원문·payload를 담지 않는다
   *
   * @param code       허용 목록 분류
   * @param type       허용 목록 예외 클래스 단순명, 없으면 OTHER
   * @param sqlState   형식 검증을 통과한 SQLState (없으면 null)
   * @param vendorCode vendor error code (없으면 null)
   */
  public record Failure(OutboxErrorCode code, String type, String sqlState, Integer vendorCode) {
  }

  public Failure classify(Throwable failure) {
    Throwable deepestAllowed = null;
    SQLException deepestSql = null;
    boolean payloadInvalid = false;

    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_DEPTH && seen.add(current); depth++) {
      if (current instanceof JsonProcessingException) {
        payloadInvalid = true;
        deepestAllowed = current;
      } else if (current instanceof SQLException sql) {
        deepestSql = sql;
        deepestAllowed = current;
      }
      current = current.getCause();
    }

    String type = deepestAllowed == null ? OTHER : deepestAllowed.getClass().getSimpleName();
    String sqlState = null;
    Integer vendorCode = null;
    if (deepestSql != null) {
      String state = deepestSql.getSQLState();
      sqlState = state != null && SQL_STATE.matcher(state).matches() ? state : null;
      vendorCode = deepestSql.getErrorCode();
    }
    return new Failure(code(payloadInvalid, deepestSql), type, sqlState, vendorCode);
  }

  private static OutboxErrorCode code(boolean payloadInvalid, SQLException sql) {
    if (payloadInvalid) {
      return OutboxErrorCode.PAYLOAD_INVALID;
    }
    if (sql == null) {
      return OutboxErrorCode.UNKNOWN;
    }
    return switch (sql.getErrorCode()) {
      case 1062 -> OutboxErrorCode.DUPLICATE_KEY;
      case 1452 -> OutboxErrorCode.FOREIGN_KEY;
      case 1205 -> OutboxErrorCode.LOCK_WAIT_TIMEOUT;
      case 1213 -> OutboxErrorCode.DEADLOCK;
      default -> OutboxErrorCode.DB_ACCESS;
    };
  }
}
