package dev.openallay.guide.semantic;

import java.util.List;

/** Immutable player-visible semantic content with exact text fallback. */
public record SemanticDocument(
        List<SemanticBlock> blocks,
        String fallbackText,
        List<SemanticDiagnostic> diagnostics) {
    public SemanticDocument {
        blocks = List.copyOf(blocks);
        diagnostics = List.copyOf(diagnostics);
        String expected = SemanticPlainText.render(blocks);
        if (!expected.equals(fallbackText)) {
            throw new IllegalArgumentException("semantic fallback does not match document");
        }
    }

    public static SemanticDocument of(
            List<SemanticBlock> blocks, List<SemanticDiagnostic> diagnostics) {
        List<SemanticBlock> copied = List.copyOf(blocks);
        return new SemanticDocument(
                copied, SemanticPlainText.render(copied), diagnostics);
    }

    public static SemanticDocument empty() {
        return of(List.of(), List.of());
    }
}
