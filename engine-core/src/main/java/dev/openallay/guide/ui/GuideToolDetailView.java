package dev.openallay.guide.ui;

import com.google.gson.JsonObject;
import dev.openallay.guide.GuideToolInvocationView;
import dev.openallay.guide.GuideToolIntent;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Player-facing tool detail plus an optional debug-only technical projection. */
@dev.openallay.value.ValueType(GuideToolDetailView.ValueSchemaProvider.class)
public final class GuideToolDetailView {
    private final String titleKey;
    private final GuideToolStatus status;
    private final GuideToolInvocationView invocation;
    private final GuideToolIntent intent;
    private final List<GuideDetailCard> cards;
    private final List<GuideToolMessage> narration;
    private final Optional<Debug> debug;
    private final GuideToolDisplayStatus displayStatus;
    private final Optional<Failure> failure;
    public GuideToolDetailView(String titleKey, GuideToolStatus status, GuideToolInvocationView invocation, GuideToolIntent intent, List<GuideDetailCard> cards, List<GuideToolMessage> narration, Optional<Debug> debug, GuideToolDisplayStatus displayStatus, Optional<Failure> failure) {

        if (titleKey == null || dev.openallay.util.Java8Strings.isBlank(titleKey)) {
            throw new IllegalArgumentException("titleKey must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(intent, "intent");
        cards = dev.openallay.util.Java8Collections.listCopyOf(cards);
        narration = dev.openallay.util.Java8Collections.listCopyOf(narration);
        debug = Objects.requireNonNull(debug, "debug");
        Objects.requireNonNull(displayStatus, "displayStatus");
        failure = Objects.requireNonNull(failure, "failure");

            cards.forEach(GuideDetailCard::requireKnown);
        this.titleKey = titleKey;
        this.status = status;
        this.invocation = invocation;
        this.intent = intent;
        this.cards = cards;
        this.narration = narration;
        this.debug = debug;
        this.displayStatus = displayStatus;
        this.failure = failure;
    }
    public String titleKey() { return titleKey; }
    public GuideToolStatus status() { return status; }
    public GuideToolInvocationView invocation() { return invocation; }
    public GuideToolIntent intent() { return intent; }
    public List<GuideDetailCard> cards() { return cards; }
    public List<GuideToolMessage> narration() { return narration; }
    public Optional<Debug> debug() { return debug; }
    public GuideToolDisplayStatus displayStatus() { return displayStatus; }
    public Optional<Failure> failure() { return failure; }
public GuideToolDetailView(
            String titleKey,
            GuideToolStatus status,
            GuideToolInvocationView invocation,
            GuideToolIntent intent,
            List<GuideDetailCard> cards,
            List<GuideToolMessage> narration,
            Optional<Debug> debug) {
        this(titleKey, status, invocation, intent, cards, narration, debug,
                GuideToolDisplayStatus.from(status, false), Optional.empty());
    }
public GuideToolDetailView(
            String titleKey,
            GuideToolStatus status,
            GuideToolInvocationView invocation,
            List<GuideDetailCard> cards,
            List<GuideToolMessage> narration,
            Optional<Debug> debug) {
        this(titleKey, status, invocation, GuideToolIntent.none(), cards, narration, debug);
    }
public GuideToolDetailView forRequest(boolean terminal) {
        GuideToolDisplayStatus projected = GuideToolDisplayStatus.from(status, terminal);
        return new GuideToolDetailView(titleKey, status, invocation, intent, cards,
                projected == GuideToolDisplayStatus.NO_RESULT_RECORDED ? dev.openallay.util.Java8Collections.listOf() : narration,
                debug, projected, failure);
    }
public GuideToolDetailView(
            String titleKey,
            GuideToolStatus status,
            List<GuideDetailCard> cards,
            List<GuideToolMessage> narration,
            Optional<Debug> debug) {
        this(titleKey, status, GuideToolInvocationView.none(), cards, narration, debug);
    }
@dev.openallay.value.ValueType(Failure.ValueSchemaProvider.class)
public static final class Failure {
    private final String code;
    private final String message;
    public Failure(String code, String message) {

            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Failure)) return false;
        Failure that = (Failure) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Failure[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Failure> schema() {
            return new dev.openallay.value.ValueSchema<>(Failure.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Failure>>asList(new dev.openallay.value.ValueSchema.Component<>(Failure.class, "code", Failure::code), new dev.openallay.value.ValueSchema.Component<>(Failure.class, "message", Failure::message)), arguments -> new Failure((String) arguments[0], (String) arguments[1]));
        }
    }
}
@dev.openallay.value.ValueType(Debug.ValueSchemaProvider.class)
public static final class Debug {
    private final String invocationId;
    private final String toolId;
    private final JsonObject invocationArguments;
    private final JsonObject normalized;
    private final String validationDiagnostic;
    public Debug(String invocationId, String toolId, JsonObject invocationArguments, JsonObject normalized, String validationDiagnostic) {

            if (invocationId == null || dev.openallay.util.Java8Strings.isBlank(invocationId)
                    || toolId == null || dev.openallay.util.Java8Strings.isBlank(toolId)) {
                throw new IllegalArgumentException("debug identity must not be blank");
            }
            invocationArguments =
                    invocationArguments == null ? null : dev.openallay.json.JsonTrees.copy(invocationArguments);
            normalized = normalized == null ? null : dev.openallay.json.JsonTrees.copy(normalized);
            validationDiagnostic = validationDiagnostic == null ? "" : validationDiagnostic;

        this.invocationId = invocationId;
        this.toolId = toolId;
        this.invocationArguments = invocationArguments;
        this.normalized = normalized;
        this.validationDiagnostic = validationDiagnostic;
    }
    public String invocationId() { return invocationId; }
    public String toolId() { return toolId; }
    public String validationDiagnostic() { return validationDiagnostic; }

        public JsonObject invocationArguments() {
            return invocationArguments == null ? null : dev.openallay.json.JsonTrees.copy(invocationArguments);
        }

        public JsonObject normalized() {
            return normalized == null ? null : dev.openallay.json.JsonTrees.copy(normalized);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Debug)) return false;
        Debug that = (Debug) other;
        return java.util.Objects.equals(invocationId, that.invocationId) && java.util.Objects.equals(toolId, that.toolId) && java.util.Objects.equals(invocationArguments, that.invocationArguments) && java.util.Objects.equals(normalized, that.normalized) && java.util.Objects.equals(validationDiagnostic, that.validationDiagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(invocationId);
        hash = 31 * hash + java.util.Objects.hashCode(toolId);
        hash = 31 * hash + java.util.Objects.hashCode(invocationArguments);
        hash = 31 * hash + java.util.Objects.hashCode(normalized);
        hash = 31 * hash + java.util.Objects.hashCode(validationDiagnostic);
        return hash;
    }
    @Override public String toString() { return "Debug[invocationId=" + invocationId + ", toolId=" + toolId + ", invocationArguments=" + invocationArguments + ", normalized=" + normalized + ", validationDiagnostic=" + validationDiagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Debug> schema() {
            return new dev.openallay.value.ValueSchema<>(Debug.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Debug>>asList(new dev.openallay.value.ValueSchema.Component<>(Debug.class, "invocationId", Debug::invocationId), new dev.openallay.value.ValueSchema.Component<>(Debug.class, "toolId", Debug::toolId), new dev.openallay.value.ValueSchema.Component<>(Debug.class, "invocationArguments", Debug::invocationArguments), new dev.openallay.value.ValueSchema.Component<>(Debug.class, "normalized", Debug::normalized), new dev.openallay.value.ValueSchema.Component<>(Debug.class, "validationDiagnostic", Debug::validationDiagnostic)), arguments -> new Debug((String) arguments[0], (String) arguments[1], (JsonObject) arguments[2], (JsonObject) arguments[3], (String) arguments[4]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideToolDetailView)) return false;
        GuideToolDetailView that = (GuideToolDetailView) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(invocation, that.invocation) && java.util.Objects.equals(intent, that.intent) && java.util.Objects.equals(cards, that.cards) && java.util.Objects.equals(narration, that.narration) && java.util.Objects.equals(debug, that.debug) && java.util.Objects.equals(displayStatus, that.displayStatus) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(invocation);
        hash = 31 * hash + java.util.Objects.hashCode(intent);
        hash = 31 * hash + java.util.Objects.hashCode(cards);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        hash = 31 * hash + java.util.Objects.hashCode(debug);
        hash = 31 * hash + java.util.Objects.hashCode(displayStatus);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "GuideToolDetailView[titleKey=" + titleKey + ", status=" + status + ", invocation=" + invocation + ", intent=" + intent + ", cards=" + cards + ", narration=" + narration + ", debug=" + debug + ", displayStatus=" + displayStatus + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideToolDetailView> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideToolDetailView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideToolDetailView>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "titleKey", GuideToolDetailView::titleKey), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "status", GuideToolDetailView::status), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "invocation", GuideToolDetailView::invocation), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "intent", GuideToolDetailView::intent), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "cards", GuideToolDetailView::cards), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "narration", GuideToolDetailView::narration), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "debug", GuideToolDetailView::debug), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "displayStatus", GuideToolDetailView::displayStatus), new dev.openallay.value.ValueSchema.Component<>(GuideToolDetailView.class, "failure", GuideToolDetailView::failure)), arguments -> new GuideToolDetailView((String) arguments[0], (GuideToolStatus) arguments[1], (GuideToolInvocationView) arguments[2], (GuideToolIntent) arguments[3], (List) arguments[4], (List) arguments[5], (Optional) arguments[6], (GuideToolDisplayStatus) arguments[7], (Optional) arguments[8]));
        }
    }
}
