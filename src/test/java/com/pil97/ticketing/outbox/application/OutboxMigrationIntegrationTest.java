package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.outbox.domain.OutboxErrorCode;
import com.pil97.ticketing.outbox.domain.OutboxEvent;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.LockWaitObserver;
import com.pil97.ticketing.support.outbox.MutableClock;
import com.pil97.ticketing.support.outbox.OutboxFaultInterceptor;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * TASK-033-1 (#115) 준비 단계 검증 - migration·프로시저 실행 가능성(M)과 테스트 하네스(H) 점검 (격리 MariaDB 컨테이너)
 *
 * <p>이 클래스는 신규 동작 AC의 RED/GREEN 증거가 아니다. 구조와 하네스가 동작하는지만 확인한다.
 * <ul>
 *   <li>M-0 datasource가 이 클래스 컨테이너를 가리키는지 (marker·hostname)</li>
 *   <li>M-1 V16 컬럼·기본값, Hibernate validate(context 기동), 새 컬럼 없는 JDBC INSERT 호환</li>
 *   <li>M-2 R__ 적용(DELIMITER parser)·CALL·SIGNAL·JSON 함수 관찰값 (관찰값은 M2-OBSERVE로 출력, 계약 판단 근거)</li>
 *   <li>M-3 테스트 전용 location의 repeatable 재적용</li>
 *   <li>H-1 잠금 관찰기의 대기자·차단자·대상 행 연결</li>
 *   <li>H-2 장애 주입기 After(PROCESSED 전이 flush만)·hook·출력 캡처·MutableClock bean</li>
 *   <li>H-3 기록기 지연 hook(spy) 발동 순서</li>
 *   <li>H-4 Spring pool connection ID를 대기자로 연결</li>
 *   <li>H-5 부분 갱신 관찰(trx_rows_modified)과 잠금 timeout 뒤 문장 rollback - DB 문장 rollback 메커니즘 증거이며 production 프로시저 원자성 증거가 아니다</li>
 *   <li>H-6 테스트 전용 장애 trigger 장치(REQUEUE_SECOND·RECORD) 독립 검증</li>
 *   <li>H-7 직접 JDBC로 알림 UNIQUE·FK 위반의 실제 SQLState·vendor code 관찰 (AC-12 기대값 근거)</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(properties =
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.outbox.OutboxFaultInterceptor")
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class,
  OutboxMigrationIntegrationTest.ClockTestConfig.class})
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class OutboxMigrationIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime CLOCK_START = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockTestConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(CLOCK_START, ZONE);
    }
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxRelayService relayService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private MutableClock clock;
  @Autowired private ApplicationContext context;
  @MockitoSpyBean private OutboxFailureRecorder recorder;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    OutboxFaultInterceptor.reset();
    SqlObserver.clear();
    clock.set(CLOCK_START);
    assertIsolatedDatasource();
  }

  @AfterEach
  void tearDown() {
    OutboxFaultInterceptor.reset();
    outbox.cleanup();
    fixture.cleanup();
    SqlObserver.clear();
  }

  /**
   * M-0 datasource가 이 클래스의 컨테이너에 연결됐는지 - 컨테이너 직접 연결로 쓴 임의 토큰을 datasource로 읽는다
   */
  private void assertIsolatedDatasource() {
    String token = UUID.randomUUID().toString();
    try (Connection direct = directConnection(); Statement st = direct.createStatement()) {
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
      String database = jdbcTemplate.queryForObject("select database()", String.class);
      if (seen == null || seen != 1L
          || !DB.getContainerInfo().getConfig().getHostName().equals(hostname)
          || !IsolatedMariaDb.DATABASE.equals(database)) {
        throw HarnessException.precondition("datasource is not the isolated container: marker=" + seen
          + ", hostname=" + hostname + ", database=" + database);
      }
    } finally {
      jdbcTemplate.update("delete from harness_marker where token = ?", token);
    }
  }

  // ===================== M-1 V16 =====================

  @Test
  @DisplayName("M-1 V16 적용: 새 컬럼 타입·NULL·기본값, 새 컬럼 없는 JDBC INSERT 행의 기본값")
  void m1_v16ColumnsAndDefaults() {
    assertThat(jdbcTemplate.queryForObject(
      "select success from flyway_schema_history where version = '16'", Boolean.class))
      .as("V16 적용 성공").isTrue();

    List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
      select column_name, data_type, character_maximum_length, is_nullable, column_default
      from information_schema.columns
      where table_schema = database() and table_name = 'outbox_event'
        and column_name in ('failed_attempts', 'next_attempt_at', 'last_failed_at', 'last_error_code',
                            'last_error_type', 'last_sql_state', 'last_vendor_code', 'state_version')
      """);
    columns.forEach(c -> System.out.println("M1-OBSERVE column " + c));
    assertThat(columns).as("새 컬럼 수").hasSize(8);
    assertColumn(columns, "failed_attempts", "int", null, "NO", "0");
    assertColumn(columns, "next_attempt_at", "datetime", null, "YES", null);
    assertColumn(columns, "last_failed_at", "datetime", null, "YES", null);
    assertColumn(columns, "last_error_code", "varchar", 40L, "YES", null);
    assertColumn(columns, "last_error_type", "varchar", 100L, "YES", null);
    assertColumn(columns, "last_sql_state", "char", 5L, "YES", null);
    assertColumn(columns, "last_vendor_code", "int", null, "YES", null);
    assertColumn(columns, "state_version", "bigint", null, "NO", "0");

    Seeded s = outbox.seedPending();
    Map<String, Object> row = jdbcTemplate.queryForMap("""
      select status, failed_attempts, next_attempt_at, last_failed_at, last_error_code, last_error_type,
             last_sql_state, last_vendor_code, state_version
      from outbox_event where id = ?
      """, s.outboxId());
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(((Number) row.get("failed_attempts")).intValue()).isZero();
    assertThat(((Number) row.get("state_version")).longValue()).isZero();
    for (String nullable : List.of("next_attempt_at", "last_failed_at", "last_error_code", "last_error_type",
      "last_sql_state", "last_vendor_code")) {
      assertThat(row.get(nullable)).as("%s 기본값", nullable).isNull();
    }
  }

  private static void assertColumn(List<Map<String, Object>> columns, String name, String dataType, Long maxLength,
                                   String nullable, String defaultValue) {
    Map<String, Object> column = columns.stream()
      .filter(c -> name.equalsIgnoreCase(String.valueOf(c.get("COLUMN_NAME"))))
      .findFirst().orElseThrow(() -> new AssertionError("column missing: " + name));
    assertThat(String.valueOf(column.get("DATA_TYPE"))).as("%s 타입", name).isEqualToIgnoringCase(dataType);
    if (maxLength != null) {
      assertThat(((Number) column.get("CHARACTER_MAXIMUM_LENGTH")).longValue()).as("%s 길이", name).isEqualTo(maxLength);
    }
    assertThat(column.get("IS_NULLABLE")).as("%s NULL 허용", name).isEqualTo(nullable);
    Object actualDefault = column.get("COLUMN_DEFAULT");
    if (defaultValue == null) {
      // MariaDB는 기본값 없는 NULL 허용 컬럼을 null 또는 문자열 'NULL'로 보고한다
      assertThat(actualDefault == null || "NULL".equals(actualDefault)).as("%s 기본값 NULL", name).isTrue();
    } else {
      assertThat(String.valueOf(actualDefault)).as("%s 기본값", name).isEqualTo(defaultValue);
    }
  }

  // ===================== M-2 프로시저·parser·JSON =====================

  @Test
  @DisplayName("M-2 R__ 적용(DELIMITER parser)·CALL OUT 수신·SIGNAL 45000 수신·JSON 함수 관찰")
  void m2_procedureAndJsonProbe() throws SQLException {
    assertThat(jdbcTemplate.queryForObject("""
      select count(*) from flyway_schema_history
      where version is null and description = 'outbox requeue failed' and success = 1
      """, Long.class)).as("R__outbox_requeue_failed 적용 성공").isEqualTo(1L);

    Map<String, Object> routine = jdbcTemplate.queryForMap("""
      select routine_type, security_type, definer from information_schema.routines
      where routine_schema = database() and routine_name = 'outbox_requeue_failed'
      """);
    System.out.println("M2-OBSERVE routine type=" + routine.get("ROUTINE_TYPE")
      + " security=" + routine.get("SECURITY_TYPE") + " definer_is_current_user="
      + String.valueOf(routine.get("DEFINER")).startsWith(DB.getUsername() + "@"));
    assertThat(routine.get("SECURITY_TYPE")).isEqualTo("DEFINER");

    // CALL과 OUT 파라미터 수신 - 완성 프로시저를 호출하지만 이 사례는 FAILED 행을 만들지 않으므로 관찰 값은 0이다
    // (CALL·OUT 수신 경로 확인일 뿐 재처리 동작 증거가 아니다. 재처리 동작은 OutboxRequeueProcedureIntegrationTest)
    try (Connection connection = dataSource.getConnection();
         CallableStatement call = connection.prepareCall("{call outbox_requeue_failed(?, ?)}")) {
      call.setString(1, "[1]");
      call.registerOutParameter(2, Types.INTEGER);
      call.execute();
      System.out.println("M2-OBSERVE procedure_call_out=" + call.getInt(2));
    }

    try (Connection direct = directConnection(); Statement st = direct.createStatement()) {
      // 프로시저 안 SIGNAL이 SQLSTATE 45000으로 전달되는지 (독립 probe, production 프로시저와 무관)
      st.execute("drop procedure if exists m2_probe_signal");
      st.execute("create procedure m2_probe_signal() begin signal sqlstate '45000' set message_text = 'm2 probe'; end");
      try {
        assertThatThrownBy(() -> st.execute("call m2_probe_signal()"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> {
            System.out.println("M2-OBSERVE signal_sqlstate=" + ((SQLException) e).getSQLState()
              + " vendor=" + ((SQLException) e).getErrorCode());
            assertThat(((SQLException) e).getSQLState()).isEqualTo("45000");
          });
      } finally {
        st.execute("drop procedure if exists m2_probe_signal");
      }

      // JSON 원래 타입·lexeme 관찰
      for (String input : List.of("[1]", "[1.0]", "[1e3]", "[\"7\"]", "[true]", "[null]", "[0]", "[-1]",
        "[9223372036854775807]", "[9223372036854775808]", "[01]", "[7,7]", "{}", "[]", "not json")) {
        observeJson(direct, input);
      }

      // JSON_TABLE 사용 가능 여부와 정수 텍스트 추출
      try (ResultSet rs = st.executeQuery(
        "select v from json_table('[1,9223372036854775807]', '$[*]' columns(v varchar(30) path '$')) t")) {
        StringBuilder values = new StringBuilder();
        while (rs.next()) {
          values.append(rs.getString(1)).append(';');
        }
        System.out.println("M2-OBSERVE json_table_values=" + values);
      }

      // ROW_COUNT 관찰 (변경 없는 UPDATE와 변경 있는 UPDATE)
      st.execute("drop table if exists m2_probe_rows");
      st.execute("create table m2_probe_rows (id int not null primary key, v int not null)");
      try {
        st.execute("insert into m2_probe_rows values (1, 0), (2, 0)");
        st.executeUpdate("update m2_probe_rows set v = v where id in (1, 2)");
        System.out.println("M2-OBSERVE row_count_unchanged=" + queryString(st, "select row_count()"));
        st.executeUpdate("update m2_probe_rows set v = v + 1 where id in (1, 2)");
        System.out.println("M2-OBSERVE row_count_changed=" + queryString(st, "select row_count()"));
      } finally {
        st.execute("drop table if exists m2_probe_rows");
      }

      // 테스트 계정의 DB 권한 (권한은 바꾸지 않는다)
      try (ResultSet rs = st.executeQuery(
        "select privilege_type from information_schema.schema_privileges where table_schema = database()")) {
        StringBuilder privileges = new StringBuilder();
        while (rs.next()) {
          privileges.append(rs.getString(1)).append(';');
        }
        System.out.println("M2-OBSERVE test_user_schema_privileges=" + privileges);
      }
    }
  }

  private static void observeJson(Connection connection, String input) {
    String sql = "select json_valid(?), json_type(?), json_type(json_extract(?, '$[0]')), "
      + "json_unquote(json_extract(?, '$[0]')), json_length(?)";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      for (int i = 1; i <= 5; i++) {
        ps.setString(i, input);
      }
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        System.out.println("M2-OBSERVE json input=" + input + " valid=" + rs.getString(1) + " type=" + rs.getString(2)
          + " first_type=" + rs.getString(3) + " first_text=" + rs.getString(4) + " length=" + rs.getString(5));
      }
    } catch (SQLException e) {
      System.out.println("M2-OBSERVE json input=" + input + " error_sqlstate=" + e.getSQLState()
        + " vendor=" + e.getErrorCode());
    }
  }

  // ===================== M-3 repeatable 재적용 =====================

  @Test
  @DisplayName("M-3 테스트 전용 location v1→v2: 같은 repeatable이 checksum만 달라져 다시 적용된다")
  void m3_repeatableReapplied() throws SQLException {
    try {
      migrateProbe("classpath:db/flyway-repeatable-v1");
      assertThat(callReapplyProbe()).as("v1 적용 후 probe").isEqualTo(1);
      migrateProbe("classpath:db/flyway-repeatable-v2");
      assertThat(callReapplyProbe()).as("v2 적용 후 probe").isEqualTo(2);

      List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        select checksum, success from m3_probe_history
        where version is null and description = 'requeue reapply probe' order by installed_rank
        """);
      rows.forEach(r -> System.out.println("M3-OBSERVE history " + r));
      assertThat(rows).as("repeatable 적용 이력").hasSize(2);
      assertThat(rows).allSatisfy(r -> assertThat(r.get("success")).isIn(true, 1, 1L));
      assertThat(rows.get(0).get("checksum")).as("checksum 변경").isNotEqualTo(rows.get(1).get("checksum"));
    } finally {
      try (Connection direct = directConnection(); Statement st = direct.createStatement()) {
        st.execute("drop procedure if exists m3_reapply_probe");
        st.execute("drop table if exists m3_probe_history");
      }
    }
  }

  private static void migrateProbe(String location) {
    Flyway.configure()
      .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
      .locations(location)
      .table("m3_probe_history")
      .baselineOnMigrate(true)
      .load()
      .migrate();
  }

  private int callReapplyProbe() throws SQLException {
    try (Connection connection = directConnection();
         CallableStatement call = connection.prepareCall("{call m3_reapply_probe(?)}")) {
      call.registerOutParameter(1, Types.INTEGER);
      call.execute();
      return call.getInt(1);
    }
  }

  // ===================== H-1 잠금 관찰기 =====================

  @Test
  @Timeout(60)
  @DisplayName("H-1 잠금 관찰기: 대기자·차단자·대상 Outbox 행을 연결해 관찰하고, 해제 후 대기자 종료·잔여 트랜잭션 0")
  void h1_lockWaitObserverLinksWaiterBlockerAndRow() throws Exception {
    Seeded s = outbox.seedPending();
    String primaryKey = String.valueOf(s.outboxId());
    String lockSql = "select id from outbox_event where id = ? for update";

    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicLong waiterThreadId = new AtomicLong(-1);
    CountDownLatch waiterReady = new CountDownLatch(1);
    long holderThreadId = -1;
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = directConnection()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      Future<Boolean> waiter = null;
      try {
        holderThreadId = LockWaitObserver.connectionId(holder);
        lockRow(holder, lockSql, s.outboxId());

        waiter = executor.submit(() -> {
          try (Connection w = directConnection()) {
            w.setAutoCommit(false);
            waiterThreadId.set(LockWaitObserver.connectionId(w));
            waiterReady.countDown();
            lockRow(w, lockSql, s.outboxId());
            w.rollback();
            return true;
          }
        });
        if (!waiterReady.await(WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
          throw HarnessException.harness("waiter connection was not ready");
        }

        Future<Boolean> running = waiter;
        Optional<LockWaitObserver.Wait> wait = LockWaitObserver.await(observer, waiterThreadId.get(), holderThreadId,
          "outbox_event", primaryKey, running::isDone, WAIT_TIMEOUT);
        assertThat(wait).as("대기자·차단자·대상 행 연결 관찰").isPresent();
        assertThat(wait.get().waiterThreadId()).isEqualTo(waiterThreadId.get());
        assertThat(wait.get().blockerThreadId()).isEqualTo(holderThreadId);
        assertThat(wait.get().lockData()).isEqualTo(primaryKey);
        System.out.println("H1-OBSERVE wait=" + wait.get());

        holder.rollback();
        assertThat(waiter.get(30, TimeUnit.SECONDS)).as("해제 후 대기자 종료").isTrue();
      } finally {
        holder.rollback();
        if (waiter != null) {
          waiter.cancel(true);
        }
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
      }
      holder.close();
      long open = LockWaitObserver.awaitNoOpenTransactions(observer, List.of(holderThreadId, waiterThreadId.get()),
        Duration.ofSeconds(5));
      if (open > 0) {
        throw HarnessException.harness("cleanup left open transactions: " + open + LockWaitObserver.diagnostics(observer));
      }
    }
  }

  private static void lockRow(Connection connection, String sql, Long id) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setLong(1, id);
      ps.executeQuery().close();
    }
  }

  // ===================== H-2 주입기 After·hook·출력 캡처 =====================

  @Test
  @DisplayName("H-2 주입기는 PROCESSED 전이 flush에서만 발동하고, hook·출력 캡처·MutableClock bean이 동작한다")
  void h2_injectorAfterHooksAndCapture(CapturedOutput output) {
    assertThat(context.getBean(Clock.class)).as("Clock 주입 대상은 MutableClock").isSameAs(clock);

    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId());
    AtomicInteger notificationHook = new AtomicInteger();
    AtomicInteger flushHook = new AtomicInteger();
    OutboxFaultInterceptor.onNotificationInsert(s.outboxId(), notificationHook::incrementAndGet);
    OutboxFaultInterceptor.onProcessedFlush(s.outboxId(), () -> {
      flushHook.incrementAndGet();
      clock.advance(Duration.ofSeconds(50));
    });

    relayService.relay();

    assertThat(OutboxFaultInterceptor.injectedCount(s.outboxId())).as("PROCESSED 전이 flush 주입").isEqualTo(1);
    assertThat(OutboxFaultInterceptor.injections(s.outboxId()).get(0).notificationInsertedBefore()).isTrue();
    assertThat(notificationHook.get()).as("알림 INSERT hook").isEqualTo(1);
    assertThat(flushHook.get()).as("처리 완료 flush hook").isEqualTo(1);
    assertThat(clock.localNow()).as("hook의 시각 진행").isEqualTo(CLOCK_START.plusSeconds(50));
    assertThat(outbox.snapshot(s).status()).isEqualTo("PENDING");
    assertThat(output.getAll()).as("앱 로그 캡처").contains("Outbox processing failed. outboxEventId=" + s.outboxId());

    // 같은 행의 PROCESSED 전이가 아닌 dirty flush는 주입하지 않는다
    Seeded other = outbox.seedPending();
    OutboxFaultInterceptor.failAlways(other.outboxId());
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      OutboxEvent event = entityManager.find(OutboxEvent.class, other.outboxId());
      ReflectionTestUtils.setField(event, "lastErrorType", "H2_PROBE");
    });
    assertThat(OutboxFaultInterceptor.injectedCount(other.outboxId())).as("비전이 dirty flush 미주입").isZero();
    assertThat(jdbcTemplate.queryForObject("select last_error_type from outbox_event where id = ?", String.class,
      other.outboxId())).isEqualTo("H2_PROBE");
  }

  // ===================== H-3 기록 지연 hook =====================

  @Test
  @DisplayName("H-3 기록기 spy hook은 실제 기록 메서드 호출 직전에 시각을 진행한다 (AC-5 T4 장치)")
  void h3_recordDelayHookRunsBeforeRealMethod() {
    Seeded s = outbox.seedPending();
    AtomicReference<LocalDateTime> seenByRealMethod = new AtomicReference<>();
    doAnswer(invocation -> {
      clock.advance(Duration.ofSeconds(40));
      seenByRealMethod.set(clock.localNow());
      return invocation.callRealMethod();
    }).when(recorder).record(eq(s.outboxId()), anyLong(), any(), any());

    // 구현 단계 (WORK-22 H-3 After): 실제 기록 메서드가 hook이 진행한 시각을 기록 시각(T_rec)으로 보고 반영한다
    OutboxFailureRecorder.Result result = recorder.record(s.outboxId(), 0L, CLOCK_START,
      new OutboxFailureClassifier.Failure(OutboxErrorCode.UNKNOWN, "OTHER", null, null));

    assertThat(seenByRealMethod.get()).as("hook 진행 시각").isEqualTo(CLOCK_START.plusSeconds(40));
    assertThat(clock.localNow()).isEqualTo(CLOCK_START.plusSeconds(40));
    assertThat(result).as("실제 기록 결과").isEqualTo(OutboxFailureRecorder.Result.APPLIED);
    OutboxFixture.RetryRow row = outbox.retryRow(s.outboxId());
    assertThat(row.failedAttempts()).as("기록된 실패").isEqualTo(1);
    assertThat(row.stateVersion()).as("상태 버전 증가").isEqualTo(1L);
    assertThat(row.lastFailedAt()).as("last_failed_at = floor_s(T_fail)").isEqualTo(CLOCK_START);
    assertThat(row.nextAttemptAt()).as("next = ceil_s(max(T_fail, T_rec) + 30s), T_rec = hook 진행 시각")
      .isEqualTo(CLOCK_START.plusSeconds(40).plusSeconds(30));
  }

  // ===================== H-4 Spring 처리 연결 ID 관찰 =====================

  @Test
  @Timeout(60)
  @DisplayName("H-4 Spring 트랜잭션의 pool connection ID를 SqlObserver in-flight로 확보해 잠금 대기의 대기자로 연결한다 (AC-10a 장치)")
  void h4_springPoolConnectionLinkedAsWaiter() throws Exception {
    Seeded s = outbox.seedPending();
    String worker = "h4-spring-worker";
    Pattern lockSql = Pattern.compile("from\\s+outbox_event\\b.*\\bfor\\s+update", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, worker));
    long holderThreadId = -1;
    long waiterThreadId = -1;
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = directConnection()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      Future<Long> waiter = null;
      try {
        holderThreadId = LockWaitObserver.connectionId(holder);
        lockRow(holder, "select id from outbox_event where id = ? for update", s.outboxId());

        waiter = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
          Long serverId = jdbcTemplate.queryForObject("select connection_id()", Long.class);
          jdbcTemplate.queryForObject("select id from outbox_event where id = ? for update", Long.class, s.outboxId());
          return serverId;
        }));

        Future<Long> running = waiter;
        Optional<LockWaitObserver.Wait> wait = LockWaitObserver.awaitResolvingWaiter(observer,
          LockWaitObserver.inFlightConnection(worker, lockSql), holderThreadId, "outbox_event",
          String.valueOf(s.outboxId()), running::isDone, WAIT_TIMEOUT);
        assertThat(wait).as("Spring pool connection 대기 관찰").isPresent();
        waiterThreadId = wait.get().waiterThreadId();
        System.out.println("H4-OBSERVE wait=" + wait.get());

        holder.rollback();
        assertThat(waiter.get(30, TimeUnit.SECONDS)).as("in-flight로 확보한 ID = 그 트랜잭션의 connection_id()")
          .isEqualTo(waiterThreadId);
      } finally {
        holder.rollback();
        if (waiter != null) {
          waiter.cancel(true);
        }
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
      }
      long open = LockWaitObserver.awaitNoOpenTransactions(observer,
        waiterThreadId < 0 ? List.of(holderThreadId) : List.of(holderThreadId, waiterThreadId), Duration.ofSeconds(5));
      if (open > 0) {
        throw HarnessException.harness("cleanup left open transactions: " + open + LockWaitObserver.diagnostics(observer));
      }
    }
  }

  // ===================== H-5 부분 갱신 관찰·문장 rollback =====================

  @Test
  @Timeout(60)
  @DisplayName("H-5 앞 행을 갱신한 UPDATE가 잠긴 뒤 행에서 대기하면 trx_rows_modified로 부분 갱신을 관찰하고, 잠금 timeout 뒤 문장 전체가 rollback된다 (AC-13 장치)")
  void h5_partialUpdateObservedAndStatementRolledBack() throws Exception {
    List<Long> rows = List.of(
      outbox.insertBare("FAILED", 5, null, 5L, null),
      outbox.insertBare("FAILED", 5, null, 5L, null),
      outbox.insertBare("FAILED", 5, null, 5L, null));
    Long locked = rows.get(2);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicLong waiterThreadId = new AtomicLong(-1);
    long holderThreadId = -1;
    try (Connection observer = LockWaitObserver.rootConnection(DB); Connection holder = directConnection()) {
      LockWaitObserver.precheck(observer);
      holder.setAutoCommit(false);
      Future<Integer> waiter = null;
      try {
        holderThreadId = LockWaitObserver.connectionId(holder);
        lockRow(holder, "select id from outbox_event where id = ? for update", locked);

        // 테스트 전용 probe 문장: 기본키 범위를 id 오름차순으로 읽으며 행마다 갱신해 앞의 두 행을 먼저 바꾸게 한다
        // (ORDER BY를 붙이면 정렬을 위해 대상 행을 먼저 모두 읽어 갱신 전에 대기할 수 있다 - 1회차 관찰). production 프로시저와 무관
        String probe = "update outbox_event set state_version = state_version + 1 where id between "
          + rows.get(0) + " and " + locked + " and status = 'FAILED'";
        try (Statement explain = holder.createStatement(); ResultSet plan = explain.executeQuery("explain " + probe)) {
          while (plan.next()) {
            System.out.println("H5-OBSERVE explain type=" + plan.getString("type") + " key=" + plan.getString("key")
              + " extra=" + plan.getString("Extra"));
          }
        }
        waiter = executor.submit(() -> {
          try (Connection w = directConnection(); Statement st = w.createStatement()) {
            st.execute("set session innodb_lock_wait_timeout = 3");
            waiterThreadId.set(LockWaitObserver.connectionId(w));
            return st.executeUpdate(probe);
          }
        });
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        while (waiterThreadId.get() < 0 && System.nanoTime() < deadline) {
          Thread.sleep(20);
        }

        Future<Integer> running = waiter;
        Optional<LockWaitObserver.Wait> wait = LockWaitObserver.await(observer, waiterThreadId.get(), holderThreadId,
          "outbox_event", String.valueOf(locked), running::isDone, WAIT_TIMEOUT);
        assertThat(wait).as("잠긴 행 대기 관찰").isPresent();
        long modified = LockWaitObserver.rowsModified(observer, wait.get().requestingTrxId());
        System.out.println("H5-OBSERVE rows_modified_while_waiting=" + modified);
        assertThat(modified).as("대기 시점에 이미 갱신한 행 수").isEqualTo(2L);

        Future<Integer> pending = waiter;
        assertThatThrownBy(() -> pending.get(30, TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .satisfies(e -> {
            SQLException cause = (SQLException) e.getCause();
            System.out.println("H5-OBSERVE timeout sqlstate=" + cause.getSQLState() + " vendor=" + cause.getErrorCode());
            assertThat(cause.getErrorCode()).isEqualTo(1205);
          });
      } finally {
        holder.rollback();
        if (waiter != null) {
          waiter.cancel(true);
        }
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
      }
      long open = LockWaitObserver.awaitNoOpenTransactions(observer, List.of(holderThreadId, waiterThreadId.get()),
        Duration.ofSeconds(5));
      if (open > 0) {
        throw HarnessException.harness("cleanup left open transactions: " + open + LockWaitObserver.diagnostics(observer));
      }
      System.out.println("H5-OBSERVE innodb_rollback_on_timeout="
        + jdbcTemplate.queryForObject("select @@global.innodb_rollback_on_timeout", String.class));
    }
    for (Long id : rows) {
      assertThat(jdbcTemplate.queryForObject("select state_version from outbox_event where id = ?", Long.class, id))
        .as("문장 rollback 후 행 %d 버전", id).isEqualTo(5L);
    }
  }

  // ===================== H-6 장애 trigger 장치 =====================

  @Test
  @DisplayName("H-6 장애 trigger 장치: REQUEUE_SECOND는 한 행 실제 갱신 뒤 다음 행에서 SIGNAL·문장 전체 rollback, RECORD는 지정 행의 실패 횟수 변경만 차단")
  void h6_faultTriggerDevice() throws SQLException {
    outbox.installFaultTriggers(DB);
    try {
      // REQUEUE_SECOND: 갱신 순서와 무관하게 첫 실제 갱신 뒤 다음 행에서 장애 (실행 계획을 지정하지 않는 IN 목록)
      List<Long> rows = List.of(
        outbox.insertBare("FAILED", 5, null, 5L, null),
        outbox.insertBare("FAILED", 5, null, 5L, null),
        outbox.insertBare("FAILED", 5, null, 5L, null));
      outbox.armFault(DB, OutboxFixture.FaultMode.REQUEUE_SECOND, null);
      String in = rows.stream().map(String::valueOf).collect(Collectors.joining(","));
      try (Connection direct = directConnection(); Statement st = direct.createStatement()) {
        assertThatThrownBy(() -> st.executeUpdate("update outbox_event set status = 'PENDING', failed_attempts = 0, "
          + "state_version = state_version + 1 where id in (" + in + ") and status = 'FAILED'"))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("45000"));
      }
      List<OutboxFixture.FaultLog> log = outbox.faultLog();
      System.out.println("H6-OBSERVE requeue_second log=" + log);
      assertThat(log.stream().filter(l -> "UPDATED".equals(l.event())).count()).as("장애 전 실제 갱신 행").isEqualTo(1L);
      assertThat(log.stream().filter(l -> "SIGNAL".equals(l.event())).count()).as("장애 발생").isEqualTo(1L);
      for (Long id : rows) {
        OutboxFixture.RetryRow row = outbox.retryRow(id);
        assertThat(row.status()).as("행 %d 상태 rollback", id).isEqualTo("FAILED");
        assertThat(row.stateVersion()).as("행 %d 버전 rollback", id).isEqualTo(5L);
      }

      // RECORD: 지정 행의 failed_attempts 변경만 차단, 다른 행·다른 컬럼 변경은 통과
      outbox.dropFaultTriggers(DB);
      outbox.installFaultTriggers(DB);
      Long target = outbox.insertBare("PENDING", 0, null, 0L, null);
      Long other = outbox.insertBare("PENDING", 0, null, 0L, null);
      outbox.armFault(DB, OutboxFixture.FaultMode.RECORD, target);
      try (Connection direct = directConnection(); Statement st = direct.createStatement()) {
        assertThatThrownBy(() -> st.executeUpdate(
          "update outbox_event set failed_attempts = failed_attempts + 1, state_version = state_version + 1 where id = " + target))
          .isInstanceOf(SQLException.class)
          .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("45000"));
        assertThat(st.executeUpdate("update outbox_event set failed_attempts = failed_attempts + 1 where id = " + other))
          .as("다른 행 실패 횟수 변경").isEqualTo(1);
        assertThat(st.executeUpdate("update outbox_event set last_error_type = 'H6' where id = " + target))
          .as("대상 행의 다른 컬럼 변경").isEqualTo(1);
      }
      System.out.println("H6-OBSERVE record log=" + outbox.faultLog());
      assertThat(outbox.retryRow(target).failedAttempts()).isZero();
      assertThat(outbox.retryRow(target).stateVersion()).isZero();
      assertThat(outbox.retryRow(other).failedAttempts()).isEqualTo(1);
    } finally {
      outbox.dropFaultTriggers(DB);
    }
  }

  // ===================== H-7 실제 DB 오류 코드 =====================

  @Test
  @DisplayName("H-7 classifier와 무관한 직접 JDBC로 알림 UNIQUE·FK 위반의 실제 SQLState·vendor code를 관찰한다")
  void h7_observeUniqueAndForeignKeyErrorCodes() throws SQLException {
    Seeded s = outbox.seedPending();
    String insert = "insert into notification (member_id, payment_id, outbox_event_id, type, message) values (?, ?, ?, ?, 'h7')";
    try (Connection direct = directConnection()) {
      try (PreparedStatement ps = direct.prepareStatement(insert)) {
        ps.setLong(1, s.memberId());
        ps.setLong(2, s.paymentId());
        ps.setLong(3, s.outboxId());
        ps.setString(4, OutboxFixture.NOTIFICATION_TYPE);
        ps.executeUpdate();
      }
      SQLException unique = captureSqlException(direct, insert, s.memberId(), s.paymentId(), s.outboxId());
      SQLException foreignKey = captureSqlException(direct, insert, s.memberId(), Long.MAX_VALUE, s.outboxId());
      System.out.println("H7-OBSERVE unique class=" + unique.getClass().getSimpleName() + " sqlState=" + unique.getSQLState()
        + " vendor=" + unique.getErrorCode());
      System.out.println("H7-OBSERVE fk class=" + foreignKey.getClass().getSimpleName() + " sqlState=" + foreignKey.getSQLState()
        + " vendor=" + foreignKey.getErrorCode());
      System.out.println("H7-OBSERVE matches_known_candidates unique_1062=" + (unique.getErrorCode() == 1062)
        + " fk_1452=" + (foreignKey.getErrorCode() == 1452));
    } finally {
      jdbcTemplate.update("delete from notification where payment_id = ?", s.paymentId());
    }
  }

  private static SQLException captureSqlException(Connection connection, String sql, long memberId, long paymentId,
                                                  long outboxId) {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setLong(1, memberId);
      ps.setLong(2, paymentId);
      ps.setLong(3, outboxId);
      ps.setString(4, OutboxFixture.NOTIFICATION_TYPE);
      ps.executeUpdate();
    } catch (SQLException e) {
      return e;
    }
    throw HarnessException.harness("expected constraint violation did not occur: payment=" + paymentId);
  }

  private static Connection directConnection() throws SQLException {
    return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
  }

  private static String queryString(Statement st, String sql) throws SQLException {
    try (ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getString(1);
    }
  }
}
