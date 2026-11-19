package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.pil97.ticketing.support.consistency.ConsistencyFixture.nowSeconds;

/**
 * TASK-033 Outbox·알림 fixture
 * - 계약 값: Outbox event_type PAYMENT_SUCCEEDED, aggregate_id = 결제 ID,
 *   payload {"paymentId","reservationId","memberId"}, 알림 type PAYMENT_COMPLETED
 * - Outbox·알림은 JDBC autocommit으로 읽는다: production 엔티티 클래스에 의존하지 않는다 (구현 전에도 컴파일)
 * - 자기가 만든 행과 추적 중인 예약의 결제에 연결된 행만 지운다 (ConsistencyFixture.cleanup 전에 호출)
 * - component scan 대상이 아니며, 사용하는 테스트가 @Import 또는 context source로만 등록한다
 */
public class OutboxFixture {

  public static final String EVENT_TYPE = "PAYMENT_SUCCEEDED";
  public static final String NOTIFICATION_TYPE = "PAYMENT_COMPLETED";

  @Autowired private ConsistencyFixture fixture;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<Long> outboxIds = new ArrayList<>();
  private final List<Long> paymentIds = new ArrayList<>();
  private final List<Long> reservationIds = new ArrayList<>();

  /**
   * 결제 서비스를 거치지 않고 만든 결제 완료 상태와 PENDING Outbox 1건
   */
  public record Seeded(Long outboxId, Long paymentId, Long reservationId, Long memberId) {
  }

  /**
   * 같은 DB 행 상태를 한 번의 조회로 읽은 결과 (Outbox 상태와 해당 결제의 알림 건수)
   */
  public record Snapshot(String status, LocalDateTime processedAt, long notificationCount) {
  }

