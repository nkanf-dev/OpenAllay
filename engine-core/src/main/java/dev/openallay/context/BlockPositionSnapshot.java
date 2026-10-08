package dev.openallay.context;

@dev.openallay.value.ValueType(BlockPositionSnapshot.ValueSchemaProvider.class)
public final class BlockPositionSnapshot {
    private final int x;
    private final int y;
    private final int z;
    public BlockPositionSnapshot(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockPositionSnapshot)) return false;
        BlockPositionSnapshot that = (BlockPositionSnapshot) other;
        return x == that.x && y == that.y && z == that.z;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(z);
        return hash;
    }
    @Override public String toString() { return "BlockPositionSnapshot[x=" + x + ", y=" + y + ", z=" + z + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BlockPositionSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(BlockPositionSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BlockPositionSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(BlockPositionSnapshot.class, "x", BlockPositionSnapshot::x),
                    new dev.openallay.value.ValueSchema.Component<>(BlockPositionSnapshot.class, "y", BlockPositionSnapshot::y),
                    new dev.openallay.value.ValueSchema.Component<>(BlockPositionSnapshot.class, "z", BlockPositionSnapshot::z)), arguments -> new BlockPositionSnapshot((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2]));
        }
    }
}
