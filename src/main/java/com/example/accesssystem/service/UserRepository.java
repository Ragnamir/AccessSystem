package com.example.accesssystem.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing users in the database.
 */
public interface UserRepository {
    
    /**
     * Creates a new user.
     * 
     * @param code the unique user code
     * @return the created user's ID
     */
    UUID create(String code);
    
    /**
     * Finds a user by ID.
     * 
     * @param id the user ID
     * @return the user record if found
     */
    Optional<UserRecord> findById(UUID id);
    
    /**
     * Finds a user by code.
     * 
     * @param code the user code
     * @return the user record if found
     */
    Optional<UserRecord> findByCode(String code);
    
    /**
     * Lists all users with pagination.
     * 
     * @param offset the offset for pagination
     * @param limit the maximum number of records to return
     * @return list of user records
     */
    List<UserRecord> findAll(int offset, int limit);
    
    /**
     * Counts total number of users.
     * 
     * @return total count
     */
    long count();
    
    /**
     * Updates a user's code.
     * 
     * @param id the user ID
     * @param newCode the new code
     * @return true if updated, false if not found
     */
    boolean update(UUID id, String newCode);
    
    /**
     * Deletes a user by ID.
     * 
     * @param id the user ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(UUID id);
    
    /**
     * User record from database.
     */
    record UserRecord(UUID id, String code, Instant createdAt) {}
}

