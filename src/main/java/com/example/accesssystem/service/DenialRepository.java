package com.example.accesssystem.service;

import com.example.accesssystem.domain.DenialReason;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for storing access denials in the database.
 * Denials are logged when access attempts are rejected for any reason.
 */
public interface DenialRepository {
    
    /**
     * Records an access denial with the specified reason and details.
     * 
     * @param eventId optional event identifier
     * @param checkpointId optional checkpoint UUID
     * @param checkpointCode checkpoint code
     * @param userId optional user UUID
     * @param userCode user code
     * @param fromZoneId optional source zone UUID
     * @param fromZoneCode source zone code (can be null for OUT zone)
     * @param toZoneId optional destination zone UUID
     * @param toZoneCode destination zone code
     * @param reason the denial reason category
     * @param details optional additional details about the denial
     */
    void recordDenial(
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
    );
    
    /**
     * Records a denial with minimal information.
     * Used when only checkpoint code and reason are available.
     * 
     * @param checkpointCode checkpoint code
     * @param reason the denial reason category
     * @param details optional additional details about the denial
     */
    void recordDenial(String checkpointCode, DenialReason reason, String details);
}

