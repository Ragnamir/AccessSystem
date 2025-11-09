package com.example.webui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class EventRepositoryImpl implements EventRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public EventRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public List<EventView> findRecent(int offset, int limit) {
        String sql = """
            SELECT e.id, u.code as user_code, c.code as checkpoint_code, 
                   zf.code as from_zone_code, zt.code as to_zone_code, e.event_timestamp
            FROM events e
            JOIN users u ON e.user_id = u.id
            JOIN checkpoints c ON e.checkpoint_id = c.id
            LEFT JOIN zones zf ON e.from_zone_id = zf.id
            LEFT JOIN zones zt ON e.to_zone_id = zt.id
            ORDER BY e.event_timestamp DESC
            LIMIT ? OFFSET ?
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EventView(
            rs.getObject("id", UUID.class),
            rs.getString("user_code"),
            rs.getString("checkpoint_code"),
            rs.getString("from_zone_code"),
            rs.getString("to_zone_code"),
            rs.getTimestamp("event_timestamp").toInstant()
        ), limit, offset);
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM events";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }
}

