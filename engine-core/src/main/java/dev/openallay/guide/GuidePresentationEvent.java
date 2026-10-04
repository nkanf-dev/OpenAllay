package dev.openallay.guide;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ephemeral accepted live result. Its sequence is an event identity, not a format version. */
public record GuidePresentationEvent(
        Key key, Kind kind, List<ContentRef> content, String preview,
        List<CardPreview> cardPreviews, Instant createdAt) {
    public GuidePresentationEvent {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        content = List.copyOf(content);
        preview = Objects.requireNonNull(preview, "preview");
        cardPreviews = List.copyOf(cardPreviews);
        if (kind != Kind.CARD_BATCH && !cardPreviews.isEmpty()) {
            throw new IllegalArgumentException("card previews require a card batch");
        }
        java.util.Set<ContentRef> sources = new java.util.HashSet<>();
        for (CardPreview card : cardPreviews) {
            if (!content.contains(card.source()) || !sources.add(card.source())) {
                throw new IllegalArgumentException("card preview must name one exact content reference");
            }
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public GuidePresentationEvent(Key key, Kind kind, List<ContentRef> content, String preview, Instant createdAt) {
        this(key, kind, content, preview, List.of(), createdAt);
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

    /** Ephemeral display metadata only. The source ref is the admitted card's exact timeline identity. */
    public record CardPreview(ContentRef source, String title, String description) {
        public CardPreview {
            Objects.requireNonNull(source, "source");
            if (title == null || title.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("card preview title and description must not be blank");
            }
            title = bounded(title, 160);
            description = bounded(description, 512);
        }

        private static String bounded(String text, int maximum) {
            int count = text.codePointCount(0, text.length());
            return count <= maximum ? text : text.substring(0, text.offsetByCodePoints(0, maximum)) + "…";
        }
    }

    public enum Kind { REPLY_FINAL, CARD_BATCH, TASK_COMPLETED, TASK_FAILED }
}
