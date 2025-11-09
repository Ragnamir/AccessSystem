package com.example.accesssystem.domain;

import com.example.accesssystem.domain.Identifiers.CheckpointId;
import com.example.accesssystem.domain.Identifiers.UserId;
import com.example.accesssystem.domain.Identifiers.ZoneId;

import java.time.Instant;
import java.util.Objects;

/**
 * Core domain models for passage attempts and directions.
 */
public final class PassageModels {

    private PassageModels() {}

    public enum PassageDirection { ENTER, EXIT, TRANSIT }

    public record PassageAttempt(
            UserId userId,
            CheckpointId checkpointId,
            ZoneId fromZone,
            ZoneId toZone,
            PassageDirection direction,
            Instant occurredAt,
            SignedPayload signedPayload
    ) {
        public PassageAttempt {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(checkpointId, "checkpointId");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(signedPayload, "signedPayload");
        }
    }

    public record SignedPayload(byte[] bytes) {
        public SignedPayload {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Signed payload must not be empty");
            }
        }
    }
}



