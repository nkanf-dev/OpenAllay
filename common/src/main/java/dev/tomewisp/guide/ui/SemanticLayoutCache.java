package dev.tomewisp.guide.ui;

import dev.tomewisp.guide.semantic.SemanticDocument;
import java.util.LinkedHashMap;
import java.util.Map;

/** Targeted semantic measurement cache keyed by stable row and presentation identity. */
public final class SemanticLayoutCache {
    public record Stats(long hits, long misses, int entries) {}
    private record Key(
            String rowId, int contentHash, int width, String locale,
            String fontIdentity, boolean animationsEnabled) {}

    private final Map<Key, SemanticLayout> values = new LinkedHashMap<>();
    private long hits;
    private long misses;

    public SemanticLayout get(
            String rowId,
            SemanticDocument document,
            int width,
            String locale,
            String fontIdentity,
            boolean animationsEnabled,
            SemanticLayoutEngine.Measurer measurer) {
        Key key = new Key(
                require(rowId), document.hashCode(), width, require(locale),
                require(fontIdentity), animationsEnabled);
        SemanticLayout cached = values.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
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

    private static String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("cache identity is required");
        return value;
    }
}
