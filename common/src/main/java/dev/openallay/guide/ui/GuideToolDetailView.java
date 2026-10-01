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
public record GuideToolDetailView(
        String titleKey,
        GuideToolStatus status,
        GuideToolInvocationView invocation,
        GuideToolIntent intent,
        List<GuideDetailCard> cards,
        List<GuideToolMessage> narration,
        Optional<Debug> debug,
        GuideToolDisplayStatus displayStatus,
        Optional<Failure> failure) {
    public GuideToolDetailView {
        if (titleKey == null || titleKey.isBlank()) {
            throw new IllegalArgumentException("titleKey must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(intent, "intent");
        cards = List.copyOf(cards);
        narration = List.copyOf(narration);
        debug = Objects.requireNonNull(debug, "debug");
        Objects.requireNonNull(displayStatus, "displayStatus");
        failure = Objects.requireNonNull(failure, "failure");
    }

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
                projected == GuideToolDisplayStatus.NO_RESULT_RECORDED ? List.of() : narration,
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

    /** The actual normalized tool failure, not a model-authored explanation. */
    public record Failure(String code, String message) {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    public record Debug(
            String invocationId,
            String toolId,
            JsonObject invocationArguments,
            JsonObject normalized,
            String validationDiagnostic) {
        public Debug {
            if (invocationId == null || invocationId.isBlank()
                    || toolId == null || toolId.isBlank()) {
                throw new IllegalArgumentException("debug identity must not be blank");
            }
            invocationArguments =
                    invocationArguments == null ? null : invocationArguments.deepCopy();
            normalized = normalized == null ? null : normalized.deepCopy();
            validationDiagnostic = validationDiagnostic == null ? "" : validationDiagnostic;
        }

        @Override
        public JsonObject invocationArguments() {
            return invocationArguments == null ? null : invocationArguments.deepCopy();
        }

        @Override
        public JsonObject normalized() {
            return normalized == null ? null : normalized.deepCopy();
        }
    }
}
