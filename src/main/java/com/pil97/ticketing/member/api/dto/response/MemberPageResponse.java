package com.pil97.ticketing.member.api.dto.response;

import com.pil97.ticketing.common.api.dto.PageInfo;
import com.pil97.ticketing.member.domain.Member;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * ✅ MemberPageResponse: "회원 목록 조회(페이징)" 전용 응답 DTO
 * <p>
 * 이 DTO의 목적:
 * - GET /members 같은 "목록 API"에서 페이징 결과를 클라이언트에게 내려준다.
 * - items(실제 데이터) + page(페이지 메타 정보)로 구성된다.
 * <p>
 * 응답 JSON 예시(개략):
 * {
 * "success": true,
 * "data": {
 * "items": [
 * { "id": 3, "email": "c@test.com", "name": "C" },
 * { "id": 2, "email": "b@test.com", "name": "B" }
 * ],
 * "page": {
 * "page": 0,
 * "size": 2,
 * "totalElements": 10,
 * "totalPages": 5
 * }
 * }
 * }
 * <p>
 * 포인트:
 * - PageInfo는 공통 DTO로 분리해서(여러 목록 API에서 재사용)
 * members 뿐 아니라 posts, reservations 등에서도 동일한 page 스펙을 유지할 수 있다.
 */
public record MemberPageResponse(
  List<MemberResponse> items, // ✅ 현재 페이지의 회원 목록(실제 데이터)
  PageInfo page               // ✅ 페이지 메타 정보(page/size/totalElements/totalPages)
) {
  /**
   * ✅ Page<Member> -> MemberPageResponse 변환 팩토리 메서드
   * <p>
   * 왜 from(Page<Member>)인가?
   * - Repository가 페이징 조회를 하면 보통 Page<Member>를 반환한다.
   * - 컨트롤러/서비스에서는 Page를 그대로 내리기보다,
   * "API 스펙에 맞는 응답 DTO"로 변환해서 내려주는 게 명확하다.
   * <p>
   * 변환 흐름:
   * 1) memberPage.getContent() : 현재 페이지의 엔티티 리스트를 가져온다.
   * 2) MemberResponse::from     : 엔티티를 응답 DTO로 매핑한다.
   * 3) PageInfo 생성            : Page가 제공하는 메타 정보(number/size/totalElements/totalPages)를 채운다.
   * 4) MemberPageResponse(items, pageInfo) 반환
   */
  public static MemberPageResponse from(Page<Member> memberPage) {

    // ✅ 1) 현재 페이지의 엔티티 목록을 꺼내서 -> 응답 DTO(MemberResponse)로 변환
    List<MemberResponse> items = memberPage.getContent()
      .stream()
      .map(MemberResponse::from) // Member -> MemberResponse
      .toList();

    // ✅ 2) 페이지 메타 정보 구성 (PageInfo는 공통 스펙)
    // - getNumber(): 현재 페이지 번호(0부터 시작)
    // - getSize(): 페이지 크기(size)
    // - getTotalElements(): 전체 데이터 개수
    // - getTotalPages(): 전체 페이지 수
    PageInfo pageInfo = new PageInfo(
      memberPage.getNumber(),
      memberPage.getSize(),
      memberPage.getTotalElements(),
      memberPage.getTotalPages()
    );

    // ✅ 3) items + pageInfo를 묶어서 최종 페이지 응답 반환
    return new MemberPageResponse(items, pageInfo);
  }
}
