package com.pil97.ticketing.support.consistency;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * worker가 의도한 경쟁 지점(대상 SQL이 서버에서 실행 중이며 반환되지 않은 상태)에 도달했는지 확인하는 테스트 도구
 *
 * <p>관찰 방식 - 하나의 절대 deadline 안에서 반복한다
 * <ol>
 *   <li>worker thread의 현재 in-flight 실행을 고른다. 대상 패턴이 아니거나 없으면 다음 poll로 넘어간다
 *       (선행 SQL이 정상 반환되면 다음 실행을 다시 고른다)</li>
 *   <li>선택한 실행(execId)이 바뀌면 연속 관찰 횟수를 0으로 초기화한다</li>
 *   <li>선택한 실행의 connection ID로 PROCESSLIST 행을 조회해 COMMAND='Query'이고
 *       INFO가 선택한 실행의 SQL 템플릿('?' 자리만 값으로 치환)과 전체 일치하는지 확인한다</li>
 *   <li>INNODB_TRX를 읽을 수 있으면 같은 thread id의 trx_state='LOCK WAIT'를 요구한다</li>
 *   <li>서버 상태 조회 뒤에도 같은 실행이 in-flight인지 다시 확인한다 (조회 중 반환되면 그 관찰은 버린다)</li>
 *   <li>같은 실행으로 2회 연속 확인되고, 반환 직전 최종 유효성(in-flight, worker 미종료)도 통과하면 성공</li>
 * </ol>
 * 판정 결과
 * <ul>
 *   <li>LOCK_WAIT: InnoDB 락 대기까지 입증</li>
 *   <li>SERVER_EXECUTING: INNODB_TRX 조회 불가 환경에서의 보조 증거. 대상 SQL이 서버에 도달해 반환되지 않았음까지만 입증하며,
 *       호출자가 경쟁 행 락을 보유하고(contentionHolder) 확인 뒤에 해제하는 경우에만 사용한다. 락 대기 자체는 입증하지 않는다</li>
 *   <li>CONFLICT_ABORT: 대기 관찰 전에 worker가 끝났지만, 그 worker thread의 <b>대상 SQL 실행</b>이 vendor errorCode 1020
 *       (ER_CHECKREAD)으로 종료됐고 worker 결과도 1020 원인으로 실패한 경우. "경쟁 지점 도달 + 충돌 종료" 증거이며
 *       RED 쪽 경쟁 지점 도달 증거로만 쓴다 (GREEN에서는 호출 측의 1020 0건 단언으로 실패한다).
 *       경쟁 보유 상태는 호출 구조로 보장한다: 각 시나리오는 보유 상태를 만든 뒤에 이 테스트 전용 이름의 worker에 작업을 제출한다</li>
 * </ul>
 * 시간 경과나 Future 미완료만으로는 통과시키지 않는다.
 * worker 종료(대상 SQL 미실행, 대상 SQL 정상 종료, 1020 이외 오류, worker 결과 불일치), connection ID 미확보,
 * PROCESSLIST 조회 불가, deadline 내 미확인은 HarnessException으로 중단한다 (Invalid RED).
 * DB 권한은 변경하지 않는다.
 */
public final class LockWaitProbe {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
  private static final int REQUIRED_CONSECUTIVE = 2;

  private LockWaitProbe() {
  }

  public enum Level {
    LOCK_WAIT, SERVER_EXECUTING, CONFLICT_ABORT
  }

  public record Evidence(
    Level level,
    String workerThread,
    long execId,
    long connectionId,
    String statement,
    String processInfo,
    String contentionHolder
  ) {
  }

