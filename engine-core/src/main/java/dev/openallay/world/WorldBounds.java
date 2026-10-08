package dev.openallay.world;

import java.util.Objects;

/** Inclusive normalized cuboid. */
@dev.openallay.value.ValueType(WorldBounds.ValueSchemaProvider.class)
public final class WorldBounds {
    private final WorldPosition from;
    private final WorldPosition to;
    public WorldBounds(WorldPosition from, WorldPosition to) {

        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        WorldPosition first = from;
        WorldPosition second = to;
        from = new WorldPosition(
                Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()));
        to = new WorldPosition(
                Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()),
                Math.max(first.z(), second.z()));

        this.from = from;
        this.to = to;
    }
    public WorldPosition from() { return from; }
    public WorldPosition to() { return to; }
public long volume() {
        return Math.multiplyExact(
                Math.multiplyExact(
                        (long) to.x() - from.x() + 1,
                        (long) to.y() - from.y() + 1),
                (long) to.z() - from.z() + 1);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldBounds)) return false;
        WorldBounds that = (WorldBounds) other;
        return java.util.Objects.equals(from, that.from) && java.util.Objects.equals(to, that.to);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(from);
        hash = 31 * hash + java.util.Objects.hashCode(to);
        return hash;
    }
    @Override public String toString() { return "WorldBounds[from=" + from + ", to=" + to + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldBounds> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldBounds.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldBounds>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldBounds.class, "from", WorldBounds::from), new dev.openallay.value.ValueSchema.Component<>(WorldBounds.class, "to", WorldBounds::to)), arguments -> new WorldBounds((WorldPosition) arguments[0], (WorldPosition) arguments[1]));
        }
    }
}
