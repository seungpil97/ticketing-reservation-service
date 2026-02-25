package com.pil97.ticketing.member.api.dto.response;

import com.pil97.ticketing.member.domain.Member;

/**
 * ✅ 회원 생성/조회 성공 시 클라이언트에게 내려줄 "응답 DTO"
 * <p>
 * record를 쓰는 이유(응답 DTO에 특히 잘 맞음):
 * 1) 불변(immutable): 한 번 만들어지면 값이 바뀌지 않아서 응답 데이터가 안전함
 * 2) 보일러플레이트 제거: 생성자/getter/equals/hashCode/toString을 자동 생성
 * 3) 의도가 명확: "이 클래스는 데이터 묶음이다"가 한눈에 보임
 * <p>
 * JSON으로 나가면 대략 이렇게 내려감:
 * {
 * "id": 1,
 * "email": "a@test.com",
 * "name": "sp"
 * }
 * <p>
 * 사용 예:
 * MemberResponse res = new MemberResponse(id, request.getEmail(), request.getName());
 * return ApiResponse.success(res);
 */
public record MemberResponse(
  Long id,      // ✅ 서버에서 생성/부여한 회원 ID (Day2는 메모리 시퀀스로 만들어도 OK)
  String email, // ✅ 회원 이메일
  String name   // ✅ 회원 이름
) {
  public static MemberResponse from(Member saved) {

    return new MemberResponse(saved.getId(), saved.getEmail(), saved.getName());
  }
}
