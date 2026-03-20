package com.pil97.ticketing.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

  private final RedissonClient redissonClient;

  /**
   * ✅ 분산락 획득 후 작업 실행
   *
   * @param lockKey   락 키 (예: "hold:seat:1:1")
   *                  - 같은 키를 가진 요청만 직렬화됨
   *                  - 다른 키는 서로 영향을 주지 않음
   * @param waitTime  락 획득 대기 시간 (초)
   *                  - 이 시간 안에 락을 못 얻으면 실패 처리
   * @param leaseTime 락 유지 시간 (초)
   *                  - 락 획득 후 이 시간이 지나면 자동 해제
   *                  - 서버 장애 시 락이 영구적으로 걸리는 것을 방지
   * @param task      락 획득 후 실행할 작업 (람다로 전달)
   * @param <T>       반환 타입
   * @return 작업 결과
   */
  public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, LockTask<T> task) {

    // 1) Redis에 lockKey로 락 객체 생성
    //    아직 락을 획득한 게 아니고 락 객체만 가져온 상태 (자물쇠를 가리키는 포인터)
    RLock lock = redissonClient.getLock(lockKey);

    try {
      // 2) 락 획득 시도
      //    waitTime 동안 대기 후 획득 성공 → true, 실패 → false
      boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

      if (!acquired) {
        log.warn("Failed to acquire lock. key={}", lockKey);
        throw new LockAcquisitionFailedException(lockKey);
      }

      // 3) 락 획득 성공 → 비즈니스 로직 실행
      return task.execute();

    } catch (InterruptedException e) {
      // 4) 대기 중 인터럽트 발생 시 스레드 상태 복원 후 예외 처리
      Thread.currentThread().interrupt();
      throw new LockAcquisitionFailedException(lockKey);

    } finally {
      // 5) 작업 성공/실패 여부와 관계없이 반드시 락 해제
      //    isHeldByCurrentThread() 확인: 현재 스레드가 락을 가지고 있을 때만 해제
      //    락 획득 실패 상태에서 unlock() 호출하면 예외가 발생하므로 반드시 확인
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }
}