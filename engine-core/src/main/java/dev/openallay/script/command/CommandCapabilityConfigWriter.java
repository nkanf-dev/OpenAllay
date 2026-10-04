package dev.openallay.script.command;

/** Deterministic writer for the strict command capability document. */
public final class CommandCapabilityConfigWriter {
    public String encode(CommandCapabilityConfig config) {
        return """
                {
                  "enabled": %s
                }
                """.formatted(config.enabled());
    }
}
