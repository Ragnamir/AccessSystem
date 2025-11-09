package com.example.accesssystem.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Builder for creating canonical form of payloads for signature verification.
 * 
 * Canonical form: checkpointId|timestamp|fromZone|toZone|userToken
 * (all fields concatenated with '|' separator, in this exact order)
 */
@Component
public class CanonicalPayloadBuilder {
    
    /**
     * Creates canonical form of the payload for signing.
     * 
     * @param checkpointId checkpoint identifier
     * @param timestamp ISO-8601 timestamp
     * @param fromZone source zone
     * @param toZone destination zone
     * @param userToken encrypted user token
     * @return canonical payload as bytes
     */
    public byte[] buildCanonicalPayload(String checkpointId, String timestamp, 
                                 String fromZone, String toZone, String userToken) {
        String canonical = String.join("|", 
            checkpointId, 
            timestamp, 
            fromZone, 
            toZone, 
            userToken
        );
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
}

