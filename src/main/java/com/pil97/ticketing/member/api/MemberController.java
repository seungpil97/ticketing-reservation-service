package com.pil97.ticketing.member.api;

import com.pil97.ticketing.common.response.ApiResponse;
import com.pil97.ticketing.member.api.dto.request.MemberCreateRequest;
import com.pil97.ticketing.member.api.dto.response.MemberResponse;
import com.pil97.ticketing.member.application.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

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
   * ✅ GET /members/{id}
   * <p>
   * 이 API의 목적:
   * - 특정 id의 회원을 "단건 조회"한다.
   * <p>
   * 상태코드 정책:
   * - 조회 성공 시 200 OK
   * - 없는 회원이면 404 NOT_FOUND (MEMBER_NOT_FOUND)
   * <p>
   * 에러 흐름:
   * - 서비스에서 memberRepository.findById(id)를 호출
   * - 없으면 BusinessException(ErrorCode.MEMBER_NOT_FOUND) 발생
   * - GlobalExceptionHandler가 잡아서 표준 에러 응답(ApiResponse.error)으로 내려준다
   */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<MemberResponse>> getById(
    // ✅ @PathVariable: URL 경로의 {id} 값을 메서드 파라미터로 바인딩
    // 예) GET /members/10  -> id = 10
    @PathVariable Long id
  ) {
    // ✅ 서비스 호출: 조회 로직/예외 처리는 서비스가 담당 (컨트롤러는 얇게 유지)
    MemberResponse response = memberService.getById(id);

    // ✅ 200 OK + 표준 응답 포맷(ApiResponse)
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * ✅ GET /members
   * <p>
   * 이 API의 목적:
   * - 회원 목록을 "간단 조회"한다.
   * - 현재는 최신 20개(id desc)만 내려준다. (정책은 Service/Repository에서 제한)
   * <p>
   * 상태코드 정책:
   * - 조회 성공 시 200 OK
   * <p>
   * 응답 정책:
   * - 표준 응답 포맷(ApiResponse)로 감싸서 반환
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<MemberResponse>>> list() {

    // ✅ 서비스 호출: 목록 조회 로직은 서비스가 담당
    List<MemberResponse> responses = memberService.list();

    // ✅ 200 OK + 표준 응답
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

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
