package com.example.accesssystem.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of UserRepository using JdbcTemplate.
 */
@Repository
public class UserRepositoryImpl implements UserRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    UserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public UUID create(String code) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO users (id, code) VALUES (?, ?)";
        try {
            jdbcTemplate.update(sql, id, code);
            return id;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("User with code '" + code + "' already exists", e);
        }
    }
    
    @Override
    public Optional<UserRecord> findById(UUID id) {
        String sql = "SELECT id, code, created_at FROM users WHERE id = ?";
        try {
            UserRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new UserRecord(
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
    public Optional<UserRecord> findByCode(String code) {
        String sql = "SELECT id, code, created_at FROM users WHERE code = ?";
        try {
            UserRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new UserRecord(
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
    public List<UserRecord> findAll(int offset, int limit) {
        String sql = "SELECT id, code, created_at FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new UserRecord(
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
        String sql = "SELECT COUNT(*) FROM users";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
    
    @Override
    public boolean update(UUID id, String newCode) {
        String sql = "UPDATE users SET code = ? WHERE id = ?";
        try {
            int rowsAffected = jdbcTemplate.update(sql, newCode, id);
            return rowsAffected > 0;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("User with code '" + newCode + "' already exists", e);
        }
    }
    
    @Override
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM users WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
}

