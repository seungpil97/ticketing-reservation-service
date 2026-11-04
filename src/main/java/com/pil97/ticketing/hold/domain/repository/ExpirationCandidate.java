package com.pil97.ticketing.hold.domain.repository;

/**
 * 만료 후보 projection (TASK-062)
 * - 후보 조회(트랜잭션 밖)에서 좌석 ID를 함께 가져와, 항목 트랜잭션의 첫 대상 테이블 접근을 좌석 잠금으로 둘 수 있게 한다
 * - HOLD의 좌석 FK는 생성 후 바뀌지 않는다
 */
public record ExpirationCandidate(Long holdId, Long showtimeSeatId) {
}
