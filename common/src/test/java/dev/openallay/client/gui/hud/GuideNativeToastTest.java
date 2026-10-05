package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.presentation.GuideNotificationPort;
import dev.openallay.guide.GuidePresentationEvent;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.toasts.Toast;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuideNativeToastTest {
    @BeforeAll
    static void bootstrapNativeRegistries() {
        // Toast.Visibility references SoundEvents. Bootstrap before its first enum access.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static GuideNotificationPort.Notification notification(GuideNotificationPort.Fence fence) {
        return new GuideNotificationPort.Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "session-1", UUID.randomUUID(), "Reply", 1,
                List.of(new GuidePresentationEvent.CardPreview(new GuidePresentationEvent.ContentRef(0, "tool:stone:card:0"),
                        "Stone supply", "Compare building blocks for the roof.")), true, true, false, 6, fence);
    }
    @Test void invalidQueuedToastCannotExtractOrRequestDisplay() {
        var fence = new GuideNotificationPort.Fence();
        var toast = new GuideNativeToast(notification(fence));
        fence.invalidate();
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility());
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 0));
        assertDoesNotThrow(() -> toast.updateGuideToast(0));
    }
    @Test void hideIsOwnedAndMakesExtractionNoOp() {
        var toast = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        toast.hide();
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility());
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 0));
    }
    @Test void fixedNativeSlotContractSurvivesCoalescedUpdate() {
        var current = notification(new GuideNotificationPort.Fence());
        var toast = new GuideNativeToast(current);
        Object token = toast.getToken();
        assertEquals(64, toast.height());
        assertEquals(2, toast.guideSlotCount());
        assertEquals(96, toast.yPos(3));
        toast.update(current);
        assertSame(token, toast.getToken());
        assertEquals(64, toast.height());
    }
    @Test void nativeExpirationNeverAcknowledgesUnreadOrRestartsForUpdate() {
        var current = notification(new GuideNotificationPort.Fence());
        var toast = new GuideNativeToast(current);
        toast.updateGuideToast(6000);
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility());
        toast.update(current);
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility());
    }
    @Test void actualCardFactsStayPrimaryAfterFinalReplyAndFailureAndCountsStaySecondary() {
        var original = notification(new GuideNotificationPort.Fence());
        var notification = new GuideNotificationPort.Notification(original.connectionGeneration(), original.actorId(),
                original.sessionOwner(), original.sessionId(), original.requestId(), "Actual reply", 3,
                original.cardPreviews(), true, true, false, 6, original.fence(), 4);
        var display = GuideNativeToast.display(notification, Component.literal("F8"));
        assertEquals("Stone supply", display.title().getString());
        assertEquals("Compare building blocks for the roof.", display.description().getString());
        assertTrue(display.secondary().getString().contains("Actual reply"));
        assertTrue(display.summary().toString().contains("notification.more_cards"));
        assertTrue(display.summary().toString().contains("notification.more_tasks"));
        assertTrue(display.hint().toString().contains("F8"));
        assertFalse(display.title().toString().contains("notification.cards"));
        assertFalse(display.hint().toString().contains("click"));
        var failed = new GuideNotificationPort.Notification(original.connectionGeneration(), original.actorId(),
                original.sessionOwner(), original.sessionId(), original.requestId(), "Actual failure", 3,
                original.cardPreviews(), true, false, true, 6, original.fence());
        var failureDisplay = GuideNativeToast.display(failed, null);
        assertEquals(display.title(), failureDisplay.title()); assertEquals(display.description(), failureDisplay.description());
        assertTrue(failureDisplay.secondary().getString().contains("Actual failure"));
        assertTrue(failureDisplay.secondary().toString().contains("notification.failed"));
        assertTrue(failureDisplay.hint().getString().isBlank(), "unbound Guide mapping cannot advertise an action");
    }

    @Test void updatesCannotRebindNativeObjectAcrossRequestOwnerOrFence() {
        var current = notification(new GuideNotificationPort.Fence());
        var toast = new GuideNativeToast(current);
        var invalid = new GuideNotificationPort.Notification(current.connectionGeneration(), current.actorId(),
                current.sessionOwner(), current.sessionId(), UUID.randomUUID(), "Wrong task", 0, List.of(),
                true, true, false, 3, current.fence());
        toast.update(invalid);
        toast.updateGuideToast(4000);
        assertEquals(Toast.Visibility.SHOW, toast.guideWantedVisibility(), "other task cannot replace duration or display payload");
        var otherFence = new GuideNotificationPort.Fence();
        var next = new GuideNotificationPort.Notification(current.connectionGeneration(), current.actorId(),
                current.sessionOwner(), current.sessionId(), current.requestId(), "Rebound", 0, List.of(),
                true, true, false, 3, otherFence);
        toast.update(next);
        current.fence().invalidate();
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility(), "old exact owned fence still controls queued object");
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 4000));
    }

    @Test void onlyNativeFinishedRenderingRetiresLifecycleNotQueueWaitOrContentUpdate() {
        var current = notification(new GuideNotificationPort.Fence());
        var toast = new GuideNativeToast(current);
        toast.queued();
        assertFalse(toast.finished()); assertEquals(Toast.Visibility.SHOW, toast.guideWantedVisibility());
        toast.update(current); assertFalse(toast.finished());
        toast.updateGuideToast(6000);
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility());
        assertFalse(toast.finished(), "native hide animation has not finished yet");
        toast.onFinishedRendering(); assertTrue(toast.finished());
        assertEquals(Toast.Visibility.HIDE, toast.guideWantedVisibility());
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 6000));
        assertNull(toast.e2eReceipt(), "finished objects cannot create a new graphical frame");
        assertTrue(current.fence().valid(), "controller retires the fence after observing native completion");
    }

    @Test void receiptReadsLifecycleAndInvalidExtractionCannotFabricateNativeFrames() {
        var current = notification(new GuideNotificationPort.Fence());
        var toast = new GuideNativeToast(current);
        assertNull(toast.e2eReceipt()); assertNull(toast.e2eReceipt());
        toast.queued(); toast.update(current);
        assertNull(toast.e2eReceipt(), "show/update are not native extraction evidence");
        toast.hide();
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 0));
        assertNull(toast.e2eReceipt(), "owned hidden objects never create a graphical frame");
        var fenced = new GuideNativeToast(current);
        current.fence().invalidate();
        assertDoesNotThrow(() -> fenced.extractRenderState(null, null, 0));
        assertNull(fenced.e2eReceipt(), "invalid native queued objects cannot create a frame");
    }

    @Test void tokensAreNeverTheSharedNativeNoToken() {
        var first = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        var second = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        assertNotSame(Toast.NO_TOKEN, first.getToken());
        assertNotSame(first.getToken(), second.getToken());
    }
}
