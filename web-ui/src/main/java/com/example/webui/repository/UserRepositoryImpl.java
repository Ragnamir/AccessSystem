package com.example.webui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class UserRepositoryImpl implements UserRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public UserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public List<UserView> findAllWithState(int offset, int limit) {
        String sql = """
            SELECT u.id, u.code, z.code as current_zone_code, us.updated_at
            FROM users u
            LEFT JOIN user_state us ON u.id = us.user_id
            LEFT JOIN zones z ON us.current_zone_id = z.id
            ORDER BY u.created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant updatedAt = rs.getTimestamp("updated_at") != null 
                ? rs.getTimestamp("updated_at").toInstant() 
                : null;
            return new UserView(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("current_zone_code"),
                updatedAt
            );
        }, limit, offset);
    }
    
    @Override
    public List<UserView> findAllWithState() {
        String sql = """
            SELECT u.id, u.code, z.code as current_zone_code, us.updated_at
            FROM users u
            LEFT JOIN user_state us ON u.id = us.user_id
            LEFT JOIN zones z ON us.current_zone_id = z.id
            ORDER BY u.created_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant updatedAt = rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toInstant()
                : null;
            return new UserView(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("current_zone_code"),
                updatedAt
            );
        });
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }
}