  /**
   * @param contentionHolder 경쟁 행 락을 보유한 주체 설명 (증거 기록용, SERVER_EXECUTING 사용 조건)
   */
  public static Evidence awaitBlocked(
    JdbcTemplate jdbcTemplate,
    String workerThreadName,
    Pattern targetSql,
    String contentionHolder,
    Future<?> worker,
    Duration timeout
  ) {
    long deadline = System.nanoTime() + timeout.toNanos();
    SqlObserver.Event selected = null;
    int consecutive = 0;
    String lastReason = "no observation";

    while (System.nanoTime() < deadline) {
      if (worker.isDone()) {
        return conflictAbortOrFail(workerThreadName, targetSql, contentionHolder, worker, lastReason);
      }

      Optional<SqlObserver.Event> current = SqlObserver.currentInFlight(workerThreadName);
      if (current.isEmpty() || !targetSql.matcher(current.get().sql()).find()) {
        selected = null;
        consecutive = 0;
        lastReason = current.map(e -> "non-target in-flight: " + e.sql()).orElse("no in-flight statement");
        sleepPoll();
        continue;
      }

      SqlObserver.Event exec = current.get();
      if (selected == null || selected.execId() != exec.execId()) {
        selected = exec;
        consecutive = 0;
      }
      if (exec.connectionId() < 0) {
        throw HarnessException.harness("server connection id unavailable for worker " + workerThreadName);
      }

      Map<String, Object> process = processRow(jdbcTemplate, exec.connectionId());
      Level level = null;
      String info = null;
      if (process == null) {
        lastReason = "connection " + exec.connectionId() + " not visible in PROCESSLIST";
      } else {
        info = SqlObserver.normalize(String.valueOf(process.get("INFO")));
        boolean corresponds = "Query".equalsIgnoreCase(String.valueOf(process.get("COMMAND")))
          && templateMatches(exec.sql(), info);
        if (!corresponds) {
          lastReason = "PROCESSLIST does not correspond to exec " + exec.execId() + ": " + info;
        } else {
          Optional<String> trxState = innodbTrxState(jdbcTemplate, exec.connectionId());
          if (trxState.isEmpty()) {
            level = Level.SERVER_EXECUTING;
          } else if ("LOCK WAIT".equalsIgnoreCase(trxState.get())) {
            level = Level.LOCK_WAIT;
          } else {
            lastReason = "INNODB_TRX state " + trxState.get() + " for exec " + exec.execId();
          }
        }
      }

      // 서버 상태 조회 중 선택한 실행이 반환됐으면 그 관찰은 이 실행에 대응한다고 볼 수 없다
      if (!SqlObserver.stillInFlight(exec)) {
        selected = null;
        consecutive = 0;
        lastReason = "exec " + exec.execId() + " returned normally; re-selecting";
        continue;
      }

      consecutive = level != null ? consecutive + 1 : 0;
      if (consecutive >= REQUIRED_CONSECUTIVE) {
        if (SqlObserver.stillInFlight(exec) && !worker.isDone()) {
          Evidence evidence = new Evidence(level, workerThreadName, exec.execId(), exec.connectionId(),
            exec.sql(), info, contentionHolder);
          System.out.println("[LockWaitProbe] " + evidence);
          return evidence;
        }
        selected = null;
        consecutive = 0;
        lastReason = "exec " + exec.execId() + " invalid at final check";
        continue;
      }
      sleepPoll();
    }

    throw HarnessException.harness("contention of worker " + workerThreadName + " not confirmed within "
      + timeout + " (last=" + lastReason + ", holder=" + contentionHolder + ")");
  }

