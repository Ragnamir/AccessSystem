package com.example.webui.repository;

import java.time.Instant;
import java.util.UUID;

public record ZoneView(
    UUID id,
    String code,
    Instant createdAt
) {}


