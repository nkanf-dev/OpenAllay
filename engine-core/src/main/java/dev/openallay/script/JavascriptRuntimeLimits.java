package dev.openallay.script;

/**
 * Reviewable resource defaults for one model-authored Rhino execution.
 *
 * <p>Rhino is an embedded engine rather than a process boundary, so these limits reject oversized
 * programs and result graphs before they can enter the request workspace or model context.
 */
public record JavascriptRuntimeLimits(
        int maxSourceCharacters,
        int maxResultDepth,
        long maxResultNodes,
        long maxArrayLength,
        int maxObjectFields,
        int maxStringCharacters) {
    public static final JavascriptRuntimeLimits DEFAULT = new JavascriptRuntimeLimits(
            65_536,
            64,
            250_000,
            250_000,
            16_384,
            524_288);

    public JavascriptRuntimeLimits {
        if (maxSourceCharacters <= 0
                || maxResultDepth <= 0
                || maxResultNodes <= 0
                || maxArrayLength <= 0
                || maxObjectFields <= 0
                || maxStringCharacters <= 0) {
            throw new IllegalArgumentException("JavaScript runtime limits must be positive");
        }
    }
}
