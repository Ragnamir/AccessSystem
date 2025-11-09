package com.example.accesssystem.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for checkpoint operations.
 */
public record CheckpointResponse(
    UUID id,
    String code,
    UUID fromZoneId,
    UUID toZoneId,
    Instant createdAt
) {}

