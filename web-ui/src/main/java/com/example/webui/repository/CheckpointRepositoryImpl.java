package com.example.webui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class CheckpointRepositoryImpl implements CheckpointRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public CheckpointRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public List<CheckpointView> findAll(int offset, int limit) {
        String sql = """
            SELECT c.id, c.code, zf.code as from_zone_code, zt.code as to_zone_code, c.created_at
            FROM checkpoints c
            LEFT JOIN zones zf ON c.from_zone_id = zf.id
            LEFT JOIN zones zt ON c.to_zone_id = zt.id
            ORDER BY c.created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CheckpointView(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("from_zone_code"),
            rs.getString("to_zone_code"),
            rs.getTimestamp("created_at").toInstant()
        ), limit, offset);
    }

    @Override
    public List<CheckpointView> findAll() {
        String sql = """
            SELECT c.id, c.code, zf.code as from_zone_code, zt.code as to_zone_code, c.created_at
            FROM checkpoints c
            LEFT JOIN zones zf ON c.from_zone_id = zf.id
            LEFT JOIN zones zt ON c.to_zone_id = zt.id
            ORDER BY c.created_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CheckpointView(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("from_zone_code"),
            rs.getString("to_zone_code"),
            rs.getTimestamp("created_at").toInstant()
        ));
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM checkpoints";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }
}

