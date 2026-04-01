package com.pil97.ticketing.queue.api;

import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.common.exception.GlobalExceptionHandler;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.queue.api.dto.response.QueueEnterResponse;
import com.pil97.ticketing.queue.api.dto.response.QueueStatusResponse;
import com.pil97.ticketing.queue.application.QueueService;
import com.pil97.ticketing.queue.error.QueueErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
  value = QueueController.class,
  excludeAutoConfiguration = SecurityAutoConfiguration.class
)
class QueueControllerTest {

  private MockMvc mockMvc;

  @MockitoBean
  private QueueService queueService;

  @Autowired
  private QueueController queueController;

  @Autowired
  private GlobalExceptionHandler globalExceptionHandler;

  /**
   * standaloneSetup으로 MockMvc 직접 구성
   * AuthenticationPrincipalArgumentResolver를 수동 등록해야
   *
   * @AuthenticationPrincipal이 정상 동작한다.
   */
  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
      .standaloneSetup(queueController)
      .setControllerAdvice(globalExceptionHandler)
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
      .build();
  }

  /**
   * SecurityContextHolder에 인증 정보 직접 주입
   * standaloneSetup 방식에서는 이 방식이 정상 동작한다.
   */
  private void setAuthentication(Long memberId) {
    Member member = new Member("a@test.com", "testUser", "encoded");
    ReflectionTestUtils.setField(member, "id", memberId);
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(member, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  // ────────────────────────────────────────────────
  // POST /queue/enter
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("POST /queue/enter: 정상 등록 → 200 + 순번 반환")
  void enter_success() throws Exception {
    // given
    setAuthentication(42L);
    when(queueService.enter(anyLong(), anyLong()))
      .thenReturn(new QueueEnterResponse(5L, 150L));

    // when & then
    mockMvc.perform(post("/queue/enter")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"eventId": 1}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.rank").value(5))
      .andExpect(jsonPath("$.data.estimatedWaitSeconds").value(150));
  }

  @Test
  @DisplayName("POST /queue/enter: eventId 누락 → 400")
  void enter_missingEventId_returns400() throws Exception {
    // given
    setAuthentication(42L);

    // when & then
    mockMvc.perform(post("/queue/enter")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /queue/enter: 존재하지 않는 eventId → 404")
  void enter_eventNotFound_returns404() throws Exception {
    // given
    setAuthentication(42L);
    when(queueService.enter(anyLong(), anyLong()))
      .thenThrow(new BusinessException(QueueErrorCode.EVENT_NOT_FOUND));

    // when & then
    mockMvc.perform(post("/queue/enter")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"eventId": 999}
          """))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(QueueErrorCode.EVENT_NOT_FOUND.getCode()));
  }

  // ────────────────────────────────────────────────
  // GET /queue/status
  // ────────────────────────────────────────────────

  @Test
  @DisplayName("GET /queue/status: 대기 중인 유저 → 200 + 순번 반환")
  void status_waiting_returnsRank() throws Exception {
    // given
    setAuthentication(42L);
    when(queueService.getStatus(anyLong(), anyLong()))
      .thenReturn(QueueStatusResponse.ofWaiting(3L, 90L));

    // when & then
    mockMvc.perform(get("/queue/status")
        .param("eventId", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data.rank").value(3))
      .andExpect(jsonPath("$.data.estimatedWaitSeconds").value(90))
      .andExpect(jsonPath("$.data.admitted").value(false));
  }

  @Test
  @DisplayName("GET /queue/status: 입장 허용된 유저 → 200 + admitted=true 반환")
  void status_admitted_returnsAdmitted() throws Exception {
    // given
    setAuthentication(42L);
    when(queueService.getStatus(anyLong(), anyLong()))
      .thenReturn(QueueStatusResponse.ofAdmitted());

    // when & then
    mockMvc.perform(get("/queue/status")
        .param("eventId", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.admitted").value(true))
      .andExpect(jsonPath("$.data.rank").value(0));
  }

  @Test
  @DisplayName("GET /queue/status: 대기열에 없는 유저 → 404")
  void status_notInQueue_returns404() throws Exception {
    // given
    setAuthentication(42L);
    when(queueService.getStatus(anyLong(), anyLong()))
      .thenThrow(new BusinessException(QueueErrorCode.NOT_IN_QUEUE));

    // when & then
    mockMvc.perform(get("/queue/status")
        .param("eventId", "1"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(QueueErrorCode.NOT_IN_QUEUE.getCode()));
  }
}