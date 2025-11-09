package com.example.accesssystem.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating an access rule.
 */
public record CreateAccessRuleRequest(
    @NotNull(message = "User ID is required")
    UUID userId,

    @NotNull(message = "Destination zone ID is required")
    UUID toZoneId
) {}

