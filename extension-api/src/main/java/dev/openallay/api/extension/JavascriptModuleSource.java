package dev.openallay.api.extension;

import java.util.Objects;

/** Reviewed CommonJS module source. Source text is preserved exactly, not evaluated by the SDK. */
public final class JavascriptModuleSource {
    private final String id;
    private final String source;

    public JavascriptModuleSource(String id, String source) {
        this.id = ApiValidation.id(id, "JavaScript module ID");
        this.source = ApiValidation.text(source, "JavaScript module source");
    }

    public String id() { return id; }
    public String source() { return source; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptModuleSource)) return false;
        JavascriptModuleSource that = (JavascriptModuleSource) other;
        return Objects.equals(id, that.id) &&
                Objects.equals(source, that.source);
    }
    @Override public int hashCode() { return Objects.hash(id, source); }
}
