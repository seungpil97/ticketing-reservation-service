package com.pil97.ticketing.queue.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.queue.api.dto.request.EnterQueueRequest;
import com.pil97.ticketing.queue.api.dto.response.QueueEnterResponse;
import com.pil97.ticketing.queue.api.dto.response.QueueStatusResponse;
import com.pil97.ticketing.queue.application.QueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "7. Queue", description = "대기열 API - 대기열 등록 / 대기 상태 조회")
@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

  private final QueueService queueService;

  /**
   * POST /queue/enter
   * <p>
   * 이 API의 목적:
   * - 특정 이벤트의 대기열에 등록하고 현재 순번과 예상 대기 시간을 반환한다.
   * <p>
   * 상태코드 정책:
   * - 등록 성공 시 200 OK (이미 등록된 유저도 기존 순번 반환)
   * <p>
   * 인증:
   * - JWT 필수, Security Filter에서 인증 처리 후 @AuthenticationPrincipal로 추출
   */
  @PostMapping("/enter")
  public ResponseEntity<ApiResponse<QueueEnterResponse>> enter(
    @AuthenticationPrincipal Member member,
    @Valid @RequestBody EnterQueueRequest request
  ) {
    QueueEnterResponse response = queueService.enter(request.getEventId(), member.getId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * GET /queue/status?eventId={eventId}
   * <p>
   * 이 API의 목적:
   * - 현재 대기 순번 또는 입장 가능 여부를 반환한다.
   * - 입장 토큰이 존재하면 admitted=true 반환
   * - 토큰 만료 + 대기열 미등록이면 reEnterRequired=true 반환
   * <p>
   * 상태코드 정책:
   * - 조회 성공 시 200 OK
   * <p>
   * 인증:
   * - JWT 필수, Security Filter에서 인증 처리 후 @AuthenticationPrincipal로 추출
   */
  @GetMapping("/status")
  public ResponseEntity<ApiResponse<QueueStatusResponse>> status(
    @AuthenticationPrincipal Member member,
    @RequestParam Long eventId
  ) {
    QueueStatusResponse response = queueService.getStatus(eventId, member.getId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}