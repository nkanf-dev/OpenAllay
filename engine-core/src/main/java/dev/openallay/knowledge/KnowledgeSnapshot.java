package dev.openallay.knowledge;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@dev.openallay.value.ValueType(KnowledgeSnapshot.ValueSchemaProvider.class)
public final class KnowledgeSnapshot {
    private final List<KnowledgeDocument> documents;
    private final Instant createdAt;
    private final List<EvidenceMetadata> evidence;
    public KnowledgeSnapshot(List<KnowledgeDocument> documents, Instant createdAt, List<EvidenceMetadata> evidence) {

        documents = List.copyOf(documents);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        evidence = List.copyOf(evidence);
        if (evidence.isEmpty()) {
            evidence = List.of(emptyEvidence(createdAt));
        }

        this.documents = documents;
        this.createdAt = createdAt;
        this.evidence = evidence;
    }
    public List<KnowledgeDocument> documents() { return documents; }
    public Instant createdAt() { return createdAt; }
    public List<EvidenceMetadata> evidence() { return evidence; }
public KnowledgeSnapshot(List<KnowledgeDocument> documents, Instant createdAt) {
        this(
                documents,
                createdAt,
                documents.stream().map(KnowledgeDocument::evidence).distinct().toList());
    }
public static KnowledgeSnapshot empty() {
        return new KnowledgeSnapshot(List.of(), Instant.EPOCH, List.of(emptyEvidence(Instant.EPOCH)));
    }
private static EvidenceMetadata emptyEvidence(Instant createdAt) {
        return new EvidenceMetadata(
                DataAuthority.INTEGRATION_API,
                DataCompleteness.UNKNOWN,
                createdAt,
                "openallay:knowledge_registry",
                "openallay:empty_snapshot",
                "unknown",
                "unknown",
                Map.of("openallay:state", "not_loaded"));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeSnapshot)) return false;
        KnowledgeSnapshot that = (KnowledgeSnapshot) other;
        return java.util.Objects.equals(documents, that.documents) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(documents);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "KnowledgeSnapshot[documents=" + documents + ", createdAt=" + createdAt + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeSnapshot.class, "documents", KnowledgeSnapshot::documents), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSnapshot.class, "createdAt", KnowledgeSnapshot::createdAt), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSnapshot.class, "evidence", KnowledgeSnapshot::evidence)), arguments -> new KnowledgeSnapshot((List) arguments[0], (Instant) arguments[1], (List) arguments[2]));
        }
    }
}
