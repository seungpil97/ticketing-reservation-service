package com.pil97.ticketing.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return (result != null && result == 1) ? "ok" : "fail";
    }
}