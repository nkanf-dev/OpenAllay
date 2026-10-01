package dev.openallay.script;

/** Persistent local opt-in for unrestricted JavaScript/JVM interop. */
public record UnrestrictedJavascriptConfig(boolean enabled) {
    public static UnrestrictedJavascriptConfig defaults() { return new UnrestrictedJavascriptConfig(false); }
}
