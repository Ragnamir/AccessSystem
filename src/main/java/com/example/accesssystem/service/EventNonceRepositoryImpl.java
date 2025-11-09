package com.example.accesssystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class EventNonceRepositoryImpl implements EventNonceRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    EventNonceRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public boolean exists(String eventId) {
        String sql = "SELECT COUNT(*) FROM event_nonces WHERE event_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, eventId);
        return count != null && count > 0;
    }
    
    @Override
    public void store(String eventId, String checkpointId, Instant eventTimestamp, Instant expiresAt) {
        String sql = "INSERT INTO event_nonces (event_id, checkpoint_id, event_timestamp, expires_at) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (event_id) DO NOTHING";
        jdbcTemplate.update(
            sql, 
            eventId, 
            checkpointId, 
            java.sql.Timestamp.from(eventTimestamp), 
            java.sql.Timestamp.from(expiresAt)
        );
    }
    
    @Override
    public int cleanupExpired() {
        String sql = "DELETE FROM event_nonces WHERE expires_at < NOW()";
        return jdbcTemplate.update(sql);
    }
}

