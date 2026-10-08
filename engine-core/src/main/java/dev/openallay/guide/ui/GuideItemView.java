package dev.openallay.guide.ui;

/** Immutable item-stack projection safe for player-facing native UI cards. */
@dev.openallay.value.ValueType(GuideItemView.ValueSchemaProvider.class)
public final class GuideItemView {
    private final String itemId;
    private final String displayName;
    private final long count;
    public GuideItemView(String itemId, String displayName, long count) {

        if (itemId == null || dev.openallay.util.Java8Strings.isBlank(itemId)) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        displayName = displayName == null || dev.openallay.util.Java8Strings.isBlank(displayName) ? itemId : displayName;
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }

        this.itemId = itemId;
        this.displayName = displayName;
        this.count = count;
    }
    public String itemId() { return itemId; }
    public String displayName() { return displayName; }
    public long count() { return count; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideItemView)) return false;
        GuideItemView that = (GuideItemView) other;
        return java.util.Objects.equals(itemId, that.itemId) && java.util.Objects.equals(displayName, that.displayName) && count == that.count;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + Long.hashCode(count);
        return hash;
    }
    @Override public String toString() { return "GuideItemView[itemId=" + itemId + ", displayName=" + displayName + ", count=" + count + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideItemView> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideItemView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideItemView>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideItemView.class, "itemId", GuideItemView::itemId), new dev.openallay.value.ValueSchema.Component<>(GuideItemView.class, "displayName", GuideItemView::displayName), new dev.openallay.value.ValueSchema.Component<>(GuideItemView.class, "count", GuideItemView::count)), arguments -> new GuideItemView((String) arguments[0], (String) arguments[1], (Long) arguments[2]));
        }
    }
}
