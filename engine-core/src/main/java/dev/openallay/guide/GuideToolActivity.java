package dev.openallay.guide;

import com.google.gson.JsonObject;
import java.util.List;

@dev.openallay.value.ValueType(GuideToolActivity.ValueSchemaProvider.class)
public final class GuideToolActivity {
    private final String invocationId;
    private final int index;
    private final String toolId;
    private final GuideToolStatus status;
    private final JsonObject invocationArguments;
    private final GuideToolInvocationView invocation;
    private final JsonObject normalized;
    private final List<GuideToolMessage> presentationMessages;
    private final List<GuideSource> sources;
    public GuideToolActivity(String invocationId, int index, String toolId, GuideToolStatus status, JsonObject invocationArguments, GuideToolInvocationView invocation, JsonObject normalized, List<GuideToolMessage> presentationMessages, List<GuideSource> sources) {

        if (invocationId == null || dev.openallay.util.Java8Strings.isBlank(invocationId)
                || index < 0 || toolId == null || dev.openallay.util.Java8Strings.isBlank(toolId)) {
            throw new IllegalArgumentException("tool activity identity is invalid");
        }
        java.util.Objects.requireNonNull(status, "status");
        invocationArguments =
                invocationArguments == null ? null : dev.openallay.json.JsonTrees.copy(invocationArguments);
        invocation = java.util.Objects.requireNonNull(invocation, "invocation");
        normalized = normalized == null ? null : dev.openallay.json.JsonTrees.copy(normalized);
        presentationMessages = dev.openallay.util.Java8Collections.listCopyOf(presentationMessages);
        sources = dev.openallay.util.Java8Collections.listCopyOf(sources);

        this.invocationId = invocationId;
        this.index = index;
        this.toolId = toolId;
        this.status = status;
        this.invocationArguments = invocationArguments;
        this.invocation = invocation;
        this.normalized = normalized;
        this.presentationMessages = presentationMessages;
        this.sources = sources;
    }
    public String invocationId() { return invocationId; }
    public int index() { return index; }
    public String toolId() { return toolId; }
    public GuideToolStatus status() { return status; }
    public GuideToolInvocationView invocation() { return invocation; }
    public List<GuideToolMessage> presentationMessages() { return presentationMessages; }
    public List<GuideSource> sources() { return sources; }
public GuideToolActivity(
            String invocationId,
            int index,
            String toolId,
            GuideToolStatus status,
            JsonObject invocationArguments,
            JsonObject normalized,
            List<GuideToolMessage> presentationMessages,
            List<GuideSource> sources) {
        this(
                invocationId,
                index,
                toolId,
                status,
                invocationArguments,
                GuideToolInvocationView.from(toolId, invocationArguments, normalized),
                normalized,
                presentationMessages,
                sources);
    }
public GuideToolActivity(
            String invocationId,
            int index,
            String toolId,
            GuideToolStatus status,
            JsonObject normalized,
            List<GuideToolMessage> presentationMessages,
            List<GuideSource> sources) {
        this(
                invocationId,
                index,
                toolId,
                status,
                null,
                GuideToolInvocationView.from(toolId, null, normalized),
                normalized,
                presentationMessages,
                sources);
    }
public GuideToolIntent intent() {
        return GuideToolIntent.from(toolId, invocationArguments, presentationMessages);
    }

    public JsonObject invocationArguments() {
        return invocationArguments == null ? null : dev.openallay.json.JsonTrees.copy(invocationArguments);
    }

    public JsonObject normalized() {
        return normalized == null ? null : dev.openallay.json.JsonTrees.copy(normalized);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideToolActivity)) return false;
        GuideToolActivity that = (GuideToolActivity) other;
        return java.util.Objects.equals(invocationId, that.invocationId) && index == that.index && java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(invocationArguments, that.invocationArguments) && java.util.Objects.equals(invocation, that.invocation) && java.util.Objects.equals(normalized, that.normalized) && java.util.Objects.equals(presentationMessages, that.presentationMessages) && java.util.Objects.equals(sources, that.sources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + Integer.hashCode(index);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(invocationArguments);
        hash = 31 * hash + java.util.Objects.hashCode(invocation);
        hash = 31 * hash + java.util.Objects.hashCode(normalized);
        hash = 31 * hash + java.util.Objects.hashCode(presentationMessages);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        return hash;
    }
    @Override public String toString() { return "GuideToolActivity[invocationId=" + invocationId + ", index=" + index + ", toolId=" + toolId + ", status=" + status + ", invocationArguments=" + invocationArguments + ", invocation=" + invocation + ", normalized=" + normalized + ", presentationMessages=" + presentationMessages + ", sources=" + sources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideToolActivity> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideToolActivity.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideToolActivity>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "invocationId", GuideToolActivity::invocationId), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "index", GuideToolActivity::index), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "toolId", GuideToolActivity::toolId), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "status", GuideToolActivity::status), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "invocationArguments", GuideToolActivity::invocationArguments), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "invocation", GuideToolActivity::invocation), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "normalized", GuideToolActivity::normalized), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "presentationMessages", GuideToolActivity::presentationMessages), new dev.openallay.value.ValueSchema.Component<>(GuideToolActivity.class, "sources", GuideToolActivity::sources)), arguments -> new GuideToolActivity((String) arguments[0], (Integer) arguments[1], (String) arguments[2], (GuideToolStatus) arguments[3], (JsonObject) arguments[4], (GuideToolInvocationView) arguments[5], (JsonObject) arguments[6], (List) arguments[7], (List) arguments[8]));
        }
    }
}
