package com.example.accesssystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CheckpointKeyRepositoryImpl implements CheckpointKeyRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    CheckpointKeyRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Optional<String> findPublicKeyByCheckpointCode(String checkpointCode) {
        String sql = "SELECT public_key_pem FROM checkpoint_keys WHERE checkpoint_code = ?";
        try {
            String key = jdbcTemplate.queryForObject(sql, String.class, checkpointCode);
            return Optional.ofNullable(key);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<String> findKeyTypeByCheckpointCode(String checkpointCode) {
        String sql = "SELECT key_type FROM checkpoint_keys WHERE checkpoint_code = ?";
        try {
            String keyType = jdbcTemplate.queryForObject(sql, String.class, checkpointCode);
            return Optional.ofNullable(keyType);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

