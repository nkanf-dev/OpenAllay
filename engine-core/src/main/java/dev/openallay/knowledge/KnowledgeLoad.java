package dev.openallay.knowledge;

import dev.openallay.context.EvidenceMetadata;
import java.util.List;

@dev.openallay.value.ValueType(KnowledgeLoad.ValueSchemaProvider.class)
public final class KnowledgeLoad {
    private final List<KnowledgeDocument> documents;
    private final List<KnowledgeDiagnostic> diagnostics;
    private final List<EvidenceMetadata> evidence;
    public KnowledgeLoad(List<KnowledgeDocument> documents, List<KnowledgeDiagnostic> diagnostics, List<EvidenceMetadata> evidence) {

        documents = List.copyOf(documents);
        diagnostics = List.copyOf(diagnostics);
        evidence = List.copyOf(evidence);

        this.documents = documents;
        this.diagnostics = diagnostics;
        this.evidence = evidence;
    }
    public List<KnowledgeDocument> documents() { return documents; }
    public List<KnowledgeDiagnostic> diagnostics() { return diagnostics; }
    public List<EvidenceMetadata> evidence() { return evidence; }
public KnowledgeLoad(
            List<KnowledgeDocument> documents, List<KnowledgeDiagnostic> diagnostics) {
        this(
                documents,
                diagnostics,
                documents.stream().map(KnowledgeDocument::evidence).distinct().toList());
    }
public static KnowledgeLoad of(List<KnowledgeDocument> documents) {
        return new KnowledgeLoad(
                documents,
                List.of(),
                documents.stream().map(KnowledgeDocument::evidence).distinct().toList());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeLoad)) return false;
        KnowledgeLoad that = (KnowledgeLoad) other;
        return java.util.Objects.equals(documents, that.documents) && java.util.Objects.equals(diagnostics, that.diagnostics) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(documents);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "KnowledgeLoad[documents=" + documents + ", diagnostics=" + diagnostics + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeLoad> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeLoad.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeLoad>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeLoad.class, "documents", KnowledgeLoad::documents), new dev.openallay.value.ValueSchema.Component<>(KnowledgeLoad.class, "diagnostics", KnowledgeLoad::diagnostics), new dev.openallay.value.ValueSchema.Component<>(KnowledgeLoad.class, "evidence", KnowledgeLoad::evidence)), arguments -> new KnowledgeLoad((List) arguments[0], (List) arguments[1], (List) arguments[2]));
        }
    }
}
