package com.example.accesssystem.api.dto;

import java.util.List;

/**
 * Generic paginated response DTO.
 */
public record PageResponse<T>(
    List<T> items,
    long total,
    int offset,
    int limit
) {}

