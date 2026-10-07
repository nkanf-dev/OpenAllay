package dev.openallay.client.presentation;

import dev.openallay.guide.GuidePresentationEvent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Native UI boundary. Calls occur on the client owner thread, never in render or I/O workers. */
public interface GuideNotificationPort {
    Handle show(Notification notification);

    interface Handle {
        void update(Notification notification);
        /** True only after native display and hide animation finish, never from time spent queued. */
        boolean finished();
        /** Hide only the OpenAllay object owned by this handle, never clear the native manager. */
        void hide();
    }

    /** Native update and extraction must check valid(), including objects still in its queue. */
    final class Fence {
        private volatile boolean valid = true;
        public boolean valid() { return valid; }
        public void invalidate() { valid = false; }
    }

    @dev.openallay.value.ValueType(Notification.ValueSchemaProvider.class)
public static final class Notification {
    private final UUID connectionGeneration;
    private final UUID actorId;
    private final UUID sessionOwner;
    private final String sessionId;
    private final UUID requestId;
    private final String preview;
    private final int cardCount;
    private final List<GuidePresentationEvent.CardPreview> cardPreviews;
    private final boolean replyCompleted;
    private final boolean taskCompleted;
    private final boolean taskFailed;
    private final int durationSeconds;
    private final Fence fence;
    private final int additionalTasks;
    public Notification(UUID connectionGeneration, UUID actorId, UUID sessionOwner, String sessionId, UUID requestId, String preview, int cardCount, List<GuidePresentationEvent.CardPreview> cardPreviews, boolean replyCompleted, boolean taskCompleted, boolean taskFailed, int durationSeconds, Fence fence, int additionalTasks) {

            Objects.requireNonNull(connectionGeneration, "connectionGeneration");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(sessionOwner, "sessionOwner");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(preview, "preview");
            cardPreviews = dev.openallay.util.Java8Collections.listCopyOf(cardPreviews);
            Objects.requireNonNull(fence, "fence");
            if (cardPreviews.size() > cardCount || cardPreviews.stream()
                    .map(GuidePresentationEvent.CardPreview::source).distinct().count() != cardPreviews.size()) {
                throw new IllegalArgumentException("card previews must name distinct batch content");
            }
            if (cardCount < 0 || additionalTasks < 0 || durationSeconds < 3 || durationSeconds > 15) {
                throw new IllegalArgumentException("invalid notification display values");
            }

        this.connectionGeneration = connectionGeneration;
        this.actorId = actorId;
        this.sessionOwner = sessionOwner;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.preview = preview;
        this.cardCount = cardCount;
        this.cardPreviews = cardPreviews;
        this.replyCompleted = replyCompleted;
        this.taskCompleted = taskCompleted;
        this.taskFailed = taskFailed;
        this.durationSeconds = durationSeconds;
        this.fence = fence;
        this.additionalTasks = additionalTasks;
    }
    public UUID connectionGeneration() { return connectionGeneration; }
    public UUID actorId() { return actorId; }
    public UUID sessionOwner() { return sessionOwner; }
    public String sessionId() { return sessionId; }
    public UUID requestId() { return requestId; }
    public String preview() { return preview; }
    public int cardCount() { return cardCount; }
    public List<GuidePresentationEvent.CardPreview> cardPreviews() { return cardPreviews; }
    public boolean replyCompleted() { return replyCompleted; }
    public boolean taskCompleted() { return taskCompleted; }
    public boolean taskFailed() { return taskFailed; }
    public int durationSeconds() { return durationSeconds; }
    public Fence fence() { return fence; }
    public int additionalTasks() { return additionalTasks; }
public Notification(UUID connectionGeneration, UUID actorId, UUID sessionOwner,
                            String sessionId, UUID requestId, String preview, int cardCount,
                            List<GuidePresentationEvent.CardPreview> cardPreviews,
                            boolean replyCompleted, boolean taskCompleted, boolean taskFailed,
                            int durationSeconds, Fence fence) {
            this(connectionGeneration, actorId, sessionOwner, sessionId, requestId, preview, cardCount,
                    cardPreviews, replyCompleted, taskCompleted, taskFailed, durationSeconds, fence, 0);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Notification)) return false;
        Notification that = (Notification) other;
        return java.util.Objects.equals(connectionGeneration, that.connectionGeneration) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionOwner, that.sessionOwner) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(preview, that.preview) && cardCount == that.cardCount && java.util.Objects.equals(cardPreviews, that.cardPreviews) && replyCompleted == that.replyCompleted && taskCompleted == that.taskCompleted && taskFailed == that.taskFailed && durationSeconds == that.durationSeconds && java.util.Objects.equals(fence, that.fence) && additionalTasks == that.additionalTasks;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(connectionGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionOwner);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        hash = 31 * hash + Integer.hashCode(cardCount);
        hash = 31 * hash + java.util.Objects.hashCode(cardPreviews);
        hash = 31 * hash + Boolean.hashCode(replyCompleted);
        hash = 31 * hash + Boolean.hashCode(taskCompleted);
        hash = 31 * hash + Boolean.hashCode(taskFailed);
        hash = 31 * hash + Integer.hashCode(durationSeconds);
        hash = 31 * hash + java.util.Objects.hashCode(fence);
        hash = 31 * hash + Integer.hashCode(additionalTasks);
        return hash;
    }
    @Override public String toString() { return "Notification[connectionGeneration=" + connectionGeneration + ", actorId=" + actorId + ", sessionOwner=" + sessionOwner + ", sessionId=" + sessionId + ", requestId=" + requestId + ", preview=" + preview + ", cardCount=" + cardCount + ", cardPreviews=" + cardPreviews + ", replyCompleted=" + replyCompleted + ", taskCompleted=" + taskCompleted + ", taskFailed=" + taskFailed + ", durationSeconds=" + durationSeconds + ", fence=" + fence + ", additionalTasks=" + additionalTasks + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Notification> schema() {
            return new dev.openallay.value.ValueSchema<>(Notification.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Notification>>asList(new dev.openallay.value.ValueSchema.Component<>(Notification.class, "connectionGeneration", Notification::connectionGeneration), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "actorId", Notification::actorId), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "sessionOwner", Notification::sessionOwner), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "sessionId", Notification::sessionId), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "requestId", Notification::requestId), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "preview", Notification::preview), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "cardCount", Notification::cardCount), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "cardPreviews", Notification::cardPreviews), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "replyCompleted", Notification::replyCompleted), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "taskCompleted", Notification::taskCompleted), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "taskFailed", Notification::taskFailed), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "durationSeconds", Notification::durationSeconds), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "fence", Notification::fence), new dev.openallay.value.ValueSchema.Component<>(Notification.class, "additionalTasks", Notification::additionalTasks)), arguments -> new Notification((UUID) arguments[0], (UUID) arguments[1], (UUID) arguments[2], (String) arguments[3], (UUID) arguments[4], (String) arguments[5], (Integer) arguments[6], (List) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9], (Boolean) arguments[10], (Integer) arguments[11], (Fence) arguments[12], (Integer) arguments[13]));
        }
    }
}
}
