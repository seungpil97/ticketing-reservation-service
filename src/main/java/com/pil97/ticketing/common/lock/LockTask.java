package com.pil97.ticketing.common.lock;

/**
 * ✅ 락 안에서 실행할 작업을 표현하는 함수형 인터페이스
 * - @FunctionalInterface: 람다로 전달 가능
 * - 제네릭 <T>: 어떤 타입이든 반환 가능
 * - 사용 예시:
 *   lockService.executeWithLock("key", 3, 5, () -> holdService.processHold(...))
 */
@FunctionalInterface
public interface LockTask<T> {
  T execute() throws InterruptedException;
}