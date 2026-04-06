package com.pil97.ticketing.queue.application;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.event.domain.repository.EventRepository;
import com.pil97.ticketing.queue.api.dto.response.QueueEnterResponse;
import com.pil97.ticketing.queue.api.dto.response.QueueStatusResponse;
import com.pil97.ticketing.queue.domain.repository.QueueRepository;
import com.pil97.ticketing.queue.error.QueueErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

  @Mock
  private QueueRepository queueRepository;

  @Mock
  private EventRepository eventRepository;

  @InjectMocks
  private QueueService queueService;

  // @Value로 주입되는 batchSize, fixedDelayMs를 테스트에서 직접 설정
  private void setSchedulerConfig(int batchSize, long fixedDelayMs) {
    ReflectionTestUtils.setField(queueService, "batchSize", batchSize);
    ReflectionTestUtils.setField(queueService, "fixedDelayMs", fixedDelayMs);
  }

  @Test
  @DisplayName("enter: 존재하지 않는 eventId면 BusinessException(EVENT_NOT_FOUND)을 던진다")
  void enter_eventNotFound_throwsBusinessException() {
    // given
    when(eventRepository.existsById(anyLong())).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> queueService.enter(999L, 1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getErrorCode()).isEqualTo(QueueErrorCode.EVENT_NOT_FOUND);
      });

    verify(eventRepository).existsById(999L);
    verifyNoInteractions(queueRepository);
  }

  @Test
  @DisplayName("enter: 신규 등록 시 순번(1-based)과 예상 대기 시간을 반환한다")
  void enter_newMember_returnsRankAndEstimatedWait() {
    // given
    setSchedulerConfig(5, 10000L);
    Long eventId = 1L;
    Long memberId = 42L;

    when(eventRepository.existsById(eventId)).thenReturn(true);
    // 입장 이력 없음 → 최초 등록
    when(queueRepository.hasAdmittedHistory(eventId, memberId)).thenReturn(false);
    when(queueRepository.addIfAbsent(eq(eventId), eq(memberId), anyDouble())).thenReturn(true);
    // 0-based rank 4 → 1-based rank 5
    when(queueRepository.getRank(eventId, memberId)).thenReturn(4L);
    when(queueRepository.nextScore(eventId)).thenReturn(1L);

    // when
    QueueEnterResponse response = queueService.enter(eventId, memberId);

    // then
    assertThat(response.rank()).isEqualTo(5L);
    assertThat(response.estimatedWaitSeconds()).isGreaterThanOrEqualTo(0L);

    verify(queueRepository).addIfAbsent(eq(eventId), eq(memberId), anyDouble());
    verify(queueRepository, never()).addOrReplace(anyLong(), anyLong(), anyDouble());
  }

  @Test
  @DisplayName("enter: 이미 등록된 유저는 ZADD NX로 기존 순번을 그대로 반환한다")
  void enter_duplicateMember_returnsExistingRank() {
    // given
    setSchedulerConfig(5, 10000L);
    Long eventId = 1L;
    Long memberId = 42L;

    when(eventRepository.existsById(eventId)).thenReturn(true);
    when(queueRepository.hasAdmittedHistory(eventId, memberId)).thenReturn(false);
    when(queueRepository.addIfAbsent(eq(eventId), eq(memberId), anyDouble())).thenReturn(false);
    when(queueRepository.getRank(eventId, memberId)).thenReturn(2L);
    when(queueRepository.nextScore(eventId)).thenReturn(1L);

    // when
    QueueEnterResponse response = queueService.enter(eventId, memberId);

    // then
    assertThat(response.rank()).isEqualTo(3L);
    verify(queueRepository, never()).addOrReplace(anyLong(), anyLong(), anyDouble());
  }

  @Test
  @DisplayName("enter: 재진입 시 ZREM 후 ZADD로 순번을 초기화하고 맨 뒤로 재등록한다")
  void enter_reEnter_resetsRankToBack() {
    // given
    setSchedulerConfig(5, 10000L);
    Long eventId = 1L;
    Long memberId = 42L;

    when(eventRepository.existsById(eventId)).thenReturn(true);
    // 입장 이력 있음 → 재진입
    when(queueRepository.hasAdmittedHistory(eventId, memberId)).thenReturn(true);
    // 재진입 후 맨 뒤 순번 반환
    when(queueRepository.getRank(eventId, memberId)).thenReturn(9L);
    when(queueRepository.nextScore(eventId)).thenReturn(1L);

    // when
    QueueEnterResponse response = queueService.enter(eventId, memberId);

    // then
    assertThat(response.rank()).isEqualTo(10L);
    verify(queueRepository).addOrReplace(eq(eventId), eq(memberId), anyDouble());
    verify(queueRepository, never()).addIfAbsent(anyLong(), anyLong(), anyDouble());
  }

  @Test
  @DisplayName("getStatus: 입장 토큰이 있으면 admitted=true를 반환한다")
  void getStatus_hasAdmissionToken_returnsAdmitted() {
    // given
    when(queueRepository.hasAdmissionToken(42L)).thenReturn(true);

    // when
    QueueStatusResponse response = queueService.getStatus(1L, 42L);

    // then
    assertThat(response.admitted()).isTrue();
    assertThat(response.rank()).isEqualTo(0L);
    assertThat(response.reEnterType()).isNull();

    verify(queueRepository, never()).getRank(anyLong(), anyLong());
  }

  @Test
  @DisplayName("getStatus: 대기열 미등록 + 입장 이력 없으면 reEnterType=NONE을 반환한다")
  void getStatus_notInQueue_noHistory_returnsNone() {
    // given
    when(queueRepository.hasAdmissionToken(42L)).thenReturn(false);
    when(queueRepository.getRank(1L, 42L)).thenReturn(null);
    when(queueRepository.hasAdmittedHistory(1L, 42L)).thenReturn(false);

    // when
    QueueStatusResponse response = queueService.getStatus(1L, 42L);

    // then
    assertThat(response.admitted()).isFalse();
    assertThat(response.reEnterType()).isEqualTo(QueueStatusResponse.ReEnterType.NONE);
  }

  @Test
  @DisplayName("getStatus: 대기열 미등록 + 입장 이력 있으면 reEnterType=EXPIRED를 반환한다")
  void getStatus_notInQueue_hasHistory_returnsExpired() {
    // given
    when(queueRepository.hasAdmissionToken(42L)).thenReturn(false);
    when(queueRepository.getRank(1L, 42L)).thenReturn(null);
    when(queueRepository.hasAdmittedHistory(1L, 42L)).thenReturn(true);

    // when
    QueueStatusResponse response = queueService.getStatus(1L, 42L);

    // then
    assertThat(response.admitted()).isFalse();
    assertThat(response.reEnterType()).isEqualTo(QueueStatusResponse.ReEnterType.EXPIRED);
  }

  @Test
  @DisplayName("getStatus: 대기 중인 유저는 순번과 예상 대기 시간을 반환한다")
  void getStatus_waiting_returnsRankAndEstimatedWait() {
    // given
    setSchedulerConfig(5, 10000L);
    when(queueRepository.hasAdmissionToken(42L)).thenReturn(false);
    // 0-based rank 2 → 1-based rank 3
    when(queueRepository.getRank(1L, 42L)).thenReturn(2L);

    // when
    QueueStatusResponse response = queueService.getStatus(1L, 42L);

    // then
    assertThat(response.admitted()).isFalse();
    assertThat(response.rank()).isEqualTo(3L);
    assertThat(response.estimatedWaitSeconds()).isGreaterThanOrEqualTo(0L);
    assertThat(response.reEnterType()).isNull();
  }
}