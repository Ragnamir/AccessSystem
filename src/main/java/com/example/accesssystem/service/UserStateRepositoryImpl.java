package com.example.accesssystem.service;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Implementation of UserStateRepository using JdbcTemplate.
 * Implements optimistic locking using version field.
 */
@Repository
public class UserStateRepositoryImpl implements UserStateRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    UserStateRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Optional<UserStateRecord> getCurrentZone(String userCode) {
        String sql = """
            SELECT z.code, us.version
            FROM user_state us
            LEFT JOIN zones z ON us.current_zone_id = z.id
            INNER JOIN users u ON us.user_id = u.id
            WHERE u.code = ?
            """;
        
        try {
            UserStateRecord result = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    String zoneCode = rs.getString("code");
                    long version = rs.getLong("version");
                    return new UserStateRecord(zoneCode, version);
                },
                userCode
            );
            return Optional.ofNullable(result);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public boolean updateZone(String userCode, String zoneCode, long expectedVersion) {
        if (zoneCode == null) {
            // Update to OUT zone (NULL)
            String sql = """
                UPDATE user_state us
                SET current_zone_id = NULL,
                    version = version + 1,
                    updated_at = NOW()
                FROM users u
                WHERE us.user_id = u.id
                  AND u.code = ?
                  AND us.version = ?
                """;
            
            int rowsUpdated = jdbcTemplate.update(sql, userCode, expectedVersion);
            if (rowsUpdated == 0) {
                // Check if version mismatch or user doesn't exist
                Optional<UserStateRecord> current = getCurrentZone(userCode);
                if (current.isPresent() && current.get().version() != expectedVersion) {
                    throw new OptimisticLockingFailureException(
                        "Version mismatch: expected " + expectedVersion + 
                        ", but current is " + current.get().version()
                    );
                }
                return false;
            }
            return true;
        } else {
            // Update to specific zone
            String sql = """
                UPDATE user_state us
                SET current_zone_id = z.id,
                    version = version + 1,
                    updated_at = NOW()
                FROM users u
                INNER JOIN zones z ON z.code = ?
                WHERE us.user_id = u.id
                  AND u.code = ?
                  AND us.version = ?
                """;
            
            int rowsUpdated = jdbcTemplate.update(sql, zoneCode, userCode, expectedVersion);
            if (rowsUpdated == 0) {
                // Check if version mismatch or user doesn't exist
                Optional<UserStateRecord> current = getCurrentZone(userCode);
                if (current.isPresent() && current.get().version() != expectedVersion) {
                    throw new OptimisticLockingFailureException(
                        "Version mismatch: expected " + expectedVersion + 
                        ", but current is " + current.get().version()
                    );
                }
                return false;
            }
            return true;
        }
    }
    
    @Override
    public boolean initializeState(String userCode, String zoneCode) {
        if (zoneCode == null) {
            // Initialize to OUT zone (NULL)
            String sql = """
                INSERT INTO user_state (user_id, current_zone_id, version)
                SELECT u.id, NULL, 0
                FROM users u
                WHERE u.code = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM user_state us WHERE us.user_id = u.id
                  )
                """;
            
            try {
                int rowsInserted = jdbcTemplate.update(sql, userCode);
                return rowsInserted > 0;
            } catch (DataAccessException e) {
                return false;
            }
        } else {
            // Initialize to specific zone
            String sql = """
                INSERT INTO user_state (user_id, current_zone_id, version)
                SELECT u.id, z.id, 0
                FROM users u
                CROSS JOIN zones z
                WHERE u.code = ?
                  AND z.code = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM user_state us WHERE us.user_id = u.id
                  )
                """;
            
            try {
                int rowsInserted = jdbcTemplate.update(sql, userCode, zoneCode);
                return rowsInserted > 0;
            } catch (DataAccessException e) {
                return false;
            }
        }
    }
}

