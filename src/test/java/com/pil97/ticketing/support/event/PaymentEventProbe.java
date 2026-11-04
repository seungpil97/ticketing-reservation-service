package com.pil97.ticketing.support.event;

import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * TASK-032 결제 이벤트 관찰·장애 주입용 테스트 리스너
 * - 이벤트 클래스를 import하지 않고 단순 클래스명으로 식별한다 (이벤트가 없는 기준 코드에서도 컴파일된다)
 * - 이벤트 클래스명 계약: PaymentSucceededEvent, PaymentFailedEvent, PaymentRefundedEvent (패키지는 고정하지 않는다)
 * - component scan 대상이 아니며, 사용하는 테스트 클래스가 @Import로만 등록한다 (기존 테스트 context에 영향 없음)
 *
 * <p>리스너 순서 계약 (기본 order 값에 의존하지 않는다):
 * <ul>
 *   <li>probe는 @Order(Ordered.LOWEST_PRECEDENCE)로 선언한다</li>
 *   <li>상태를 변경하는 업무 리스너는 @Order(또는 Ordered)를 명시하고 그 값은 Ordered.LOWEST_PRECEDENCE보다 작아야 한다</li>
 *   <li>Spring은 같은 이벤트의 리스너를 order 오름차순으로 호출하므로 probe의 observer·장애 주입은 업무 리스너 뒤에 실행된다</li>
 *   <li>조건 위반(예: 업무 리스너가 order 미지정 또는 LOWEST_PRECEDENCE)은 AC-6의 수신 시점 상태 단언으로 드러난다</li>
 * </ul>
 *
 * <p>수신 시점 트랜잭션에 TransactionSynchronization을 등록해 커밋 직전 관찰값과 완료 상태를 기록한다.
 * beforeCommit은 모든 afterCommit보다 먼저 실행되므로 synchronization 등록 순서와 무관하다.
 */
public class PaymentEventProbe {

  public static final String SUCCEEDED = "PaymentSucceededEvent";
  public static final String FAILED = "PaymentFailedEvent";
  public static final String REFUNDED = "PaymentRefundedEvent";

  private static final Set<String> NAMES = Set.of(SUCCEEDED, FAILED, REFUNDED);

  private final List<Captured> captured = new CopyOnWriteArrayList<>();
  private final AtomicInteger injected = new AtomicInteger();
  private volatile Supplier<Map<String, Object>> observer;
  private volatile Supplier<Map<String, Object>> beforeCommitObserver;
  private volatile String failOn;

  @EventListener
  @Order(Ordered.LOWEST_PRECEDENCE)
  public void on(Object event) {
    String name = event.getClass().getSimpleName();
    if (!NAMES.contains(name)) {
      return;
    }
    Supplier<Map<String, Object>> current = observer;
    Captured record = new Captured(
      name,
      Thread.currentThread().getName(),
      TransactionSynchronizationManager.isActualTransactionActive(),
      TransactionSynchronizationManager.getCurrentTransactionName(),
      current == null ? Map.of() : current.get(),
      new TxOutcome());
    captured.add(record);
    registerOutcome(record.outcome());
    if (name.equals(failOn)) {
      injected.incrementAndGet();
      throw new InjectedListenerFailure("injected listener failure: " + name);
    }
  }

  /**
   * 수신한 트랜잭션의 커밋 직전 관찰값과 완료 상태 기록
   */
  private void registerOutcome(TxOutcome outcome) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    outcome.registered = true;
    Supplier<Map<String, Object>> beforeCommit = beforeCommitObserver;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

      @Override
      public void beforeCommit(boolean readOnly) {
        outcome.beforeCommit = beforeCommit == null ? Map.of() : beforeCommit.get();
      }

      @Override
      public void afterCompletion(int status) {
        outcome.completionStatus = status;
      }
    });
  }

  /**
   * 이벤트 수신 시점(같은 트랜잭션 안)에 실행할 관찰 함수 등록
   */
  public void observeWith(Supplier<Map<String, Object>> observer) {
    this.observer = observer;
  }

  /**
   * 이벤트를 수신한 트랜잭션의 커밋 직전(beforeCommit)에 실행할 관찰 함수 등록
   */
  public void observeBeforeCommitWith(Supplier<Map<String, Object>> beforeCommitObserver) {
    this.beforeCommitObserver = beforeCommitObserver;
  }

  /**
   * 지정한 이벤트 수신 시 InjectedListenerFailure를 던진다
   */
  public void failOn(String eventName) {
    this.failOn = eventName;
  }

  public List<Captured> events(String eventName) {
    return captured.stream().filter(c -> c.name().equals(eventName)).toList();
  }

  public List<Captured> all() {
    return List.copyOf(captured);
  }

  public int injectedCount() {
    return injected.get();
  }

  public void reset() {
    captured.clear();
    injected.set(0);
    observer = null;
    beforeCommitObserver = null;
    failOn = null;
  }

  /**
   * 수신한 이벤트와 수신 시점의 실행 맥락
   *
   * @param threadName      수신 thread (동기 전달이면 요청 thread와 같다)
   * @param txActive        수신 시점에 실제 트랜잭션이 활성인지
   * @param transactionName 수신 시점의 트랜잭션 이름 (진단용, 단언하지 않는다)
   * @param observed        observer가 같은 트랜잭션 안에서 읽은 값
   * @param outcome         수신한 트랜잭션의 커밋 직전 관찰값과 완료 상태
   */
  public record Captured(String name, String threadName, boolean txActive, String transactionName,
                         Map<String, Object> observed, TxOutcome outcome) {
  }

  /**
   * 수신한 트랜잭션의 종료 기록 (synchronization 콜백이 채운다)
   */
  public static class TxOutcome {

    private volatile boolean registered;
    private volatile Map<String, Object> beforeCommit;
    private volatile Integer completionStatus;

    public boolean registered() {
      return registered;
    }

    // 커밋 단계에 도달하지 않았으면 null
    public Map<String, Object> beforeCommit() {
      return beforeCommit;
    }

    // TransactionSynchronization.STATUS_*; 완료 전이면 null
    public Integer completionStatus() {
      return completionStatus;
    }

    @Override
    public String toString() {
      return "TxOutcome{registered=" + registered + ", beforeCommit=" + beforeCommit
        + ", completionStatus=" + completionStatus + "}";
    }
  }

  /**
   * 의도적 리스너 장애 주입 전용 예외 (실제 장애와 혼동 방지)
   */
  public static class InjectedListenerFailure extends RuntimeException {
    public InjectedListenerFailure(String message) {
      super(message);
    }
  }
}
