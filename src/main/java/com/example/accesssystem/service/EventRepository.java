package com.example.accesssystem.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for storing access events in the database.
 * Events are logged after successful zone transitions.
 */
public interface EventRepository {
    
    /**
     * Records a successful access event after a zone transition.
     * 
     * @param eventId the unique event identifier (links to event_nonces.event_id)
     * @param checkpointId the checkpoint UUID
     * @param userId the user UUID
     * @param fromZoneId the source zone UUID, can be null for OUT zone
     * @param toZoneId the destination zone UUID, can be null for OUT zone
     * @param eventTimestamp the timestamp of the event
     */
    void recordEvent(
        String eventId,
        UUID checkpointId,
        UUID userId,
        UUID fromZoneId,
        UUID toZoneId,
        Instant eventTimestamp
    );
    
    /**
     * Gets checkpoint UUID by checkpoint code.
     * 
     * @param checkpointCode the checkpoint code
     * @return Optional containing checkpoint UUID if found
     */
    Optional<UUID> findCheckpointIdByCode(String checkpointCode);
    
    /**
     * Gets user UUID by user code.
     * 
     * @param userCode the user code
     * @return Optional containing user UUID if found
     */
    Optional<UUID> findUserIdByCode(String userCode);
    
    /**
     * Gets zone UUID by zone code.
     * 
     * @param zoneCode the zone code, can be null for OUT zone
     * @return Optional containing zone UUID if found, empty if zoneCode is null
     */
    Optional<UUID> findZoneIdByCode(String zoneCode);
}

