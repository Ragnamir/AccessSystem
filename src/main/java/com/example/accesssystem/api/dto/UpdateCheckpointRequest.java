package com.example.accesssystem.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for updating a checkpoint.
 */
public record UpdateCheckpointRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 128, message = "Code must not exceed 128 characters")
    String code,
    
    UUID fromZoneId,
    
    @NotNull(message = "To zone ID is required")
    UUID toZoneId
) {}

