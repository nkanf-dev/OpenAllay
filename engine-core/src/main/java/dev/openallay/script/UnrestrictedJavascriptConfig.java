package dev.openallay.script;

/** Persistent local opt-in for unrestricted JavaScript/JVM interop. */
@dev.openallay.value.ValueType(UnrestrictedJavascriptConfig.ValueSchemaProvider.class)
public final class UnrestrictedJavascriptConfig {
    private final boolean enabled;
    public UnrestrictedJavascriptConfig(boolean enabled) {
        this.enabled = enabled;
    }
    public boolean enabled() { return enabled; }
public static UnrestrictedJavascriptConfig defaults() { return new UnrestrictedJavascriptConfig(false); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UnrestrictedJavascriptConfig)) return false;
        UnrestrictedJavascriptConfig that = (UnrestrictedJavascriptConfig) other;
        return enabled == that.enabled;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        return hash;
    }
    @Override public String toString() { return "UnrestrictedJavascriptConfig[enabled=" + enabled + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UnrestrictedJavascriptConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(UnrestrictedJavascriptConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UnrestrictedJavascriptConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(UnrestrictedJavascriptConfig.class, "enabled", UnrestrictedJavascriptConfig::enabled)), arguments -> new UnrestrictedJavascriptConfig((Boolean) arguments[0]));
        }
    }
}
