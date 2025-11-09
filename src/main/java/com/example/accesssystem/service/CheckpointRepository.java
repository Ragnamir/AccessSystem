package com.example.accesssystem.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing checkpoints in the database.
 */
public interface CheckpointRepository {
    
    /**
     * Creates a new checkpoint.
     * 
     * @param code the unique checkpoint code
     * @param fromZoneId the source zone ID (can be null)
     * @param toZoneId the destination zone ID
     * @return the created checkpoint's ID
     */
    UUID create(String code, UUID fromZoneId, UUID toZoneId);
    
    /**
     * Finds a checkpoint by ID.
     * 
     * @param id the checkpoint ID
     * @return the checkpoint record if found
     */
    Optional<CheckpointRecord> findById(UUID id);
    
    /**
     * Finds a checkpoint by code.
     * 
     * @param code the checkpoint code
     * @return the checkpoint record if found
     */
    Optional<CheckpointRecord> findByCode(String code);
    
    /**
     * Lists all checkpoints with pagination.
     * 
     * @param offset the offset for pagination
     * @param limit the maximum number of records to return
     * @return list of checkpoint records
     */
    List<CheckpointRecord> findAll(int offset, int limit);
    
    /**
     * Counts total number of checkpoints.
     * 
     * @return total count
     */
    long count();
    
    /**
     * Updates a checkpoint.
     * 
     * @param id the checkpoint ID
     * @param newCode the new code
     * @param fromZoneId the new source zone ID (can be null)
     * @param toZoneId the new destination zone ID
     * @return true if updated, false if not found
     */
    boolean update(UUID id, String newCode, UUID fromZoneId, UUID toZoneId);
    
    /**
     * Deletes a checkpoint by ID.
     * 
     * @param id the checkpoint ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(UUID id);
    
    /**
     * Checkpoint record from database.
     */
    record CheckpointRecord(UUID id, String code, UUID fromZoneId, UUID toZoneId, Instant createdAt) {}

    /**
     * Checks whether there is at least one checkpoint leading from the specified zone to the outside.
     *
     * @param fromZoneId the originating zone ID
     * @return true if an exit checkpoint exists, false otherwise
     */
    boolean hasExit(UUID fromZoneId);
}

