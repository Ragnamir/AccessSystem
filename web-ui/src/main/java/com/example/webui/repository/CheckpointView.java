package com.example.webui.repository;

import java.time.Instant;
import java.util.UUID;

public record CheckpointView(
    UUID id,
    String code,
    String fromZoneCode,
    String toZoneCode,
    Instant createdAt
) {}

