package dev.openallay.guide.ui;

import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuidePersistenceSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.semantic.SemanticDocument;
import java.util.List;
import java.util.UUID;

/** Visible transcript projection. Reasoning is deliberately not representable. */
public sealed interface GuideUiRow
        permits GuideUiRow.Persistence, GuideUiRow.User, GuideUiRow.Assistant,
                GuideUiRow.Tool, GuideUiRow.Status {

    @dev.openallay.value.ValueType(Persistence.ValueSchemaProvider.class)
public static final class Persistence implements GuideUiRow {
    private final GuidePersistenceSnapshot.State state;
    private final String translationKey;
    private final GuideFailure failure;
    public Persistence(GuidePersistenceSnapshot.State state, String translationKey, GuideFailure failure) {
        this.state = state;
        this.translationKey = translationKey;
        this.failure = failure;
    }
    public GuidePersistenceSnapshot.State state() { return state; }
    public String translationKey() { return translationKey; }
    public GuideFailure failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Persistence)) return false;
        Persistence that = (Persistence) other;
        return java.util.Objects.equals(state, that.state) && java.util.Objects.equals(translationKey, that.translationKey) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(translationKey);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Persistence[state=" + state + ", translationKey=" + translationKey + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Persistence> schema() {
            return new dev.openallay.value.ValueSchema<>(Persistence.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Persistence>>asList(new dev.openallay.value.ValueSchema.Component<>(Persistence.class, "state", Persistence::state), new dev.openallay.value.ValueSchema.Component<>(Persistence.class, "translationKey", Persistence::translationKey), new dev.openallay.value.ValueSchema.Component<>(Persistence.class, "failure", Persistence::failure)), arguments -> new Persistence((GuidePersistenceSnapshot.State) arguments[0], (String) arguments[1], (GuideFailure) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(User.ValueSchemaProvider.class)
public static final class User implements GuideUiRow {
    private final UUID requestId;
    private final String text;
    public User(UUID requestId, String text) {
        this.requestId = requestId;
        this.text = text;
    }
    public UUID requestId() { return requestId; }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof User)) return false;
        User that = (User) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "User[requestId=" + requestId + ", text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<User> schema() {
            return new dev.openallay.value.ValueSchema<>(User.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<User>>asList(new dev.openallay.value.ValueSchema.Component<>(User.class, "requestId", User::requestId), new dev.openallay.value.ValueSchema.Component<>(User.class, "text", User::text)), arguments -> new User((UUID) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Assistant.ValueSchemaProvider.class)
public static final class Assistant implements GuideUiRow {
    private final UUID requestId;
    private final int ordinal;
    private final String text;
    private final SemanticDocument semantic;
    private final boolean streaming;
    private final List<GuideSource> sources;
    public Assistant(UUID requestId, int ordinal, String text, SemanticDocument semantic, boolean streaming, List<GuideSource> sources) {

            java.util.Objects.requireNonNull(semantic, "semantic");
            sources = dev.openallay.util.Java8Collections.listCopyOf(sources);

        this.requestId = requestId;
        this.ordinal = ordinal;
        this.text = text;
        this.semantic = semantic;
        this.streaming = streaming;
        this.sources = sources;
    }
    public UUID requestId() { return requestId; }
    public int ordinal() { return ordinal; }
    public String text() { return text; }
    public SemanticDocument semantic() { return semantic; }
    public boolean streaming() { return streaming; }
    public List<GuideSource> sources() { return sources; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Assistant)) return false;
        Assistant that = (Assistant) other;
        return java.util.Objects.equals(requestId, that.requestId) && ordinal == that.ordinal && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(semantic, that.semantic) && streaming == that.streaming && java.util.Objects.equals(sources, that.sources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(semantic);
        hash = 31 * hash + Boolean.hashCode(streaming);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        return hash;
    }
    @Override public String toString() { return "Assistant[requestId=" + requestId + ", ordinal=" + ordinal + ", text=" + text + ", semantic=" + semantic + ", streaming=" + streaming + ", sources=" + sources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Assistant> schema() {
            return new dev.openallay.value.ValueSchema<>(Assistant.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Assistant>>asList(new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "requestId", Assistant::requestId), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "ordinal", Assistant::ordinal), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "text", Assistant::text), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "semantic", Assistant::semantic), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "streaming", Assistant::streaming), new dev.openallay.value.ValueSchema.Component<>(Assistant.class, "sources", Assistant::sources)), arguments -> new Assistant((UUID) arguments[0], (Integer) arguments[1], (String) arguments[2], (SemanticDocument) arguments[3], (Boolean) arguments[4], (List) arguments[5]));
        }
    }
}

    @dev.openallay.value.ValueType(Tool.ValueSchemaProvider.class)
