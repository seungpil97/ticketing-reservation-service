package com.pil97.ticketing.common.persistence;

import com.pil97.ticketing.hold.application.scheduler.HoldExpirationScheduler;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeatStatus;
import com.pil97.ticketing.showtimeseat.domain.repository.ShowtimeSeatRepository;
import com.pil97.ticketing.support.consistency.ConsistencyFixture;
import com.pil97.ticketing.support.consistency.ConsistencyFixture.SeatRef;
import com.pil97.ticketing.support.consistency.HarnessException;
import com.pil97.ticketing.support.consistency.SqlObserver;
import com.pil97.ticketing.support.consistency.SqlObserverConfig;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;
import org.springframework.orm.jpa.JpaDialect;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.pil97.ticketing.support.consistency.ConsistencyRequests.hasSnapshotConflictCause;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.shutdown;
import static com.pil97.ticketing.support.consistency.ConsistencyRequests.worker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * TASK-062 (#104) 실제 MariaDB → JPA 경로의 1020 전달 계약 (RED_REQUIRED)
 *
 * <p>증거 범위
 * <ul>
 *   <li>등록 경로(C3): 애플리케이션 EntityManagerFactory의 dialect가 SnapshotConflictHibernateJpaDialect이고,
 *       JpaTransactionManager가 같은 인스턴스를 쓰는지. 등록된 dialect의 변환 동작은 아래 두 경계 테스트가 따로 검증한다</li>
 *   <li>문장 실행 경계: repository 잠금 조회 중 실제 DB가 낸 1020이 ConcurrentUpdateConflictException으로 전달되는지</li>
 *   <li>flush·commit 경계: commit flush 중 실제 DB가 낸 1020이 트랜잭션 종료 후 ConcurrentUpdateConflictException으로 전달되는지</li>
 * </ul>
 * 1020은 테스트가 만든 실제 DB 조건(일관 읽기 → 다른 커넥션의 갱신·커밋 → 같은 트랜잭션의 잠금·갱신)에서 DB가 직접 낸다.
 * DB가 1020을 내지 않았으면(관찰 이벤트에 1020 종료가 없으면) HarnessException(PRECONDITION)으로 중단한다 (Invalid).
 * 경쟁자는 별도 worker thread의 JdbcTemplate(autocommit)으로 커밋한다.
 * 업무 서비스의 경로가 1020을 낸다는 증거는 아니다. HTTP 응답은 PaymentConflictHttpIntegrationTest가 담당한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ConsistencyFixture.class, SqlObserverConfig.class})
class SnapshotConflictJpaPathIntegrationTest {

  private static final Pattern SEAT_LOCK = Pattern.compile("from showtime_seat \\S+ where .* for update");
  private static final Pattern SEAT_UPDATE = Pattern.compile("update showtime_seat ");

  @MockitoBean
  private HoldExpirationScheduler holdExpirationScheduler;

  @Autowired private ConsistencyFixture fixture;
  @Autowired private ShowtimeSeatRepository showtimeSeatRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private ExecutorService competitor;

  @BeforeEach
  void setUp() {
    SqlObserver.clear();
    competitor = worker("t062-jpa-competitor-" + System.nanoTime());
  }

  @AfterEach
  void tearDown() {
    shutdown(competitor);
    SqlObserver.clear();
    fixture.cleanup();
  }

  @Test
  @DisplayName("등록 경로(C3): EntityManagerFactory의 dialect가 SnapshotConflictHibernateJpaDialect이고 JpaTransactionManager가 같은 인스턴스를 사용한다")
  void registration_emfAndTransactionManagerShareC3Dialect() {
    JpaDialect emfDialect = ((EntityManagerFactoryInfo) entityManagerFactory).getJpaDialect();
    assertThat(emfDialect).isInstanceOf(SnapshotConflictHibernateJpaDialect.class);
    assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
    assertThat(((JpaTransactionManager) transactionManager).getJpaDialect()).isSameAs(emfDialect);
  }

