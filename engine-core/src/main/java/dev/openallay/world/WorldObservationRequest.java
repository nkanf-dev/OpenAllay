package dev.openallay.world;

import java.util.Objects;

public record WorldObservationRequest(
        WorldBounds bounds,
        boolean includeAir,
        String entityType) {
    public WorldObservationRequest {
        Objects.requireNonNull(bounds, "bounds");
        entityType = entityType == null ? "" : entityType.strip();
    }

    public static WorldObservationRequest blocks(WorldBounds bounds, boolean includeAir) {
        return new WorldObservationRequest(bounds, includeAir, "");
    }

    public static WorldObservationRequest entities(WorldBounds bounds, String entityType) {
        return new WorldObservationRequest(bounds, false, entityType);
    }
}
