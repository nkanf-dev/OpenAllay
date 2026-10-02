package dev.openallay.guide;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ephemeral accepted live result. Its sequence is an event identity, not a format version. */
public record GuidePresentationEvent(
        Key key, Kind kind, List<ContentRef> content, String preview, Instant createdAt) {
    public GuidePresentationEvent {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        content = List.copyOf(content);
        preview = Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public record Key(UUID connectionGeneration, UUID actorId, UUID sessionOwner,
                      String sessionId, UUID requestId, long sequence) {
        public Key {
            Objects.requireNonNull(connectionGeneration, "connectionGeneration");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(sessionOwner, "sessionOwner");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(requestId, "requestId");
            if (sequence < 1) throw new IllegalArgumentException("receipt sequence must be positive");
        }
    }

    /** Ordinal is the existing timeline row; contentId is a semantic node/tool-card/status identity. */
    public record ContentRef(int timelineOrdinal, String contentId) {
        public ContentRef {
            if (timelineOrdinal < -1) throw new IllegalArgumentException("invalid timeline ordinal");
            if (contentId == null || contentId.isBlank()) throw new IllegalArgumentException("content ID required");
        }
    }

    public enum Kind { REPLY_FINAL, CARD_BATCH, TASK_COMPLETED, TASK_FAILED }
}
