package com.example.accesssystem.service;

import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.Identifiers.ZoneId;
import com.example.accesssystem.domain.contracts.AccessControlContracts;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing user state with optimistic locking.
 * Implements AccessControlContracts.UserStateService.
 * 
 * Uses optimistic locking to prevent race conditions when multiple threads
 * try to update the same user's zone simultaneously.
 */
@Service
public class UserStateService implements AccessControlContracts.UserStateService {
    
    private final UserStateRepository userStateRepository;
    private static final int MAX_RETRIES = 3;
    
    UserStateService(UserStateRepository userStateRepository) {
        this.userStateRepository = userStateRepository;
    }
    
    @Override
    public ZoneId currentZone(UserId userId) {
        Optional<UserStateRepository.UserStateRecord> record = 
            userStateRepository.getCurrentZone(userId.value());
        
        if (record.isEmpty()) {
            // User state not initialized - user is outside (OUT zone)
            return null;
        }
        
        String zoneCode = record.get().zoneCode();
        if (zoneCode == null) {
            // NULL zone code means OUT zone
            return null;
        }
        
        return new ZoneId(zoneCode);
    }
    
    @Override
    @Transactional
    public void updateZone(UserId userId, ZoneId newZone) {
        String userCode = userId.value();
        String zoneCode = newZone == null ? null : newZone.value();
        
        // Try to update with retries on optimistic lock failure
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Get current state
            Optional<UserStateRepository.UserStateRecord> current = 
                userStateRepository.getCurrentZone(userCode);
            
            if (current.isEmpty()) {
                // Initialize state if it doesn't exist
                boolean initialized = userStateRepository.initializeState(userCode, zoneCode);
                if (initialized) {
                    return; // Successfully initialized
                }
                // If initialization failed, retry getting current state
                continue;
            }
            
            // Update with optimistic locking
            long expectedVersion = current.get().version();
            try {
                boolean updated = userStateRepository.updateZone(userCode, zoneCode, expectedVersion);
                
                if (updated) {
                    return; // Successfully updated
                }
            } catch (OptimisticLockingFailureException e) {
                // Version conflict - retry
                if (attempt < MAX_RETRIES - 1) {
                    // Brief pause before retry (in real system, might use exponential backoff)
                    try {
                        Thread.sleep(10 * (attempt + 1)); // 10ms, 20ms, 30ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    continue; // Retry with fresh read
                } else {
                    // All retries exhausted, rethrow
                    throw e;
                }
            }
            
            // Update failed for other reason (e.g., user doesn't exist) - retry
            if (attempt < MAX_RETRIES - 1) {
                try {
                    Thread.sleep(10 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        
        // All retries exhausted
        throw new OptimisticLockingFailureException(
            "Failed to update zone for user " + userCode + 
            " after " + MAX_RETRIES + " attempts due to concurrent modifications"
        );
    }
}

