package com.example.accesssystem.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for access rule operations.
 */
public record AccessRuleResponse(
    UUID id,
    UUID userId,
    UUID toZoneId,
    Instant createdAt
) {}