  /**
   * 대기 관찰 전에 worker가 끝난 경우의 판정
   * - 대상 SQL 실행이 1020으로 종료됐고 worker 결과도 1020 원인 실패이면 CONFLICT_ABORT
   * - 그 외(대상 SQL 미실행, 대상 SQL 정상 종료, 1020 이외 오류, worker 결과 불일치)는 HarnessException
   */
  private static Evidence conflictAbortOrFail(String workerThreadName, Pattern targetSql, String contentionHolder,
                                              Future<?> worker, String lastReason) {
    List<SqlObserver.Event> targets = SqlObserver.executions(workerThreadName).stream()
      .filter(e -> targetSql.matcher(e.sql()).find())
      .toList();
    String prefix = "worker " + workerThreadName + " finished before contention was confirmed (last=" + lastReason + "); ";
    if (targets.isEmpty()) {
      throw HarnessException.harness(prefix + "no target statement executed");
    }
    SqlObserver.Event conflicted = null;
    SqlObserver.Event conflictedEnd = null;
    StringBuilder outcomes = new StringBuilder();
    for (SqlObserver.Event start : targets) {
      Optional<SqlObserver.Event> end = SqlObserver.endOf(start);
      outcomes.append("[exec ").append(start.execId()).append(": ")
        .append(end.map(e -> e.failed() ? "errorCode=" + e.errorCode() + " sqlState=" + e.sqlState() : "ok")
          .orElse("not ended"))
        .append("]");
      if (end.isPresent() && end.get().errorCode() == SqlObserver.ER_CHECKREAD) {
        conflicted = start;
        conflictedEnd = end.get();
      }
    }
    if (conflicted == null) {
      throw HarnessException.harness(prefix + "target statement(s) did not end with 1020 " + outcomes);
    }
    Throwable workerFailure = workerFailure(worker);
    if (!ConsistencyRequests.hasSnapshotConflictCause(workerFailure)) {
      throw HarnessException.harness(prefix + "target exec " + conflicted.execId()
        + " ended with 1020 but worker result is not a 1020 failure: " + workerFailure);
    }
    Evidence evidence = new Evidence(Level.CONFLICT_ABORT, workerThreadName, conflicted.execId(),
      conflicted.connectionId(), conflicted.sql(),
      "EXEC_END seq=" + conflictedEnd.seq() + " errorCode=" + conflictedEnd.errorCode()
        + " sqlState=" + conflictedEnd.sqlState(), contentionHolder);
    System.out.println("[LockWaitProbe] " + evidence);
    return evidence;
  }

  private static Throwable workerFailure(Future<?> worker) {
    try {
      worker.get(0, TimeUnit.MILLISECONDS);
      return null;
    } catch (ExecutionException e) {
      return e.getCause();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw HarnessException.harness("interrupted while reading worker result", e);
    } catch (TimeoutException | CancellationException e) {
      return e;
    }
  }

  /**
   * PROCESSLIST INFO가 선택한 실행의 SQL 템플릿과 대응하는지 확인 - '?' 자리만 임의 값으로 허용하는 전체 일치
   */
  static boolean templateMatches(String template, String info) {
    String[] parts = template.split("\\?", -1);
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        regex.append(".+?");
      }
      regex.append(Pattern.quote(parts[i]));
    }
    return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(info).matches();
  }

  private static Map<String, Object> processRow(JdbcTemplate jdbcTemplate, long connectionId) {
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select ID, COMMAND, STATE, INFO from information_schema.PROCESSLIST where ID = ?", connectionId);
      return rows.isEmpty() ? null : rows.get(0);
    } catch (DataAccessException e) {
      throw HarnessException.harness("information_schema.PROCESSLIST not readable: " + e.getMessage(), e);
    }
  }

  /**
   * INNODB_TRX 상태 조회 - 권한 등으로 조회할 수 없으면 empty (권한은 변경하지 않음)
   */
  private static Optional<String> innodbTrxState(JdbcTemplate jdbcTemplate, long connectionId) {
    try {
      List<String> states = jdbcTemplate.queryForList(
        "select trx_state from information_schema.INNODB_TRX where trx_mysql_thread_id = ?", String.class, connectionId);
      return states.isEmpty() ? Optional.of("NO_TRX") : Optional.of(states.get(0));
    } catch (DataAccessException e) {
      return Optional.empty();
    }
  }

  private static void sleepPoll() {
    try {
      TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw HarnessException.harness("interrupted while polling contention", e);
    }
  }
}
