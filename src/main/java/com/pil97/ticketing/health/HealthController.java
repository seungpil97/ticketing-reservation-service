package com.pil97.ticketing.health;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "99. Health", description = "헬스체크 API - 서버 / DB 상태 확인")
@RestController
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  public HealthController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // GET /health -> "ok"
  @GetMapping("/health")
  public String health() {
    return "ok";
  }

  // GET /health/db -> SELECT 1 성공하면 "ok"
  @GetMapping("/health/db")
  public String healthDb() {
    try {
      Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class);
      return (count != null) ? "ok" : "fail";
    } catch (Exception e) {
      return "fail";
    }
  }
}