  @Test
  @DisplayName("문장 실행 경계: 일관 읽기 후 다른 커넥션이 갱신·커밋한 좌석을 repository로 잠금 조회하면 ConcurrentUpdateConflictException(원인 1020)")
  void statementBoundary_repositoryLockingRead_translated() throws Exception {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    String testThread = Thread.currentThread().getName();
    AtomicReference<Throwable> lockError = new AtomicReference<>();
    AtomicInteger completion = new AtomicInteger(-1);

    transactionTemplate.executeWithoutResult(status -> {
      registerCompletion(completion);
      showtimeSeatRepository.findById(seat.showtimeSeatId()).orElseThrow();   // read view
      commitByCompetitor(seat, ShowtimeSeatStatus.RESERVED);
      lockError.set(catchThrowable(() -> showtimeSeatRepository.findByIdForUpdate(seat.showtimeSeatId())));
      // 1020 이후 같은 트랜잭션에서 더 진행하지 않는다
      status.setRollbackOnly();
    });

    requireDbConflict(testThread, SEAT_LOCK);
    SqlObserver.requireSessionDiagnostics(testThread);

    assertThat(lockError.get()).isInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(hasSnapshotConflictCause(lockError.get())).as("cause chain keeps 1020 SQLException").isTrue();
    assertThat(completion.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(fixture.seatStatus(seat)).as("competitor commit preserved").isEqualTo("RESERVED");
  }

  @Test
  @DisplayName("flush·commit 경계: 일관 읽기 후 다른 커넥션이 갱신·커밋한 좌석을 수정해 commit하면 트랜잭션 종료 후 ConcurrentUpdateConflictException(원인 1020)")
  void commitBoundary_flushUpdate_translated() throws Exception {
    SeatRef seat = fixture.seat(ShowtimeSeatStatus.HELD);
    String testThread = Thread.currentThread().getName();
    AtomicInteger completion = new AtomicInteger(-1);

    Throwable commitError = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
      registerCompletion(completion);
      ShowtimeSeat loaded = showtimeSeatRepository.findById(seat.showtimeSeatId()).orElseThrow();   // read view
      commitByCompetitor(seat, ShowtimeSeatStatus.RESERVED);
      loaded.markAvailable();   // 메모리 상태 HELD → AVAILABLE, commit flush에서 UPDATE
    }));

    requireDbConflict(testThread, SEAT_UPDATE);
    SqlObserver.requireSessionDiagnostics(testThread);

    assertThat(commitError).isInstanceOf(ConcurrentUpdateConflictException.class);
    assertThat(hasSnapshotConflictCause(commitError)).as("cause chain keeps 1020 SQLException").isTrue();
    assertThat(completion.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(fixture.seatStatus(seat)).as("stale update not applied, competitor commit preserved").isEqualTo("RESERVED");
  }

  private void commitByCompetitor(SeatRef seat, ShowtimeSeatStatus status) {
    try {
      competitor.submit(() -> fixture.setSeatStatus(seat, status)).get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw HarnessException.harness("interrupted while waiting for competitor commit", e);
    } catch (Exception e) {
      throw HarnessException.harness("competitor commit failed: " + e, e);
    }
  }

  private static void registerCompletion(AtomicInteger completion) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        completion.set(status);
      }
    });
  }

  /**
   * 사전조건: 이 thread의 대상 SQL 실행이 실제 DB에서 1020으로 끝났어야 한다 (아니면 Invalid)
   */
  private static void requireDbConflict(String threadName, Pattern targetSql) {
    List<SqlObserver.Event> conflicts = SqlObserver.snapshotConflicts(threadName).stream()
      .filter(e -> targetSql.matcher(e.sql()).find())
      .toList();
    if (conflicts.isEmpty()) {
      throw HarnessException.precondition("DB did not raise 1020 for " + targetSql + " on thread " + threadName
        + " (executions=" + SqlObserver.executions(threadName) + ")");
    }
    System.out.println("[SnapshotConflict] db-raised " + conflicts);
  }
}
