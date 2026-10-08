package dev.openallay.integration.patchouli;

import dev.openallay.knowledge.KnowledgeDiagnostic;
import dev.openallay.knowledge.KnowledgeDocument;
import java.util.List;
import java.util.Map;

@dev.openallay.value.ValueType(PatchouliParseResult.ValueSchemaProvider.class)
public final class PatchouliParseResult {
    private final List<KnowledgeDocument> documents;
    private final Map<String, PatchouliMultiblock> multiblocks;
    private final List<KnowledgeDiagnostic> diagnostics;
    public PatchouliParseResult(List<KnowledgeDocument> documents, Map<String, PatchouliMultiblock> multiblocks, List<KnowledgeDiagnostic> diagnostics) {

        documents = dev.openallay.util.Java8Collections.listCopyOf(documents);
        multiblocks = dev.openallay.util.Java8Collections.mapCopyOf(multiblocks);
        diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.documents = documents;
        this.multiblocks = multiblocks;
        this.diagnostics = diagnostics;
    }
    public List<KnowledgeDocument> documents() { return documents; }
    public Map<String, PatchouliMultiblock> multiblocks() { return multiblocks; }
    public List<KnowledgeDiagnostic> diagnostics() { return diagnostics; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PatchouliParseResult)) return false;
        PatchouliParseResult that = (PatchouliParseResult) other;
        return java.util.Objects.equals(documents, that.documents) && java.util.Objects.equals(multiblocks, that.multiblocks) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(documents);
        hash = 31 * hash + java.util.Objects.hashCode(multiblocks);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "PatchouliParseResult[documents=" + documents + ", multiblocks=" + multiblocks + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PatchouliParseResult> schema() {
            return new dev.openallay.value.ValueSchema<>(PatchouliParseResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PatchouliParseResult>>asList(new dev.openallay.value.ValueSchema.Component<>(PatchouliParseResult.class, "documents", PatchouliParseResult::documents), new dev.openallay.value.ValueSchema.Component<>(PatchouliParseResult.class, "multiblocks", PatchouliParseResult::multiblocks), new dev.openallay.value.ValueSchema.Component<>(PatchouliParseResult.class, "diagnostics", PatchouliParseResult::diagnostics)), arguments -> new PatchouliParseResult((List) arguments[0], (Map) arguments[1], (List) arguments[2]));
        }
    }
}
