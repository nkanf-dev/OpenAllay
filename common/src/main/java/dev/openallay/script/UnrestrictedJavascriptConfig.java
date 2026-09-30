package dev.openallay.script;

/** Persistent local opt-in for unrestricted JavaScript/JVM interop. */
public record UnrestrictedJavascriptConfig(int schemaVersion, boolean enabled) {
    public static final int SCHEMA_VERSION = 1;
    public UnrestrictedJavascriptConfig {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported unrestricted JavaScript schema");
    }
    public static UnrestrictedJavascriptConfig defaults() { return new UnrestrictedJavascriptConfig(SCHEMA_VERSION, false); }
}
