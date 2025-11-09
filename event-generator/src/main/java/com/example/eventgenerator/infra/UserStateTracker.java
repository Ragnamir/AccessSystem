package com.example.eventgenerator.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks user state by reading from database and updating after successful transitions.
 * Maintains in-memory cache for performance, but always reads from DB to ensure consistency.
 */
public class UserStateTracker {
    private static final Logger logger = LoggerFactory.getLogger(UserStateTracker.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> stateCache = new ConcurrentHashMap<>();
    
    public UserStateTracker(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
    
    /**
     * Gets current zone for a user from database.
     * Returns null if user is in OUT zone or state not initialized.
     */
    public String getCurrentZone(String userCode) {
        try {
            String sql = """
                SELECT z.code
                FROM user_state us
                INNER JOIN users u ON us.user_id = u.id
                LEFT JOIN zones z ON us.current_zone_id = z.id
                WHERE u.code = ?
                """;
            
            String zoneCode = jdbcTemplate.queryForObject(sql, String.class, userCode);
            // NULL from DB means OUT zone
            String currentZone = (zoneCode == null) ? "OUT" : zoneCode;
            stateCache.put(userCode, currentZone);
            return currentZone;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // User state not initialized - user is in OUT zone
            stateCache.put(userCode, "OUT");
            return "OUT";
        } catch (Exception e) {
            logger.warn("Failed to get current zone for user {}: {}", userCode, e.getMessage());
            // Return cached value or default to OUT
            return stateCache.getOrDefault(userCode, "OUT");
        }
    }
    
    /**
     * Updates user state after successful transition.
     * This should be called when system returns 202 (ACCEPTED).
     */
    public void updateState(String userCode, String toZone) {
        String normalizedToZone = (toZone == null || toZone.equals("OUT")) ? null : toZone;
        stateCache.put(userCode, normalizedToZone == null ? "OUT" : normalizedToZone);
        logger.debug("Updated state cache: user={}, zone={}", userCode, normalizedToZone == null ? "OUT" : normalizedToZone);
    }
    
    /**
     * Refreshes state from database (useful after external state changes).
     */
    public void refreshState(String userCode) {
        stateCache.remove(userCode);
        getCurrentZone(userCode);
    }
    
    /**
     * Gets available transitions for a user based on their current state and access rules.
     * Returns map of checkpoint -> (fromZone, toZone) transitions.
     */
    public Map<String, Transition> getAvailableTransitions(String userCode) {
        String currentZone = getCurrentZone(userCode);
        String normalizedCurrentZone = (currentZone == null || currentZone.isBlank()) ? "OUT" : currentZone;
        Map<String, Transition> transitions = new HashMap<>();
        
        try {
            if ("OUT".equals(normalizedCurrentZone)) {
                collectTransitionsFromOutside(userCode, transitions);
            } else {
                collectTransitionsFromZone(userCode, normalizedCurrentZone, transitions);
            }
        } catch (Exception e) {
            logger.warn("Failed to get available transitions for user {} from zone {}: {}", 
                userCode, normalizedCurrentZone, e.getMessage());
        }
        
        return transitions;
    }
    
    private void collectTransitionsFromOutside(String userCode, Map<String, Transition> transitions) {
        String sql = """
            SELECT 
                c.code AS checkpoint_code,
                to_z.code AS to_zone_code
            FROM checkpoints c
            LEFT JOIN zones to_z ON c.to_zone_id = to_z.id
            WHERE c.from_zone_id IS NULL
              AND (
                    c.to_zone_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM access_rules ar
                        INNER JOIN users u ON ar.user_id = u.id
                        WHERE u.code = ?
                          AND ar.to_zone_id = c.to_zone_id
                    )
                )
            """;
        
        jdbcTemplate.query(sql, new Object[]{userCode}, rs -> {
            String checkpointCode = rs.getString("checkpoint_code");
            if (checkpointCode == null) {
                return;
            }
            String toZoneCode = rs.getString("to_zone_code");
            String normalizedToZone = (toZoneCode == null) ? "OUT" : toZoneCode;
            transitions.put(checkpointCode, new Transition("OUT", normalizedToZone));
        });
    }
    
    private void collectTransitionsFromZone(String userCode, String currentZone, Map<String, Transition> transitions) {
        String sql = """
            SELECT 
                c.code AS checkpoint_code,
                from_z.code AS from_zone_code,
                to_z.code AS to_zone_code
            FROM checkpoints c
            INNER JOIN zones from_z ON c.from_zone_id = from_z.id
            LEFT JOIN zones to_z ON c.to_zone_id = to_z.id
            WHERE from_z.code = ?
              AND (
                    c.to_zone_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM access_rules ar
                        INNER JOIN users u ON ar.user_id = u.id
                        WHERE u.code = ?
                          AND ar.to_zone_id = c.to_zone_id
                    )
                )
            """;
        
        jdbcTemplate.query(sql, new Object[]{currentZone, userCode}, rs -> {
            String checkpointCode = rs.getString("checkpoint_code");
            if (checkpointCode == null) {
                return;
            }
            String fromZoneCode = rs.getString("from_zone_code");
            String toZoneCode = rs.getString("to_zone_code");
            
            String normalizedFromZone = (fromZoneCode == null) ? "OUT" : fromZoneCode;
            String normalizedToZone = (toZoneCode == null) ? "OUT" : toZoneCode;
            
            transitions.put(checkpointCode, new Transition(normalizedFromZone, normalizedToZone));
        });
    }
    
    public record Transition(String fromZone, String toZone) {}
}

