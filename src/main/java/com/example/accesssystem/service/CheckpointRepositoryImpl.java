package com.example.accesssystem.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CheckpointRepository using JdbcTemplate.
 */
@Repository
public class CheckpointRepositoryImpl implements CheckpointRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    CheckpointRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public UUID create(String code, UUID fromZoneId, UUID toZoneId) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO checkpoints (id, code, from_zone_id, to_zone_id) VALUES (?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, id, code, fromZoneId, toZoneId);
            return id;
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                throw new IllegalArgumentException("Checkpoint with code '" + code + "' already exists", e);
            }
            throw new IllegalArgumentException("Invalid zone reference", e);
        }
    }
    
    @Override
    public Optional<CheckpointRecord> findById(UUID id) {
        String sql = "SELECT id, code, from_zone_id, to_zone_id, created_at FROM checkpoints WHERE id = ?";
        try {
            CheckpointRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new CheckpointRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getObject("from_zone_id", UUID.class),
                    rs.getObject("to_zone_id", UUID.class),
                    rs.getTimestamp("created_at").toInstant()
                ),
                id
            );
            return Optional.ofNullable(result);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<CheckpointRecord> findByCode(String code) {
        String sql = "SELECT id, code, from_zone_id, to_zone_id, created_at FROM checkpoints WHERE code = ?";
        try {
            CheckpointRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new CheckpointRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getObject("from_zone_id", UUID.class),
                    rs.getObject("to_zone_id", UUID.class),
                    rs.getTimestamp("created_at").toInstant()
                ),
                code
            );
            return Optional.ofNullable(result);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public List<CheckpointRecord> findAll(int offset, int limit) {
        String sql = "SELECT id, code, from_zone_id, to_zone_id, created_at FROM checkpoints ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new CheckpointRecord(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getObject("from_zone_id", UUID.class),
                rs.getObject("to_zone_id", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            ),
            limit,
            offset
        );
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM checkpoints";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public boolean update(UUID id, String newCode, UUID fromZoneId, UUID toZoneId) {
        String sql = "UPDATE checkpoints SET code = ?, from_zone_id = ?, to_zone_id = ? WHERE id = ?";
        try {
            int rowsAffected = jdbcTemplate.update(sql, newCode, fromZoneId, toZoneId, id);
            return rowsAffected > 0;
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                throw new IllegalArgumentException("Checkpoint with code '" + newCode + "' already exists", e);
            }
            throw new IllegalArgumentException("Invalid zone reference", e);
        }
    }
    
    @Override
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM checkpoints WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }

    @Override
    public boolean hasExit(UUID fromZoneId) {
        if (fromZoneId == null) {
            return false;
        }
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM checkpoints
                WHERE from_zone_id = ?
                  AND to_zone_id IS NULL
            )
            """;
        Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, fromZoneId);
        return Boolean.TRUE.equals(result);
    }
}

