package dev.openallay.world;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record WorldBlockSnapshot(
        String id,
        WorldPosition position,
        WorldPosition relative,
        Map<String, String> state,
        String fluid,
        boolean blockEntity) {
    public WorldBlockSnapshot {
        id = require(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(relative, "relative");
        state = Map.copyOf(new TreeMap<>(Objects.requireNonNull(state, "state")));
        fluid = fluid == null ? "" : fluid.strip();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
