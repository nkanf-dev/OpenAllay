package dev.openallay.script.command;

/** Strict local configuration for the default-off experimental command bridge. */
public record CommandCapabilityConfig(boolean enabled) {

    public static CommandCapabilityConfig defaults() {
        return new CommandCapabilityConfig(false);
    }
}
