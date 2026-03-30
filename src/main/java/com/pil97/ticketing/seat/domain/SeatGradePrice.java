package com.pil97.ticketing.seat.domain;


import com.pil97.ticketing.event.domain.Event;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "seat_grade_price")
public class SeatGradePrice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SeatGrade grade;

  @Column(nullable = false)
  private int price;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
