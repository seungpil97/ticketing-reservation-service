package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.support.consistency.SqlObserver;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 테스트 전용 Hibernate Interceptor - 지정한 Outbox 행의 처리 완료 표시 flush에 의도적 장애를 주입한다 (TASK-033)
 * - test property(spring.jpa.properties.hibernate.session_factory.interceptor)로만 등록한다
 * - 엔티티는 단순 클래스명(OutboxEvent)으로 식별한다: production 엔티티가 없는 RED 단계에서도 컴파일된다
 * - 주입 대상은 status가 PROCESSED로 바뀌는 flush뿐이다. 같은 행의 다른 dirty flush(실패 기록 등)는 통과시킨다 (TASK-033-1)
 * - 주입 직전, 같은 thread·connection에서 해당 처리의 Outbox 행 잠금 이후 알림 INSERT가 실행됐는지 기록한다
 *   (SqlObserverConfig가 DataSource를 감싸고 있어야 한다)
 * - 주입 장애는 전용 예외 InjectedOutboxFailure로 구분한다
 * - 시각 진행 등 테스트 동작을 끼워 넣는 hook: 알림 INSERT 직전(onNotificationInsert), 처리 완료 표시 flush 직전(onProcessedFlush).
 *   hook은 장애 주입 여부와 관계없이 먼저 실행한다 (TASK-033-1)
 */
public class OutboxFaultInterceptor implements Interceptor {

  private static final String OUTBOX_ENTITY = "OutboxEvent";
  private static final String NOTIFICATION_ENTITY = "Notification";
  private static final String STATUS_PROPERTY = "status";
  private static final String PROCESSED = "PROCESSED";
  private static final String OUTBOX_EVENT_ID_PROPERTY = "outboxEventId";
  private static final Pattern OUTBOX_LOCK = Pattern.compile("from\\s+outbox_event\\b.*\\bfor\\s+update",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern NOTIFICATION_INSERT = Pattern.compile("^\\s*insert\\s+into\\s+notification\\b",
    Pattern.CASE_INSENSITIVE);
  // 남은 주입 횟수, 음수면 해제 전까지 계속 주입
  private static final Map<Long, AtomicInteger> FAILING = new ConcurrentHashMap<>();
  private static final Map<Long, AtomicInteger> INJECTED = new ConcurrentHashMap<>();
  private static final List<Injection> INJECTIONS = new CopyOnWriteArrayList<>();
  private static final Map<Long, Runnable> NOTIFICATION_INSERT_HOOKS = new ConcurrentHashMap<>();
  private static final Map<Long, Runnable> PROCESSED_FLUSH_HOOKS = new ConcurrentHashMap<>();
  private static final Map<Long, String> MESSAGES = new ConcurrentHashMap<>();

  /**
   * 주입 시점 기록
   *
   * @param outboxId                    장애를 주입한 Outbox ID
   * @param notificationInsertedBefore 같은 처리(Outbox 행 잠금 이후, 같은 connection)에서 알림 INSERT가 이미 실행됐는지
   */
  public record Injection(Long outboxId, boolean notificationInsertedBefore) {
  }

  public static void failOnce(Long outboxId) {
    FAILING.put(outboxId, new AtomicInteger(1));
  }

  public static void failAlways(Long outboxId) {
    FAILING.put(outboxId, new AtomicInteger(-1));
  }

  /**
   * 주입 예외 메시지를 지정해 1회 주입 (오류 원문 비노출 사례, TASK-033-1)
   */
  public static void failOnce(Long outboxId, String message) {
    FAILING.put(outboxId, new AtomicInteger(1));
    MESSAGES.put(outboxId, message);
  }

  public static void disarm() {
    FAILING.clear();
  }

  public static int injectedCount(Long outboxId) {
    AtomicInteger count = INJECTED.get(outboxId);
    return count == null ? 0 : count.get();
  }

  public static List<Injection> injections(Long outboxId) {
    return INJECTIONS.stream().filter(i -> i.outboxId().equals(outboxId)).toList();
  }

  /**
   * 지정한 Outbox 처리의 알림 INSERT 직전에 실행할 동작 (해당 처리의 Outbox 행 잠금 이후)
   */
  public static void onNotificationInsert(Long outboxId, Runnable action) {
    NOTIFICATION_INSERT_HOOKS.put(outboxId, action);
  }

  /**
   * 지정한 Outbox 행의 처리 완료 표시 flush 직전에 실행할 동작 (장애 주입 판단보다 먼저)
   */
  public static void onProcessedFlush(Long outboxId, Runnable action) {
    PROCESSED_FLUSH_HOOKS.put(outboxId, action);
  }

  public static void reset() {
    FAILING.clear();
    INJECTED.clear();
    INJECTIONS.clear();
    NOTIFICATION_INSERT_HOOKS.clear();
    PROCESSED_FLUSH_HOOKS.clear();
    MESSAGES.clear();
  }

  @Override
  public boolean onPersist(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
    if (NOTIFICATION_ENTITY.equals(entity.getClass().getSimpleName())
        && propertyValue(state, propertyNames, OUTBOX_EVENT_ID_PROPERTY) instanceof Long outboxId) {
      Runnable hook = NOTIFICATION_INSERT_HOOKS.get(outboxId);
      if (hook != null) {
        hook.run();
      }
    }
    return false;
  }

  @Override
  public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
                              String[] propertyNames, Type[] types) {
    if (!OUTBOX_ENTITY.equals(entity.getClass().getSimpleName()) || !(id instanceof Long outboxId)
        || !becomesProcessed(currentState, previousState, propertyNames)) {
      return false;
    }
    Runnable hook = PROCESSED_FLUSH_HOOKS.get(outboxId);
    if (hook != null) {
      hook.run();
    }
    AtomicInteger remaining = FAILING.get(outboxId);
    if (remaining == null || remaining.get() == 0) {
      return false;
    }
    if (remaining.get() > 0) {
      remaining.decrementAndGet();
    }
    INJECTIONS.add(new Injection(outboxId, notificationInsertedAfterLock(outboxId)));
    INJECTED.computeIfAbsent(outboxId, k -> new AtomicInteger()).incrementAndGet();
    throw new InjectedOutboxFailure(
      MESSAGES.getOrDefault(outboxId, "injected outbox flush failure: outboxId=" + outboxId));
  }

