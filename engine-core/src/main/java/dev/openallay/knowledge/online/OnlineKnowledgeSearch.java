package dev.openallay.knowledge.online;

import java.util.List;

@dev.openallay.value.ValueType(OnlineKnowledgeSearch.ValueSchemaProvider.class)
public final class OnlineKnowledgeSearch {
    private final List<OnlineKnowledgeHit> hits;
    private final List<OnlineKnowledgeDiagnostic> diagnostics;
    public OnlineKnowledgeSearch(List<OnlineKnowledgeHit> hits, List<OnlineKnowledgeDiagnostic> diagnostics) {

        hits = List.copyOf(hits);
        diagnostics = List.copyOf(diagnostics);

        this.hits = hits;
        this.diagnostics = diagnostics;
    }
    public List<OnlineKnowledgeHit> hits() { return hits; }
    public List<OnlineKnowledgeDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnlineKnowledgeSearch)) return false;
        OnlineKnowledgeSearch that = (OnlineKnowledgeSearch) other;
        return java.util.Objects.equals(hits, that.hits) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(hits);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "OnlineKnowledgeSearch[hits=" + hits + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OnlineKnowledgeSearch> schema() {
            return new dev.openallay.value.ValueSchema<>(OnlineKnowledgeSearch.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OnlineKnowledgeSearch>>asList(new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeSearch.class, "hits", OnlineKnowledgeSearch::hits), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeSearch.class, "diagnostics", OnlineKnowledgeSearch::diagnostics)), arguments -> new OnlineKnowledgeSearch((List) arguments[0], (List) arguments[1]));
        }
    }
}
