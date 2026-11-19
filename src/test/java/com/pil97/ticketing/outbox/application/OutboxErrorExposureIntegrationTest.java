package com.pil97.ticketing.outbox.application;

import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import com.pil97.ticketing.support.outbox.IsolatedMariaDb;
import com.pil97.ticketing.support.outbox.MutableClock;
import com.pil97.ticketing.support.outbox.OutboxFaultInterceptor;
import com.pil97.ticketing.support.outbox.OutboxFixture;
import com.pil97.ticketing.support.outbox.OutboxFixture.Seeded;
import com.pil97.ticketing.support.scheduling.SchedulerExclusionConfig;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-033-1 (#115) AC-14 오류 원문 비노출 통합 테스트 (격리 MariaDB 컨테이너, test profile, 출력 캡처)
 *
 * <p>보장·단정 범위: (1) Outbox 행에 저장되는 오류 정보(payload 제외 전 컬럼) (2) logger 이름 com.pil97.ticketing.outbox 아래
 * 애플리케이션 logger가 남긴 로그 이벤트 - 형식화된 메시지와 첨부 예외 체인의 클래스명·메시지 전체.
 * 로그 이벤트는 Logback ListAppender로 logger 이름 기준으로 직접 수집한다.
 *
 * <p>관찰만(단정 아님): 같은 실행의 stdout·stderr 전체(CapturedOutput.getAll)에서 가짜 문자열 포함 여부와 전역 logger
 * (Hibernate bind trace, SqlExceptionHelper) 출력 여부를 AC14-OBSERVE로 남긴다. application 로그 검증 통과는 전체 출력 비노출을 뜻하지 않는다.
 */
@Testcontainers
@SpringBootTest(properties = {
  "outbox.relay.batch-size=2",
  "outbox.relay.retry.max-attempts=5",
  "outbox.relay.retry.initial-backoff=30s",
  "outbox.relay.retry.max-backoff=10m",
  "spring.jpa.properties.hibernate.session_factory.interceptor="
    + "com.pil97.ticketing.support.outbox.OutboxFaultInterceptor"})
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, OutboxFixture.class, SchedulerExclusionConfig.class, SqlObserverConfig.class,
  MutableClock.Config.class})
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class OutboxErrorExposureIntegrationTest {

  private static final String FAKE_SECRET = "FAKE-SECRET-7f3c";
  private static final String FAKE_URL = "jdbc:mariadb://fake-host:1/x?password=FAKE";
  private static final String OUTBOX_LOGGER = "com.pil97.ticketing.outbox";
  private static final List<String> FORBIDDEN = List.of(FAKE_SECRET, "fake-host", "password=FAKE");

  private final ListAppender<ILoggingEvent> outboxEvents = new ListAppender<>();

  @Container
  static final MariaDBContainer<?> DB = IsolatedMariaDb.create();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    IsolatedMariaDb.register(registry, DB);
  }

  @Autowired private ConsistencyFixture fixture;
  @Autowired private OutboxFixture outbox;
  @Autowired private OutboxRelayService relayService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;
  @Autowired private ApplicationContext context;

  @BeforeEach
  void setUp() {
    SchedulerExclusionConfig.assertNoScheduledTasks(context);
    outbox.assertIsolated(DB);
    OutboxFaultInterceptor.reset();
    SqlObserver.clear();
    clock.set(MutableClock.START);
    outboxEvents.list.clear();
    outboxEvents.start();
    ((Logger) LoggerFactory.getLogger(OUTBOX_LOGGER)).addAppender(outboxEvents);
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(OUTBOX_LOGGER)).detachAppender(outboxEvents);
    outboxEvents.stop();
    OutboxFaultInterceptor.reset();
    outbox.cleanup();
    fixture.cleanup();
    SqlObserver.clear();
  }

  /**
   * 로그 이벤트에서 검사할 문자열 - 형식화된 메시지, 첨부 예외 체인의 클래스명·메시지 (suppressed 포함)
   */
  private static List<String> eventTexts(ILoggingEvent event) {
    List<String> texts = new ArrayList<>();
    texts.add(event.getFormattedMessage());
    collectThrowable(event.getThrowableProxy(), texts);
    return texts;
  }

  private static void collectThrowable(IThrowableProxy proxy, List<String> texts) {
    if (proxy == null) {
      return;
    }
    texts.add(proxy.getClassName());
    texts.add(String.valueOf(proxy.getMessage()));
    if (proxy.getSuppressed() != null) {
      for (IThrowableProxy suppressed : proxy.getSuppressed()) {
        collectThrowable(suppressed, texts);
      }
    }
    collectThrowable(proxy.getCause(), texts);
  }

  private void assertNotExposed(Long outboxId, CapturedOutput output, String label) {
    Map<String, Object> row = jdbcTemplate.queryForMap("select * from outbox_event where id = ?", outboxId);
    row.entrySet().stream()
      .filter(e -> !"payload".equalsIgnoreCase(e.getKey()))
      .forEach(e -> {
        for (String forbidden : FORBIDDEN) {
          assertThat(String.valueOf(e.getValue())).as("%s Outbox 컬럼 %s", label, e.getKey()).doesNotContain(forbidden);
        }
      });
    assertThat(row.get("last_error_code")).as("%s 오류 분류 기록", label).isNotNull();

    // 우연 통과 방지: 실패를 알리는 Outbox application 로그 이벤트가 실제로 수집됐다
    List<ILoggingEvent> events = List.copyOf(outboxEvents.list);
    assertThat(events).as("%s Outbox 실패 로그 이벤트", label)
      .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("outboxEventId=" + outboxId));
    for (ILoggingEvent event : events) {
      for (String text : eventTexts(event)) {
        for (String forbidden : FORBIDDEN) {
          assertThat(text).as("%s logger %s 이벤트", label, event.getLoggerName()).doesNotContain(forbidden);
        }
      }
    }

    // 관찰만: 전체 출력과 전역 logger
    String all = output.getAll();
    System.out.println("AC14-OBSERVE " + label + " full_output_contains_fake="
      + FORBIDDEN.stream().anyMatch(all::contains));
    for (String outsideScope : List.of("SqlExceptionHelper", "org.hibernate.orm.jdbc.bind")) {
      System.out.println("AC14-OBSERVE " + label + " global logger " + outsideScope + " present=" + all.contains(outsideScope));
    }
  }

  @Test
  @DisplayName("AC-14 깨진 payload의 가짜 민감 문자열·접속 URL이 payload 외 Outbox 저장 컬럼과 Outbox logger 이벤트에 나오지 않는다 (전체 캡처 출력은 관찰만)")
  void ac14_payloadContentNotExposed(CapturedOutput output) {
    Seeded s = outbox.seedPendingWithPayload("{\"paymentId\": " + FAKE_SECRET + " " + FAKE_URL + "}");

    relayService.relay();

    assertThat(outbox.snapshot(s).status()).isEqualTo("PENDING");
    assertNotExposed(s.outboxId(), output, "payload");
  }

  @Test
  @DisplayName("AC-14 예외 메시지의 가짜 민감 문자열·접속 URL이 payload 외 Outbox 저장 컬럼과 Outbox logger 이벤트에 나오지 않는다 (전체 캡처 출력은 관찰만)")
  void ac14_exceptionMessageNotExposed(CapturedOutput output) {
    Seeded s = outbox.seedPending();
    OutboxFaultInterceptor.failOnce(s.outboxId(), "flush failed " + FAKE_SECRET + " url=" + FAKE_URL);

    relayService.relay();

    assertThat(OutboxFaultInterceptor.injectedCount(s.outboxId())).isEqualTo(1);
    assertNotExposed(s.outboxId(), output, "exception");
  }
}
