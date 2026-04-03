package com.pil97.ticketing.queue.domain.repository;

import java.util.Set;

/**
 * 대기열 Redis 연산 추상화 인터페이스
 * <p>
 * 대기열은 JPA가 아닌 Redis Sorted Set 기반이므로 JpaRepository를 상속하지 않는다.
 * Service는 이 인터페이스에만 의존하며, 실제 Redis 구현체는 infra 패키지에 위치한다.
 */
public interface QueueRepository {

  /**
   * 대기열 등록
   * ZADD NX 옵션으로 이미 등록된 유저는 score 갱신 없이 무시한다.
   *
   * @param eventId  이벤트 ID
   * @param memberId 회원 ID
   * @param score    진입 timestamp (System.currentTimeMillis())
   * @return 실제로 추가된 경우 true, 이미 존재하면 false
   */
  boolean addIfAbsent(Long eventId, Long memberId, double score);

  /**
   * 대기열 강제 등록
   * ZREM 후 ZADD로 기존 순번을 초기화하고 맨 뒤로 재등록한다.
   * 재진입 시 사용한다.
   *
   * @param eventId  이벤트 ID
   * @param memberId 회원 ID
   * @param score    진입 timestamp (System.currentTimeMillis())
   */
  void addOrReplace(Long eventId, Long memberId, double score);

  /**
   * 대기열 순번 조회 (0-based)
   * 순번이 없으면 null 반환
   *
   * @param eventId  이벤트 ID
   * @param memberId 회원 ID
   * @return 0-based 순번, 대기열에 없으면 null
   */
  Long getRank(Long eventId, Long memberId);

  /**
   * 상위 N명 memberId 조회 (score 오름차순)
   *
   * @param eventId 이벤트 ID
   * @param count   조회할 인원 수
   * @return memberId 문자열 Set
   */
  Set<String> getTopMembers(Long eventId, long count);

  /**
   * 대기열에서 특정 유저 제거
   *
   * @param eventId  이벤트 ID
   * @param memberId 회원 ID
   */
  void remove(Long eventId, Long memberId);

  /**
   * 입장 토큰 저장
   * TTL 30분 적용
   *
   * @param memberId 회원 ID
   * @param token    입장 토큰 값
   */
  void saveAdmissionToken(Long memberId, String token);

  /**
   * 입장 토큰 존재 여부 확인
   *
   * @param memberId 회원 ID
   * @return 토큰이 유효하면 true, 없거나 만료되면 false
   */
  boolean hasAdmissionToken(Long memberId);

  /**
   * 활성 대기열 이벤트 등록
   * SADD queue:active:events {eventId}
   * 대기열 등록(POST /queue/enter) 시 호출한다.
   *
   * @param eventId 이벤트 ID
   */
  void addActiveEvent(Long eventId);

  /**
   * 활성 대기열 이벤트 제거
   * SREM queue:active:events {eventId}
   * 이벤트 종료 시 스케줄러에서 호출한다.
   *
   * @param eventId 이벤트 ID
   */
  void removeActiveEvent(Long eventId);

  /**
   * 활성 대기열 이벤트 ID 목록 조회
   * SMEMBERS queue:active:events
   * 스케줄러에서 처리 대상 이벤트 목록을 가져올 때 사용한다.
   *
   * @return 활성 이벤트 ID 문자열 Set
   */
  Set<String> getActiveEventIds();

  /**
   * 대기열 key 삭제
   * DEL queue:event:{eventId}
   * 이벤트 종료 시 스케줄러에서 호출한다.
   *
   * @param eventId 이벤트 ID
   */
  void deleteQueue(Long eventId);

  /**
   * 입장 허용 이력 저장
   * SADD queue:admitted:members:{eventId} {memberId}
   * admitTopMembers() 에서 입장 토큰 발급 시 함께 호출한다.
   *
   * @param eventId  이벤트 ID
   * @param memberId 회원 ID
   */
  void saveAdmittedHistory(Long eventId, Long memberId);

  /**
   * 입장 허용 이력 존재 여부 확인
   * SISMEMBER queue:admitted:members:{eventId} {memberId}
   * getStatus()에서 최초 미진입 vs 토큰 만료 구분에 사용한다.
   *
   * @param eventId  이벤트 ID
   * @param memberId 회원 ID
   * @return 입장 허용 이력이 있으면 true
   */
  boolean hasAdmittedHistory(Long eventId, Long memberId);

  /**
   * 입장 허용 이력 Set 전체 조회
   * SMEMBERS queue:admitted:members:{eventId}
   * 이벤트 종료 시 cleanUpEndedQueue()에서 이력 key 일괄 삭제에 사용한다.
   *
   * @param eventId 이벤트 ID
   * @return 입장 허용된 memberId 문자열 Set
   */
  Set<String> getAdmittedMembers(Long eventId);

  /**
   * 입장 허용 이력 key 삭제
   * DEL queue:admitted:members:{eventId}
   * 이벤트 종료 시 cleanUpEndedQueue()에서 호출한다.
   *
   * @param eventId 이벤트 ID
   */
  void deleteAdmittedHistory(Long eventId);
}