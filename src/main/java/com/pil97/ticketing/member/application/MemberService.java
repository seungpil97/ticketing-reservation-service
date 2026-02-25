package com.pil97.ticketing.member.application;

import com.pil97.ticketing.common.error.ErrorCode;
import com.pil97.ticketing.common.exception.BusinessException;
import com.pil97.ticketing.member.api.dto.request.MemberCreateRequest;
import com.pil97.ticketing.member.api.dto.response.MemberResponse;
import com.pil97.ticketing.member.domain.Member;
import com.pil97.ticketing.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ✅ Member 유스케이스(서비스)
 * - 컨트롤러는 얇게 유지하고(요청/응답)
 * - "생성 로직"은 서비스로 옮겨서 레이어 분리
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

  private final MemberRepository memberRepository;

  /**
   * ✅ 회원 생성
   */
  @Transactional
  public MemberResponse create(
    MemberCreateRequest request
  ) {

    Member member = new Member(request.getEmail(), request.getName());
    Member saved = memberRepository.save(member);

    return MemberResponse.from(saved);
  }

  /**
   * ✅ 회원 조회
   */
  public MemberResponse getById(Long id) {

    Member member = memberRepository.findById(id)
      .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    return MemberResponse.from(member);
  }

  /**
   * ✅ 회원 목록 조회 최신 20건
   */
  public List<MemberResponse> list() {
    return memberRepository.findTop20ByOrderByIdDesc()
      .stream()
      .map(MemberResponse::from)
      .toList();
  }
}
