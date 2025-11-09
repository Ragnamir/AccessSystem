package com.example.eventgenerator.infra;

import com.example.eventgenerator.util.PemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.UUID;

public class SeedService {
    private static final Logger logger = LoggerFactory.getLogger(SeedService.class);
    
    private final JdbcTemplate jdbcTemplate;

    public SeedService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public SeededData seedIfNeeded() {
        Integer zones = jdbcTemplate.queryForObject("select count(*) from zones", Integer.class);
        if (zones != null && zones > 0) {
            // Ensure all necessary reference data exists for generator operation
            UUID zoneAId = jdbcTemplate.queryForObject("select id from zones where code = ?", UUID.class, "zone-a");
            UUID zoneBId = jdbcTemplate.queryForObject("select id from zones where code = ?", UUID.class, "zone-b");
            UUID zoneCId = jdbcTemplate.queryForObject("select id from zones where code = ?", UUID.class, "zone-c");
            
            ensureCheckpoint("cp-out-a", null, zoneAId);
            ensureCheckpoint("cp-a-b", zoneAId, zoneBId);
            ensureCheckpoint("cp-b-c", zoneBId, zoneCId);
            ensureCheckpoint("cp-c-out", zoneCId, null);

            // Ensure users exist with required access rules
            ensureUserStateAndAccess("user-1", null, zoneAId, zoneBId, zoneCId);
            ensureUserStateAndAccess("user-2", null, zoneAId, zoneBId);
            ensureUserStateAndAccess("user-3", null, zoneAId);
            ensureUserStateAndAccess("user-4", null, zoneAId, zoneBId, zoneCId);
            ensureUserStateAndAccess("user-5", null, zoneAId, zoneBId);

            // Проверяем наличие issuer_code, если нет - создаем новый
            String issuerCode;
            try {
                issuerCode = jdbcTemplate.queryForObject("select issuer_code from issuer_keys limit 1", String.class);
            } catch (Exception e) {
                // Если таблица пуста или нет данных, создаем новый issuer
                issuerCode = "gen-issuer-1";
                logger.info("No issuer found in DB, will create new one: {}", issuerCode);
            }
            
            // Для генератора событий всегда нужны приватные ключи для подписи
            // Генерируем новые ключи и обновляем/создаем публичные ключи в БД для всех checkpoint'ов
            logger.info("Existing data found. Generating new keys for event generation");
            KeyPair checkpointKey = generateRsa();
            String cpPem = PemUtil.toPem(checkpointKey.getPublic(), "PUBLIC KEY");
            
            String[] allCheckpoints = {"cp-out-a", "cp-a-b", "cp-b-c", "cp-c-out"};
            for (String checkpoint : allCheckpoints) {
                int updated = jdbcTemplate.update(
                    "insert into checkpoint_keys (id, checkpoint_code, public_key_pem, key_type, updated_at) " +
                    "values (?, ?, ?, ?, NOW()) " +
                    "on conflict (checkpoint_code) do update set public_key_pem = excluded.public_key_pem, " +
                    "key_type = excluded.key_type, updated_at = NOW()",
                    UUID.randomUUID(), checkpoint, cpPem, "RSA"
                );
                logger.info("Upserted checkpoint key in DB: checkpoint={}, rowsAffected={}", checkpoint, updated);
            }
            
            KeyPair issuerKey = generateRsa();
            String issuerPem = PemUtil.toPem(issuerKey.getPublic(), "PUBLIC KEY");
            int updatedIssuer = jdbcTemplate.update(
                "insert into issuer_keys (id, issuer_code, public_key_pem, key_type, algorithm, updated_at) " +
                "values (?, ?, ?, ?, ?, NOW()) " +
                "on conflict (issuer_code) do update set public_key_pem = excluded.public_key_pem, " +
                "key_type = excluded.key_type, algorithm = excluded.algorithm, updated_at = NOW()",
                UUID.randomUUID(), issuerCode, issuerPem, "RSA", "RS256"
            );
            logger.info("Upserted issuer key in DB: issuer={}, rowsAffected={}", issuerCode, updatedIssuer);
            
            return new SeededData("user-1", "cp-out-a", issuerCode, checkpointKey, issuerKey);
        }

        // Create 3 zones: zone-a, zone-b, zone-c
        UUID zoneAId = UUID.randomUUID();
        UUID zoneBId = UUID.randomUUID();
        UUID zoneCId = UUID.randomUUID();
        
        jdbcTemplate.update("insert into zones (id, code) values (?, ?)", zoneAId, "zone-a");
        jdbcTemplate.update("insert into zones (id, code) values (?, ?)", zoneBId, "zone-b");
        jdbcTemplate.update("insert into zones (id, code) values (?, ?)", zoneCId, "zone-c");
        logger.info("Created zones: zone-a, zone-b, zone-c");
        
        // Create users, access rules, and initial states
        ensureUserStateAndAccess("user-1", null, zoneAId, zoneBId, zoneCId);
        ensureUserStateAndAccess("user-2", null, zoneAId, zoneBId);
        ensureUserStateAndAccess("user-3", null, zoneAId);
        ensureUserStateAndAccess("user-4", null, zoneAId, zoneBId, zoneCId);
        ensureUserStateAndAccess("user-5", null, zoneAId, zoneBId);
        
        // Create checkpoints between zones
        UUID checkpointOutAId = UUID.randomUUID();
        UUID checkpointABId = UUID.randomUUID();
        UUID checkpointBCId = UUID.randomUUID();
        UUID checkpointCOutId = UUID.randomUUID();
        
        jdbcTemplate.update(
            "insert into checkpoints (id, code, from_zone_id, to_zone_id) values (?, ?, ?, ?)",
            checkpointOutAId, "cp-out-a", null, zoneAId
        );
        jdbcTemplate.update(
            "insert into checkpoints (id, code, from_zone_id, to_zone_id) values (?, ?, ?, ?)",
            checkpointABId, "cp-a-b", zoneAId, zoneBId
        );
        jdbcTemplate.update(
            "insert into checkpoints (id, code, from_zone_id, to_zone_id) values (?, ?, ?, ?)",
            checkpointBCId, "cp-b-c", zoneBId, zoneCId
        );
        jdbcTemplate.update(
            "insert into checkpoints (id, code, from_zone_id, to_zone_id) values (?, ?, ?, ?)",
            checkpointCOutId, "cp-c-out", zoneCId, null
        );
        logger.info("Created checkpoints: cp-out-a (OUT -> zone-a), cp-a-b (zone-a -> zone-b), cp-b-c (zone-b -> zone-c), cp-c-out (zone-c -> OUT)");
        
        logger.info("Ensured users, access rules, and initial states: user-1..user-5");

        // Keys: checkpoint and issuer
        logger.info("Seeding new database. Generating keys for checkpoints and issuer");
        
        // Generate one key pair for all checkpoints (for simplicity in testing)
        // In production, each checkpoint should have its own key pair
        KeyPair checkpointKey = generateRsa();
        String cpPem = PemUtil.toPem(checkpointKey.getPublic(), "PUBLIC KEY");
        
        // Insert/update keys for all checkpoints with the same public key
        String[] checkpointCodes = {"cp-out-a", "cp-a-b", "cp-b-c", "cp-c-out"};
        for (String checkpoint : checkpointCodes) {
            jdbcTemplate.update(
                "insert into checkpoint_keys (id, checkpoint_code, public_key_pem, key_type) " +
                "values (?, ?, ?, ?) " +
                "on conflict (checkpoint_code) do update set public_key_pem = excluded.public_key_pem, key_type = excluded.key_type",
                UUID.randomUUID(), checkpoint, cpPem, "RSA"
            );
            logger.info("Created/updated checkpoint key: checkpoint={}", checkpoint);
        }

        KeyPair issuerKey = generateRsa();
        String issuerPem = PemUtil.toPem(issuerKey.getPublic(), "PUBLIC KEY");
        String issuerCode = "gen-issuer-1";
        jdbcTemplate.update(
            "insert into issuer_keys (id, issuer_code, public_key_pem, key_type, algorithm) values (?, ?, ?, ?, ?)",
            UUID.randomUUID(), issuerCode, issuerPem, "RSA", "RS256"
        );
        logger.info("Created issuer key: issuer={}", issuerCode);

        // Return data for default user and entry checkpoint
        return new SeededData("user-1", "cp-out-a", issuerCode, checkpointKey, issuerKey);
    }

