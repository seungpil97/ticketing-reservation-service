package com.pil97.ticketing.common.exception;

import com.pil97.ticketing.common.persistence.ConcurrentUpdateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-062 (#104) 전역 예외 처리기의 COMMON-008 응답 매핑 (RED_REQUIRED)
 *
 * <p>증거 범위: GlobalExceptionHandler의 예외 → HTTP 응답 매핑만 검증한다.
 * 테스트 전용 controller가 예외를 직접 던지므로 실제 MariaDB 1020, JPA 변환, 트랜잭션 종료, Redis 정리의 증거가 아니다
 * (실제 경로는 PaymentConflictHttpIntegrationTest가 담당).
 */
class GlobalExceptionHandlerConflictTest {

  private MockMvc mockMvc;

  @RestController
  static class ThrowingController {

    @PostMapping("/t062/conflict")
    void conflict() {
      throw new ConcurrentUpdateConflictException("snapshot conflict",
        new SQLException("Record has changed since last read in table 'holds'", "HY000", 1020));
    }

    @PostMapping("/t062/lock")
    void lock() {
      throw new CannotAcquireLockException("deadlock", new SQLException("deadlock", "40001", 1213));
    }

    @PostMapping("/t062/integrity")
    void integrity() {
      throw new DataIntegrityViolationException("duplicate", new SQLException("dup", "23000", 1062));
    }

    @PostMapping("/t062/raw-1020")
    void raw1020() {
      // 변환기를 거치지 않은 1020 (현행 JPA fallback 형태)
      throw new JpaSystemException(new RuntimeException(
        new SQLException("Record has changed since last read in table 'holds'", "HY000", 1020)));
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
      .standaloneSetup(new ThrowingController())
      .setControllerAdvice(new GlobalExceptionHandler())
      .build();
  }

  @Test
  @DisplayName("ConcurrentUpdateConflictException → 409, error.code COMMON-008, 기존 ApiResponse 오류 형식")
  void concurrentUpdateConflict_mapsTo409Common008() throws Exception {
    mockMvc.perform(post("/t062/conflict"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.data").doesNotExist())
      .andExpect(jsonPath("$.error.code").value("COMMON-008"))
      .andExpect(jsonPath("$.error.message").value("Concurrent update conflict"))
      .andExpect(jsonPath("$.error.path").value("/t062/conflict"))
      .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("guard: 1020 이외 동시성 오류(CannotAcquireLockException)는 기존 처리(500 COMMON-007)를 유지한다")
  void otherConcurrencyFailure_keepsExistingHandling() throws Exception {
    mockMvc.perform(post("/t062/lock"))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.error.code").value("COMMON-007"));
  }

  @Test
  @DisplayName("guard: 무결성 오류(DataIntegrityViolationException)는 기존 처리(409 COMMON-006)를 유지한다")
  void integrityViolation_keepsExistingHandling() throws Exception {
    mockMvc.perform(post("/t062/integrity"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.error.code").value("COMMON-006"));
  }

  @Test
  @DisplayName("guard: 변환되지 않은 JpaSystemException은 원인이 1020이어도 처리기에서 따로 해석하지 않는다 (500 COMMON-007 유지)")
  void untranslatedJpaSystemException_keepsExistingHandling() throws Exception {
    mockMvc.perform(post("/t062/raw-1020"))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.error.code").value("COMMON-007"));
  }
}
