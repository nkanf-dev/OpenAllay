package dev.openallay.knowledge.online;

import dev.openallay.context.EvidenceMetadata;

/** One partial public-documentation search hit from a fixed registered origin. */
@dev.openallay.value.ValueType(OnlineKnowledgeHit.ValueSchemaProvider.class)
public final class OnlineKnowledgeHit {
    private final String sourceId;
    private final String title;
    private final String excerpt;
    private final String reference;
    private final EvidenceMetadata evidence;
    public OnlineKnowledgeHit(String sourceId, String title, String excerpt, String reference, EvidenceMetadata evidence) {

        if (sourceId == null || dev.openallay.util.Java8Strings.isBlank(sourceId)
                || title == null || dev.openallay.util.Java8Strings.isBlank(title)
                || excerpt == null
                || reference == null || dev.openallay.util.Java8Strings.isBlank(reference)) {
            throw new IllegalArgumentException("invalid online knowledge hit");
        }
        java.util.Objects.requireNonNull(evidence, "evidence");

        this.sourceId = sourceId;
        this.title = title;
        this.excerpt = excerpt;
        this.reference = reference;
        this.evidence = evidence;
    }
    public String sourceId() { return sourceId; }
    public String title() { return title; }
    public String excerpt() { return excerpt; }
    public String reference() { return reference; }
    public EvidenceMetadata evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnlineKnowledgeHit)) return false;
        OnlineKnowledgeHit that = (OnlineKnowledgeHit) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(excerpt, that.excerpt) && java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(excerpt);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "OnlineKnowledgeHit[sourceId=" + sourceId + ", title=" + title + ", excerpt=" + excerpt + ", reference=" + reference + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OnlineKnowledgeHit> schema() {
            return new dev.openallay.value.ValueSchema<>(OnlineKnowledgeHit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OnlineKnowledgeHit>>asList(new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeHit.class, "sourceId", OnlineKnowledgeHit::sourceId), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeHit.class, "title", OnlineKnowledgeHit::title), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeHit.class, "excerpt", OnlineKnowledgeHit::excerpt), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeHit.class, "reference", OnlineKnowledgeHit::reference), new dev.openallay.value.ValueSchema.Component<>(OnlineKnowledgeHit.class, "evidence", OnlineKnowledgeHit::evidence)), arguments -> new OnlineKnowledgeHit((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (EvidenceMetadata) arguments[4]));
        }
    }
}
