package dev.openallay.model;

import java.util.List;

@dev.openallay.value.ValueType(ModelTurn.ValueSchemaProvider.class)
public final class ModelTurn {
    private final String providerId;
    private final String model;
    private final List<ModelContent> content;
    private final String stopReason;
    private final ModelUsage usage;
    public ModelTurn(String providerId, String model, List<ModelContent> content, String stopReason, ModelUsage usage) {

        if (providerId == null || dev.openallay.util.Java8Strings.isBlank(providerId) || model == null || dev.openallay.util.Java8Strings.isBlank(model)) {
            throw new IllegalArgumentException("Provider and model are required");
        }
        content = dev.openallay.util.Java8Collections.listCopyOf(content);
        content.forEach(ModelContent::requireKnown);
        if (stopReason == null || dev.openallay.util.Java8Strings.isBlank(stopReason)) {
            throw new IllegalArgumentException("Stop reason is required");
        }

        this.providerId = providerId;
        this.model = model;
        this.content = content;
        this.stopReason = stopReason;
        this.usage = usage;
    }
    public String providerId() { return providerId; }
    public String model() { return model; }
    public List<ModelContent> content() { return content; }
    public String stopReason() { return stopReason; }
    public ModelUsage usage() { return usage; }
public List<ModelContent.ToolUse> toolUses() {
        return dev.openallay.util.Java8Collections.toList(content.stream()
                .filter(ModelContent.ToolUse.class::isInstance)
                .map(ModelContent.ToolUse.class::cast));
    }
public String text() {
        return content.stream()
                .filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text)
                .reduce("", String::concat);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelTurn)) return false;
        ModelTurn that = (ModelTurn) other;
        return java.util.Objects.equals(providerId, that.providerId) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(content, that.content) && java.util.Objects.equals(stopReason, that.stopReason) && java.util.Objects.equals(usage, that.usage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(providerId);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        hash = 31 * hash + java.util.Objects.hashCode(stopReason);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        return hash;
    }
    @Override public String toString() { return "ModelTurn[providerId=" + providerId + ", model=" + model + ", content=" + content + ", stopReason=" + stopReason + ", usage=" + usage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelTurn> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelTurn.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelTurn>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelTurn.class, "providerId", ModelTurn::providerId), new dev.openallay.value.ValueSchema.Component<>(ModelTurn.class, "model", ModelTurn::model), new dev.openallay.value.ValueSchema.Component<>(ModelTurn.class, "content", ModelTurn::content), new dev.openallay.value.ValueSchema.Component<>(ModelTurn.class, "stopReason", ModelTurn::stopReason), new dev.openallay.value.ValueSchema.Component<>(ModelTurn.class, "usage", ModelTurn::usage)), arguments -> new ModelTurn((String) arguments[0], (String) arguments[1], (List) arguments[2], (String) arguments[3], (ModelUsage) arguments[4]));
        }
    }
}
