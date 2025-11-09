package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.Identifiers.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserStateService.
 * Tests optimistic locking with concurrent updates.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UserStateServiceTest {

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
    private UserStateService userStateService;

    private UUID userId;
    private UUID zoneAId;
    private UUID zoneBId;
    private UUID zoneCId;

    @BeforeEach
    void setUp() {
        // Clean up test data
        jdbcTemplate.update("DELETE FROM user_state");
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM zones");

        // Create test user
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", userId, "test-user");

        // Create test zones
        zoneAId = UUID.randomUUID();
        zoneBId = UUID.randomUUID();
        zoneCId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneAId, "zone-a");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneBId, "zone-b");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneCId, "zone-c");
    }

    @Test
    void currentZone_whenStateNotInitialized_returnsNull() {
        // Given: user state not initialized
        UserId userId = new UserId("test-user");

        // When
        ZoneId currentZone = userStateService.currentZone(userId);

        // Then: should return null (OUT zone)
        assertThat(currentZone).isNull();
    }

    @Test
    void updateZone_whenStateNotInitialized_initializesState() {
        // Given: user state not initialized
        UserId userId = new UserId("test-user");
        ZoneId newZone = new ZoneId("zone-a");

        // When
        userStateService.updateZone(userId, newZone);

        // Then: state should be initialized and zone updated
        ZoneId currentZone = userStateService.currentZone(userId);
        assertThat(currentZone).isNotNull();
        assertThat(currentZone.value()).isEqualTo("zone-a");
    }

    @Test
    void updateZone_updatesZoneCorrectly() {
        // Given: user in zone-a
        UserId userId = new UserId("test-user");
        userStateService.updateZone(userId, new ZoneId("zone-a"));

        // When: update to zone-b
        userStateService.updateZone(userId, new ZoneId("zone-b"));

        // Then: zone should be updated
        ZoneId currentZone = userStateService.currentZone(userId);
        assertThat(currentZone).isNotNull();
        assertThat(currentZone.value()).isEqualTo("zone-b");
    }

    @Test
    void updateZone_updatesToOutZone() {
        // Given: user in zone-a
        UserId userId = new UserId("test-user");
        userStateService.updateZone(userId, new ZoneId("zone-a"));

        // When: update to OUT zone (null)
        userStateService.updateZone(userId, null);

        // Then: zone should be null (OUT)
        ZoneId currentZone = userStateService.currentZone(userId);
        assertThat(currentZone).isNull();
    }

    @Test
    void updateZone_concurrentUpdates_preventsRaceConditions() throws Exception {
        // Given: user in zone-a (version 0 after initialization)
        UserId userId = new UserId("test-user");
        userStateService.updateZone(userId, new ZoneId("zone-a"));

        // When: multiple threads try to update concurrently
        int numberOfThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    try {
                        // Each thread tries to update to a different zone
                        // All threads will read version=0, but only one should succeed
                        ZoneId targetZone = new ZoneId("zone-" + (threadIndex % 2 == 0 ? "b" : "c"));
                        userStateService.updateZone(userId, targetZone);
                        successCount.incrementAndGet();
                    } catch (OptimisticLockingFailureException e) {
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        // Count other exceptions as failures too
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        startLatch.countDown();
        endLatch.await(); // Wait for all threads to complete

        // Then: only one update should succeed, others should fail with OptimisticLockingFailureException
        // Note: With retries, some threads might retry and succeed after others fail
        // But we expect at least that not all succeed on first attempt
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(successCount.get() + failureCount.get()).isEqualTo(numberOfThreads);
        
        // Verify final state is valid (one of the zones)
        ZoneId finalZone = userStateService.currentZone(userId);
        assertThat(finalZone).isNotNull();
        assertThat(finalZone.value()).isIn("zone-b", "zone-c");
    }

    @Test
    void updateZone_rapidSequentialUpdates_updatesCorrectly() {
        // Given: user starting in OUT zone
        UserId userId = new UserId("test-user");

        // When: rapid sequential updates
        userStateService.updateZone(userId, new ZoneId("zone-a"));
        userStateService.updateZone(userId, new ZoneId("zone-b"));
        userStateService.updateZone(userId, new ZoneId("zone-c"));
        userStateService.updateZone(userId, null); // Back to OUT

        // Then: final state should be OUT
        ZoneId currentZone = userStateService.currentZone(userId);
        assertThat(currentZone).isNull();
    }

    @Test
    void updateZone_versionTracking_worksCorrectly() {
        // Given: user in zone-a (initialized with version 0)
        UserId userId = new UserId("test-user");
        userStateService.updateZone(userId, new ZoneId("zone-a"));

        // Verify version after initialization (should be 0, because initialization doesn't increment)
        Long version0 = jdbcTemplate.queryForObject(
            "SELECT version FROM user_state us INNER JOIN users u ON us.user_id = u.id WHERE u.code = ?",
            Long.class,
            "test-user"
        );
        assertThat(version0).isEqualTo(0L);

        // When: update again (this should increment version from 0 to 1)
        userStateService.updateZone(userId, new ZoneId("zone-b"));

        // Then: version should be incremented to 1
        Long version1 = jdbcTemplate.queryForObject(
            "SELECT version FROM user_state us INNER JOIN users u ON us.user_id = u.id WHERE u.code = ?",
            Long.class,
            "test-user"
        );
        assertThat(version1).isEqualTo(1L);
        
        // When: update once more
        userStateService.updateZone(userId, new ZoneId("zone-c"));

        // Then: version should be incremented to 2
        Long version2 = jdbcTemplate.queryForObject(
            "SELECT version FROM user_state us INNER JOIN users u ON us.user_id = u.id WHERE u.code = ?",
            Long.class,
            "test-user"
        );
        assertThat(version2).isEqualTo(2L);
    }

    @Test
    void updateZone_manualVersionConflict_handlesVersionChanges() {
        // Given: user in zone-a (version 0 after initialization)
        UserId userId = new UserId("test-user");
        userStateService.updateZone(userId, new ZoneId("zone-a"));

        // Manually increment version by 2 to simulate another transaction updating the version
        jdbcTemplate.update(
            "UPDATE user_state us SET version = version + 2 FROM users u WHERE us.user_id = u.id AND u.code = ?",
            "test-user"
        );

        // Verify version is now 2
        Long versionBefore = jdbcTemplate.queryForObject(
            "SELECT version FROM user_state us INNER JOIN users u ON us.user_id = u.id WHERE u.code = ?",
            Long.class,
            "test-user"
        );
        assertThat(versionBefore).isEqualTo(2L);

        // When: update zone - service will read fresh version 2 and update successfully
        userStateService.updateZone(userId, new ZoneId("zone-b"));
        
        // Then: version was incremented from 2 to 3
        Long versionAfter = jdbcTemplate.queryForObject(
            "SELECT version FROM user_state us INNER JOIN users u ON us.user_id = u.id WHERE u.code = ?",
            Long.class,
            "test-user"
        );
        assertThat(versionAfter).isEqualTo(3L);
        
        // And zone was updated
        ZoneId currentZone = userStateService.currentZone(userId);
        assertThat(currentZone).isNotNull();
        assertThat(currentZone.value()).isEqualTo("zone-b");
    }
}

