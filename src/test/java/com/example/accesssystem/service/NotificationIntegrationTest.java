package com.example.accesssystem.service;

import com.example.accesssystem.domain.DenialReason;
import com.example.accesssystem.service.notification.StubNotificationSender;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for denial notifications.
 * Verifies that denial notifications are sent when access denials are recorded.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("access-system.notifications.type", () -> "stub");
    }

    @Autowired
    private DenialRepository denialRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean up
        jdbcTemplate.update("DELETE FROM denials");
        jdbcTemplate.update("DELETE FROM checkpoints");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM zones");
        StubNotificationSender.clearNotifications();
    }

    @Test
    void recordDenial_shouldSendNotification() {
        // Given - create test data
        String eventId = UUID.randomUUID().toString();
        UUID checkpointId = UUID.randomUUID();
        String checkpointCode = "cp-1";
        UUID userId = UUID.randomUUID();
        String userCode = "user-123";
        UUID fromZoneId = UUID.randomUUID();
        String fromZoneCode = "zone-a";
        UUID toZoneId = UUID.randomUUID();
        String toZoneCode = "zone-b";
        DenialReason reason = DenialReason.ACCESS_DENIED;
        String details = "Access denied by rule";
        
        // Create zones
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", fromZoneId, fromZoneCode);
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", toZoneId, toZoneCode);
        
        // Create user
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", userId, userCode);
        
        // Create checkpoint
        jdbcTemplate.update(
            "INSERT INTO checkpoints (id, code, from_zone_id, to_zone_id) VALUES (?, ?, ?, ?)",
            checkpointId, checkpointCode, fromZoneId, toZoneId
        );

        // When
        denialRepository.recordDenial(
            eventId,
            checkpointId,
            checkpointCode,
            userId,
            userCode,
            fromZoneId,
            fromZoneCode,
            toZoneId,
            toZoneCode,
            reason,
            details
        );

        // Then - verify notification was sent
        List<NotificationSender.DenialNotification> notifications = StubNotificationSender.getSentNotifications();
        assertThat(notifications).hasSize(1);

        NotificationSender.DenialNotification notification = notifications.get(0);
        assertThat(notification.eventId()).isEqualTo(eventId);
        assertThat(notification.checkpointId()).isEqualTo(checkpointId);
        assertThat(notification.checkpointCode()).isEqualTo(checkpointCode);
        assertThat(notification.userId()).isEqualTo(userId);
        assertThat(notification.userCode()).isEqualTo(userCode);
        assertThat(notification.fromZoneId()).isEqualTo(fromZoneId);
        assertThat(notification.fromZoneCode()).isEqualTo(fromZoneCode);
        assertThat(notification.toZoneId()).isEqualTo(toZoneId);
        assertThat(notification.toZoneCode()).isEqualTo(toZoneCode);
        assertThat(notification.reason()).isEqualTo(reason);
        assertThat(notification.details()).isEqualTo(details);
        assertThat(notification.timestamp()).isPositive();
    }

    @Test
    void recordDenialMinimal_shouldSendNotification() {
        // Given
        String checkpointCode = "cp-1";
        DenialReason reason = DenialReason.SIGNATURE_INVALID;
        String details = "Invalid signature";

        // When
        denialRepository.recordDenial(checkpointCode, reason, details);

        // Then - verify notification was sent
        List<NotificationSender.DenialNotification> notifications = StubNotificationSender.getSentNotifications();
        assertThat(notifications).hasSize(1);

        NotificationSender.DenialNotification notification = notifications.get(0);
        assertThat(notification.checkpointCode()).isEqualTo(checkpointCode);
        assertThat(notification.reason()).isEqualTo(reason);
        assertThat(notification.details()).isEqualTo(details);
        assertThat(notification.eventId()).isNull();
        assertThat(notification.checkpointId()).isNull();
        assertThat(notification.userId()).isNull();
        assertThat(notification.userCode()).isNull();
    }

    @Test
    void recordMultipleDenials_shouldSendMultipleNotifications() {
        // Given
        String checkpointCode1 = "cp-1";
        String checkpointCode2 = "cp-2";

        // When
        denialRepository.recordDenial(checkpointCode1, DenialReason.ACCESS_DENIED, "First denial");
        denialRepository.recordDenial(checkpointCode2, DenialReason.TOKEN_INVALID, "Second denial");

        // Then
        assertThat(StubNotificationSender.getNotificationCount()).isEqualTo(2);
        
        List<NotificationSender.DenialNotification> notifications = StubNotificationSender.getSentNotifications();
        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).checkpointCode()).isEqualTo(checkpointCode1);
        assertThat(notifications.get(1).checkpointCode()).isEqualTo(checkpointCode2);
    }

    @Test
    void recordDenial_shouldRecordInDatabaseEvenIfNotificationFails() {
        // Given - notification will fail due to special details marker
        String checkpointCode = "cp-1";
        String details = "[SIMULATE_ERROR] Test error";

        // When
        denialRepository.recordDenial(checkpointCode, DenialReason.INTERNAL_ERROR, details);

        // Then - denial should still be recorded in database
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE checkpoint_code = ? AND reason = ?",
            Integer.class,
            checkpointCode,
            DenialReason.INTERNAL_ERROR.name()
        );
        assertThat(denialCount).isEqualTo(1);

        // Notification attempt was made (stored in stub despite failure)
        assertThat(StubNotificationSender.getNotificationCount()).isEqualTo(1);
    }
}

