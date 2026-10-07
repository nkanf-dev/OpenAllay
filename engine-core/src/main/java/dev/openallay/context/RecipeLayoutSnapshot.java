package dev.openallay.context;

@dev.openallay.value.ValueType(RecipeLayoutSnapshot.ValueSchemaProvider.class)
public final class RecipeLayoutSnapshot {
    private final int width;
    private final int height;
    private final boolean shaped;
    public RecipeLayoutSnapshot(int width, int height, boolean shaped) {

        if (width < 0 || height < 0 || (width == 0) != (height == 0)) {
            throw new IllegalArgumentException("recipe layout dimensions are invalid");
        }
        if (shaped && width == 0) {
            throw new IllegalArgumentException("shaped recipe layout requires dimensions");
        }
            this.width = width;
        this.height = height;
        this.shaped = shaped;
    }
    public int width() { return width; }
    public int height() { return height; }
    public boolean shaped() { return shaped; }



    public static RecipeLayoutSnapshot unknown() {
        return new RecipeLayoutSnapshot(0, 0, false);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeLayoutSnapshot)) return false;
        RecipeLayoutSnapshot that = (RecipeLayoutSnapshot) other;
        return width == that.width && height == that.height && shaped == that.shaped;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Boolean.hashCode(shaped);
        return hash;
    }
    @Override public String toString() { return "RecipeLayoutSnapshot[width=" + width + ", height=" + height + ", shaped=" + shaped + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeLayoutSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeLayoutSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeLayoutSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeLayoutSnapshot.class, "width", RecipeLayoutSnapshot::width),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeLayoutSnapshot.class, "height", RecipeLayoutSnapshot::height),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeLayoutSnapshot.class, "shaped", RecipeLayoutSnapshot::shaped)), arguments -> new RecipeLayoutSnapshot((Integer) arguments[0], (Integer) arguments[1], (Boolean) arguments[2]));
        }
    }
}
