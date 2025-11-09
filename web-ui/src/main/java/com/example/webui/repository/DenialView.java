package com.example.webui.repository;

import java.time.Instant;
import java.util.UUID;

public record DenialView(
    UUID id,
    String checkpointCode,
    String userCode,
    String fromZoneCode,
    String toZoneCode,
    String reason,
    Instant createdAt
) {}

