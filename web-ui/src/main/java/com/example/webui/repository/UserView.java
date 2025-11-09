package com.example.webui.repository;

import java.time.Instant;
import java.util.UUID;

public record UserView(
    UUID id,
    String code,
    String currentZoneCode,
    Instant updatedAt
) {}

