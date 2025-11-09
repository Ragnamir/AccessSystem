package com.example.accesssystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class IssuerKeyRepositoryImpl implements IssuerKeyRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    IssuerKeyRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Optional<String> findPublicKeyByIssuerCode(String issuerCode) {
        String sql = "SELECT public_key_pem FROM issuer_keys WHERE issuer_code = ?";
        try {
            String key = jdbcTemplate.queryForObject(sql, String.class, issuerCode);
            return Optional.ofNullable(key);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<String> findKeyTypeByIssuerCode(String issuerCode) {
        String sql = "SELECT key_type FROM issuer_keys WHERE issuer_code = ?";
        try {
            String keyType = jdbcTemplate.queryForObject(sql, String.class, issuerCode);
            return Optional.ofNullable(keyType);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<String> findAlgorithmByIssuerCode(String issuerCode) {
        String sql = "SELECT algorithm FROM issuer_keys WHERE issuer_code = ?";
        try {
            String algorithm = jdbcTemplate.queryForObject(sql, String.class, issuerCode);
            return Optional.ofNullable(algorithm);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

