package com.example.webui.repository;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    protected void createTestSchema(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");

            conn.createStatement().execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'events') THEN
                        TRUNCATE TABLE events CASCADE;
                    END IF;
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'denials') THEN
                        TRUNCATE TABLE denials;
                    END IF;
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'user_state') THEN
                        TRUNCATE TABLE user_state CASCADE;
                    END IF;
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'checkpoints') THEN
                        TRUNCATE TABLE checkpoints CASCADE;
                    END IF;
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'zones') THEN
                        TRUNCATE TABLE zones CASCADE;
                    END IF;
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'users') THEN
                        TRUNCATE TABLE users CASCADE;
                    END IF;
                END
                $$;
                """);

            // Create tables
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY,
                    code VARCHAR(128) NOT NULL UNIQUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS zones (
                    id UUID PRIMARY KEY,
                    code VARCHAR(128) NOT NULL UNIQUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS checkpoints (
                    id UUID PRIMARY KEY,
                    code VARCHAR(128) NOT NULL UNIQUE,
                    from_zone_id UUID,
                    to_zone_id UUID,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_checkpoint_from_zone FOREIGN KEY (from_zone_id) REFERENCES zones(id),
                    CONSTRAINT fk_checkpoint_to_zone FOREIGN KEY (to_zone_id) REFERENCES zones(id)
                )
                """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS user_state (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id UUID NOT NULL UNIQUE,
                    current_zone_id UUID,
                    version BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_user_state_user FOREIGN KEY (user_id) REFERENCES users(id),
                    CONSTRAINT fk_user_state_zone FOREIGN KEY (current_zone_id) REFERENCES zones(id)
                )
                """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    event_id VARCHAR(512) NOT NULL UNIQUE,
                    checkpoint_id UUID NOT NULL,
                    user_id UUID NOT NULL,
                    from_zone_id UUID,
                    to_zone_id UUID,
                    event_timestamp TIMESTAMPTZ NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_events_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id),
                    CONSTRAINT fk_events_user FOREIGN KEY (user_id) REFERENCES users(id),
                    CONSTRAINT fk_events_from_zone FOREIGN KEY (from_zone_id) REFERENCES zones(id),
                    CONSTRAINT fk_events_to_zone FOREIGN KEY (to_zone_id) REFERENCES zones(id)
                )
                """);
            
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS denials (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    checkpoint_code VARCHAR(128),
                    user_code VARCHAR(128),
                    from_zone_code VARCHAR(128),
                    to_zone_code VARCHAR(128),
                    reason VARCHAR(64) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        }
    }

    protected UUID insertUser(DataSource dataSource, String code) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO users (id, code) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, code);
            ps.executeUpdate();
        }
        return id;
    }

    protected UUID insertZone(DataSource dataSource, String code) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO zones (id, code) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, code);
            ps.executeUpdate();
        }
        return id;
    }

    protected UUID insertCheckpoint(DataSource dataSource, String code, UUID fromZoneId, UUID toZoneId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO checkpoints (id, code, from_zone_id, to_zone_id) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, code);
            ps.setObject(3, fromZoneId);
            ps.setObject(4, toZoneId);
            ps.executeUpdate();
        }
        return id;
    }
}

