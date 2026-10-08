package dev.openallay.guide.ui;

/** Stable row and pixel offset used to preserve reading position across page merges. */
@dev.openallay.value.ValueType(GuideViewportAnchor.ValueSchemaProvider.class)
public final class GuideViewportAnchor {
    private final String rowId;
    private final int pixelOffset;
    public GuideViewportAnchor(String rowId, int pixelOffset) {

        if (rowId == null || dev.openallay.util.Java8Strings.isBlank(rowId)) {
            throw new IllegalArgumentException("viewport anchor row is required");
        }
        if (pixelOffset < 0) {
            throw new IllegalArgumentException("viewport anchor offset must not be negative");
        }

        this.rowId = rowId;
        this.pixelOffset = pixelOffset;
    }
    public String rowId() { return rowId; }
    public int pixelOffset() { return pixelOffset; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideViewportAnchor)) return false;
        GuideViewportAnchor that = (GuideViewportAnchor) other;
        return java.util.Objects.equals(rowId, that.rowId) && pixelOffset == that.pixelOffset;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(rowId);
        hash = 31 * hash + Integer.hashCode(pixelOffset);
        return hash;
    }
    @Override public String toString() { return "GuideViewportAnchor[rowId=" + rowId + ", pixelOffset=" + pixelOffset + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideViewportAnchor> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideViewportAnchor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideViewportAnchor>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideViewportAnchor.class, "rowId", GuideViewportAnchor::rowId), new dev.openallay.value.ValueSchema.Component<>(GuideViewportAnchor.class, "pixelOffset", GuideViewportAnchor::pixelOffset)), arguments -> new GuideViewportAnchor((String) arguments[0], (Integer) arguments[1]));
        }
    }
}
