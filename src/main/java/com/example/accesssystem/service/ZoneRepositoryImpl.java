package com.example.accesssystem.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of ZoneRepository using JdbcTemplate.
 */
@Repository
public class ZoneRepositoryImpl implements ZoneRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    ZoneRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public UUID create(String code) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO zones (id, code) VALUES (?, ?)";
        try {
            jdbcTemplate.update(sql, id, code);
            return id;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Zone with code '" + code + "' already exists", e);
        }
    }
    
    @Override
    public Optional<ZoneRecord> findById(UUID id) {
        String sql = "SELECT id, code, created_at FROM zones WHERE id = ?";
        try {
            ZoneRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new ZoneRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
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
    public Optional<ZoneRecord> findByCode(String code) {
        String sql = "SELECT id, code, created_at FROM zones WHERE code = ?";
        try {
            ZoneRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new ZoneRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
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
    public List<ZoneRecord> findAll(int offset, int limit) {
        String sql = "SELECT id, code, created_at FROM zones ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ZoneRecord(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getTimestamp("created_at").toInstant()
            ),
            limit,
            offset
        );
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM zones";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public boolean update(UUID id, String newCode) {
        String sql = "UPDATE zones SET code = ? WHERE id = ?";
        try {
            int rowsAffected = jdbcTemplate.update(sql, newCode, id);
            return rowsAffected > 0;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Zone with code '" + newCode + "' already exists", e);
        }
    }
    
    @Override
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM zones WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
}

