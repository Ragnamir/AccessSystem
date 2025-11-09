package com.example.accesssystem.service.notification;

import com.example.accesssystem.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stub implementation of NotificationSender for testing and development.
 * Stores notifications in memory for verification in tests.
 * 
 * This implementation is activated when property 'access-system.notifications.type' is set to 'stub'.
 */
@Component
@ConditionalOnProperty(
    name = "access-system.notifications.type",
    havingValue = "stub",
    matchIfMissing = true
)
public class StubNotificationSender implements NotificationSender {
    
    private static final Logger log = LoggerFactory.getLogger(StubNotificationSender.class);
    
    private static final List<DenialNotification> sentNotifications = Collections.synchronizedList(new ArrayList<>());
    
    @Override
    public void sendDenialNotification(DenialNotification notification) throws NotificationException {
        log.info("Stub: Sending denial notification: eventId={}, checkpoint={}, user={}, reason={}, details={}",
            notification.eventId(),
            notification.checkpointCode(),
            notification.userCode(),
            notification.reason(),
            notification.details());
        
        // Store notification for test verification
        sentNotifications.add(notification);
        
        // Simulate potential failure for testing error handling
        if (notification.details() != null && notification.details().contains("[SIMULATE_ERROR]")) {
            throw new NotificationException("Simulated notification failure");
        }
    }
    
    /**
     * Returns all notifications sent so far (for testing).
     * 
     * @return list of sent notifications
     */
    public static List<DenialNotification> getSentNotifications() {
        return new ArrayList<>(sentNotifications);
    }
    
    /**
     * Clears all stored notifications (for testing).
     */
    public static void clearNotifications() {
        sentNotifications.clear();
    }
    
    /**
     * Returns the number of notifications sent.
     * 
     * @return count of notifications
     */
    public static int getNotificationCount() {
        return sentNotifications.size();
    }
}

