package com.ticketsystem.it_service_backend.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/test-db")
public class TestDbController {
    private final JdbcTemplate jdbcTemplate;
    public TestDbController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @GetMapping
    public List<Map<String, Object>> getTickets() {
        return jdbcTemplate.queryForList("SELECT id, status, sla_elapsed_ms, sla_paused_at, sla_resumed_at, created_at FROM tickets ORDER BY id DESC LIMIT 5");
    }
}
