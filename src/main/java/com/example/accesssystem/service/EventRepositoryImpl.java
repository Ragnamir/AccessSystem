package com.example.accesssystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of EventRepository using JdbcTemplate.
 * Inserts events into the events table.
 */
@Repository
public class EventRepositoryImpl implements EventRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    EventRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void recordEvent(
        String eventId,
        UUID checkpointId,
        UUID userId,
        UUID fromZoneId,
        UUID toZoneId,
        Instant eventTimestamp
    ) {
        String sql = """
            INSERT INTO events (
                event_id, 
                checkpoint_id, 
                user_id, 
                from_zone_id, 
                to_zone_id, 
                event_timestamp
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(
            sql,
            eventId,
            checkpointId,
            userId,
            fromZoneId,
            toZoneId,
            java.sql.Timestamp.from(eventTimestamp)
        );
    }
    
    @Override
    public Optional<UUID> findCheckpointIdByCode(String checkpointCode) {
        String sql = "SELECT id FROM checkpoints WHERE code = ?";
        try {
            UUID id = jdbcTemplate.queryForObject(sql, UUID.class, checkpointCode);
            return Optional.ofNullable(id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<UUID> findUserIdByCode(String userCode) {
        String sql = "SELECT id FROM users WHERE code = ?";
        try {
            UUID id = jdbcTemplate.queryForObject(sql, UUID.class, userCode);
            return Optional.ofNullable(id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<UUID> findZoneIdByCode(String zoneCode) {
        if (zoneCode == null) {
            return Optional.empty();
        }
        String sql = "SELECT id FROM zones WHERE code = ?";
        try {
            UUID id = jdbcTemplate.queryForObject(sql, UUID.class, zoneCode);
            return Optional.ofNullable(id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

