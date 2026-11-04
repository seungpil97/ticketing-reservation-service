package com.pil97.ticketing.support.consistency;

import com.pil97.ticketing.reservation.domain.Reservation;
import org.hibernate.Interceptor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.Type;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트 전용 Hibernate Interceptor - 지정 예약의 flush 시점에 <b>실제 DB가 1020(ER_CHECKREAD)을 반환하는 조건</b>을 만든다
 * - test property(spring.jpa.properties.hibernate.session_factory.interceptor)로만 등록한다
 *
 * <p>동작 (지정 예약의 onFlushDirty에서 1회)
 * <ol>
 *   <li>현재 트랜잭션에 바인딩된 커넥션으로 probe 좌석 행을 일관 읽기 (read view 보장)</li>
 *   <li>별도 thread·별도 커넥션(autocommit)에서 probe 좌석 행을 갱신·커밋 (경쟁자)</li>
 *   <li>같은 트랜잭션 커넥션으로 probe 좌석 행 FOR UPDATE → DB가 1020을 반환하고 트랜잭션 전체를 rollback</li>
 *   <li>SQLException을 Hibernate의 실제 변환기(SqlExceptionHelper)로 JDBCException으로 바꿔 던진다</li>
 * </ol>
 * 입증 범위: 실제 MariaDB가 결제 트랜잭션 안에서 낸 1020이 JPA commit 경계 → 예외 변환 → 트랜잭션 종료 → HTTP로 전달되는 경로.
 * 입증하지 않는 것: 결제 업무 코드 자체가 1020을 일으킨다는 것 (발생 지점은 이 interceptor가 인위적으로 만든다).
 * DB가 1020을 내지 않으면 HarnessException(PRECONDITION)으로 중단한다.
 *
 * <p>경쟁 worker 종료·격리
 * <ul>
 *   <li>경쟁 executor는 작업 제출 전에 정적 목록에 등록하고, 성공·실패와 관계없이 SqlObserver.terminateWorkers로 종료 요청·확인한다.
 *       shutdownNow 호출만으로 종료를 확인했다고 보지 않는다</li>
 *   <li>종료를 확인하지 못한 executor는 SqlObserver 격리 목록에 참조·원인이 보존되고, 이후 bind/arm/reset/clear와
 *       fixture 정리가 차단된다 (자동 초기화로 격리를 풀지 않는다)</li>
 *   <li>최초 실패(제출 실패·timeout·실행 예외·interrupt)가 있으면 종료 실패는 그 예외의 suppressed로 남긴다</li>
 *   <li>경쟁자 작업은 autocommit 갱신이다. CommitGate/StatementGate에 등록되지 않으므로 gate abort로 취소·rollback되지 않는다.
 *       종료 확인 전 갱신이 늦게 반영될 수 있다는 점이 격리가 필요한 이유다</li>
 * </ul>
 */
public class SnapshotConflictTrigger implements Interceptor {

  private static final AtomicReference<DataSource> DATA_SOURCE = new AtomicReference<>();
  private static final AtomicReference<SessionFactoryImplementor> SESSION_FACTORY = new AtomicReference<>();
  private static final AtomicReference<Arm> ARM = new AtomicReference<>();
  private static final AtomicInteger FIRED = new AtomicInteger();
  private static final AtomicReference<Throwable> TRIGGER_FAILURE = new AtomicReference<>();
  // 생성 후 아직 종료 확인 절차에 넘기지 않은 경쟁 executor (작업 제출 전에 등록)
  private static final List<ExecutorService> COMPETITORS = new ArrayList<>();

  private record Arm(Long reservationId, Long probeShowtimeSeatId, Runnable competitorCommit) {
  }

  /**
   * 테스트 context의 DataSource bean과 SessionFactory를 연결한다 (interceptor는 Hibernate가 생성하므로 static 보관)
   * - 격리 중이면 HarnessException (이전 테스트의 상태를 덮어쓰지 않는다)
   */
  public static void bind(DataSource dataSource, SessionFactoryImplementor sessionFactory) {
    requireIdle("bind");
    DATA_SOURCE.set(dataSource);
    SESSION_FACTORY.set(sessionFactory);
  }

  public static void arm(Long reservationId, Long probeShowtimeSeatId, Runnable competitorCommit) {
    requireIdle("arm");
    ARM.set(new Arm(reservationId, probeShowtimeSeatId, competitorCommit));
  }

  public static int firedCount() {
    return FIRED.get();
  }

  public static Throwable triggerFailure() {
    return TRIGGER_FAILURE.get();
  }

  /**
   * arm·발동 횟수·실패 원인을 지운다
   * - 격리 중이거나 종료 확인 절차에 넘기지 않은 경쟁 executor가 남아 있으면 아무것도 지우지 않고 HarnessException
   */
  public static void reset() {
    requireIdle("reset");
    ARM.set(null);
    FIRED.set(0);
    TRIGGER_FAILURE.set(null);
  }

  /**
   * teardown 1단계: 등록된 경쟁 executor 전부에 종료 요청 후 종료 확인
   * - 미확인 executor는 SqlObserver 격리 목록으로 옮겨지고 HarnessException (참조·원인 보존)
   */
  public static void terminateCompetitors() {
    HarnessException failure = terminateRegistered();
    if (failure != null) {
      throw failure;
    }
  }

