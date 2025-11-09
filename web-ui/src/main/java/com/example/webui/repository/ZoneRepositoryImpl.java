package com.example.webui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ZoneRepositoryImpl implements ZoneRepository {

    private final JdbcTemplate jdbcTemplate;

    public ZoneRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ZoneView> findAll() {
        String sql = """
            SELECT id, code, created_at
            FROM zones
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ZoneView(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getTimestamp("created_at").toInstant()
        ));
    }
}


