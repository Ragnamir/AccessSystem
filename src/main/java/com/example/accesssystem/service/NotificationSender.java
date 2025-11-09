package com.example.accesssystem.service;

import com.example.accesssystem.domain.DenialReason;

import java.util.UUID;

/**
 * Interface for sending notifications about access denials.
 * Supports multiple implementations: webhook, message queue, etc.
 */
public interface NotificationSender {
    
    /**
     * Sends a notification about an access denial.
     * 
     * @param denialNotification the denial notification data
     * @throws NotificationException if the notification cannot be sent
     */
    void sendDenialNotification(DenialNotification denialNotification) throws NotificationException;
    
    /**
     * Data class representing a denial notification.
     */
    record DenialNotification(
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
        String details,
        long timestamp
    ) {
        /**
         * Creates a DenialNotification from denial parameters.
         */
        public static DenialNotification of(
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
            return new DenialNotification(
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
                details,
                System.currentTimeMillis()
            );
        }
        
        /**
         * Creates a DenialNotification with minimal information.
         */
        public static DenialNotification minimal(
            String checkpointCode,
            DenialReason reason,
            String details
        ) {
            return new DenialNotification(
                null,
                null,
                checkpointCode,
                null,
                null,
                null,
                null,
                null,
                null,
                reason,
                details,
                System.currentTimeMillis()
            );
        }
    }
    
    /**
     * Exception thrown when notification sending fails.
     */
    class NotificationException extends Exception {
        public NotificationException(String message) {
            super(message);
        }
        
        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

