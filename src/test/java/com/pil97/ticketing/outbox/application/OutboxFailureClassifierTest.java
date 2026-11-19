package com.pil97.ticketing.outbox.application;

import com.fasterxml.jackson.core.JsonParseException;
import com.pil97.ticketing.outbox.application.OutboxFailureClassifier.Failure;
import com.pil97.ticketing.outbox.domain.OutboxErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033-1 (#115) AC-2 실패 분류 Unit 테스트 (DB 없음)
 *
 * <p>계약:
 * <ul>
 *   <li>예외 타입과 숫자 코드만 본다. getMessage()·getLocalizedMessage()·toString()을 호출하지 않는다</li>
 *   <li>type: 원인 체인에서 허용 목록에 있는 가장 깊은 예외 클래스 단순명, 없으면 OTHER</li>
 *   <li>sqlState: 원인 체인의 가장 깊은 SQLException 값이 ^[0-9A-Z]{5}$일 때만, vendorCode는 그 errorCode</li>
 *   <li>code: Jackson 처리 예외는 PAYLOAD_INVALID, SQLException은 vendor 매핑(아래 후보), 매핑 밖 SQLException은 DB_ACCESS,
 *       그 밖은 UNKNOWN</li>
 * </ul>
 * 분류 규칙(명시): vendor 1062 → DUPLICATE_KEY, 1452 → FOREIGN_KEY, 1205 → LOCK_WAIT_TIMEOUT, 1213 → DEADLOCK,
 * 그 밖의 SQLException → DB_ACCESS.
 * 이 테스트는 synthetic 예외로 규칙만 검증한다. 실제 DB 관찰: 1062·1452는 H-7(직접 JDBC, SQLState 23000,
 * SQLIntegrityConstraintViolationException), 1205는 H-5(SQLState HY000). 1213은 실제 관찰하지 않은 synthetic 값이며
 * AC-12에서도 관찰하지 않는다.
 */
class OutboxFailureClassifierTest {

  private final OutboxFailureClassifier classifier = new OutboxFailureClassifier();

  @Test
  @DisplayName("AC-2 Jackson 파싱 실패는 PAYLOAD_INVALID, SQL 정보 없음")
  void payloadParseFailure() {
    Throwable failure = new IllegalStateException("Invalid outbox payload. outboxEventId=1",
      new JsonParseException(null, "Unexpected character"));

    Failure result = classifier.classify(failure);

    assertThat(result.code()).isEqualTo(OutboxErrorCode.PAYLOAD_INVALID);
    assertThat(result.type()).isEqualTo("JsonParseException");
    assertThat(result.sqlState()).isNull();
    assertThat(result.vendorCode()).isNull();
  }

  @Test
  @DisplayName("AC-2 중복 키(vendor 1062, 실제 관찰 H-7)는 DUPLICATE_KEY, 가장 깊은 SQLException의 SQLState·vendor")
  void duplicateKey() {
    SQLException root = new SQLIntegrityConstraintViolationException("dup", "23000", 1062);
    Throwable failure = new DataIntegrityViolationException("could not execute statement",
      new ConstraintViolationException("could not execute statement", root, "uk_notification_type_payment"));

    Failure result = classifier.classify(failure);

    assertThat(result.code()).isEqualTo(OutboxErrorCode.DUPLICATE_KEY);
    assertThat(result.type()).isEqualTo("SQLIntegrityConstraintViolationException");
    assertThat(result.sqlState()).isEqualTo("23000");
    assertThat(result.vendorCode()).isEqualTo(1062);
  }

  @Test
  @DisplayName("AC-2 FK(1452)·잠금 대기 timeout(1205)·deadlock(1213, synthetic) 매핑, 매핑 밖 SQLException은 DB_ACCESS")
  void vendorMappingCandidates() {
    assertThat(classifier.classify(new RuntimeException(new SQLException("fk", "23000", 1452))).code())
      .isEqualTo(OutboxErrorCode.FOREIGN_KEY);
    assertThat(classifier.classify(new RuntimeException(new SQLException("timeout", "HY000", 1205))).code())
      .isEqualTo(OutboxErrorCode.LOCK_WAIT_TIMEOUT);
    assertThat(classifier.classify(new RuntimeException(new SQLException("deadlock", "40001", 1213))).code())
      .isEqualTo(OutboxErrorCode.DEADLOCK);
    Failure other = classifier.classify(new RuntimeException(new SQLException("io", "08S01", 0)));
    assertThat(other.code()).isEqualTo(OutboxErrorCode.DB_ACCESS);
    assertThat(other.type()).isEqualTo("SQLException");
    assertThat(other.sqlState()).isEqualTo("08S01");
    assertThat(other.vendorCode()).isEqualTo(0);
  }

  @Test
  @DisplayName("AC-2 형식이 아닌 SQLState는 저장하지 않고 vendor만 유지")
  void malformedSqlStateDropped() {
    Failure result = classifier.classify(new RuntimeException(new SQLException("x", "ab'c;", 9999)));

    assertThat(result.code()).isEqualTo(OutboxErrorCode.DB_ACCESS);
    assertThat(result.sqlState()).isNull();
    assertThat(result.vendorCode()).isEqualTo(9999);
  }

  @Test
  @DisplayName("AC-2 분류 밖 예외는 UNKNOWN·OTHER")
  void unknownFailure() {
    Failure result = classifier.classify(new IllegalArgumentException("x"));

    assertThat(result.code()).isEqualTo(OutboxErrorCode.UNKNOWN);
    assertThat(result.type()).isEqualTo("OTHER");
    assertThat(result.sqlState()).isNull();
    assertThat(result.vendorCode()).isNull();
  }

  @Test
  @DisplayName("AC-2 메시지에 접근하면 실패하는 예외도 분류된다 (메시지 미접근)")
  void messageNeverRead() {
    Throwable failure = new MessageForbiddenException(new SQLException(null, "23000", 1062));

    Failure result = classifier.classify(failure);

    assertThat(result.code()).isEqualTo(OutboxErrorCode.DUPLICATE_KEY);
    assertThat(result.sqlState()).isEqualTo("23000");
  }

  /**
   * 메시지 계열 메서드를 호출하면 실패하는 테스트 예외
   */
  private static final class MessageForbiddenException extends RuntimeException {
    MessageForbiddenException(Throwable cause) {
      super(null, cause, false, false);
    }

    @Override
    public String getMessage() {
      throw new AssertionError("getMessage() must not be called");
    }

    @Override
    public String getLocalizedMessage() {
      throw new AssertionError("getLocalizedMessage() must not be called");
    }

    @Override
    public String toString() {
      throw new AssertionError("toString() must not be called");
    }
  }
}
