package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.Identifiers.ZoneId;
import com.example.accesssystem.domain.contracts.AccessControlContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.ALLOW;
import static com.example.accesssystem.domain.contracts.AccessControlContracts.AccessDecision.DENY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AccessRuleEvaluatorService.
 * Verifies decisions when permissions are granted per destination zone only.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccessRuleEvaluatorServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccessRuleEvaluatorImpl accessRuleEvaluator;

    private UUID user1Id;
    private UUID user2Id;
    private UUID zoneAId;
    private UUID zoneBId;
    private UUID zoneCId;

    @BeforeEach
    void setUp() {
        // Clean up test data
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM zones");

        // Create test users
        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", user1Id, "user-1");
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", user2Id, "user-2");

        // Create test zones
        zoneAId = UUID.randomUUID();
        zoneBId = UUID.randomUUID();
        zoneCId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneAId, "zone-a");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneBId, "zone-b");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneCId, "zone-c");

        // Set up access rules based on destination-only permissions:
        // user-1: allowed zones -> zone-a, zone-b
        // user-2: allowed zone -> zone-b

        jdbcTemplate.update(
            "INSERT INTO access_rules (id, user_id, to_zone_id) VALUES (gen_random_uuid(), ?, ?)",
            user1Id, zoneAId
        );

        jdbcTemplate.update(
            "INSERT INTO access_rules (id, user_id, to_zone_id) VALUES (gen_random_uuid(), ?, ?)",
            user1Id, zoneBId
        );

        jdbcTemplate.update(
            "INSERT INTO access_rules (id, user_id, to_zone_id) VALUES (gen_random_uuid(), ?, ?)",
            user2Id, zoneBId
        );
    }

    // Positive scenarios

    @Test
    void canTransit_user1EntryToZoneA_returnsAllow() {
        // Given: user-1 entering zone-a from outside
        UserId userId = new UserId("user-1");
        ZoneId fromZone = null;
        ZoneId toZone = new ZoneId("zone-a");

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(ALLOW);
    }

    @Test
    void canTransit_user1TransitToZoneB_returnsAllow() {
        // Given: user-1 transitions to zone-b (source zone should not matter)
        UserId userId = new UserId("user-1");
        ZoneId fromZone = new ZoneId("zone-a");
        ZoneId toZone = new ZoneId("zone-b");

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(ALLOW);
    }

    @Test
    void canTransit_user2EntryToZoneB_returnsAllow() {
        // Given: user-2 entering zone-b from outside
        UserId userId = new UserId("user-2");
        ZoneId fromZone = null;
        ZoneId toZone = new ZoneId("zone-b");

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(ALLOW);
    }

    @Test
    void canTransit_user1TransitToZoneC_returnsDeny() {
        // Given: user-1 trying to reach zone-c (no rule for this zone)
        UserId userId = new UserId("user-1");
        ZoneId fromZone = new ZoneId("zone-a");
        ZoneId toZone = new ZoneId("zone-c");

        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        assertThat(decision).isEqualTo(DENY);
    }

    @Test
    void canTransit_user2TransitToZoneA_returnsDeny() {
        UserId userId = new UserId("user-2");
        ZoneId fromZone = new ZoneId("zone-b");
        ZoneId toZone = new ZoneId("zone-a");

        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        assertThat(decision).isEqualTo(DENY);
    }

    @Test
    void canTransit_user2EntryToZoneA_returnsDeny() {
        // Given: user-2 trying to enter zone-a from outside (no rule for this)
        UserId userId = new UserId("user-2");
        ZoneId fromZone = null;
        ZoneId toZone = new ZoneId("zone-a");

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(DENY);
    }

    @Test
    void canTransit_nonexistentUser_returnsDeny() {
        // Given: non-existent user trying to transit
        UserId userId = new UserId("user-nonexistent");
        ZoneId fromZone = new ZoneId("zone-a");
        ZoneId toZone = new ZoneId("zone-b");

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(DENY);
    }

    @Test
    void canTransit_nonexistentZone_returnsDeny() {
        // Given: user trying to transit to non-existent zone
        UserId userId = new UserId("user-1");
        ZoneId fromZone = new ZoneId("zone-a");
        ZoneId toZone = new ZoneId("zone-nonexistent");

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(DENY);
    }

    @Test
    void canTransit_exitIsAllowedForAnyUser() {
        UserId userId = new UserId("user-2");
        ZoneId fromZone = new ZoneId("zone-b");
        ZoneId toZone = null; // OUT

        // When
        AccessControlContracts.AccessDecision decision = accessRuleEvaluator.canTransit(userId, fromZone, toZone);

        // Then
        assertThat(decision).isEqualTo(ALLOW);
    }
}

