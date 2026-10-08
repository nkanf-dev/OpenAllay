package dev.openallay.tool;

import java.util.Objects;

/** Immutable registration identity retained across filtered runtime snapshots. */
@dev.openallay.value.ValueType(RegisteredTool.ValueSchemaProvider.class)
public final class RegisteredTool {
    private final String providerId;
    private final Tool<?, ?> tool;
    public RegisteredTool(String providerId, Tool<?, ?> tool) {

        if (providerId == null || dev.openallay.util.Java8Strings.isBlank(providerId)) {
            throw new IllegalArgumentException("Provider id must not be blank");
        }
        Objects.requireNonNull(tool, "tool");

        this.providerId = providerId;
        this.tool = tool;
    }
    public String providerId() { return providerId; }
    public Tool<?, ?> tool() { return tool; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredTool)) return false;
        RegisteredTool that = (RegisteredTool) other;
        return java.util.Objects.equals(providerId, that.providerId) && java.util.Objects.equals(tool, that.tool);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(providerId);
        hash = 31 * hash + java.util.Objects.hashCode(tool);
        return hash;
    }
    @Override public String toString() { return "RegisteredTool[providerId=" + providerId + ", tool=" + tool + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegisteredTool> schema() {
            return new dev.openallay.value.ValueSchema<>(RegisteredTool.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegisteredTool>>asList(new dev.openallay.value.ValueSchema.Component<>(RegisteredTool.class, "providerId", RegisteredTool::providerId), new dev.openallay.value.ValueSchema.Component<>(RegisteredTool.class, "tool", RegisteredTool::tool)), arguments -> new RegisteredTool((String) arguments[0], (Tool) arguments[1]));
        }
    }
}
