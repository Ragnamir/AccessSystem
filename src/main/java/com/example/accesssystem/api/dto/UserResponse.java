package com.example.accesssystem.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for user operations.
 */
public record UserResponse(
    UUID id,
    String code,
    Instant createdAt
) {}

