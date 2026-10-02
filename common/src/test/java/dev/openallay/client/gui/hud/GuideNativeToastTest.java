package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.UUID;
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
                "session-1", UUID.randomUUID(), "Reply", 1, true, true, false, 6, fence);
    }
    @Test void invalidQueuedToastCannotExtractOrRequestDisplay() {
        var fence = new GuideNotificationPort.Fence();
        var toast = new GuideNativeToast(notification(fence));
        fence.invalidate();
        assertEquals(Toast.Visibility.HIDE, toast.getWantedVisibility());
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 0));
        assertDoesNotThrow(() -> toast.update(null, 0));
    }
    @Test void hideIsOwnedAndMakesExtractionNoOp() {
        var toast = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        toast.hide();
        assertEquals(Toast.Visibility.HIDE, toast.getWantedVisibility());
        assertDoesNotThrow(() -> toast.extractRenderState(null, null, 0));
    }
    @Test void fixedNativeSlotContractSurvivesCoalescedUpdate() {
        var toast = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        Object token = toast.getToken();
        assertEquals(64, toast.height());
        assertEquals(2, toast.occcupiedSlotCount());
        assertEquals(96, toast.yPos(3));
        toast.update(notification(new GuideNotificationPort.Fence()));
        assertSame(token, toast.getToken());
        assertEquals(64, toast.height());
    }
    @Test void nativeExpirationNeverAcknowledgesUnreadOrRestartsForUpdate() {
        var toast = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        toast.update(null, 6000);
        assertEquals(Toast.Visibility.HIDE, toast.getWantedVisibility());
        toast.update(notification(new GuideNotificationPort.Fence()));
        assertEquals(Toast.Visibility.HIDE, toast.getWantedVisibility());
    }
    @Test void tokensAreNeverTheSharedNativeNoToken() {
        var first = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        var second = new GuideNativeToast(notification(new GuideNotificationPort.Fence()));
        assertNotSame(Toast.NO_TOKEN, first.getToken());
        assertNotSame(first.getToken(), second.getToken());
    }
}
