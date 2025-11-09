package com.example.accesssystem.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for querying and managing access rules from the database.
 * Access rules define which users can transit from one zone to another.
 */
public interface AccessRuleRepository {
    
    /**
     * Checks if a user is allowed to enter a destination zone.
     *
     * @param userCode   the user code (UserId.value)
     * @param toZoneCode the destination zone code (ZoneId.value), {@code null} represents exit to outside (OUT)
     * @return true if the access rule exists or exit is permitted, false otherwise
     */
    boolean hasAccess(String userCode, String toZoneCode);
    
    /**
     * Creates a new access rule.
     * 
     * @param userId the user ID
     * @param toZoneId the destination zone ID (must not be {@code null})
     * @return the created access rule's ID
     */
    UUID create(UUID userId, UUID toZoneId);
    
    /**
     * Finds an access rule by ID.
     * 
     * @param id the access rule ID
     * @return the access rule record if found
     */
    Optional<AccessRuleRecord> findById(UUID id);
    
    /**
     * Finds access rules by user ID.
     * 
     * @param userId the user ID
     * @param offset the offset for pagination
     * @param limit the maximum number of records to return
     * @return list of access rule records
     */
    List<AccessRuleRecord> findByUserId(UUID userId, int offset, int limit);
    
    /**
     * Lists all access rules with pagination.
     * 
     * @param offset the offset for pagination
     * @param limit the maximum number of records to return
     * @return list of access rule records
     */
    List<AccessRuleRecord> findAll(int offset, int limit);
    
    /**
     * Counts total number of access rules.
     * 
     * @return total count
     */
    long count();
    
    /**
     * Counts access rules for a specific user.
     * 
     * @param userId the user ID
     * @return count for the user
     */
    long countByUserId(UUID userId);
    
    /**
     * Updates an access rule with a new destination zone.
     *
     * @param id the access rule ID
     * @param newToZoneId the new destination zone ID (must not be {@code null})
     * @return true if updated, false if not found
     */
    boolean update(UUID id, UUID newToZoneId);
    
    /**
     * Deletes an access rule by ID.
     * 
     * @param id the access rule ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(UUID id);
    
    /**
     * Access rule record from database.
     */
    record AccessRuleRecord(UUID id, UUID userId, UUID toZoneId, Instant createdAt) {}
}