  private static void requireIdle(String operation) {
    SqlObserver.requireNoIsolation();
    synchronized (COMPETITORS) {
      if (!COMPETITORS.isEmpty()) {
        throw HarnessException.harness(operation + " refused: " + COMPETITORS.size()
          + " trigger competitor executor(s) not yet passed to termination; call terminateCompetitors first");
      }
    }
  }

  private static ExecutorService newCompetitor() {
    ExecutorService executor = ConsistencyRequests.worker("t062-trigger-competitor-" + System.nanoTime());
    synchronized (COMPETITORS) {
      COMPETITORS.add(executor);
    }
    return executor;
  }

  /**
   * 등록된 executor를 공통 terminateWorkers에 넘긴다 (모든 executor 종료 요청 후 하나의 deadline 안에서 확인)
   * - 넘긴 executor는 목록에서 빠진다: 종료 확인되면 버리고, 미확인이면 SqlObserver 격리 목록이 참조를 보존한다
   * - 예상하지 못한 예외로 terminateWorkers가 끝나면 참조를 목록에 되돌려 보존한다
   * @return 종료 실패 (없으면 null)
   */
  private static HarnessException terminateRegistered() {
    List<ExecutorService> targets;
    synchronized (COMPETITORS) {
      targets = new ArrayList<>(COMPETITORS);
      COMPETITORS.clear();
    }
    if (targets.isEmpty()) {
      return null;
    }
    try {
      SqlObserver.terminateWorkers(targets, Duration.ofSeconds(ConsistencyRequests.WORKER_TIMEOUT_SECONDS));
      return null;
    } catch (HarnessException e) {
      return e;
    } catch (RuntimeException | Error e) {
      synchronized (COMPETITORS) {
        COMPETITORS.addAll(targets);
      }
      return HarnessException.harness("trigger competitor termination failed unexpectedly; references retained", e);
    }
  }

  @Override
  public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
                              String[] propertyNames, Type[] types) {
    Arm arm = ARM.get();
    if (arm == null || !(entity instanceof Reservation) || !Objects.equals(id, arm.reservationId())) {
      return false;
    }
    if (!ARM.compareAndSet(arm, null)) {
      return false;
    }
    FIRED.incrementAndGet();
    fire(arm);
    return false;
  }

  private static void fire(Arm arm) {
    DataSource dataSource = DATA_SOURCE.get();
    SessionFactoryImplementor sessionFactory = SESSION_FACTORY.get();
    if (dataSource == null || sessionFactory == null) {
      throw record(HarnessException.harness("SnapshotConflictTrigger not bound"));
    }
    Connection connection = DataSourceUtils.getConnection(dataSource);
    String lockSql = "select id from showtime_seat where id = ? for update";
    try {
      // 1) 일관 읽기 - read view 보장
      try (PreparedStatement ps = connection.prepareStatement("select status from showtime_seat where id = ?")) {
        ps.setLong(1, arm.probeShowtimeSeatId());
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
        }
      } catch (SQLException e) {
        throw record(HarnessException.harness("probe consistent read failed: " + e));
      }

      // 2) 경쟁자 커밋 - 별도 thread·별도 커넥션. executor는 제출 전에 등록하고, 결과와 관계없이 종료를 확인한다
      ExecutorService competitor = newCompetitor();
      HarnessException primary = null;
      try {
        competitor.submit(arm.competitorCommit()).get(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        primary = HarnessException.harness("interrupted while waiting for competitor commit", e);
      } catch (TimeoutException e) {
        primary = HarnessException.harness("competitor commit not finished within 10s (may still commit late)", e);
      } catch (ExecutionException e) {
        primary = HarnessException.harness("competitor commit failed: " + e.getCause(), e.getCause());
      } catch (RuntimeException | Error e) {
        // 작업 제출 실패 (RejectedExecutionException 등)
        primary = HarnessException.harness("competitor submit failed: " + e, e);
      }
      HarnessException termination = terminateRegistered();
      if (primary != null) {
        if (termination != null) {
          primary.addSuppressed(termination);
        }
        throw record(primary);
      }
      if (termination != null) {
        // 경쟁자 커밋은 끝났지만 worker 종료를 확인하지 못함 → 1020 단계로 진행하지 않는다
        throw record(termination);
      }

      // 3) 같은 트랜잭션에서 잠금 읽기 → DB 1020 기대
      try (PreparedStatement ps = connection.prepareStatement(lockSql)) {
        ps.setLong(1, arm.probeShowtimeSeatId());
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
        }
      } catch (SQLException e) {
        if (e.getErrorCode() != SqlObserver.ER_CHECKREAD) {
          throw record(HarnessException.harness("probe lock failed with non-1020 error: " + e));
        }
        // 4) Hibernate 실제 변환기로 JDBCException 변환 (flush 중 SQL 실패와 같은 경로)
        throw sessionFactory.getJdbcServices().getSqlExceptionHelper()
          .convert(e, "snapshot conflict trigger", lockSql);
      }
      throw record(HarnessException.precondition("DB did not raise 1020 on probe lock (snapshot isolation off?)"));
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  private static HarnessException record(HarnessException e) {
    TRIGGER_FAILURE.compareAndSet(null, e);
    return e;
  }
}
