package com.pil97.ticketing.support.consistency;

import com.pil97.ticketing.event.domain.Event;
import com.pil97.ticketing.event.domain.EventStatus;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.domain.repository.HoldRepository;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import com.pil97.ticketing.queue.domain.repository.QueueRepository;
import com.pil97.ticketing.reservation.domain.Reservation;
import com.pil97.ticketing.reservation.domain.ReservationStatus;
import com.pil97.ticketing.reservation.domain.repository.ReservationRepository;
import com.pil97.ticketing.seat.domain.Seat;
import com.pil97.ticketing.seat.domain.SeatGrade;
import com.pil97.ticketing.seat.domain.repository.SeatRepository;
import com.pil97.ticketing.showtime.domain.Showtime;
import com.pil97.ticketing.showtime.domain.repository.ShowtimeRepository;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * TASK-062 정합성 테스트 fixture
 * - 테스트가 생성한 행 ID와 Redis key만 추적하고 정리한다 (FLUSHALL·패턴 삭제 금지)
 * - 최종 상태는 JDBC autocommit 조회로 읽는다: 테스트 트랜잭션·persistence context 밖의 새 트랜잭션에서 커밋된 값을 확인
 * - 모든 시각은 holds.expires_at(DATETIME, 초 단위) 정밀도에 맞춰 초 단위로 정규화한다
 * - component scan 대상이 아니며, 사용하는 테스트 클래스가 @Import로만 등록한다 (기존 테스트 context에 영향 없음)
 */
public class ConsistencyFixture {

  private static final AtomicLong SEQ = new AtomicLong();

  @Autowired private MemberRepository memberRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private SeatRepository seatRepository;
  @Autowired private ShowtimeRepository showtimeRepository;
  @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
  @Autowired private HoldRepository holdRepository;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private QueueRepository queueRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate stringRedisTemplate;
  @Autowired private RedissonClient redissonClient;

  private final List<Long> memberIds = new ArrayList<>();
  private final List<Long> eventIds = new ArrayList<>();
  private final List<Long> seatIds = new ArrayList<>();
  private final List<Long> showtimeIds = new ArrayList<>();
  private final List<Long> showtimeSeatIds = new ArrayList<>();
  private final Set<String> redisKeys = new HashSet<>();
  private final Set<String> redissonLockKeys = new HashSet<>();

  /**
   * 초 단위로 정규화한 현재 시각
   */
  public static LocalDateTime nowSeconds() {
    return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
  }

  // ===================== 생성 =====================

  public Member member() {
    Member member = memberRepository.save(
      new Member("t062-" + System.nanoTime() + "@test.com", "tester", "encoded-pw"));
    memberIds.add(member.getId());
    return member;
  }

  /**
   * 회원에게 HOLD 생성용 입장 토큰 발급 (실제 Redis)
   */
  public void admit(Member member) {
    queueRepository.saveAdmissionToken(member.getId(), "t062-token-" + System.nanoTime());
    redisKeys.add("token:user:" + member.getId());
  }

  /**
   * 이벤트·회차·좌석·회차별 좌석 1개 생성
   */
  public SeatRef seat(ShowtimeSeatStatus status) {
    LocalDateTime now = nowSeconds();

    Event event = BeanUtils.instantiateClass(Event.class);
    ReflectionTestUtils.setField(event, "name", "TASK-062 이벤트");
    ReflectionTestUtils.setField(event, "venue", "테스트 공연장");
    ReflectionTestUtils.setField(event, "status", EventStatus.ON_SALE);
    ReflectionTestUtils.setField(event, "endTime", now.plusDays(1));
    ReflectionTestUtils.setField(event, "createdAt", now);
    ReflectionTestUtils.setField(event, "updatedAt", now);
    event = eventRepository.save(event);
    eventIds.add(event.getId());

    long seq = SEQ.incrementAndGet();
    Seat seat = BeanUtils.instantiateClass(Seat.class);
    // seat_number VARCHAR(20) UNIQUE
    ReflectionTestUtils.setField(seat, "seatNumber", "T62-" + (System.nanoTime() % 1_000_000_000L) + "-" + seq);
    ReflectionTestUtils.setField(seat, "grade", SeatGrade.VIP);
    ReflectionTestUtils.setField(seat, "rowLabel", "T");
    ReflectionTestUtils.setField(seat, "seatNo", (int) (seq % 100_000));
    ReflectionTestUtils.setField(seat, "createdAt", now);
    ReflectionTestUtils.setField(seat, "updatedAt", now);
    seat = seatRepository.save(seat);
    seatIds.add(seat.getId());

    Showtime showtime = BeanUtils.instantiateClass(Showtime.class);
    ReflectionTestUtils.setField(showtime, "event", event);
    ReflectionTestUtils.setField(showtime, "showAt", now.plusHours(2));
    ReflectionTestUtils.setField(showtime, "createdAt", now);
    ReflectionTestUtils.setField(showtime, "updatedAt", now);
    showtime = showtimeRepository.save(showtime);
    showtimeIds.add(showtime.getId());

    ShowtimeSeat showtimeSeat = BeanUtils.instantiateClass(ShowtimeSeat.class);
    ReflectionTestUtils.setField(showtimeSeat, "showtime", showtime);
    ReflectionTestUtils.setField(showtimeSeat, "seat", seat);
    ReflectionTestUtils.setField(showtimeSeat, "status", status);
    ReflectionTestUtils.setField(showtimeSeat, "createdAt", now);
    ReflectionTestUtils.setField(showtimeSeat, "updatedAt", now);
    showtimeSeat = showtimeSeatRepository.save(showtimeSeat);
    showtimeSeatIds.add(showtimeSeat.getId());

    redissonLockKeys.add("hold:seat:" + showtime.getId() + ":" + seat.getId());
    return new SeatRef(showtimeSeat.getId(), showtime.getId(), seat.getId());
  }

