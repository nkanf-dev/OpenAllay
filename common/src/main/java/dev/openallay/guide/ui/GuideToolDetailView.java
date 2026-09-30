package dev.openallay.guide.ui;

import com.google.gson.JsonObject;
import dev.openallay.guide.GuideSource;
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
        Optional<Debug> debug) {
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

    public GuideToolDetailView(
            String titleKey,
            GuideToolStatus status,
            List<GuideDetailCard> cards,
            List<GuideToolMessage> narration,
            Optional<Debug> debug) {
        this(
                titleKey,
                status,
                GuideToolInvocationView.none(),
                cards,
                narration,
                debug);
    }

    public record Debug(
            String invocationId,
            String toolId,
            List<GuideSource> sources,
            JsonObject invocationArguments,
            JsonObject normalized,
            String validationDiagnostic) {
        public Debug {
            if (invocationId == null || invocationId.isBlank()
                    || toolId == null || toolId.isBlank()) {
                throw new IllegalArgumentException("debug identity must not be blank");
            }
            sources = List.copyOf(sources);
            invocationArguments =
                    invocationArguments == null ? null : invocationArguments.deepCopy();
            normalized = normalized == null ? null : normalized.deepCopy();
            validationDiagnostic = validationDiagnostic == null ? "" : validationDiagnostic;
        }

        public Debug(
                String invocationId,
                String toolId,
                List<GuideSource> sources,
                JsonObject normalized,
                String validationDiagnostic) {
            this(
                    invocationId,
                    toolId,
                    sources,
                    null,
                    normalized,
                    validationDiagnostic);
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
