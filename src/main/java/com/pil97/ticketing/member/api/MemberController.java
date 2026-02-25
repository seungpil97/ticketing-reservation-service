package com.pil97.ticketing.member.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.member.api.dto.request.MemberCreateRequest;
import com.pil97.ticketing.member.api.dto.response.MemberResponse;
import com.pil97.ticketing.member.application.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * ✅ API 컨트롤러
 * - 요청을 받고(@RequestBody)
 * - 검증하고(@Valid)
 * - 서비스 호출하고
 * - 표준 응답(ApiResponse)으로 감싸서 반환
 */
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;

  /**
   * ✅ POST /members
   * <p>
   * 이 API의 목적:
   * - "회원 생성" 요청을 받아 새 회원을 만든다.
   * <p>
   * 상태코드 정책:
   * - 생성 성공 시 201 Created를 반환한다.
   * - 그리고 Location 헤더로 "새로 생성된 리소스의 URI"를 알려준다.
   * 예) Location: http://localhost:8080/members/1
   * <p>
   * 에러 정책:
   * - 요청 DTO에 @Valid가 붙어 있으므로 email/name 검증 실패 시
   * MethodArgumentNotValidException이 발생한다.
   * - 이 예외는 GlobalExceptionHandler가 잡아서 표준 에러 응답(ApiResponse.error)으로 내려준다.
   */
  @PostMapping
  public ResponseEntity<ApiResponse<MemberResponse>> create(
    // ✅ @RequestBody: HTTP body(JSON)를 MemberCreateRequest로 변환해서 받는다.
    // ✅ @Valid: 변환된 객체에 대해 Bean Validation(@NotBlank 등)을 수행한다.
    @Valid @RequestBody MemberCreateRequest request
  ) {
    // ✅ 서비스(유스케이스) 호출: 실제 생성 로직은 서비스에 위임해서 컨트롤러를 얇게 유지
    MemberResponse response = memberService.create(request);

    /**
     * ✅ Location 헤더에 넣을 URI 생성
     *
     * fromCurrentRequest():
     * - 현재 요청 URI를 기반으로 시작한다.
     * - 예: http://localhost:8080/members
     *
     * path("/{id}"):
     * - 뒤에 "/{id}"를 붙여서 새 리소스의 경로를 만든다.
     * - 결과: http://localhost:8080/members/{id}
     *
     * buildAndExpand(response.id()):
     * - {id} 자리에 실제 생성된 id를 치환한다.
     * - record는 getter 대신 "컴포넌트명()" 형태 접근자 사용
     *   예: response.id()
     *
     * toUri():
     * - 최종적으로 URI 객체로 변환
     */
    URI location = ServletUriComponentsBuilder
      .fromCurrentRequest()
      .path("/{id}")
      .buildAndExpand(response.id()) // ✅ record accessor: id 값 가져오기
      .toUri();

    /**
     * ✅ 201 Created + Location 헤더 + 표준 응답 바디
     *
     * ResponseEntity.created(location):
     * - HTTP 상태를 201 Created로 설정하고
     * - Location 헤더를 자동으로 포함한다.
     *
     * body(ApiResponse.success(response)):
     * - 응답 바디는 우리 표준 포맷(ApiResponse)으로 감싼다.
     */
    return ResponseEntity.created(location)
      .body(ApiResponse.success(response));
  }
}
