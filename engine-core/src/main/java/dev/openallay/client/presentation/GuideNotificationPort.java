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

    record Notification(UUID connectionGeneration, UUID actorId, UUID sessionOwner,
                        String sessionId, UUID requestId, String preview, int cardCount,
                        List<GuidePresentationEvent.CardPreview> cardPreviews, boolean replyCompleted, boolean taskCompleted, boolean taskFailed,
                        int durationSeconds, Fence fence, int additionalTasks) {
        public Notification {
            Objects.requireNonNull(connectionGeneration, "connectionGeneration");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(sessionOwner, "sessionOwner");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(preview, "preview");
            cardPreviews = List.copyOf(cardPreviews);
            Objects.requireNonNull(fence, "fence");
            if (cardPreviews.size() > cardCount || cardPreviews.stream()
                    .map(GuidePresentationEvent.CardPreview::source).distinct().count() != cardPreviews.size()) {
                throw new IllegalArgumentException("card previews must name distinct batch content");
            }
            if (cardCount < 0 || additionalTasks < 0 || durationSeconds < 3 || durationSeconds > 15) {
                throw new IllegalArgumentException("invalid notification display values");
            }
        }
        public Notification(UUID connectionGeneration, UUID actorId, UUID sessionOwner,
                            String sessionId, UUID requestId, String preview, int cardCount,
                            List<GuidePresentationEvent.CardPreview> cardPreviews,
                            boolean replyCompleted, boolean taskCompleted, boolean taskFailed,
                            int durationSeconds, Fence fence) {
            this(connectionGeneration, actorId, sessionOwner, sessionId, requestId, preview, cardCount,
                    cardPreviews, replyCompleted, taskCompleted, taskFailed, durationSeconds, fence, 0);
        }
    }
}
