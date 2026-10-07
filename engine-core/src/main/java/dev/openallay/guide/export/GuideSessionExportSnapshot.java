package dev.openallay.guide.export;

import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImagePayloadResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Closed, credential-free, point-in-time projection for player-initiated export. */
@dev.openallay.value.ValueType(GuideSessionExportSnapshot.ValueSchemaProvider.class)
public final class GuideSessionExportSnapshot implements AutoCloseable {
    private final String sessionId;
    private final List<Request> requests;
    private final Instant capturedAt;
    private final ImagePayloadResolver imagePayloadResolver;
    public GuideSessionExportSnapshot(String sessionId, List<Request> requests, Instant capturedAt, ImagePayloadResolver imagePayloadResolver) {

        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid export session ID");
        }
        requests = dev.openallay.util.Java8Collections.listCopyOf(requests);
        java.util.Objects.requireNonNull(capturedAt, "capturedAt");
        java.util.Objects.requireNonNull(imagePayloadResolver, "imagePayloadResolver");

        this.sessionId = sessionId;
        this.requests = requests;
        this.capturedAt = capturedAt;
        this.imagePayloadResolver = imagePayloadResolver;
    }
    public String sessionId() { return sessionId; }
    public List<Request> requests() { return requests; }
    public Instant capturedAt() { return capturedAt; }
    public ImagePayloadResolver imagePayloadResolver() { return imagePayloadResolver; }
public GuideSessionExportSnapshot(String sessionId, List<Request> requests, Instant capturedAt) {
        this(sessionId, requests, capturedAt, ImagePayloadResolver.unavailable());
    }
public GuideSessionExportSnapshot withImagePayloadResolver(ImagePayloadResolver resolver) {
        return new GuideSessionExportSnapshot(sessionId, requests, capturedAt, resolver);
    }
@Override
    public void close() {
        imagePayloadResolver.close();
    }
@dev.openallay.value.ValueType(Request.ValueSchemaProvider.class)
public static final class Request {
    private final UUID requestId;
    private final Instant createdAt;
    private final GuideRequestStatus status;
    private final String userMessage;
    private final List<Entry> timeline;
    private final List<ModelMessage> originalContext;
    private final GuideFailure failure;
    public Request(UUID requestId, Instant createdAt, GuideRequestStatus status, String userMessage, List<Entry> timeline, List<ModelMessage> originalContext, GuideFailure failure) {

            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(createdAt, "createdAt");
            java.util.Objects.requireNonNull(status, "status");
            if (userMessage == null || dev.openallay.util.Java8Strings.isBlank(userMessage)) {
                throw new IllegalArgumentException("export user message is blank");
            }
            timeline = dev.openallay.util.Java8Collections.listCopyOf(timeline);
            originalContext = dev.openallay.util.Java8Collections.listCopyOf(originalContext);
            if (originalContext.stream().flatMap(message -> message.content().stream())
                    .anyMatch(ModelContent.Reasoning.class::isInstance)) {
                throw new IllegalArgumentException("export cannot contain reasoning");
            }

            timeline.forEach(Entry::requireKnown);
        this.requestId = requestId;
        this.createdAt = createdAt;
        this.status = status;
        this.userMessage = userMessage;
        this.timeline = timeline;
        this.originalContext = originalContext;
        this.failure = failure;
    }
    public UUID requestId() { return requestId; }
    public Instant createdAt() { return createdAt; }
    public GuideRequestStatus status() { return status; }
    public String userMessage() { return userMessage; }
    public List<Entry> timeline() { return timeline; }
    public List<ModelMessage> originalContext() { return originalContext; }
    public GuideFailure failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Request)) return false;
        Request that = (Request) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(userMessage, that.userMessage) && java.util.Objects.equals(timeline, that.timeline) && java.util.Objects.equals(originalContext, that.originalContext) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(userMessage);
        hash = 31 * hash + java.util.Objects.hashCode(timeline);
        hash = 31 * hash + java.util.Objects.hashCode(originalContext);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Request[requestId=" + requestId + ", createdAt=" + createdAt + ", status=" + status + ", userMessage=" + userMessage + ", timeline=" + timeline + ", originalContext=" + originalContext + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Request> schema() {
            return new dev.openallay.value.ValueSchema<>(Request.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Request>>asList(new dev.openallay.value.ValueSchema.Component<>(Request.class, "requestId", Request::requestId), new dev.openallay.value.ValueSchema.Component<>(Request.class, "createdAt", Request::createdAt), new dev.openallay.value.ValueSchema.Component<>(Request.class, "status", Request::status), new dev.openallay.value.ValueSchema.Component<>(Request.class, "userMessage", Request::userMessage), new dev.openallay.value.ValueSchema.Component<>(Request.class, "timeline", Request::timeline), new dev.openallay.value.ValueSchema.Component<>(Request.class, "originalContext", Request::originalContext), new dev.openallay.value.ValueSchema.Component<>(Request.class, "failure", Request::failure)), arguments -> new Request((UUID) arguments[0], (Instant) arguments[1], (GuideRequestStatus) arguments[2], (String) arguments[3], (List) arguments[4], (List) arguments[5], (GuideFailure) arguments[6]));
        }
    }
}
public interface Entry {
    /** Runtime admission for the exact canonical closed variant family. */
    static Entry requireKnown(Entry value) {
        java.util.Objects.requireNonNull(value, "value");
        Class<?> type = value.getClass();
        if (type == dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.User.class || type == dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.Assistant.class || type == dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.Tool.class) return value;
        throw new IncompatibleClassChangeError("Unknown Entry subtype");
    }

