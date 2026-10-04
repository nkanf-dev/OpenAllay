package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record WorldEntitySnapshot(
        String observationId,
        UUID uuid,
        String type,
        String name,
        WorldPosition position,
        Map<String, Object> data,
        EvidenceMetadata evidence) {
    public WorldEntitySnapshot {
        observationId = require(observationId, "observationId");
        Objects.requireNonNull(uuid, "uuid");
        type = require(type, "type");
        name = name == null ? "" : name;
        Objects.requireNonNull(position, "position");
        data = Map.copyOf(new TreeMap<>(Objects.requireNonNull(data, "data")));
        Objects.requireNonNull(evidence, "evidence");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
