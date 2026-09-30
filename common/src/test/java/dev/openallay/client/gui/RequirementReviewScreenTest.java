package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** No Minecraft client or graphics initialization is needed for callback ownership. */
final class RequirementReviewScreenTest {
    @Test
    void removalLetsConfirmedPublicationFinishButNeverReopensTheParentScreen() {
        var attachment = new RequirementReviewScreen.AttachmentState();
        long epoch = attachment.attach();
        var published = new CompletableFuture<Boolean>();
        var navigations = new AtomicInteger();
        published.thenAccept(success -> {
            if (!attachment.isCurrent(epoch)) return;
            navigations.incrementAndGet();
        });

        attachment.detach();
        published.complete(true);

        assertTrue(published.join());
        assertEquals(0, navigations.get());
        assertFalse(attachment.isCurrent(epoch));
    }

    @Test
    void lateEnableAndSnapshotNotificationsCannotRebuildADetachedReview() {
        var attachment = new RequirementReviewScreen.AttachmentState();
        long epoch = attachment.attach();
        var enabled = new CompletableFuture<Boolean>();
        var rebuilt = new AtomicInteger();
        Runnable listener = () -> {
            if (attachment.isCurrent(epoch)) rebuilt.incrementAndGet();
        };
        enabled.thenAccept(success -> listener.run());
        listener.run();
        assertEquals(1, rebuilt.get());

        attachment.detach();
        enabled.complete(true);
        listener.run();

        assertTrue(enabled.join());
        assertEquals(1, rebuilt.get());
    }

    @Test
    void reattachmentDoesNotReviveAnEarlierCallbackEpoch() {
        var attachment = new RequirementReviewScreen.AttachmentState();
        assertFalse(attachment.isCurrent(attachment.epoch()));
        long first = attachment.attach();
        assertTrue(attachment.isCurrent(first));
        attachment.detach();
        long second = attachment.attach();

        assertFalse(attachment.isCurrent(first));
        assertTrue(attachment.isCurrent(second));
        assertEquals(second, attachment.epoch());
        attachment.detach();
        assertFalse(attachment.isCurrent(second));
    }
}
