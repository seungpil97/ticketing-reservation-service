package com.pil97.ticketing.hold.domain;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.hold.domain.Hold;
import com.pil97.ticketing.hold.domain.HoldStatus;
import com.pil97.ticketing.hold.error.HoldErrorCode;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.showtimeseat.domain.ShowtimeSeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HoldTest {

  private Hold hold;

  @BeforeEach
  void setUp() {
    // 실제 Hold 객체 생성 - ACTIVE 상태로 시작
    hold = Hold.create(
      mock(ShowtimeSeat.class),
      mock(Member.class),
      LocalDateTime.now().plusMinutes(10)
    );
  }

  // ===================== expire() =====================

  @Test
  @DisplayName("expire: ACTIVE 상태에서 EXPIRED 전환 성공")
  void expire_active_success() {
    // when
    hold.expire();

    // then
    assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);
  }

  @Test
  @DisplayName("expire: 이미 EXPIRED 상태에서 재호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void expire_alreadyExpired_throwsException() {
    // given
    hold.expire();

    // when & then
    assertThatThrownBy(() -> hold.expire())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(HoldErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  @Test
  @DisplayName("expire: CONFIRMED 상태에서 호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void expire_confirmed_throwsException() {
    // given
    hold.confirm();

    // when & then
    assertThatThrownBy(() -> hold.expire())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(HoldErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  // ===================== confirm() =====================

  @Test
  @DisplayName("confirm: ACTIVE 상태에서 CONFIRMED 전환 성공")
  void confirm_active_success() {
    // when
    hold.confirm();

    // then
    assertThat(hold.getStatus()).isEqualTo(HoldStatus.CONFIRMED);
  }

  @Test
  @DisplayName("confirm: 이미 CONFIRMED 상태에서 재호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void confirm_alreadyConfirmed_throwsException() {
    // given
    hold.confirm();

    // when & then
    assertThatThrownBy(() -> hold.confirm())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(HoldErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  @Test
  @DisplayName("confirm: EXPIRED 상태에서 호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void confirm_expired_throwsException() {
    // given
    hold.expire();

    // when & then
    assertThatThrownBy(() -> hold.confirm())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(HoldErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  // ===================== refund() =====================

  @Test
  @DisplayName("refund: CONFIRMED 상태에서 REFUNDED 전환 성공")
  void refund_confirmed_success() {
    // given
    hold.confirm();

    // when
    hold.refund();

    // then
    assertThat(hold.getStatus()).isEqualTo(HoldStatus.REFUNDED);
  }

  @Test
  @DisplayName("refund: ACTIVE 상태에서 호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void refund_active_throwsException() {
    // when & then
    assertThatThrownBy(() -> hold.refund())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(HoldErrorCode.INVALID_STATUS_TRANSITION);
      });
  }

  @Test
  @DisplayName("refund: EXPIRED 상태에서 호출 시 INVALID_STATUS_TRANSITION 예외를 던진다")
  void refund_expired_throwsException() {
    // given
    hold.expire();

    // when & then
    assertThatThrownBy(() -> hold.refund())
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(HoldErrorCode.INVALID_STATUS_TRANSITION);
      });
  }
}