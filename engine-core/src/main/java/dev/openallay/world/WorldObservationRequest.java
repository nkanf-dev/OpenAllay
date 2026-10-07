package dev.openallay.world;

import java.util.Objects;

@dev.openallay.value.ValueType(WorldObservationRequest.ValueSchemaProvider.class)
public final class WorldObservationRequest {
    private final WorldBounds bounds;
    private final boolean includeAir;
    private final String entityType;
    public WorldObservationRequest(WorldBounds bounds, boolean includeAir, String entityType) {

        Objects.requireNonNull(bounds, "bounds");
        entityType = entityType == null ? "" : dev.openallay.util.Java8Strings.strip(entityType);

        this.bounds = bounds;
        this.includeAir = includeAir;
        this.entityType = entityType;
    }
    public WorldBounds bounds() { return bounds; }
    public boolean includeAir() { return includeAir; }
    public String entityType() { return entityType; }
public static WorldObservationRequest blocks(WorldBounds bounds, boolean includeAir) {
        return new WorldObservationRequest(bounds, includeAir, "");
    }
public static WorldObservationRequest entities(WorldBounds bounds, String entityType) {
        return new WorldObservationRequest(bounds, false, entityType);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldObservationRequest)) return false;
        WorldObservationRequest that = (WorldObservationRequest) other;
        return java.util.Objects.equals(bounds, that.bounds) && includeAir == that.includeAir && java.util.Objects.equals(entityType, that.entityType);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(bounds);
        hash = 31 * hash + Boolean.hashCode(includeAir);
        hash = 31 * hash + java.util.Objects.hashCode(entityType);
        return hash;
    }
    @Override public String toString() { return "WorldObservationRequest[bounds=" + bounds + ", includeAir=" + includeAir + ", entityType=" + entityType + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldObservationRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldObservationRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldObservationRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldObservationRequest.class, "bounds", WorldObservationRequest::bounds), new dev.openallay.value.ValueSchema.Component<>(WorldObservationRequest.class, "includeAir", WorldObservationRequest::includeAir), new dev.openallay.value.ValueSchema.Component<>(WorldObservationRequest.class, "entityType", WorldObservationRequest::entityType)), arguments -> new WorldObservationRequest((WorldBounds) arguments[0], (Boolean) arguments[1], (String) arguments[2]));
        }
    }
}
