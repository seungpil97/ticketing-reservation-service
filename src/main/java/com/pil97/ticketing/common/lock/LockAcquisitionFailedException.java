package com.pil97.ticketing.common.lock;

/**
 * ✅ 분산락 획득 실패 시 던지는 커스텀 예외
 * - 락 획득 대기 시간 초과 또는 인터럽트 발생 시 사용
 * - 이후 GlobalExceptionHandler에서 잡아서 클라이언트에 적절한 응답 가능
 */
public class LockAcquisitionFailedException extends RuntimeException {
  public LockAcquisitionFailedException(String lockKey) {
    super("Failed to acquire distributed lock. key=" + lockKey);
  }
}