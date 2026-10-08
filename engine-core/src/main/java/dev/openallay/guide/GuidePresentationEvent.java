package dev.openallay.guide;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ephemeral accepted live result. Its sequence is an event identity, not a format version. */
@dev.openallay.value.ValueType(GuidePresentationEvent.ValueSchemaProvider.class)
public final class GuidePresentationEvent {
    private final Key key;
    private final Kind kind;
    private final List<ContentRef> content;
    private final String preview;
    private final List<CardPreview> cardPreviews;
    private final Instant createdAt;
    public GuidePresentationEvent(Key key, Kind kind, List<ContentRef> content, String preview, List<CardPreview> cardPreviews, Instant createdAt) {

        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        content = dev.openallay.util.Java8Collections.listCopyOf(content);
        preview = Objects.requireNonNull(preview, "preview");
        cardPreviews = dev.openallay.util.Java8Collections.listCopyOf(cardPreviews);
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

        this.key = key;
        this.kind = kind;
        this.content = content;
        this.preview = preview;
        this.cardPreviews = cardPreviews;
        this.createdAt = createdAt;
    }
    public Key key() { return key; }
    public Kind kind() { return kind; }
    public List<ContentRef> content() { return content; }
    public String preview() { return preview; }
    public List<CardPreview> cardPreviews() { return cardPreviews; }
    public Instant createdAt() { return createdAt; }
public GuidePresentationEvent(Key key, Kind kind, List<ContentRef> content, String preview, Instant createdAt) {
        this(key, kind, content, preview, dev.openallay.util.Java8Collections.listOf(), createdAt);
    }
@dev.openallay.value.ValueType(Key.ValueSchemaProvider.class)
public static final class Key {
    private final UUID connectionGeneration;
    private final UUID actorId;
    private final UUID sessionOwner;
    private final String sessionId;
    private final UUID requestId;
    private final long sequence;
    public Key(UUID connectionGeneration, UUID actorId, UUID sessionOwner, String sessionId, UUID requestId, long sequence) {

            Objects.requireNonNull(connectionGeneration, "connectionGeneration");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(sessionOwner, "sessionOwner");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(requestId, "requestId");
            if (sequence < 1) throw new IllegalArgumentException("receipt sequence must be positive");

        this.connectionGeneration = connectionGeneration;
        this.actorId = actorId;
        this.sessionOwner = sessionOwner;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.sequence = sequence;
    }
    public UUID connectionGeneration() { return connectionGeneration; }
    public UUID actorId() { return actorId; }
    public UUID sessionOwner() { return sessionOwner; }
    public String sessionId() { return sessionId; }
    public UUID requestId() { return requestId; }
    public long sequence() { return sequence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Key)) return false;
        Key that = (Key) other;
        return java.util.Objects.equals(connectionGeneration, that.connectionGeneration) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionOwner, that.sessionOwner) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(requestId, that.requestId) && sequence == that.sequence;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(connectionGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionOwner);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Long.hashCode(sequence);
        return hash;
    }
    @Override public String toString() { return "Key[connectionGeneration=" + connectionGeneration + ", actorId=" + actorId + ", sessionOwner=" + sessionOwner + ", sessionId=" + sessionId + ", requestId=" + requestId + ", sequence=" + sequence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Key> schema() {
            return new dev.openallay.value.ValueSchema<>(Key.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Key>>asList(new dev.openallay.value.ValueSchema.Component<>(Key.class, "connectionGeneration", Key::connectionGeneration), new dev.openallay.value.ValueSchema.Component<>(Key.class, "actorId", Key::actorId), new dev.openallay.value.ValueSchema.Component<>(Key.class, "sessionOwner", Key::sessionOwner), new dev.openallay.value.ValueSchema.Component<>(Key.class, "sessionId", Key::sessionId), new dev.openallay.value.ValueSchema.Component<>(Key.class, "requestId", Key::requestId), new dev.openallay.value.ValueSchema.Component<>(Key.class, "sequence", Key::sequence)), arguments -> new Key((UUID) arguments[0], (UUID) arguments[1], (UUID) arguments[2], (String) arguments[3], (UUID) arguments[4], (Long) arguments[5]));
        }
    }
}
@dev.openallay.value.ValueType(ContentRef.ValueSchemaProvider.class)
public static final class ContentRef {
    private final int timelineOrdinal;
    private final String contentId;
    public ContentRef(int timelineOrdinal, String contentId) {

            if (timelineOrdinal < -1) throw new IllegalArgumentException("invalid timeline ordinal");
            if (contentId == null || dev.openallay.util.Java8Strings.isBlank(contentId)) throw new IllegalArgumentException("content ID required");

        this.timelineOrdinal = timelineOrdinal;
        this.contentId = contentId;
    }
    public int timelineOrdinal() { return timelineOrdinal; }
    public String contentId() { return contentId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContentRef)) return false;
        ContentRef that = (ContentRef) other;
        return timelineOrdinal == that.timelineOrdinal && java.util.Objects.equals(contentId, that.contentId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(timelineOrdinal);
        hash = 31 * hash + java.util.Objects.hashCode(contentId);
        return hash;
    }
    @Override public String toString() { return "ContentRef[timelineOrdinal=" + timelineOrdinal + ", contentId=" + contentId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContentRef> schema() {
            return new dev.openallay.value.ValueSchema<>(ContentRef.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContentRef>>asList(new dev.openallay.value.ValueSchema.Component<>(ContentRef.class, "timelineOrdinal", ContentRef::timelineOrdinal), new dev.openallay.value.ValueSchema.Component<>(ContentRef.class, "contentId", ContentRef::contentId)), arguments -> new ContentRef((Integer) arguments[0], (String) arguments[1]));
        }
    }
}
@dev.openallay.value.ValueType(CardPreview.ValueSchemaProvider.class)
public static final class CardPreview {
    private final ContentRef source;
    private final String title;
    private final String description;
    public CardPreview(ContentRef source, String title, String description) {

            Objects.requireNonNull(source, "source");
            if (title == null || dev.openallay.util.Java8Strings.isBlank(title) || description == null || dev.openallay.util.Java8Strings.isBlank(description)) {
                throw new IllegalArgumentException("card preview title and description must not be blank");
            }
            title = bounded(title, 160);
            description = bounded(description, 512);

        this.source = source;
        this.title = title;
        this.description = description;
    }
    public ContentRef source() { return source; }
    public String title() { return title; }
    public String description() { return description; }
private static String bounded(String text, int maximum) {
            int count = text.codePointCount(0, text.length());
            return count <= maximum ? text : text.substring(0, text.offsetByCodePoints(0, maximum)) + "…";
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CardPreview)) return false;
        CardPreview that = (CardPreview) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        return hash;
    }
    @Override public String toString() { return "CardPreview[source=" + source + ", title=" + title + ", description=" + description + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CardPreview> schema() {
            return new dev.openallay.value.ValueSchema<>(CardPreview.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CardPreview>>asList(new dev.openallay.value.ValueSchema.Component<>(CardPreview.class, "source", CardPreview::source), new dev.openallay.value.ValueSchema.Component<>(CardPreview.class, "title", CardPreview::title), new dev.openallay.value.ValueSchema.Component<>(CardPreview.class, "description", CardPreview::description)), arguments -> new CardPreview((ContentRef) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
public enum Kind { REPLY_FINAL, CARD_BATCH, TASK_COMPLETED, TASK_FAILED }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuidePresentationEvent)) return false;
        GuidePresentationEvent that = (GuidePresentationEvent) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(content, that.content) && java.util.Objects.equals(preview, that.preview) && java.util.Objects.equals(cardPreviews, that.cardPreviews) && java.util.Objects.equals(createdAt, that.createdAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        hash = 31 * hash + java.util.Objects.hashCode(cardPreviews);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        return hash;
    }
    @Override public String toString() { return "GuidePresentationEvent[key=" + key + ", kind=" + kind + ", content=" + content + ", preview=" + preview + ", cardPreviews=" + cardPreviews + ", createdAt=" + createdAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuidePresentationEvent> schema() {
            return new dev.openallay.value.ValueSchema<>(GuidePresentationEvent.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuidePresentationEvent>>asList(new dev.openallay.value.ValueSchema.Component<>(GuidePresentationEvent.class, "key", GuidePresentationEvent::key), new dev.openallay.value.ValueSchema.Component<>(GuidePresentationEvent.class, "kind", GuidePresentationEvent::kind), new dev.openallay.value.ValueSchema.Component<>(GuidePresentationEvent.class, "content", GuidePresentationEvent::content), new dev.openallay.value.ValueSchema.Component<>(GuidePresentationEvent.class, "preview", GuidePresentationEvent::preview), new dev.openallay.value.ValueSchema.Component<>(GuidePresentationEvent.class, "cardPreviews", GuidePresentationEvent::cardPreviews), new dev.openallay.value.ValueSchema.Component<>(GuidePresentationEvent.class, "createdAt", GuidePresentationEvent::createdAt)), arguments -> new GuidePresentationEvent((Key) arguments[0], (Kind) arguments[1], (List) arguments[2], (String) arguments[3], (List) arguments[4], (Instant) arguments[5]));
        }
    }
}
