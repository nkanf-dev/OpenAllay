package dev.openallay.script.result;

/**
 * Closed declaration for an Extension-owned typed result view.
 *
 * <p>The renderer remains Java-owned; an Extension declaration can select only a supported
 * semantic kind and cannot inject scripts or callbacks into the player UI.
 */
public interface JavascriptResultViewProvider {
    String id();

    JavascriptSemanticKind kind();

    String summary();

    record Declaration(String id, JavascriptSemanticKind kind, String summary)
            implements JavascriptResultViewProvider {
        public Declaration {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid result view ID: " + id);
            }
            java.util.Objects.requireNonNull(kind, "kind");
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("Result view summary must not be blank");
            }
            summary = summary.strip();
        }
    }
}
