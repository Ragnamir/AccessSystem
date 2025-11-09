package com.example.accesssystem.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for zone operations.
 */
public record ZoneResponse(
    UUID id,
    String code,
    Instant createdAt
) {}

