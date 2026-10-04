package dev.openallay.api.extension;

import java.util.Objects;

/** Closed semantic declaration; the core owns rendering, not Extension scripts or callbacks. */
public final class ResultViewDeclaration {
    private final String id;
    private final Kind kind;
    private final String summary;

    public ResultViewDeclaration(String id, Kind kind, String summary) {
        this.id = ApiValidation.id(id, "result view ID");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.summary = ApiValidation.text(summary, "summary");
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String summary() { return summary; }

    public enum Kind { RECIPE, ITEM, TABLE, KEY_VALUE, SCALAR, GENERIC }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResultViewDeclaration)) return false;
        ResultViewDeclaration that = (ResultViewDeclaration) other;
        return Objects.equals(id, that.id) &&
                Objects.equals(kind, that.kind) &&
                Objects.equals(summary, that.summary);
    }
    @Override public int hashCode() { return Objects.hash(id, kind, summary); }
}
