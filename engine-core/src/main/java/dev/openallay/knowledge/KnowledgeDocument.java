package dev.openallay.knowledge;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@dev.openallay.value.ValueType(KnowledgeDocument.ValueSchemaProvider.class)
public final class KnowledgeDocument {
    private final String sourceId;
    private final String documentId;
    private final KnowledgeKind kind;
    private final String title;
    private final String body;
    private final String namespace;
    private final Set<String> itemIds;
    private final Set<String> recipeIds;
    private final String structureRef;
    private final boolean visible;
    private final String provenance;
    private final EvidenceMetadata evidence;
    public KnowledgeDocument(String sourceId, String documentId, KnowledgeKind kind, String title, String body, String namespace, Set<String> itemIds, Set<String> recipeIds, String structureRef, boolean visible, String provenance, EvidenceMetadata evidence) {

        require(sourceId, "sourceId");
        require(documentId, "documentId");
        java.util.Objects.requireNonNull(kind, "kind");
        require(title, "title");
        body = body == null ? "" : body;
        namespace = namespace == null ? "" : namespace;
        itemIds = dev.openallay.util.Java8Collections.setCopyOf(itemIds);
        recipeIds = dev.openallay.util.Java8Collections.setCopyOf(recipeIds);
        require(provenance, "provenance");
        java.util.Objects.requireNonNull(evidence, "evidence");

        this.sourceId = sourceId;
        this.documentId = documentId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.namespace = namespace;
        this.itemIds = itemIds;
        this.recipeIds = recipeIds;
        this.structureRef = structureRef;
        this.visible = visible;
        this.provenance = provenance;
        this.evidence = evidence;
    }
    public String sourceId() { return sourceId; }
    public String documentId() { return documentId; }
    public KnowledgeKind kind() { return kind; }
    public String title() { return title; }
    public String body() { return body; }
    public String namespace() { return namespace; }
    public Set<String> itemIds() { return itemIds; }
    public Set<String> recipeIds() { return recipeIds; }
    public String structureRef() { return structureRef; }
    public boolean visible() { return visible; }
    public String provenance() { return provenance; }
    public EvidenceMetadata evidence() { return evidence; }
public KnowledgeDocument(
            String sourceId,
            String documentId,
            KnowledgeKind kind,
            String title,
            String body,
            String namespace,
            Set<String> itemIds,
            Set<String> recipeIds,
            String structureRef,
            boolean visible,
            String provenance) {
        this(
                sourceId,
                documentId,
                kind,
                title,
                body,
                namespace,
                itemIds,
                recipeIds,
                structureRef,
                visible,
                provenance,
                new EvidenceMetadata(
                        DataAuthority.DETERMINISTIC_TEST,
                        DataCompleteness.COMPLETE,
                        Instant.EPOCH,
                        "openallay:test_fixture",
                        "openallay:knowledge_fixture",
                        "test",
                        "common-test",
                        dev.openallay.util.Java8Collections.mapOf("openallay:fixture_provenance", provenance)));
    }
public String key() {
        return sourceId + ":" + documentId;
    }
private static void require(String value, String field) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeDocument)) return false;
        KnowledgeDocument that = (KnowledgeDocument) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(documentId, that.documentId) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(body, that.body) && java.util.Objects.equals(namespace, that.namespace) && java.util.Objects.equals(itemIds, that.itemIds) && java.util.Objects.equals(recipeIds, that.recipeIds) && java.util.Objects.equals(structureRef, that.structureRef) && visible == that.visible && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(documentId);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        hash = 31 * hash + java.util.Objects.hashCode(namespace);
        hash = 31 * hash + java.util.Objects.hashCode(itemIds);
        hash = 31 * hash + java.util.Objects.hashCode(recipeIds);
        hash = 31 * hash + java.util.Objects.hashCode(structureRef);
        hash = 31 * hash + Boolean.hashCode(visible);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "KnowledgeDocument[sourceId=" + sourceId + ", documentId=" + documentId + ", kind=" + kind + ", title=" + title + ", body=" + body + ", namespace=" + namespace + ", itemIds=" + itemIds + ", recipeIds=" + recipeIds + ", structureRef=" + structureRef + ", visible=" + visible + ", provenance=" + provenance + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KnowledgeDocument> schema() {
            return new dev.openallay.value.ValueSchema<>(KnowledgeDocument.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KnowledgeDocument>>asList(new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "sourceId", KnowledgeDocument::sourceId), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "documentId", KnowledgeDocument::documentId), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "kind", KnowledgeDocument::kind), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "title", KnowledgeDocument::title), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "body", KnowledgeDocument::body), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "namespace", KnowledgeDocument::namespace), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "itemIds", KnowledgeDocument::itemIds), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "recipeIds", KnowledgeDocument::recipeIds), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "structureRef", KnowledgeDocument::structureRef), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "visible", KnowledgeDocument::visible), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "provenance", KnowledgeDocument::provenance), new dev.openallay.value.ValueSchema.Component<>(KnowledgeDocument.class, "evidence", KnowledgeDocument::evidence)), arguments -> new KnowledgeDocument((String) arguments[0], (String) arguments[1], (KnowledgeKind) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (Set) arguments[6], (Set) arguments[7], (String) arguments[8], (Boolean) arguments[9], (String) arguments[10], (EvidenceMetadata) arguments[11]));
        }
    }
}
