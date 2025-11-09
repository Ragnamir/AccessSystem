package com.example.accesssystem.service;

import java.time.Instant;

/**
 * Repository for managing event nonces (eventId) to prevent replay attacks.
 */
public interface EventNonceRepository {
    
    /**
     * Checks if an eventId already exists in the repository.
     * 
     * @param eventId the event identifier to check
     * @return true if the eventId exists (already used), false otherwise
     */
    boolean exists(String eventId);
    
    /**
     * Stores an eventId with expiration time.
     * 
     * @param eventId the event identifier
     * @param checkpointId the checkpoint identifier
     * @param eventTimestamp the timestamp of the event
     * @param expiresAt the expiration time for this nonce
     */
    void store(String eventId, String checkpointId, Instant eventTimestamp, Instant expiresAt);
    
    /**
     * Cleans up expired nonces from the repository.
     * 
     * @return number of nonces removed
     */
    int cleanupExpired();
}

