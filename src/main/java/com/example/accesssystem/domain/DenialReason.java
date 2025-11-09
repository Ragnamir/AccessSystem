package com.example.accesssystem.domain;

/**
 * Enumeration of denial reasons for access control system.
 * Used to categorize why access was denied in the denials table.
 */
public enum DenialReason {
    /**
     * Checkpoint signature verification failed.
     * The cryptographic signature of the checkpoint message is invalid.
     */
    SIGNATURE_INVALID,
    
    /**
     * User token (JWT/JWS) verification failed.
     * The token signature is invalid, expired, or malformed.
     */
    TOKEN_INVALID,
    
    /**
     * Replay attack detected.
     * The eventId was already used or timestamp is outside acceptable window.
     */
    REPLAY,
    
    /**
     * Access denied by access rules.
     * No matching access rule found or rule explicitly denies access.
     */
    ACCESS_DENIED,
    
    /**
     * User state mismatch.
     * User's current zone doesn't match the expected from_zone for the transition.
     */
    STATE_MISMATCH,
    
    /**
     * Internal error occurred during processing.
     * Unexpected exception or system error during event processing.
     */
    INTERNAL_ERROR
}

