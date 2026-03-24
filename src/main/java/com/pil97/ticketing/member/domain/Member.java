package com.pil97.ticketing.member.domain;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "members")
public class Member {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  @Column(nullable = false, length = 30)
  private String name;

  /**
   * ✅ 비밀번호 (BCrypt 암호화 저장)
   * - 원문 비밀번호는 저장하지 않음
   * - 로그인 시 BCryptPasswordEncoder로 비교
   */
  @Column(nullable = false, length = 100)
  private String password;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * 수정 시각
   * - 이메일/이름/비밀번호 변경 시 자동 갱신
   */
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    this.createdAt = LocalDateTime.now();
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  // “의도된 생성”만 열어두기
  public Member(String email, String name, String password) {
    this.email = email;
    this.name = name;
    this.password = password;
  }

  // 변경이 필요하면 setter 대신 “의미 있는 메서드”로
  public void changeEmail(String email) {
    this.email = email;
  }

  public void changeName(String name) {
    this.name = name;
  }
}