  /**
   * HOLD 직접 생성 (fixture 상태 구성용)
   */
  public Long hold(SeatRef seat, Member member, HoldStatus status, LocalDateTime expiresAt) {
    ShowtimeSeat showtimeSeat = showtimeSeatRepository.findById(seat.showtimeSeatId()).orElseThrow();
    Hold hold = holdRepository.save(
      Hold.create(showtimeSeat, member, expiresAt.truncatedTo(ChronoUnit.SECONDS)));
    if (status != HoldStatus.ACTIVE) {
      setHoldStatus(hold.getId(), status);
    }
    return hold.getId();
  }

  /**
   * 예약 직접 생성 (fixture 상태 구성용)
   */
  public Long reservation(Long holdId, ReservationStatus status) {
    Hold hold = holdRepository.findById(holdId).orElseThrow();
    ShowtimeSeat showtimeSeat = showtimeSeatRepository.findById(
      holdRepository.findShowtimeSeatIdById(holdId).orElseThrow()).orElseThrow();
    Member member = memberRepository.findById(
      jdbcTemplate.queryForObject("select member_id from holds where id = ?", Long.class, holdId)).orElseThrow();
    Reservation reservation = reservationRepository.save(
      Reservation.create(hold, showtimeSeat.getShowtime(), showtimeSeat.getSeat(), member));
    if (status != ReservationStatus.PENDING) {
      jdbcTemplate.update("update reservations set status = ? where id = ?", status.name(), reservation.getId());
    }
    return reservation.getId();
  }

  /**
   * 결제 직접 생성 (fixture 상태 구성용) - SUCCESS 결제는 paid_at 포함
   */
  public Long payment(Long reservationId, String status, LocalDateTime paidAt) {
    jdbcTemplate.update(
      "insert into payment (reservation_id, amount, status, paid_at) values (?, ?, ?, ?)",
      reservationId, 150000, status, paidAt);
    return jdbcTemplate.queryForObject(
      "select max(id) from payment where reservation_id = ?", Long.class, reservationId);
  }

  public void setHoldStatus(Long holdId, HoldStatus status) {
    jdbcTemplate.update("update holds set status = ? where id = ?", status.name(), holdId);
  }

  public void setSeatStatus(SeatRef seat, ShowtimeSeatStatus status) {
    jdbcTemplate.update("update showtime_seat set status = ? where id = ?", status.name(), seat.showtimeSeatId());
  }

  /**
   * 테스트 전용 고유 idempotency key 생성 및 정리 대상 등록
   * - production prefix는 바꾸지 않는다
   */
  public String idempotencyKey(String prefix) {
    String key = "t062-" + UUID.randomUUID();
    redisKeys.add(prefix + ":" + key);
    redisKeys.add(prefix + ":lock:" + key);
    return key;
  }

  // ===================== 조회 (새 트랜잭션, JDBC autocommit) =====================

  public String holdStatus(Long holdId) {
    return jdbcTemplate.queryForObject("select status from holds where id = ?", String.class, holdId);
  }

  public String seatStatus(SeatRef seat) {
    return jdbcTemplate.queryForObject(
      "select status from showtime_seat where id = ?", String.class, seat.showtimeSeatId());
  }

  public String reservationStatus(Long reservationId) {
    return jdbcTemplate.queryForObject(
      "select status from reservations where id = ?", String.class, reservationId);
  }

  public List<Map<String, Object>> payments(Long reservationId) {
    return jdbcTemplate.queryForList(
      "select id, status, paid_at, refunded_at from payment where reservation_id = ? order by id", reservationId);
  }

  public Map<String, Object> paymentRow(Long paymentId) {
    return jdbcTemplate.queryForMap(
      "select id, status, paid_at, refunded_at from payment where id = ?", paymentId);
  }

