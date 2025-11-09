package com.example.accesssystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Service for anti-replay protection:
 * - Validates event timestamp within allowed skew window
 * - Prevents duplicate eventId/nonce usage
 */
@Service
public class AntiReplayService {
    
    private static final Logger log = LoggerFactory.getLogger(AntiReplayService.class);
    
    private final EventNonceRepository eventNonceRepository;
    private final long timestampSkewSeconds;
    private final long eventNonceTtlSeconds;
    
    AntiReplayService(
            EventNonceRepository eventNonceRepository,
            @Value("${access-system.anti-replay.timestamp-skew-seconds:300}") long timestampSkewSeconds,
            @Value("${access-system.anti-replay.event-nonce-ttl-seconds:86400}") long eventNonceTtlSeconds) {
        this.eventNonceRepository = eventNonceRepository;
        this.timestampSkewSeconds = timestampSkewSeconds;
        this.eventNonceTtlSeconds = eventNonceTtlSeconds;
    }
    
    /**
     * Validates event for anti-replay protection.
     * 
     * @param eventId the unique event identifier (nonce)
     * @param checkpointId the checkpoint identifier
     * @param timestampIso8601 the event timestamp in ISO-8601 format
     * @return ValidationResult with validation status
     */
    public ValidationResult validateEvent(String eventId, String checkpointId, String timestampIso8601) {
        // Parse timestamp using Instant.parse() which correctly handles ISO-8601 format
        // from Instant.toString() (supports variable precision: 0-9 digits for fractional seconds)
        Instant eventTimestamp;
        try {
            eventTimestamp = Instant.parse(timestampIso8601);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse timestamp: {}", timestampIso8601);
            return ValidationResult.rejected("invalid_timestamp", 
                "Invalid timestamp format: " + e.getMessage());
        }
        
        // Check timestamp skew
        Instant now = Instant.now();
        long skewSeconds = Math.abs(java.time.Duration.between(now, eventTimestamp).getSeconds());
        
        if (skewSeconds > timestampSkewSeconds) {
            log.warn("Event timestamp out of allowed skew window. Event: {}, Now: {}, Skew: {}s", 
                eventTimestamp, now, skewSeconds);
            return ValidationResult.rejected("timestamp_out_of_window", 
                String.format("Event timestamp is outside allowed skew window (max %d seconds). " +
                    "Event timestamp: %s, Current time: %s, Difference: %d seconds", 
                    timestampSkewSeconds, eventTimestamp, now, skewSeconds));
        }
        
        // Check if eventId already exists (replay detection)
        if (eventNonceRepository.exists(eventId)) {
            log.warn("Duplicate eventId detected: {} from checkpoint {}", eventId, checkpointId);
            return ValidationResult.rejected("duplicate_event_id", 
                "Event ID already used (possible replay attack): " + eventId);
        }
        
        // Store eventId with TTL
        Instant expiresAt = now.plusSeconds(eventNonceTtlSeconds);
        eventNonceRepository.store(eventId, checkpointId, eventTimestamp, expiresAt);
        
        log.debug("Event validated successfully. EventId: {}, Checkpoint: {}, Timestamp: {}", 
            eventId, checkpointId, eventTimestamp);
        
        return ValidationResult.accepted();
    }
    
    /**
     * Result of anti-replay validation.
     */
    public static class ValidationResult {
        private final boolean accepted;
        private final String reason;
        private final String details;
        
        private ValidationResult(boolean accepted, String reason, String details) {
            this.accepted = accepted;
            this.reason = reason;
            this.details = details;
        }
        
        public static ValidationResult accepted() {
            return new ValidationResult(true, "accepted", null);
        }
        
        public static ValidationResult rejected(String reason, String details) {
            return new ValidationResult(false, reason, details);
        }
        
        public boolean isAccepted() {
            return accepted;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getDetails() {
            return details;
        }
    }
}

