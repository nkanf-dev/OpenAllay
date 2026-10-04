package dev.openallay.extension;

import java.util.regex.Pattern;

/** Reviewed CommonJS module source contributed by one registered Extension. */
public record JavascriptModuleSource(String id, String source) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public JavascriptModuleSource {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid JavaScript module ID: " + id);
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("JavaScript module source must not be blank");
        }
    }
}
