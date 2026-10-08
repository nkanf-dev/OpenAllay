package dev.openallay.tool;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import dev.openallay.context.ContextCapability;

@dev.openallay.value.ValueType(ToolDescriptor.ValueSchemaProvider.class)
public final class ToolDescriptor<I, O> {
    private final String id;
    private final String description;
    private final Class<I> inputType;
    private final Class<O> outputType;
    private final ToolAccess access;
    private final Set<ContextCapability> requiredContext;
    public ToolDescriptor(String id, String description, Class<I> inputType, Class<O> outputType, ToolAccess access, Set<ContextCapability> requiredContext) {

        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid tool id: " + id);
        }
        if (description == null || dev.openallay.util.Java8Strings.isBlank(description)) {
            throw new IllegalArgumentException("Tool description must not be blank");
        }
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(access, "access");
        requiredContext = dev.openallay.util.Java8Collections.setCopyOf(requiredContext);

        this.id = id;
        this.description = description;
        this.inputType = inputType;
        this.outputType = outputType;
        this.access = access;
        this.requiredContext = requiredContext;
    }
    public String id() { return id; }
    public String description() { return description; }
    public Class<I> inputType() { return inputType; }
    public Class<O> outputType() { return outputType; }
    public ToolAccess access() { return access; }
    public Set<ContextCapability> requiredContext() { return requiredContext; }
private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
public ToolDescriptor(
            String id,
            String description,
            Class<I> inputType,
            Class<O> outputType,
            ToolAccess access) {
        this(id, description, inputType, outputType, access, dev.openallay.util.Java8Collections.setOf());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolDescriptor)) return false;
        ToolDescriptor that = (ToolDescriptor) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(inputType, that.inputType) && java.util.Objects.equals(outputType, that.outputType) && java.util.Objects.equals(access, that.access) && java.util.Objects.equals(requiredContext, that.requiredContext);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(inputType);
        hash = 31 * hash + java.util.Objects.hashCode(outputType);
        hash = 31 * hash + java.util.Objects.hashCode(access);
        hash = 31 * hash + java.util.Objects.hashCode(requiredContext);
        return hash;
    }
    @Override public String toString() { return "ToolDescriptor[id=" + id + ", description=" + description + ", inputType=" + inputType + ", outputType=" + outputType + ", access=" + access + ", requiredContext=" + requiredContext + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolDescriptor> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolDescriptor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolDescriptor>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolDescriptor.class, "id", ToolDescriptor::id), new dev.openallay.value.ValueSchema.Component<>(ToolDescriptor.class, "description", ToolDescriptor::description), new dev.openallay.value.ValueSchema.Component<>(ToolDescriptor.class, "inputType", ToolDescriptor::inputType), new dev.openallay.value.ValueSchema.Component<>(ToolDescriptor.class, "outputType", ToolDescriptor::outputType), new dev.openallay.value.ValueSchema.Component<>(ToolDescriptor.class, "access", ToolDescriptor::access), new dev.openallay.value.ValueSchema.Component<>(ToolDescriptor.class, "requiredContext", ToolDescriptor::requiredContext)), arguments -> new ToolDescriptor((String) arguments[0], (String) arguments[1], (Class) arguments[2], (Class) arguments[3], (ToolAccess) arguments[4], (Set) arguments[5]));
        }
    }
}
