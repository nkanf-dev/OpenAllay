package dev.openallay.client.presentation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source wiring checks supplement the deterministic live receipts and native toast tests. */
final class GuideNotificationLayerArchitectureTest {
    private static Path root() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return root;
    }
    private static String source(String path) throws Exception { return Files.readString(root().resolve(path)); }

    @Test void notificationsBindImmediatelyAndTickWithoutPassiveHudGate() throws Exception {
        String source = source("common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java");
        assertTrue(source.contains("services.listenPresentation(notifications)"));
        assertTrue(source.contains("notifications.tick()"));
        assertFalse(source.contains("bindCurrent()"));
        assertFalse(source.contains("snapshot().updatedAt()"));
        assertFalse(source.contains("client.voice"));
        String constructor = source.substring(source.indexOf("public GuideClientUiCoordinator("), source.indexOf("public void openGuide("));
        assertFalse(constructor.contains("forActor("), "constructing presentation must not create a task/service");
        assertTrue(constructor.contains("settings.listen(ignored -> notifications.settingsChanged())"));
    }

    @Test void fullscreenReadsOnlyActualReceiptContentAndLitePreviewCannotAck() throws Exception {
        String screen = source("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java");
        assertTrue(screen.contains("reportVisibleReceipts()"));
        assertTrue(screen.contains("if (active) notifications.markSeen(seen)"));
        assertTrue(screen.contains("if (ref.contentId().startsWith(\"tool:\")) return false"));
        assertTrue(screen.contains("bounds.bottom() <= viewport.bottom()"));
        assertTrue(screen.contains("!sessionOverlay && !overflowOpen && !modelSelectorOpen"));
        assertTrue(screen.contains("notifications.clearVisibility(service)"));
        assertFalse(source("common/src/main/java/dev/openallay/client/gui/hud/GuideChatLiteScreen.java").contains("markSeen("));
        assertFalse(source("common/src/main/java/dev/openallay/client/gui/hud/GuideHudRenderer.java").contains("markSeen("));
    }

    @Test void nativeAndSettingsPortsAreRealOwnedAndNoGlobalClear() throws Exception {
        String toast = source("common/src/main/java/dev/openallay/client/gui/hud/GuideNativeToast.java");
        String port = source("common/src/main/java/dev/openallay/client/gui/hud/GuideNativeToastPort.java");
        assertTrue(toast.contains("notification.fence().valid()"));
        assertTrue(toast.contains("if (!valid()) return"));
        assertTrue(port.contains("toastManager().addToast(toast)"));
        assertFalse(port.contains(".clear()"));
        assertFalse(toast.contains("markSeen("));
        String settings = source("common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java");
        assertTrue(settings.contains("case NOTIFICATIONS ->"));
        assertTrue(settings.contains("previewNotification(uiDraft.ui().notifications())"));
        assertFalse(settings.contains("VoiceSettingsActions"));
        assertFalse(settings.contains("SettingsSection.VOICE"));
    }
}
