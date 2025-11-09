package com.example.accesssystem.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing zones in the database.
 */
public interface ZoneRepository {
    
    /**
     * Creates a new zone.
     * 
     * @param code the unique zone code
     * @return the created zone's ID
     */
    UUID create(String code);
    
    /**
     * Finds a zone by ID.
     * 
     * @param id the zone ID
     * @return the zone record if found
     */
    Optional<ZoneRecord> findById(UUID id);
    
    /**
     * Finds a zone by code.
     * 
     * @param code the zone code
     * @return the zone record if found
     */
    Optional<ZoneRecord> findByCode(String code);
    
    /**
     * Lists all zones with pagination.
     * 
     * @param offset the offset for pagination
     * @param limit the maximum number of records to return
     * @return list of zone records
     */
    List<ZoneRecord> findAll(int offset, int limit);
    
    /**
     * Counts total number of zones.
     * 
     * @return total count
     */
    long count();
    
    /**
     * Updates a zone's code.
     * 
     * @param id the zone ID
     * @param newCode the new code
     * @return true if updated, false if not found
     */
    boolean update(UUID id, String newCode);
    
    /**
     * Deletes a zone by ID.
     * 
     * @param id the zone ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(UUID id);
    
    /**
     * Zone record from database.
     */
    record ZoneRecord(UUID id, String code, Instant createdAt) {}
}

