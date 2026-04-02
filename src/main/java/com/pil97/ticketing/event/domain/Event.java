package com.pil97.ticketing.event.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "event")
public class Event {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 100)
  private String venue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EventStatus status;

  // 이벤트 종료 시각 - 스케줄러에서 대기열 자동 종료 판단 기준으로 사용
  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // end_time 기준으로 이벤트 종료 여부 판단
  public boolean isEnded() {
    return endTime != null && LocalDateTime.now().isAfter(endTime);
  }
}
