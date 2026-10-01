package dev.openallay.guide.export;

import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Closed, credential-free, point-in-time projection for player-initiated export. */
public record GuideSessionExportSnapshot(
        String sessionId,
        List<Request> requests,
        Instant capturedAt) {
    public GuideSessionExportSnapshot {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid export session ID");
        }
        requests = List.copyOf(requests);
        java.util.Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public record Request(
            UUID requestId,
            Instant createdAt,
            GuideRequestStatus status,
            String userMessage,
            List<Entry> timeline,
            List<ModelMessage> originalContext,
            GuideFailure failure) {
        public Request {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(createdAt, "createdAt");
            java.util.Objects.requireNonNull(status, "status");
            if (userMessage == null || userMessage.isBlank()) {
                throw new IllegalArgumentException("export user message is blank");
            }
            timeline = List.copyOf(timeline);
            originalContext = List.copyOf(originalContext);
            if (originalContext.stream().flatMap(message -> message.content().stream())
                    .anyMatch(ModelContent.Reasoning.class::isInstance)) {
                throw new IllegalArgumentException("export cannot contain reasoning");
            }
        }
    }

    public sealed interface Entry permits Entry.Assistant, Entry.Tool {
        record Assistant(String text, boolean streaming) implements Entry {
            public Assistant { text = text == null ? "" : text; }
        }

        record Tool(String invocationId, String toolId, GuideToolStatus status) implements Entry {
            public Tool {
                if (invocationId == null || invocationId.isBlank()) {
                    throw new IllegalArgumentException("export invocation ID is blank");
                }
                if (toolId == null || toolId.isBlank()) {
                    throw new IllegalArgumentException("export Tool ID is blank");
                }
                java.util.Objects.requireNonNull(status, "status");
            }
        }
    }
}
