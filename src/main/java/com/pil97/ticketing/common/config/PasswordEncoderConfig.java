package com.pil97.ticketing.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

  /**
   * ✅ BCryptPasswordEncoder 빈 등록
   * - 비밀번호 암호화/검증에 사용
   * - BCrypt: 단방향 해시 알고리즘으로 원문 복원 불가
   * - 같은 비밀번호라도 매번 다른 해시값 생성 (salt 자동 포함)
   * - SecurityConfig와 분리해서 순환 참조 방지
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}