        @dev.openallay.value.ValueType(User.ValueSchemaProvider.class)
public static final class User implements Entry {
    private final UUID messageId;
    private final String text;
    public User(UUID messageId, String text) {

                java.util.Objects.requireNonNull(messageId, "messageId");
                java.util.Objects.requireNonNull(text, "text");

        this.messageId = messageId;
        this.text = text;
    }
    public UUID messageId() { return messageId; }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof User)) return false;
        User that = (User) other;
        return java.util.Objects.equals(messageId, that.messageId) && java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "User[messageId=" + messageId + ", text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<User> schema() {
            return new dev.openallay.value.ValueSchema<>(User.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<User>>asList(new dev.openallay.value.ValueSchema.Component<>(User.class, "messageId", User::messageId), new dev.openallay.value.ValueSchema.Component<>(User.class, "text", User::text)), arguments -> new User((UUID) arguments[0], (String) arguments[1]));
        }
    }
}

        @dev.openallay.value.ValueType(Assistant.ValueSchemaProvider.class)
public static final class Assistant implements Entry {
    private final String text;
    private final boolean streaming;
    public Assistant(String text, boolean streaming) {
 text = text == null ? "" : text;
        this.text = text;
        this.streaming = streaming;
    }
    public String text() { return text; }
    public boolean streaming() { return streaming; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Assistant)) return false;
        Assistant that = (Assistant) other;
        return java.util.Objects.equals(text, that.text) && streaming == that.streaming;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + Boolean.hashCode(streaming);
        return hash;
    }
    @Override public String toString() { return "Assistant[text=" + text + ", streaming=" + streaming + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Assistant> schema() {
            return new dev.openallay.value.ValueSchema<>(Assistant.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Assistant>>asList(new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "text", Assistant::text), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "streaming", Assistant::streaming)), arguments -> new Assistant((String) arguments[0], (Boolean) arguments[1]));
        }
    }
}

        @dev.openallay.value.ValueType(Tool.ValueSchemaProvider.class)
public static final class Tool implements Entry {
    private final String invocationId;
    private final String toolId;
    private final GuideToolStatus status;
    public Tool(String invocationId, String toolId, GuideToolStatus status) {

                if (invocationId == null || dev.openallay.util.Java8Strings.isBlank(invocationId)) {
                    throw new IllegalArgumentException("export invocation ID is blank");
                }
                if (toolId == null || dev.openallay.util.Java8Strings.isBlank(toolId)) {
                    throw new IllegalArgumentException("export Tool ID is blank");
                }
                java.util.Objects.requireNonNull(status, "status");

        this.invocationId = invocationId;
        this.toolId = toolId;
        this.status = status;
    }
    public String invocationId() { return invocationId; }
    public String toolId() { return toolId; }
    public GuideToolStatus status() { return status; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Tool)) return false;
        Tool that = (Tool) other;
        return java.util.Objects.equals(invocationId, that.invocationId) && java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(status, that.status);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        return hash;
    }
    @Override public String toString() { return "Tool[invocationId=" + invocationId + ", toolId=" + toolId + ", status=" + status + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Tool> schema() {
            return new dev.openallay.value.ValueSchema<>(Tool.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Tool>>asList(new dev.openallay.value.ValueSchema.Component<>(Tool.class, "invocationId", Tool::invocationId), new dev.openallay.value.ValueSchema.Component<>(Tool.class, "toolId", Tool::toolId), new dev.openallay.value.ValueSchema.Component<>(Tool.class, "status", Tool::status)), arguments -> new Tool((String) arguments[0], (String) arguments[1], (GuideToolStatus) arguments[2]));
        }
    }
}
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideSessionExportSnapshot)) return false;
        GuideSessionExportSnapshot that = (GuideSessionExportSnapshot) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(requests, that.requests) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(imagePayloadResolver, that.imagePayloadResolver);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(requests);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(imagePayloadResolver);
        return hash;
    }
    @Override public String toString() { return "GuideSessionExportSnapshot[sessionId=" + sessionId + ", requests=" + requests + ", capturedAt=" + capturedAt + ", imagePayloadResolver=" + imagePayloadResolver + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideSessionExportSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideSessionExportSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideSessionExportSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideSessionExportSnapshot.class, "sessionId", GuideSessionExportSnapshot::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideSessionExportSnapshot.class, "requests", GuideSessionExportSnapshot::requests), new dev.openallay.value.ValueSchema.Component<>(GuideSessionExportSnapshot.class, "capturedAt", GuideSessionExportSnapshot::capturedAt), new dev.openallay.value.ValueSchema.Component<>(GuideSessionExportSnapshot.class, "imagePayloadResolver", GuideSessionExportSnapshot::imagePayloadResolver)), arguments -> new GuideSessionExportSnapshot((String) arguments[0], (List) arguments[1], (Instant) arguments[2], (ImagePayloadResolver) arguments[3]));
        }
    }
}
