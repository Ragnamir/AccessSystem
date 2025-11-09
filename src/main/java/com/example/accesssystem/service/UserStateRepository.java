package com.example.accesssystem.service;

import java.util.Optional;

/**
 * Repository for managing user state in the database.
 * Tracks current zone of each user with optimistic locking support.
 */
public interface UserStateRepository {
    
    /**
     * Gets the current zone and version for a user.
     * 
     * @param userCode the user code (UserId.value)
     * @return Optional containing the zone code (ZoneId.value) and version, empty if user state not found
     */
    Optional<UserStateRecord> getCurrentZone(String userCode);
    
    /**
     * Updates the user's current zone with optimistic locking.
     * Only updates if the version matches the expected version.
     * 
     * @param userCode the user code (UserId.value)
     * @param zoneCode the new zone code (ZoneId.value), null for OUT zone
     * @param expectedVersion the expected version (for optimistic locking)
     * @return true if update was successful (version matched), false if version conflict occurred
     */
    boolean updateZone(String userCode, String zoneCode, long expectedVersion);
    
    /**
     * Initializes user state if it doesn't exist.
     * Creates a new record with version 0.
     * 
     * @param userCode the user code (UserId.value)
     * @param zoneCode the initial zone code (ZoneId.value), null for OUT zone
     * @return true if initialization was successful
     */
    boolean initializeState(String userCode, String zoneCode);
    
    /**
     * Record containing zone code and version for optimistic locking.
     */
    record UserStateRecord(String zoneCode, long version) {}
}

