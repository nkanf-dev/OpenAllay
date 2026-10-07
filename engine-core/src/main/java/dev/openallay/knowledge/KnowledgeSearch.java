package dev.openallay.knowledge;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.knowledge.search.KnowledgeSearchResult;
import java.util.List;

/** Search results and evidence captured from one atomically published knowledge generation. */
@dev.openallay.value.ValueType(KnowledgeSearch.ValueSchemaProvider.class)
public final class KnowledgeSearch {
    private final List<KnowledgeSearchResult> results;
    private final List<EvidenceMetadata> evidence;
    public KnowledgeSearch(List<KnowledgeSearchResult> results, List<EvidenceMetadata> evidence) {

        results = List.copyOf(results);
        evidence = List.copyOf(evidence);

        this.results = results;
        this.evidence = evidence;
    }
    public List<KnowledgeSearchResult> results() { return results; }
    public List<EvidenceMetadata> evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeSearch)) return false;
        KnowledgeSearch that = (KnowledgeSearch) other;
        return java.util.Objects.equals(results, that.results) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(results);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "KnowledgeSearch[results=" + results + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeSearch> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeSearch.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeSearch>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearch.class, "results", KnowledgeSearch::results), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearch.class, "evidence", KnowledgeSearch::evidence)), arguments -> new KnowledgeSearch((List) arguments[0], (List) arguments[1]));
        }
    }
}
