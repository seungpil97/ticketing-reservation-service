package com.pil97.ticketing.support.consistency;

import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트 전용 Hibernate Interceptor - 지정한 회차별 좌석의 flush에 의도적 장애를 주입한다
 * - test property(spring.jpa.properties.hibernate.session_factory.interceptor)로만 등록한다
 * - 주입 장애는 전용 예외 InjectedFlushFailure로 구분한다 (실제 인프라 장애와 혼동 방지)
 */
public class FaultInjectingInterceptor implements Interceptor {

  private static final AtomicReference<Long> FAILING_SHOWTIME_SEAT_ID = new AtomicReference<>();
  private static final AtomicInteger INJECTED = new AtomicInteger();

  public static void failFlushOfShowtimeSeat(Long showtimeSeatId) {
    FAILING_SHOWTIME_SEAT_ID.set(showtimeSeatId);
  }

  public static int injectedCount() {
    return INJECTED.get();
  }

  public static void reset() {
    FAILING_SHOWTIME_SEAT_ID.set(null);
    INJECTED.set(0);
  }

  @Override
  public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
                              String[] propertyNames, Type[] types) {
    Long failing = FAILING_SHOWTIME_SEAT_ID.get();
    if (failing != null && entity instanceof ShowtimeSeat && Objects.equals(id, failing)) {
      INJECTED.incrementAndGet();
      throw new InjectedFlushFailure("injected flush failure: showtimeSeatId=" + failing);
    }
    return false;
  }

  /**
   * 의도적 장애 주입 전용 예외
   */
  public static class InjectedFlushFailure extends RuntimeException {
    public InjectedFlushFailure(String message) {
      super(message);
    }
  }
}
