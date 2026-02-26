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

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    this.createdAt = LocalDateTime.now();
  }

  // “의도된 생성”만 열어두기
  public Member(String email, String name) {
    this.email = email;
    this.name = name;
  }

  // 변경이 필요하면 setter 대신 “의미 있는 메서드”로
  public void changeEmail(String email) {
    this.email = email;
  }

  public void changeName(String name) {
    this.name = name;
  }
}
