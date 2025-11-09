package com.example.accesssystem.service.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("keys")
public class KeysHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public KeysHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Integer issuerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM issuer_keys", Integer.class);
            Integer checkpointCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM checkpoint_keys", Integer.class);

            boolean ok = issuerCount != null && issuerCount > 0 && checkpointCount != null && checkpointCount > 0;

            Health.Builder builder = ok ? Health.up() : Health.down();
            return builder
                .withDetail("issuer_keys.count", issuerCount)
                .withDetail("checkpoint_keys.count", checkpointCount)
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}