  /**
   * 이번 flush에서 status가 PROCESSED가 아닌 값에서 PROCESSED로 바뀌는지
   */
  private static boolean becomesProcessed(Object[] currentState, Object[] previousState, String[] propertyNames) {
    Object current = propertyValue(currentState, propertyNames, STATUS_PROPERTY);
    Object previous = propertyValue(previousState, propertyNames, STATUS_PROPERTY);
    return current != null && PROCESSED.equals(current.toString())
      && (previous == null || !PROCESSED.equals(previous.toString()));
  }

  private static Object propertyValue(Object[] state, String[] propertyNames, String property) {
    if (state == null) {
      return null;
    }
    for (int i = 0; i < propertyNames.length; i++) {
      if (property.equals(propertyNames[i])) {
        return state[i];
      }
    }
    return null;
  }

  /**
   * 현재 thread의 마지막 Outbox 행 잠금 이후 같은 connection에서 알림 INSERT가 실행됐는지
   */
  private static boolean notificationInsertedAfterLock(Long outboxId) {
    List<SqlObserver.Event> executions = SqlObserver.executions(Thread.currentThread().getName());
    int lockIndex = -1;
    for (int i = 0; i < executions.size(); i++) {
      if (OUTBOX_LOCK.matcher(executions.get(i).sql()).find()) {
        lockIndex = i;
      }
    }
    if (lockIndex < 0) {
      return false;
    }
    long connectionId = executions.get(lockIndex).connectionId();
    return executions.subList(lockIndex + 1, executions.size()).stream()
      .anyMatch(e -> e.connectionId() == connectionId && NOTIFICATION_INSERT.matcher(e.sql()).find());
  }

  /**
   * 의도적 Outbox 처리 장애 주입 전용 예외 (실제 장애와 혼동 방지)
   */
  public static class InjectedOutboxFailure extends RuntimeException {
    public InjectedOutboxFailure(String message) {
      super(message);
    }
  }
}
