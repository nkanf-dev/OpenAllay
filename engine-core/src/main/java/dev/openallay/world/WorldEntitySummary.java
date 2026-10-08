package dev.openallay.world;

import java.util.Objects;

@dev.openallay.value.ValueType(WorldEntitySummary.ValueSchemaProvider.class)
public final class WorldEntitySummary {
    private final String observationId;
    private final String type;
    private final String name;
    private final WorldPosition position;
    private final boolean alive;
    public WorldEntitySummary(String observationId, String type, String name, WorldPosition position, boolean alive) {

        observationId = require(observationId, "observationId");
        type = require(type, "type");
        name = name == null ? "" : name;
        Objects.requireNonNull(position, "position");

        this.observationId = observationId;
        this.type = type;
        this.name = name;
        this.position = position;
        this.alive = alive;
    }
    public String observationId() { return observationId; }
    public String type() { return type; }
    public String name() { return name; }
    public WorldPosition position() { return position; }
    public boolean alive() { return alive; }
private static String require(String value, String field) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldEntitySummary)) return false;
        WorldEntitySummary that = (WorldEntitySummary) other;
        return java.util.Objects.equals(observationId, that.observationId) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(position, that.position) && alive == that.alive;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(observationId);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(position);
        hash = 31 * hash + Boolean.hashCode(alive);
        return hash;
    }
    @Override public String toString() { return "WorldEntitySummary[observationId=" + observationId + ", type=" + type + ", name=" + name + ", position=" + position + ", alive=" + alive + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldEntitySummary> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldEntitySummary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldEntitySummary>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldEntitySummary.class, "observationId", WorldEntitySummary::observationId), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySummary.class, "type", WorldEntitySummary::type), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySummary.class, "name", WorldEntitySummary::name), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySummary.class, "position", WorldEntitySummary::position), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySummary.class, "alive", WorldEntitySummary::alive)), arguments -> new WorldEntitySummary((String) arguments[0], (String) arguments[1], (String) arguments[2], (WorldPosition) arguments[3], (Boolean) arguments[4]));
        }
    }
}
