package com.pil97.ticketing.infra.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 요청 본문의 SHA-256 fingerprint 생성 유틸리티
 * - 멱등성 검증 시 동일 key + 다른 본문 요청을 구분하기 위해 사용
 * - 입력값은 요청 본문을 JSON 직렬화한 문자열
 */
public class IdempotencyFingerprintUtil {

  private IdempotencyFingerprintUtil() {
    // 유틸리티 클래스 - 인스턴스 생성 금지
  }

  /**
   * 입력 문자열의 SHA-256 hash를 hex 문자열로 반환
   *
   * @param input 요청 본문 JSON 문자열
   * @return SHA-256 hex 문자열
   */
  public static String hash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256은 JVM 표준 보장 알고리즘 - 발생하지 않음
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
    }
  }
}