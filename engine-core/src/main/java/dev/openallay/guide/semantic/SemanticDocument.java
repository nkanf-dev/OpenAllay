package dev.openallay.guide.semantic;

import java.util.List;

/** Immutable player-visible semantic content with exact text fallback. */
@dev.openallay.value.ValueType(SemanticDocument.ValueSchemaProvider.class)
public final class SemanticDocument {
    private final List<SemanticBlock> blocks;
    private final String fallbackText;
    private final List<SemanticDiagnostic> diagnostics;
    public SemanticDocument(List<SemanticBlock> blocks, String fallbackText, List<SemanticDiagnostic> diagnostics) {

        blocks = List.copyOf(blocks);
        diagnostics = List.copyOf(diagnostics);
        String expected = SemanticPlainText.render(blocks);
        if (!expected.equals(fallbackText)) {
            throw new IllegalArgumentException("semantic fallback does not match document");
        }

        this.blocks = blocks;
        this.fallbackText = fallbackText;
        this.diagnostics = diagnostics;
    }
    public List<SemanticBlock> blocks() { return blocks; }
    public String fallbackText() { return fallbackText; }
    public List<SemanticDiagnostic> diagnostics() { return diagnostics; }
public static SemanticDocument of(
            List<SemanticBlock> blocks, List<SemanticDiagnostic> diagnostics) {
        List<SemanticBlock> copied = List.copyOf(blocks);
        return new SemanticDocument(
                copied, SemanticPlainText.render(copied), diagnostics);
    }
public static SemanticDocument empty() {
        return of(List.of(), List.of());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SemanticDocument)) return false;
        SemanticDocument that = (SemanticDocument) other;
        return java.util.Objects.equals(blocks, that.blocks) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(blocks);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "SemanticDocument[blocks=" + blocks + ", fallbackText=" + fallbackText + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SemanticDocument> schema() {
            return new dev.openallay.value.ValueSchema<>(SemanticDocument.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SemanticDocument>>asList(new dev.openallay.value.ValueSchema.Component<>(SemanticDocument.class, "blocks", SemanticDocument::blocks), new dev.openallay.value.ValueSchema.Component<>(SemanticDocument.class, "fallbackText", SemanticDocument::fallbackText), new dev.openallay.value.ValueSchema.Component<>(SemanticDocument.class, "diagnostics", SemanticDocument::diagnostics)), arguments -> new SemanticDocument((List) arguments[0], (String) arguments[1], (List) arguments[2]));
        }
    }
}
