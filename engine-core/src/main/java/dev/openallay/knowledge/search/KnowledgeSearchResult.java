package dev.openallay.knowledge.search;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.knowledge.KnowledgeKind;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@dev.openallay.value.ValueType(KnowledgeSearchResult.ValueSchemaProvider.class)
public final class KnowledgeSearchResult {
    private final String sourceId;
    private final String documentId;
    private final String sectionId;
    private final String sectionTitle;
    private final KnowledgeKind kind;
    private final String title;
    private final String excerpt;
    private final int score;
    private final Set<String> matchedFields;
    private final String provenance;
    private final EvidenceMetadata evidence;
    public KnowledgeSearchResult(String sourceId, String documentId, String sectionId, String sectionTitle, KnowledgeKind kind, String title, String excerpt, int score, Set<String> matchedFields, String provenance, EvidenceMetadata evidence) {

        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (sectionId == null || sectionId.isBlank()) {
            throw new IllegalArgumentException("sectionId must not be blank");
        }
        sectionTitle = sectionTitle == null ? "" : sectionTitle;
        matchedFields = Set.copyOf(matchedFields);
        java.util.Objects.requireNonNull(evidence, "evidence");

        this.sourceId = sourceId;
        this.documentId = documentId;
        this.sectionId = sectionId;
        this.sectionTitle = sectionTitle;
        this.kind = kind;
        this.title = title;
        this.excerpt = excerpt;
        this.score = score;
        this.matchedFields = matchedFields;
        this.provenance = provenance;
        this.evidence = evidence;
    }
    public String sourceId() { return sourceId; }
    public String documentId() { return documentId; }
    public String sectionId() { return sectionId; }
    public String sectionTitle() { return sectionTitle; }
    public KnowledgeKind kind() { return kind; }
    public String title() { return title; }
    public String excerpt() { return excerpt; }
    public int score() { return score; }
    public Set<String> matchedFields() { return matchedFields; }
    public String provenance() { return provenance; }
    public EvidenceMetadata evidence() { return evidence; }
public String documentReference() {
        return "openallay-knowledge:" + referencePart(sourceId) + "/" + referencePart(documentId);
    }
public String sectionReference() {
        return documentReference() + "#" + referencePart(sectionId);
    }
private static String referencePart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeSearchResult)) return false;
        KnowledgeSearchResult that = (KnowledgeSearchResult) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(documentId, that.documentId) && java.util.Objects.equals(sectionId, that.sectionId) && java.util.Objects.equals(sectionTitle, that.sectionTitle) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(excerpt, that.excerpt) && score == that.score && java.util.Objects.equals(matchedFields, that.matchedFields) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(documentId);
        hash = 31 * hash + java.util.Objects.hashCode(sectionId);
        hash = 31 * hash + java.util.Objects.hashCode(sectionTitle);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(excerpt);
        hash = 31 * hash + Integer.hashCode(score);
        hash = 31 * hash + java.util.Objects.hashCode(matchedFields);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "KnowledgeSearchResult[sourceId=" + sourceId + ", documentId=" + documentId + ", sectionId=" + sectionId + ", sectionTitle=" + sectionTitle + ", kind=" + kind + ", title=" + title + ", excerpt=" + excerpt + ", score=" + score + ", matchedFields=" + matchedFields + ", provenance=" + provenance + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeSearchResult> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeSearchResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeSearchResult>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "sourceId", KnowledgeSearchResult::sourceId), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "documentId", KnowledgeSearchResult::documentId), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "sectionId", KnowledgeSearchResult::sectionId), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "sectionTitle", KnowledgeSearchResult::sectionTitle), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "kind", KnowledgeSearchResult::kind), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "title", KnowledgeSearchResult::title), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "excerpt", KnowledgeSearchResult::excerpt), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "score", KnowledgeSearchResult::score), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "matchedFields", KnowledgeSearchResult::matchedFields), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "provenance", KnowledgeSearchResult::provenance), new dev.openallay.value.ValueSchema.Component<>(KnowledgeSearchResult.class, "evidence", KnowledgeSearchResult::evidence)), arguments -> new KnowledgeSearchResult((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (KnowledgeKind) arguments[4], (String) arguments[5], (String) arguments[6], (Integer) arguments[7], (Set) arguments[8], (String) arguments[9], (EvidenceMetadata) arguments[10]));
        }
    }
}
