package com.pil97.ticketing.common.response;

/**
 * ✅ PageInfo: 페이징 응답에서 공통으로 사용하는 "페이지 메타 정보"
 * <p>
 * 규약:
 * - page는 0부터 시작(0-based)
 * - size는 한 페이지의 최대 항목 수
 * - totalElements는 전체 데이터 개수
 * - totalPages는 전체 페이지 수
 */
public record PageInfo(
  int page,              // 0-based (0부터 시작)
  int size,              // 페이지 크기
  long totalElements,    // 전체 데이터 수
  int totalPages         // 전체 페이지 수
) {
}
