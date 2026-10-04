package dev.openallay.tool;

import java.util.Objects;

/** Size and lifetime of a canonical value retained outside the model transcript. */
public record ModelResultView(String handle, String type, long cardinality,
        long canonicalUtf8Bytes, boolean complete, String lifetime, String inputCoverage) {
    public ModelResultView(String handle, String type, long cardinality,
            long canonicalUtf8Bytes, boolean complete, String lifetime) {
        this(handle, type, cardinality, canonicalUtf8Bytes, complete, lifetime, "");
    }
    public ModelResultView {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lifetime, "lifetime");
        inputCoverage = inputCoverage == null ? "" : inputCoverage;
        if (cardinality < 0 || canonicalUtf8Bytes < 0) throw new IllegalArgumentException("Negative result size");
    }
}
