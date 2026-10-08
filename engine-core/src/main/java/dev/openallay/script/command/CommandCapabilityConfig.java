package dev.openallay.script.command;

/** Strict local configuration for the default-off experimental command bridge. */
@dev.openallay.value.ValueType(CommandCapabilityConfig.ValueSchemaProvider.class)
public final class CommandCapabilityConfig {
    private final boolean enabled;
    public CommandCapabilityConfig(boolean enabled) {
        this.enabled = enabled;
    }
    public boolean enabled() { return enabled; }
public static CommandCapabilityConfig defaults() {
        return new CommandCapabilityConfig(false);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandCapabilityConfig)) return false;
        CommandCapabilityConfig that = (CommandCapabilityConfig) other;
        return enabled == that.enabled;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        return hash;
    }
    @Override public String toString() { return "CommandCapabilityConfig[enabled=" + enabled + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CommandCapabilityConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(CommandCapabilityConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CommandCapabilityConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(CommandCapabilityConfig.class, "enabled", CommandCapabilityConfig::enabled)), arguments -> new CommandCapabilityConfig((Boolean) arguments[0]));
        }
    }
}
