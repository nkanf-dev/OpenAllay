package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the exact selected native entry seam. The real UI fixture verifies native click behavior. */
final class GuideLegacyButtonDispatchTest {
    private static Path root() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return root;
    }
    private static String source(String name) throws Exception {
        return Files.readString(root().resolve("common/src/targets/1.12.2/java/dev/openallay/client/gui/" + name + ".java"));
    }
    private static String method(String source, String start, String next) {
        return source.substring(source.indexOf(start), source.indexOf(next, source.indexOf(start)));
    }
    @Test void buttonIsAGenuineNativeButtonRatherThanThePrimitiveWidgetFamily() throws Exception {
        String button = source("GuideNativeButton");
        assertTrue(button.contains("extends GuiButton implements GuideWidgetInput, GuideWidget"));
        assertFalse(button.contains("extends GuideNativeWidget"));
        String screen = source("GuideNativeScreen");
        assertTrue(screen.contains("if (button instanceof GuideNativeButton guideButton) guideButton.onPress();"));
    }
    @Test void physicalAndTypedClicksEnterTheSameNativeButtonRouteExactlyOnce() throws Exception {
        String callbacks = source("GuideNativeScreenCallbacks");
        String physical = method(callbacks, "@Override protected final void mouseClicked(",
                "@Override protected final void mouseReleased(");
        assertTrue(physical.contains("guideMouseClicked("));
        assertFalse(physical.contains("super.mouseClicked("), "physical entry must not dispatch a second native action");
        String typed = method(callbacks, "public boolean guideMouseClicked(",
                "public boolean guideMouseDragged(");
        assertEquals(1, typed.split("super\\.mouseClicked\\(", -1).length - 1);
        assertTrue(typed.contains("event.leftClick() && selectedButton != null"));
        assertFalse(typed.contains(".onPress("), "the native GuiScreen owns selection, sound and Forge action hooks");
        String screen = source("GuideNativeScreen");
        String fallback = method(screen, "@Override public boolean guideMouseClicked(",
                "@Override public boolean guideMouseDragged(");
        assertTrue(fallback.contains("super.guideMouseClicked(event, doubleClick)"));
    }
    @Test void releaseRetiresNativeSelectionOnceEvenWhenAButtonChangedScreens() throws Exception {
        String callbacks = source("GuideNativeScreenCallbacks");
        String physical = method(callbacks, "@Override protected final void mouseReleased(",
                "@Override protected final void mouseClickMove(");
        assertTrue(physical.contains("guideMouseReleased("));
        assertFalse(physical.contains("super.mouseReleased("));
        String typed = method(callbacks, "public boolean guideMouseReleased(",
                "public boolean guideMouseScrolled(");
        assertEquals(1, typed.split("super\\.mouseReleased\\(", -1).length - 1);
        assertTrue(typed.contains("selectedButton != null && event.leftClick()"));
        String screen = source("GuideNativeScreen");
        String release = method(screen, "@Override public boolean guideMouseReleased(",
                "@Override public boolean guideMouseScrolled(");
        assertTrue(release.contains("super.guideMouseReleased(event) || focused"));
    }
}
