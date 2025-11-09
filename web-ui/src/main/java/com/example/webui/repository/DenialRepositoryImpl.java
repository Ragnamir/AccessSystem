package com.example.webui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class DenialRepositoryImpl implements DenialRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public DenialRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public List<DenialView> findAll(int offset, int limit) {
        String sql = """
            SELECT d.id, d.checkpoint_code, d.user_code, d.from_zone_code, 
                   d.to_zone_code, d.reason, d.created_at
            FROM denials d
            ORDER BY d.created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DenialView(
            rs.getObject("id", UUID.class),
            rs.getString("checkpoint_code"),
            rs.getString("user_code"),
            rs.getString("from_zone_code"),
            rs.getString("to_zone_code"),
            rs.getString("reason"),
            rs.getTimestamp("created_at").toInstant()
        ), limit, offset);
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM denials";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }
}

