package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
