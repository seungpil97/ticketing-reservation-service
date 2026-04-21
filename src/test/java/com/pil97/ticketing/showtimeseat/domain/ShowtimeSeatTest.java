package com.pil97.ticketing.showtimeseat.domain;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.showtimeseat.error.ShowtimeSeatErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowtimeSeatTest {

  private ShowtimeSeat showtimeSeat;

  @BeforeEach
  void setUp() {
    showtimeSeat = new ShowtimeSeat();
  }

  @Test
  @DisplayName("markAvailable: HELD 상태에서 AVAILABLE 전환 성공")
  void markAvailable_held_success() {
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.HELD);
    showtimeSeat.markAvailable();
    assertThat(showtimeSeat.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE);
  }

  @Test
  @DisplayName("markAvailable: RESERVED 상태에서 AVAILABLE 전환 성공")
  void markAvailable_reserved_success() {
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.RESERVED);
    showtimeSeat.markAvailable();
    assertThat(showtimeSeat.getStatus()).isEqualTo(ShowtimeSeatStatus.AVAILABLE);
  }

  @Test
  @DisplayName("markAvailable: AVAILABLE 상태에서 호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void markAvailable_available_throwsException() {
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.AVAILABLE);

    assertThatThrownBy(() -> showtimeSeat.markAvailable())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ShowtimeSeatErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  @Test
  @DisplayName("markReserved: HELD 상태에서 RESERVED 전환 성공")
  void markReserved_held_success() {
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.HELD);
    showtimeSeat.markReserved();
    assertThat(showtimeSeat.getStatus()).isEqualTo(ShowtimeSeatStatus.RESERVED);
  }

  @Test
  @DisplayName("markReserved: AVAILABLE 상태에서 호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void markReserved_available_throwsException() {
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.AVAILABLE);

    assertThatThrownBy(() -> showtimeSeat.markReserved())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ShowtimeSeatErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  @Test
  @DisplayName("markReserved: RESERVED 상태에서 재호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void markReserved_reserved_throwsException() {
    ReflectionTestUtils.setField(showtimeSeat, "status", ShowtimeSeatStatus.RESERVED);

    assertThatThrownBy(() -> showtimeSeat.markReserved())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(ShowtimeSeatErrorCode.INVALID_STATUS_TRANSITION);
      });
  }
}