public static final class Tool implements GuideUiRow {
    private final UUID requestId;
    private final int ordinal;
    private final GuideToolActivity activity;
    private final GuideToolDetailView detail;
    public Tool(UUID requestId, int ordinal, GuideToolActivity activity, GuideToolDetailView detail) {

            java.util.Objects.requireNonNull(activity, "activity");
            java.util.Objects.requireNonNull(detail, "detail");

        this.requestId = requestId;
        this.ordinal = ordinal;
        this.activity = activity;
        this.detail = detail;
    }
    public UUID requestId() { return requestId; }
    public int ordinal() { return ordinal; }
    public GuideToolActivity activity() { return activity; }
    public GuideToolDetailView detail() { return detail; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Tool)) return false;
        Tool that = (Tool) other;
        return java.util.Objects.equals(requestId, that.requestId) && ordinal == that.ordinal && java.util.Objects.equals(activity, that.activity) && java.util.Objects.equals(detail, that.detail);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(activity);
        hash = 31 * hash + java.util.Objects.hashCode(detail);
        return hash;
    }
    @Override public String toString() { return "Tool[requestId=" + requestId + ", ordinal=" + ordinal + ", activity=" + activity + ", detail=" + detail + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Tool> schema() {
            return new dev.openallay.value.ValueSchema<>(Tool.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Tool>>asList(new dev.openallay.value.ValueSchema.Component<>(Tool.class, "requestId", Tool::requestId), new dev.openallay.value.ValueSchema.Component<>(Tool.class, "ordinal", Tool::ordinal), new dev.openallay.value.ValueSchema.Component<>(Tool.class, "activity", Tool::activity), new dev.openallay.value.ValueSchema.Component<>(Tool.class, "detail", Tool::detail)), arguments -> new Tool((UUID) arguments[0], (Integer) arguments[1], (GuideToolActivity) arguments[2], (GuideToolDetailView) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Status.ValueSchemaProvider.class)
public static final class Status implements GuideUiRow {
    private final UUID requestId;
    private final GuideRequestStatus status;
    private final String text;
    private final GuideFailure failure;
    public Status(UUID requestId, GuideRequestStatus status, String text, GuideFailure failure) {
        this.requestId = requestId;
        this.status = status;
        this.text = text;
        this.failure = failure;
    }
    public UUID requestId() { return requestId; }
    public GuideRequestStatus status() { return status; }
    public String text() { return text; }
    public GuideFailure failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Status)) return false;
        Status that = (Status) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Status[requestId=" + requestId + ", status=" + status + ", text=" + text + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Status> schema() {
            return new dev.openallay.value.ValueSchema<>(Status.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Status>>asList(new dev.openallay.value.ValueSchema.Component<>(Status.class, "requestId", Status::requestId), new dev.openallay.value.ValueSchema.Component<>(Status.class, "status", Status::status), new dev.openallay.value.ValueSchema.Component<>(Status.class, "text", Status::text), new dev.openallay.value.ValueSchema.Component<>(Status.class, "failure", Status::failure)), arguments -> new Status((UUID) arguments[0], (GuideRequestStatus) arguments[1], (String) arguments[2], (GuideFailure) arguments[3]));
        }
    }
}
}
