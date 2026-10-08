package dev.openallay.world;

/** Detached integer block coordinate used by spatial observations. */
@dev.openallay.value.ValueType(WorldPosition.ValueSchemaProvider.class)
public final class WorldPosition {
    private final int x;
    private final int y;
    private final int z;
    public WorldPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
public WorldPosition subtract(WorldPosition origin) {
        return new WorldPosition(x - origin.x, y - origin.y, z - origin.z);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldPosition)) return false;
        WorldPosition that = (WorldPosition) other;
        return x == that.x && y == that.y && z == that.z;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(z);
        return hash;
    }
    @Override public String toString() { return "WorldPosition[x=" + x + ", y=" + y + ", z=" + z + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldPosition> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldPosition.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldPosition>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldPosition.class, "x", WorldPosition::x), new dev.openallay.value.ValueSchema.Component<>(WorldPosition.class, "y", WorldPosition::y), new dev.openallay.value.ValueSchema.Component<>(WorldPosition.class, "z", WorldPosition::z)), arguments -> new WorldPosition((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2]));
        }
    }
}
