package dev.openallay.world;

import java.util.Objects;

public record WorldEntitySummary(
        String observationId,
        String type,
        String name,
        WorldPosition position,
        boolean alive) {
    public WorldEntitySummary {
        observationId = require(observationId, "observationId");
        type = require(type, "type");
        name = name == null ? "" : name;
        Objects.requireNonNull(position, "position");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
