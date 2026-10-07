package dev.openallay.world;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@dev.openallay.value.ValueType(WorldBlockSnapshot.ValueSchemaProvider.class)
public final class WorldBlockSnapshot {
    private final String id;
    private final WorldPosition position;
    private final WorldPosition relative;
    private final Map<String, String> state;
    private final String fluid;
    private final boolean blockEntity;
    public WorldBlockSnapshot(String id, WorldPosition position, WorldPosition relative, Map<String, String> state, String fluid, boolean blockEntity) {

        id = require(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(relative, "relative");
        state = Map.copyOf(new TreeMap<>(Objects.requireNonNull(state, "state")));
        fluid = fluid == null ? "" : fluid.strip();

        this.id = id;
        this.position = position;
        this.relative = relative;
        this.state = state;
        this.fluid = fluid;
        this.blockEntity = blockEntity;
    }
    public String id() { return id; }
    public WorldPosition position() { return position; }
    public WorldPosition relative() { return relative; }
    public Map<String, String> state() { return state; }
    public String fluid() { return fluid; }
    public boolean blockEntity() { return blockEntity; }
private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldBlockSnapshot)) return false;
        WorldBlockSnapshot that = (WorldBlockSnapshot) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(position, that.position) && java.util.Objects.equals(relative, that.relative) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(fluid, that.fluid) && blockEntity == that.blockEntity;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(position);
        hash = 31 * hash + java.util.Objects.hashCode(relative);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(fluid);
        hash = 31 * hash + Boolean.hashCode(blockEntity);
        return hash;
    }
    @Override public String toString() { return "WorldBlockSnapshot[id=" + id + ", position=" + position + ", relative=" + relative + ", state=" + state + ", fluid=" + fluid + ", blockEntity=" + blockEntity + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldBlockSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldBlockSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldBlockSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldBlockSnapshot.class, "id", WorldBlockSnapshot::id), new dev.openallay.value.ValueSchema.Component<>(WorldBlockSnapshot.class, "position", WorldBlockSnapshot::position), new dev.openallay.value.ValueSchema.Component<>(WorldBlockSnapshot.class, "relative", WorldBlockSnapshot::relative), new dev.openallay.value.ValueSchema.Component<>(WorldBlockSnapshot.class, "state", WorldBlockSnapshot::state), new dev.openallay.value.ValueSchema.Component<>(WorldBlockSnapshot.class, "fluid", WorldBlockSnapshot::fluid), new dev.openallay.value.ValueSchema.Component<>(WorldBlockSnapshot.class, "blockEntity", WorldBlockSnapshot::blockEntity)), arguments -> new WorldBlockSnapshot((String) arguments[0], (WorldPosition) arguments[1], (WorldPosition) arguments[2], (Map) arguments[3], (String) arguments[4], (Boolean) arguments[5]));
        }
    }
}
