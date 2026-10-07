package dev.openallay.knowledge.online;

import dev.openallay.net.HttpCancellation;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OnlineKnowledgeSource {
    String sourceId();

    String provenance();

    CompletableFuture<List<RawHit>> search(
            String query, int limit, HttpCancellation cancellation);

    @dev.openallay.value.ValueType(RawHit.ValueSchemaProvider.class)
public static final class RawHit {
    private final String title;
    private final String excerpt;
    private final String reference;
    public RawHit(String title, String excerpt, String reference) {

            if (title == null || title.isBlank()
                    || excerpt == null
                    || reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("invalid raw online knowledge hit");
            }

        this.title = title;
        this.excerpt = excerpt;
        this.reference = reference;
    }
    public String title() { return title; }
    public String excerpt() { return excerpt; }
    public String reference() { return reference; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RawHit)) return false;
        RawHit that = (RawHit) other;
        return java.util.Objects.equals(title, that.title) && java.util.Objects.equals(excerpt, that.excerpt) && java.util.Objects.equals(reference, that.reference);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(excerpt);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        return hash;
    }
    @Override public String toString() { return "RawHit[title=" + title + ", excerpt=" + excerpt + ", reference=" + reference + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RawHit> schema() {
            return new dev.openallay.value.ValueSchema<>(RawHit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RawHit>>asList(new dev.openallay.value.ValueSchema.Component<>(RawHit.class, "title", RawHit::title), new dev.openallay.value.ValueSchema.Component<>(RawHit.class, "excerpt", RawHit::excerpt), new dev.openallay.value.ValueSchema.Component<>(RawHit.class, "reference", RawHit::reference)), arguments -> new RawHit((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
}
