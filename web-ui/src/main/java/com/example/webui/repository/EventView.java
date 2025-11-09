package com.example.webui.repository;

import java.time.Instant;
import java.util.UUID;

public record EventView(
    UUID id,
    String userCode,
    String checkpointCode,
    String fromZoneCode,
    String toZoneCode,
    Instant eventTimestamp
) {}

