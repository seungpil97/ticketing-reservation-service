package com.pil97.ticketing.member.application;

import com.pil97.ticketing.member.api.dto.request.MemberCreateRequest;
import com.pil97.ticketing.member.api.dto.response.MemberResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ✅ Member 유스케이스(서비스)
 * - 컨트롤러는 얇게 유지하고(요청/응답)
 * - "생성 로직"은 서비스로 옮겨서 레이어 분리
 */
@Service
public class MemberService {

  // ✅ DB 대신 메모리 저장소
  private final Map<Long, MemberResponse> store = new ConcurrentHashMap<>();
  private final AtomicLong seq = new AtomicLong(1);

  /**
   * ✅ 회원 생성 (메모리 저장)
   */
  public MemberResponse create(
    MemberCreateRequest request
  ) {

    Long id = seq.getAndIncrement();

    // record는 new로 생성
    MemberResponse response =
      new MemberResponse(id, request.getEmail(), request.getName());

    store.put(id, response);
    return response;
  }
}
