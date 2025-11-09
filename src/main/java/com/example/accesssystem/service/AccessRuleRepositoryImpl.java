package com.example.accesssystem.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AccessRuleRepository using JdbcTemplate.
 * Performs JOIN with users and zones tables to match codes.
 */
@Repository
public class AccessRuleRepositoryImpl implements AccessRuleRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    AccessRuleRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public boolean hasAccess(String userCode, String toZoneCode) {
        if (toZoneCode == null) {
            return true;
        }

        String sql = """
            SELECT COUNT(*) > 0
            FROM access_rules ar
            INNER JOIN users u ON ar.user_id = u.id
            INNER JOIN zones to_zone ON ar.to_zone_id = to_zone.id
            WHERE u.code = ?
              AND to_zone.code = ?
            """;

        try {
            Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, userCode, toZoneCode);
            return Boolean.TRUE.equals(result);
        } catch (org.springframework.dao.DataAccessException e) {
            return false;
        }
    }
    
    @Override
    public UUID create(UUID userId, UUID toZoneId) {
        Objects.requireNonNull(toZoneId, "Destination zone ID must be provided");
        String sql = "INSERT INTO access_rules (user_id, from_zone_id, to_zone_id) VALUES (?, NULL, ?) RETURNING id";
        try {
            UUID id = jdbcTemplate.queryForObject(
                sql,
                UUID.class,
                userId,
                toZoneId
            );
            return id != null ? id : UUID.randomUUID();
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                throw new IllegalArgumentException("Access rule already exists", e);
            }
            throw new IllegalArgumentException("Invalid user or zone reference", e);
        }
    }
    
    @Override
    public Optional<AccessRuleRecord> findById(UUID id) {
        String sql = "SELECT id, user_id, to_zone_id, created_at FROM access_rules WHERE id = ?";
        try {
            AccessRuleRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new AccessRuleRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
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
    public List<AccessRuleRecord> findByUserId(UUID userId, int offset, int limit) {
        String sql = """
            SELECT id, user_id, to_zone_id, created_at 
            FROM access_rules 
            WHERE user_id = ? 
            ORDER BY created_at DESC 
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new AccessRuleRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("to_zone_id", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            ),
            userId,
            limit,
            offset
        );
    }
    
    @Override
    public List<AccessRuleRecord> findAll(int offset, int limit) {
        String sql = """
            SELECT id, user_id, to_zone_id, created_at 
            FROM access_rules 
            ORDER BY created_at DESC 
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new AccessRuleRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("to_zone_id", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            ),
            limit,
            offset
        );
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM access_rules";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public long countByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) FROM access_rules WHERE user_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count != null ? count : 0L;
    }
    
    @Override
    public boolean update(UUID id, UUID newToZoneId) {
        Objects.requireNonNull(newToZoneId, "Destination zone ID must be provided");
        String sql = "UPDATE access_rules SET from_zone_id = NULL, to_zone_id = ? WHERE id = ?";
        try {
            int rowsAffected = jdbcTemplate.update(sql, newToZoneId, id);
            return rowsAffected > 0;
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("unique")) {
                throw new IllegalArgumentException("Access rule already exists", e);
            }
            throw new IllegalArgumentException("Invalid zone reference", e);
        }
    }
    
    @Override
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM access_rules WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
}

