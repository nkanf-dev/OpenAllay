package dev.openallay.guide.ui;

import dev.openallay.guide.semantic.SemanticDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Targeted semantic measurement cache keyed by stable row and presentation identity. */
public final class SemanticLayoutCache {
    @dev.openallay.value.ValueType(Stats.ValueSchemaProvider.class)
public static final class Stats {
    private final long hits;
    private final long misses;
    private final int entries;
    public Stats(long hits, long misses, int entries) {
        this.hits = hits;
        this.misses = misses;
        this.entries = entries;
    }
    public long hits() { return hits; }
    public long misses() { return misses; }
    public int entries() { return entries; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Stats)) return false;
        Stats that = (Stats) other;
        return hits == that.hits && misses == that.misses && entries == that.entries;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(hits);
        hash = 31 * hash + Long.hashCode(misses);
        hash = 31 * hash + Integer.hashCode(entries);
        return hash;
    }
    @Override public String toString() { return "Stats[hits=" + hits + ", misses=" + misses + ", entries=" + entries + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Stats> schema() {
            return new dev.openallay.value.ValueSchema<>(Stats.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Stats>>asList(new dev.openallay.value.ValueSchema.Component<>(Stats.class, "hits", Stats::hits), new dev.openallay.value.ValueSchema.Component<>(Stats.class, "misses", Stats::misses), new dev.openallay.value.ValueSchema.Component<>(Stats.class, "entries", Stats::entries)), arguments -> new Stats((Long) arguments[0], (Long) arguments[1], (Integer) arguments[2]));
        }
    }
}
    private static final AtomicLong GLOBAL_HITS = new AtomicLong();
    private static final AtomicLong GLOBAL_MISSES = new AtomicLong();
    @dev.openallay.value.ValueType(Key.ValueSchemaProvider.class)
private static final class Key {
    private final String rowId;
    private final int contentHash;
    private final int width;
    private final String locale;
    private final String fontIdentity;
    private Key(String rowId, int contentHash, int width, String locale, String fontIdentity) {
        this.rowId = rowId;
        this.contentHash = contentHash;
        this.width = width;
        this.locale = locale;
        this.fontIdentity = fontIdentity;
    }
    public String rowId() { return rowId; }
    public int contentHash() { return contentHash; }
    public int width() { return width; }
    public String locale() { return locale; }
    public String fontIdentity() { return fontIdentity; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Key)) return false;
        Key that = (Key) other;
        return java.util.Objects.equals(rowId, that.rowId) && contentHash == that.contentHash && width == that.width && java.util.Objects.equals(locale, that.locale) && java.util.Objects.equals(fontIdentity, that.fontIdentity);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(rowId);
        hash = 31 * hash + Integer.hashCode(contentHash);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(locale);
        hash = 31 * hash + java.util.Objects.hashCode(fontIdentity);
        return hash;
    }
    @Override public String toString() { return "Key[rowId=" + rowId + ", contentHash=" + contentHash + ", width=" + width + ", locale=" + locale + ", fontIdentity=" + fontIdentity + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Key> schema() {
            return new dev.openallay.value.ValueSchema<>(Key.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Key>>asList(new dev.openallay.value.ValueSchema.Component<>(Key.class, "rowId", Key::rowId), new dev.openallay.value.ValueSchema.Component<>(Key.class, "contentHash", Key::contentHash), new dev.openallay.value.ValueSchema.Component<>(Key.class, "width", Key::width), new dev.openallay.value.ValueSchema.Component<>(Key.class, "locale", Key::locale), new dev.openallay.value.ValueSchema.Component<>(Key.class, "fontIdentity", Key::fontIdentity)), arguments -> new Key((String) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}

    private final Map<Key, SemanticLayout> values = new LinkedHashMap<>();
    private long hits;
    private long misses;

    public SemanticLayout get(
            String rowId,
            SemanticDocument document,
            int width,
            String locale,
            String fontIdentity,
            SemanticLayoutEngine.Measurer measurer) {
        Key key = new Key(
                require(rowId), document.hashCode(), width, require(locale),
                require(fontIdentity));
        SemanticLayout cached = values.get(key);
        if (cached != null) {
            hits++;
            GLOBAL_HITS.incrementAndGet();
            return cached;
        }
        misses++;
        GLOBAL_MISSES.incrementAndGet();
        SemanticLayout created = new SemanticLayoutEngine().layout(document, width, measurer);
        values.put(key, created);
        return created;
    }

    public void invalidateRow(String rowId) {
        values.keySet().removeIf(key -> key.rowId().equals(rowId));
    }

    public void clear() {
        values.clear();
    }

    public Stats stats() {
        return new Stats(hits, misses, values.size());
    }

    public static Stats globalStats() {
        return new Stats(GLOBAL_HITS.get(), GLOBAL_MISSES.get(), 0);
    }

    private static String require(String value) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) throw new IllegalArgumentException("cache identity is required");
        return value;
    }
}
