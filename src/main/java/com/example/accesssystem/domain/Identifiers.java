package com.example.accesssystem.domain;

import java.util.Objects;

/**
 * Value objects for strong typing of identifiers in the domain.
 */
public final class Identifiers {

    private Identifiers() {}

    public record UserId(String value) {
        public UserId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("UserId cannot be null or blank");
            }
        }
        @Override public String toString() { return value; }
    }

    public record ZoneId(String value) {
        public ZoneId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("ZoneId cannot be null or blank");
            }
        }
        @Override public String toString() { return value; }
    }

    public record CheckpointId(String value) {
        public CheckpointId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("CheckpointId cannot be null or blank");
            }
        }
        @Override public String toString() { return value; }
    }

    public record IssuerId(String value) {
        public IssuerId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("IssuerId cannot be null or blank");
            }
        }
        @Override public String toString() { return value; }
    }
}



