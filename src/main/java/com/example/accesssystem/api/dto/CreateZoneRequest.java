package com.example.accesssystem.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a zone.
 */
public record CreateZoneRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 128, message = "Code must not exceed 128 characters")
    String code
) {}