  /**
   * 결제 완료(예약 CONFIRMED·HOLD CONFIRMED·좌석 RESERVED·결제 SUCCESS) 상태를 직접 만들고 PENDING Outbox 1건 저장
   * - HOLD·좌석은 만료 처리 대상이 아닌 상태로 만든다
   */
  public Seeded seedPending() {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.CONFIRMED, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.CONFIRMED);
    Long paymentId = fixture.payment(reservationId, "SUCCESS", nowSeconds());
    paymentIds.add(paymentId);
    Long outboxId = insertPending(paymentId, reservationId, member.getId());
    return new Seeded(outboxId, paymentId, reservationId, member.getId());
  }

  private Long insertPending(Long paymentId, Long reservationId, Long memberId) {
    String payload = "{\"paymentId\":" + paymentId + ",\"reservationId\":" + reservationId
      + ",\"memberId\":" + memberId + "}";
    jdbcTemplate.update(
      "insert into outbox_event (event_type, aggregate_id, payload, status) values (?, ?, ?, 'PENDING')",
      EVENT_TYPE, paymentId, payload);
    Long outboxId = jdbcTemplate.queryForObject(
      "select id from outbox_event where event_type = ? and aggregate_id = ?", Long.class, EVENT_TYPE, paymentId);
    outboxIds.add(outboxId);
    return outboxId;
  }

  /**
   * 재시도 상태까지 포함한 Outbox 행 (TASK-033-1) - JDBC autocommit으로 읽는다
   */
  public record RetryRow(String status, int failedAttempts, LocalDateTime nextAttemptAt, LocalDateTime lastFailedAt,
                         String lastErrorCode, String lastErrorType, String lastSqlState, Integer lastVendorCode,
                         long stateVersion, LocalDateTime processedAt) {
  }

  private final AtomicLong bareAggregateId = new AtomicLong(-1_000_000L);

  /**
   * 결제 완료 상태와 지정 payload의 PENDING Outbox 1건 (깨진 payload 사례, TASK-033-1)
   */
  public Seeded seedPendingWithPayload(String payload) {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.RESERVED);
    Member member = fixture.member();
    Long holdId = fixture.hold(seat, member, HoldStatus.CONFIRMED, nowSeconds().plusMinutes(5));
    Long reservationId = fixture.reservation(holdId, ReservationStatus.CONFIRMED);
    Long paymentId = fixture.payment(reservationId, "SUCCESS", nowSeconds());
    paymentIds.add(paymentId);
    jdbcTemplate.update(
      "insert into outbox_event (event_type, aggregate_id, payload, status) values (?, ?, ?, 'PENDING')",
      EVENT_TYPE, paymentId, payload);
    Long outboxId = jdbcTemplate.queryForObject(
      "select id from outbox_event where event_type = ? and aggregate_id = ?", Long.class, EVENT_TYPE, paymentId);
    outboxIds.add(outboxId);
    return new Seeded(outboxId, paymentId, reservationId, member.getId());
  }

  /**
   * 결제·알림과 무관한 Outbox 행 (재처리 프로시저·관측 사례, TASK-033-1)
   * - aggregate_id는 실제 결제 ID와 겹치지 않는 음수를 쓴다
   *
   * @return Outbox ID
   */
  public Long insertBare(String status, int failedAttempts, LocalDateTime nextAttemptAt, long stateVersion,
                         LocalDateTime createdAt) {
    long aggregateId = bareAggregateId.getAndDecrement();
    jdbcTemplate.update("""
      insert into outbox_event (event_type, aggregate_id, payload, status, failed_attempts, next_attempt_at,
                                state_version, created_at)
      values (?, ?, '{}', ?, ?, ?, ?, coalesce(?, current_timestamp))
      """, EVENT_TYPE, aggregateId, status, failedAttempts, nextAttemptAt, stateVersion, createdAt);
    Long outboxId = jdbcTemplate.queryForObject(
      "select id from outbox_event where event_type = ? and aggregate_id = ?", Long.class, EVENT_TYPE, aggregateId);
    outboxIds.add(outboxId);
    return outboxId;
  }

  /**
   * 재시도 상태 직접 설정 (TASK-033-1) - 오류 정보는 last_error_code만 지정한다
   */
  public void setRetryState(Long outboxId, String status, int failedAttempts, LocalDateTime nextAttemptAt,
                            long stateVersion, String lastErrorCode, LocalDateTime lastFailedAt) {
    jdbcTemplate.update("""
      update outbox_event set status = ?, failed_attempts = ?, next_attempt_at = ?, state_version = ?,
                              last_error_code = ?, last_failed_at = ?
      where id = ?
      """, status, failedAttempts, nextAttemptAt, stateVersion, lastErrorCode, lastFailedAt, outboxId);
  }

  public void markProcessed(Long outboxId, LocalDateTime processedAt, long stateVersion) {
    jdbcTemplate.update(
      "update outbox_event set status = 'PROCESSED', processed_at = ?, state_version = ? where id = ?",
      processedAt, stateVersion, outboxId);
  }

  public RetryRow retryRow(Long outboxId) {
    return jdbcTemplate.queryForObject("""
      select status, failed_attempts, next_attempt_at, last_failed_at, last_error_code, last_error_type,
             last_sql_state, last_vendor_code, state_version, processed_at
      from outbox_event where id = ?
      """,
      (rs, i) -> new RetryRow(
        rs.getString("status"),
        rs.getInt("failed_attempts"),
        rs.getObject("next_attempt_at", LocalDateTime.class),
        rs.getObject("last_failed_at", LocalDateTime.class),
        rs.getString("last_error_code"),
        rs.getString("last_error_type"),
        rs.getString("last_sql_state"),
        (Integer) rs.getObject("last_vendor_code", Integer.class),
        rs.getLong("state_version"),
        rs.getObject("processed_at", LocalDateTime.class)),
      outboxId);
  }

  /**
   * 이미 알림이 있는 PENDING 상태 만들기 (UNIQUE 위반 사례) - 같은 결제의 알림을 직접 넣는다
   */
  public void insertNotification(Seeded seeded) {
    jdbcTemplate.update("""
      insert into notification (member_id, payment_id, outbox_event_id, type, message)
      values (?, ?, ?, ?, 'fixture notification')
      """, seeded.memberId(), seeded.paymentId(), seeded.outboxId(), NOTIFICATION_TYPE);
  }

  /**
   * 원본 결제 삭제 (FK 위반 사례) - 알림이 없는 결제만 삭제할 수 있다
   */
  public void deletePayment(Long paymentId) {
    jdbcTemplate.update("delete from payment where id = ?", paymentId);
  }

  /**
   * datasource가 지정 격리 컨테이너를 가리키는지 확인 - 컨테이너 직접 연결로 쓴 임의 토큰을 datasource로 읽는다 (TASK-033-1)
   */
  public void assertIsolated(MariaDBContainer<?> db) {
    String token = UUID.randomUUID().toString();
    try (Connection direct = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
         Statement st = direct.createStatement()) {
      st.execute("create table if not exists harness_marker (token varchar(64) not null primary key)");
      try (PreparedStatement ps = direct.prepareStatement("insert into harness_marker (token) values (?)")) {
        ps.setString(1, token);
        ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw HarnessException.harness("isolation marker write failed", e);
    }
    try {
      Long seen = jdbcTemplate.queryForObject("select count(*) from harness_marker where token = ?", Long.class, token);
      String hostname = jdbcTemplate.queryForObject("select @@hostname", String.class);
      if (seen == null || seen != 1L || !db.getContainerInfo().getConfig().getHostName().equals(hostname)
          || !IsolatedMariaDb.DATABASE.equals(currentDatabase())) {
        throw HarnessException.precondition("datasource is not the isolated container: marker=" + seen
          + ", hostname=" + hostname);
      }
    } finally {
      jdbcTemplate.update("delete from harness_marker where token = ?", token);
    }
  }

  // ===================== 테스트 전용 DB 장애 trigger (TASK-033-1) =====================

  /**
   * 장애 trigger 모드
   * <ul>
   *   <li>REQUEUE_SECOND: FAILED→PENDING 전이 UPDATE에서, 같은 장치 기록상 이미 한 행이 실제 갱신된 뒤 다음 행 갱신 직전에 SIGNAL.
   *       갱신 순서·특정 ID에 의존하지 않는다</li>
   *   <li>RECORD: 지정 Outbox 행의 failed_attempts를 바꾸는 UPDATE 직전에 SIGNAL (실패 기록 트랜잭션 장애)</li>
   * </ul>
   */
  public enum FaultMode {
    REQUEUE_SECOND, RECORD
  }

  /**
   * 장치 기록 (비트랜잭션 MyISAM 표라 장애 문장이 rollback돼도 남는다)
   *
   * @param event UPDATED(AFTER UPDATE에서 실제 갱신 확인), SIGNAL(장애 발생)
   */
  public record FaultLog(Long outboxId, String event) {
  }

  /**
   * 격리 컨테이너에 장애 trigger와 제어·기록 표를 만든다 (root connection). 사용 후 dropFaultTriggers로 반드시 지운다.
   * production migration·프로시저와 무관한 테스트 전용 장치다
   */
  public void installFaultTriggers(MariaDBContainer<?> db) {
    executeAsRoot(db, List.of(
      "drop trigger if exists test_fault_before",
      "drop trigger if exists test_fault_after",
      "create table if not exists test_fault_arm (outbox_id bigint null, mode varchar(20) not null) engine = MyISAM",
      "create table if not exists test_fault_log (seq bigint not null auto_increment primary key, "
        + "outbox_id bigint not null, event varchar(20) not null) engine = MyISAM",
      "delete from test_fault_arm",
      "delete from test_fault_log",
      """
        create trigger test_fault_after after update on outbox_event for each row
        begin
          if old.status = 'FAILED' and new.status = 'PENDING'
             and exists (select 1 from test_fault_arm where mode = 'REQUEUE_SECOND') then
            insert into test_fault_log (outbox_id, event) values (old.id, 'UPDATED');
          end if;
        end
        """,
      """
        create trigger test_fault_before before update on outbox_event for each row
        begin
          if old.status = 'FAILED' and new.status = 'PENDING'
             and exists (select 1 from test_fault_arm where mode = 'REQUEUE_SECOND')
             and (select count(*) from test_fault_log where event = 'UPDATED') >= 1 then
            insert into test_fault_log (outbox_id, event) values (old.id, 'SIGNAL');
            signal sqlstate '45000' set message_text = 'test fault trigger: requeue';
          end if;
          if new.failed_attempts <> old.failed_attempts
             and exists (select 1 from test_fault_arm where mode = 'RECORD' and outbox_id = old.id) then
            insert into test_fault_log (outbox_id, event) values (old.id, 'SIGNAL');
            signal sqlstate '45000' set message_text = 'test fault trigger: record';
          end if;
        end
        """));
  }

  public void armFault(MariaDBContainer<?> db, FaultMode mode, Long outboxId) {
    try (Connection root = rootConnection(db);
         PreparedStatement ps = root.prepareStatement("insert into test_fault_arm (outbox_id, mode) values (?, ?)")) {
      if (outboxId == null) {
        ps.setNull(1, java.sql.Types.BIGINT);
      } else {
        ps.setLong(1, outboxId);
      }
      ps.setString(2, mode.name());
      ps.executeUpdate();
    } catch (SQLException e) {
      throw HarnessException.harness("fault trigger arm failed", e);
    }
  }

  public List<FaultLog> faultLog() {
    return jdbcTemplate.query("select outbox_id, event from test_fault_log order by seq",
      (rs, i) -> new FaultLog(rs.getLong("outbox_id"), rs.getString("event")));
  }

  public void dropFaultTriggers(MariaDBContainer<?> db) {
    executeAsRoot(db, List.of(
      "drop trigger if exists test_fault_before",
      "drop trigger if exists test_fault_after",
      "drop table if exists test_fault_arm",
      "drop table if exists test_fault_log"));
  }

  private static void executeAsRoot(MariaDBContainer<?> db, List<String> statements) {
    try (Connection root = rootConnection(db); Statement st = root.createStatement()) {
      for (String sql : statements) {
        st.execute(sql);
      }
    } catch (SQLException e) {
      throw HarnessException.harness("fault trigger setup failed", e);
    }
  }

  private static Connection rootConnection(MariaDBContainer<?> db) throws SQLException {
    return DriverManager.getConnection(db.getJdbcUrl(), "root", db.getPassword());
  }

  /**
   * production 경로가 만든 Outbox를 정리 대상으로 추적 (결제 서비스 호출 테스트)
   */
  public void trackReservation(Long reservationId) {
    reservationIds.add(reservationId);
  }

  public Snapshot snapshot(Seeded seeded) {
    return snapshot(seeded.outboxId(), seeded.paymentId());
  }

  public Snapshot snapshot(Long outboxId, Long paymentId) {
    return jdbcTemplate.queryForObject(
      "select o.status, o.processed_at, "
        + "(select count(*) from notification n where n.payment_id = ?) as notification_count "
        + "from outbox_event o where o.id = ?",
      (rs, i) -> new Snapshot(
        rs.getString("status"),
        rs.getObject("processed_at", LocalDateTime.class),
        rs.getLong("notification_count")),
      paymentId, outboxId);
  }

  public List<Map<String, Object>> outboxRowsOfPayment(Long paymentId) {
    return jdbcTemplate.queryForList(
      "select * from outbox_event where event_type = ? and aggregate_id = ?", EVENT_TYPE, paymentId);
  }

  public long outboxCountOfReservation(Long reservationId) {
    return jdbcTemplate.queryForObject(
      "select count(*) from outbox_event o join payment p on o.aggregate_id = p.id "
        + "where o.event_type = ? and p.reservation_id = ?", Long.class, EVENT_TYPE, reservationId);
  }

  public long notificationCountOfReservation(Long reservationId) {
    return jdbcTemplate.queryForObject(
      "select count(*) from notification n join payment p on n.payment_id = p.id where p.reservation_id = ?",
      Long.class, reservationId);
  }

  public List<Map<String, Object>> notificationsOfPayment(Long paymentId) {
    return jdbcTemplate.queryForList("select * from notification where payment_id = ?", paymentId);
  }

  /**
   * 현재 연결된 DB 이름 (격리 DB 사용 확인용)
   */
  public String currentDatabase() {
    return jdbcTemplate.queryForObject("select database()", String.class);
  }

  public void cleanup() {
    List<Long> payments = new ArrayList<>(paymentIds);
    if (!reservationIds.isEmpty()) {
      payments.addAll(jdbcTemplate.queryForList(
        "select id from payment where reservation_id in (" + join(reservationIds) + ")", Long.class));
    }
    if (!payments.isEmpty()) {
      jdbcTemplate.update("delete from notification where payment_id in (" + join(payments) + ")");
      jdbcTemplate.update("delete from outbox_event where event_type = ? and aggregate_id in (" + join(payments) + ")",
        EVENT_TYPE);
    }
    if (!outboxIds.isEmpty()) {
      jdbcTemplate.update("delete from outbox_event where id in (" + join(outboxIds) + ")");
    }
    outboxIds.clear();
    paymentIds.clear();
    reservationIds.clear();
  }

  private static String join(List<Long> ids) {
    return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
  }
}
