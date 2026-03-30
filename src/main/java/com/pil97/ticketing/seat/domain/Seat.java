package com.pil97.ticketing.seat.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "seat")
public class Seat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "seat_number", nullable = false, length = 20)
  private String seatNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SeatGrade grade;

  @Column(name = "row_label", nullable = false, length = 10)
  private String rowLabel;

  @Column(name = "seat_no", nullable = false)
  private int seatNo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
