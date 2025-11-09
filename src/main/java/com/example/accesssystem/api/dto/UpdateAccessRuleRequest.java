package com.example.accesssystem.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for updating an access rule.
 */
public record UpdateAccessRuleRequest(
    @NotNull(message = "Destination zone ID is required")
    UUID toZoneId
) {}

