package com.example.accesssystem.service;

import com.example.accesssystem.domain.DenialReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of DenialRepository using JdbcTemplate.
 * Inserts denial records into the denials table and sends notifications.
 */
@Repository
public class DenialRepositoryImpl implements DenialRepository {
    
    private static final Logger log = LoggerFactory.getLogger(DenialRepositoryImpl.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final Optional<NotificationSender> notificationSender;
    
    DenialRepositoryImpl(
        JdbcTemplate jdbcTemplate,
        Optional<NotificationSender> notificationSender,
        MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationSender = notificationSender;
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public void recordDenial(
        String eventId,
        UUID checkpointId,
        String checkpointCode,
        UUID userId,
        String userCode,
        UUID fromZoneId,
        String fromZoneCode,
        UUID toZoneId,
        String toZoneCode,
        DenialReason reason,
        String details
    ) {
        String sql = """
            INSERT INTO denials (
                event_id,
                checkpoint_id,
                checkpoint_code,
                user_id,
                user_code,
                from_zone_id,
                from_zone_code,
                to_zone_id,
                to_zone_code,
                reason,
                details
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(
            sql,
            eventId,
            checkpointId,
            checkpointCode,
            userId,
            userCode,
            fromZoneId,
            fromZoneCode,
            toZoneId,
            toZoneCode,
            reason.name(),
            details
        );

        // Metrics: increment denial counter with reason tag
        meterRegistry.counter("access_denials_total", "reason", reason.name())
            .increment();
        
        // Send notification asynchronously (errors should not affect denial recording)
        sendNotificationIfAvailable(
            NotificationSender.DenialNotification.of(
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
            )
        );
    }
    
    @Override
    public void recordDenial(String checkpointCode, DenialReason reason, String details) {
        String sql = """
            INSERT INTO denials (
                checkpoint_code,
                reason,
                details
            )
            VALUES (?, ?, ?)
            """;
        
        jdbcTemplate.update(
            sql,
            checkpointCode,
            reason.name(),
            details
        );

        // Metrics: increment denial counter with reason tag
        meterRegistry.counter("access_denials_total", "reason", reason.name())
            .increment();
        
        // Send notification asynchronously (errors should not affect denial recording)
        sendNotificationIfAvailable(
            NotificationSender.DenialNotification.minimal(
                checkpointCode,
                reason,
                details
            )
        );
    }
    
    /**
     * Sends notification if NotificationSender is available.
     * Errors during notification sending are logged but do not affect denial recording.
     */
    private void sendNotificationIfAvailable(NotificationSender.DenialNotification notification) {
        notificationSender.ifPresent(sender -> {
            try {
                sender.sendDenialNotification(notification);
                log.debug("Denial notification sent: eventId={}, checkpoint={}, reason={}",
                    notification.eventId(), notification.checkpointCode(), notification.reason());
            } catch (NotificationSender.NotificationException e) {
                log.warn("Failed to send denial notification: eventId={}, checkpoint={}, reason={}",
                    notification.eventId(), notification.checkpointCode(), notification.reason(), e);
            } catch (Exception e) {
                log.error("Unexpected error sending denial notification: eventId={}, checkpoint={}",
                    notification.eventId(), notification.checkpointCode(), e);
            }
        });
    }
}