    private static KeyPair generateRsa() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    private void grantZoneAccess(UUID userId, UUID zoneId) {
        jdbcTemplate.update(
            "insert into access_rules (user_id, from_zone_id, to_zone_id) values (?, NULL, ?) " +
            "on conflict do nothing",
            userId, zoneId
        );
    }

    private void initializeUserState(UUID userId, UUID zoneId) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "insert into user_state (id, user_id, current_zone_id, version) values (?, ?, ?, 0) " +
                "on conflict (user_id) do update set current_zone_id = excluded.current_zone_id, " +
                "version = 0, updated_at = NOW()"
            );
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            if (zoneId == null) {
                ps.setNull(3, Types.OTHER);
            } else {
                ps.setObject(3, zoneId);
            }
            return ps;
        });
    }

    private void initializeUserStateByCodes(String userCode, String zoneCode) {
        try {
            UUID userId = jdbcTemplate.queryForObject(
                "select id from users where code = ?",
                UUID.class,
                userCode
            );
            UUID zoneId = null;
            if (zoneCode != null) {
                zoneId = jdbcTemplate.queryForObject(
                    "select id from zones where code = ?",
                    UUID.class,
                    zoneCode
                );
            }
            initializeUserState(userId, zoneId);
            logger.info("Ensured user state: {} -> {}", userCode, zoneCode == null ? "OUT" : zoneCode);
        } catch (Exception e) {
            logger.warn("Failed to initialize user state for user={}, zone={}: {}", userCode, zoneCode, e.getMessage());
        }
    }

    private UUID ensureUserStateAndAccess(String userCode, UUID initialZoneId, UUID... allowedZones) {
        UUID userId = ensureUser(userCode);
        for (UUID zoneId : allowedZones) {
            if (zoneId != null) {
                grantZoneAccess(userId, zoneId);
            }
        }
        initializeUserState(userId, initialZoneId);
        logger.info("Ensured user={}, allowedZones={}, state={}", userCode, allowedZones.length, initialZoneId == null ? "OUT" : initialZoneId);
        return userId;
    }

    private UUID ensureUser(String userCode) {
        try {
            UUID existingId = jdbcTemplate.queryForObject(
                "select id from users where code = ?",
                UUID.class,
                userCode
            );
            if (existingId != null) {
                return existingId;
            }
        } catch (EmptyResultDataAccessException e) {
            // ignore, will create new user
        }
        UUID newId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id, code) values (?, ?) on conflict (code) do nothing", newId, userCode);
        try {
            return jdbcTemplate.queryForObject(
                "select id from users where code = ?",
                UUID.class,
                userCode
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure user " + userCode, e);
        }
    }

    private void ensureCheckpoint(String code, UUID fromZoneId, UUID toZoneId) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "insert into checkpoints (id, code, from_zone_id, to_zone_id) values (?, ?, ?, ?) " +
                "on conflict (code) do update set from_zone_id = excluded.from_zone_id, to_zone_id = excluded.to_zone_id"
            );
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, code);
            if (fromZoneId == null) {
                ps.setNull(3, Types.OTHER);
            } else {
                ps.setObject(3, fromZoneId);
            }
            if (toZoneId == null) {
                ps.setNull(4, Types.OTHER);
            } else {
                ps.setObject(4, toZoneId);
            }
            return ps;
        });
        logger.info("Ensured checkpoint configuration: {}", code);
    }

    public record SeededData(String userCode,
                             String checkpointCode,
                             String issuerCode,
                             KeyPair checkpointKeyPair,
                             KeyPair issuerKeyPair) {
        // Helper methods to get available users and zones
        public static String[] getAvailableUsers() {
            return new String[]{"user-1", "user-2", "user-3", "user-4", "user-5"};
        }
        
        public static String[] getAvailableZones() {
            return new String[]{"zone-a", "zone-b", "zone-c"};
        }
        
        public static String[] getAvailableCheckpoints() {
            return new String[]{"cp-out-a", "cp-a-b", "cp-b-c", "cp-c-out"};
        }
    }
}


