package com.pil97.ticketing.support.consistency;

import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.api.dto.request.HoldCreateRequest;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.payment.api.dto.request.CreatePaymentRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-062 테스트 요청 생성과 worker 결과 회수 도구
 */
public final class ConsistencyRequests {

  // worker 결과 회수 제한 시간
  public static final long WORKER_TIMEOUT_SECONDS = 15;

  private ConsistencyRequests() {
  }

  public static HoldCreateRequest holdRequest(ConsistencyFixture.SeatRef seat, Member member) {
    HoldCreateRequest request = new HoldCreateRequest();
    ReflectionTestUtils.setField(request, "seatId", seat.seatId());
    ReflectionTestUtils.setField(request, "memberId", member.getId());
    return request;
  }

  public static CreatePaymentRequest payRequest(Long reservationId, boolean forceFailure) {
    CreatePaymentRequest request = new CreatePaymentRequest();
    ReflectionTestUtils.setField(request, "reservationId", reservationId);
    ReflectionTestUtils.setField(request, "amount", 150000);
    ReflectionTestUtils.setField(request, "forceFailure", forceFailure);
    return request;
  }

  /**
   * 이름이 고정된 단일 worker thread (SqlObserver·CommitGate가 thread 이름으로 식별)
   */
  public static ExecutorService worker(String threadName) {
    return Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
  }

  /**
   * worker 정상 종료 결과 회수 - 예외가 있으면 원인을 그대로 던진다
   */
  public static <T> T result(Future<T> future) throws Exception {
    try {
      return future.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw e;
    }
  }

  /**
   * worker 실패 원인 회수 - 정상 종료하면 null
   */
  public static Throwable failure(Future<?> future) throws Exception {
    try {
      future.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return null;
    } catch (ExecutionException e) {
      return e.getCause();
    }
  }

  public static void assertBusinessError(Throwable error, ErrorCode expected) {
    assertThat(error)
      .as("expected BusinessException(%s)", expected)
      .isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) error).getErrorCode()).isEqualTo(expected);
  }

  /**
   * 원인 체인에서 vendor errorCode 1020(ER_CHECKREAD) SQLException을 찾는다
   */
  public static Optional<SQLException> snapshotConflictCause(Throwable error) {
    Set<Throwable> seen = new HashSet<>();
    for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
      if (t instanceof SQLException sql && sql.getErrorCode() == SqlObserver.ER_CHECKREAD) {
        return Optional.of(sql);
      }
    }
    return Optional.empty();
  }

  public static boolean hasSnapshotConflictCause(Throwable error) {
    return snapshotConflictCause(error).isPresent();
  }

  /**
   * 모든 worker에 종료를 요청하고 하나의 deadline 안에서 종료를 확인한다
   * - 종료를 확인하지 못하면 SqlObserver 격리 목록에 보존하고 HarnessException을 던진다
   */
  public static void shutdown(ExecutorService... executors) {
    SqlObserver.terminateWorkers(Arrays.asList(executors), Duration.ofSeconds(WORKER_TIMEOUT_SECONDS));
  }
}
