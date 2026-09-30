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
    void knownPermissionUsesFriendlyNameWithoutRenamingUnknownRequirements() {
        var known = new dev.openallay.client.gui.settings.RequirementSettingsProjection.Row(
                dev.openallay.requirement.RequirementKind.CAPABILITY, "unrestricted-javascript",
                "unrestricted-javascript", dev.openallay.requirement.RequirementStatus.DISABLED,
                "", true, true);
        var translated = (net.minecraft.network.chat.contents.TranslatableContents)
                RequirementReviewScreen.rowLabel(known).getContents();
        var identity = (net.minecraft.network.chat.Component) translated.getArgs()[1];
        var name = (net.minecraft.network.chat.contents.TranslatableContents) identity.getContents();
        assertEquals("screen.openallay.settings.requirements.name.unrestricted_javascript", name.getKey());
        assertTrue(identity.getString().contains("(unrestricted-javascript)"));
        var unknown = new dev.openallay.client.gui.settings.RequirementSettingsProjection.Row(
                dev.openallay.requirement.RequirementKind.CAPABILITY, "thirdparty:unrestricted-javascript",
                "Other permission", dev.openallay.requirement.RequirementStatus.UNKNOWN, "", false, false);
        var unknownLabel = (net.minecraft.network.chat.contents.TranslatableContents)
                RequirementReviewScreen.rowLabel(unknown).getContents();
        assertEquals("Other permission (thirdparty:unrestricted-javascript)",
                ((net.minecraft.network.chat.Component) unknownLabel.getArgs()[1]).getString());
    }

    @Test
    void inlineSnapshotDuringAddedWaitsForNativeScreenLayout() {
        var attachment = new RequirementReviewScreen.AttachmentState();
        long epoch = attachment.attach();
        var snapshots = new AtomicInteger();
        var rebuilt = new AtomicInteger();
        Runnable inlineSnapshot = () -> {
            if (!attachment.isCurrent(epoch)) return;
            snapshots.incrementAndGet();
            if (attachment.canRebuild(epoch)) rebuilt.incrementAndGet();
        };

        // Gui.setScreen calls added() before Screen.init(width, height). The initial
        // ClientSettingsService snapshot can arrive inline while width/height are 0.
        inlineSnapshot.run();
        assertEquals(1, snapshots.get());
        assertEquals(0, rebuilt.get());
        assertFalse(attachment.canRebuild(epoch));

        // Native init constructs the first widgets from the retained snapshot. Later
        // notifications may rebuild them using the now valid screen dimensions.
        attachment.layoutInitialized();
        assertTrue(attachment.canRebuild(epoch));
        inlineSnapshot.run();
        assertEquals(2, snapshots.get());
        assertEquals(1, rebuilt.get());
    }

    @Test
    void reattachmentWaitsForItsOwnNativeLayoutBeforeSnapshotRebuilds() {
        var attachment = new RequirementReviewScreen.AttachmentState();
        long first = attachment.attach();
        attachment.layoutInitialized();
        assertTrue(attachment.canRebuild(first));

        attachment.detach();
        assertFalse(attachment.canRebuild(first));
        long second = attachment.attach();
        assertFalse(attachment.canRebuild(first));
        assertFalse(attachment.canRebuild(second));
        attachment.layoutInitialized();
        assertFalse(attachment.canRebuild(first));
        assertTrue(attachment.canRebuild(second));
        attachment.detach();
        assertFalse(attachment.canRebuild(second));
    }

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