  public List<Long> holdIdsBySeatAndStatus(SeatRef seat, HoldStatus status) {
    return jdbcTemplate.queryForList(
      "select id from holds where showtime_seat_id = ? and status = ? order by id",
      Long.class, seat.showtimeSeatId(), status.name());
  }

  public Long reservationIdByHold(Long holdId) {
    List<Long> ids = jdbcTemplate.queryForList(
      "select id from reservations where hold_id = ?", Long.class, holdId);
    return ids.isEmpty() ? null : ids.get(0);
  }

  public boolean redisKeyExists(String key) {
    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
  }

  public boolean redissonLocked(SeatRef seat) {
    return redissonClient.getLock("hold:seat:" + seat.showtimeId() + ":" + seat.seatId()).isLocked();
  }

  /**
   * 사전조건: 주어진 now 기준 DB 전체의 만료 후보가 fixture가 지정한 집합과 같아야 한다
   * - 다른 테스트의 잔여 데이터가 있으면 환경 문제로 보고 HarnessException(PRECONDITION)을 던진다
   * - 계약 단언(AssertionError)과 구분하기 위함
   */
  public void requireExpirationCandidates(LocalDateTime now, Set<Long> expected) {
    Set<Long> actual = new HashSet<>(jdbcTemplate.queryForList(
      "select id from holds where status = 'ACTIVE' and expires_at < ?", Long.class, now));
    if (!actual.equals(expected)) {
      throw HarnessException.precondition(
        "expiration candidates mismatch. expected=" + expected + ", actual=" + actual);
    }
  }

  /**
   * 다른 회원의 결제 완료 상태 fixture: HOLD CONFIRMED, 예약 CONFIRMED, Payment SUCCESS
   * - 좌석 상태는 호출자가 시나리오에 맞게 구성한다 (정상: RESERVED, HELD 오염 사례: HELD)
   */
  public PaidByOther paidByOther(SeatRef seat) {
    Member other = member();
    Long holdId = hold(seat, other, HoldStatus.CONFIRMED, nowSeconds().plusMinutes(5));
    Long reservationId = reservation(holdId, ReservationStatus.CONFIRMED);
    Long paymentId = payment(reservationId, "SUCCESS", nowSeconds());
    return new PaidByOther(other, holdId, reservationId, paymentId);
  }

  /**
   * 다른 회원 결제 완료 상태가 그대로인지 단언용 조회
   */
  public void assertPaidByOtherPreserved(PaidByOther paid) {
    if (!"CONFIRMED".equals(holdStatus(paid.holdId()))
      || !"CONFIRMED".equals(reservationStatus(paid.reservationId()))
      || !"SUCCESS".equals(paymentRow(paid.paymentId()).get("status"))
      || payments(paid.reservationId()).size() != 1) {
      throw new AssertionError("paid-by-other state changed: hold=" + holdStatus(paid.holdId())
        + ", reservation=" + reservationStatus(paid.reservationId())
        + ", payments=" + payments(paid.reservationId()));
    }
  }

  public record PaidByOther(Member member, Long holdId, Long reservationId, Long paymentId) {
  }

  // ===================== 정리 =====================

  /**
   * 생성한 좌석 범위의 행과 등록한 Redis key만 삭제
   * - production 경로가 생성한 HOLD·예약·결제도 fixture 좌석 범위로 한정해 삭제한다
   * - Redisson 락 key는 직접 삭제하지 않는다 (worker 종료 후 정상 해제 여부만 호출자가 확인)
   */
  public void cleanup() {
    if (!showtimeSeatIds.isEmpty()) {
      String in = showtimeSeatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
      jdbcTemplate.update("delete from payment where reservation_id in "
        + "(select r.id from reservations r join holds h on r.hold_id = h.id where h.showtime_seat_id in (" + in + "))");
      jdbcTemplate.update("delete from reservations where hold_id in "
        + "(select h.id from holds h where h.showtime_seat_id in (" + in + "))");
      jdbcTemplate.update("delete from holds where showtime_seat_id in (" + in + ")");
      jdbcTemplate.update("delete from showtime_seat where id in (" + in + ")");
    }
    deleteByIds("showtime", showtimeIds);
    deleteByIds("seat", seatIds);
    deleteByIds("event", eventIds);
    deleteByIds("members", memberIds);
    if (!redisKeys.isEmpty()) {
      stringRedisTemplate.delete(redisKeys);
    }
    memberIds.clear();
    eventIds.clear();
    seatIds.clear();
    showtimeIds.clear();
    showtimeSeatIds.clear();
    redisKeys.clear();
    redissonLockKeys.clear();
  }

  private void deleteByIds(String table, List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    jdbcTemplate.update("delete from " + table + " where id in (" + in + ")");
  }

  /**
   * 회차별 좌석 참조
   */
  public record SeatRef(Long showtimeSeatId, Long showtimeId, Long seatId) {
  }
}
