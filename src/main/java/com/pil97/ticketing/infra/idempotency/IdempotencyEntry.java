package com.pil97.ticketing.infra.idempotency;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis에 저장되는 멱등성 envelope 객체
 * - fingerprint: 최초 요청 본문의 SHA-256 hash
 * - responseBody: 직렬화된 응답 JSON 문자열
 * fingerprint + responseBody를 하나의 JSON으로 직렬화하여 단일 key에 저장한다
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyEntry {

  private String fingerprint;
  private String responseBody;
}