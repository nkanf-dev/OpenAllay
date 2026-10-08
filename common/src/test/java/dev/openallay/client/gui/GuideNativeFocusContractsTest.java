package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts only; packaged-client tests own native focus behavior verification. */
final class GuideNativeFocusContractsTest {
    @Test
    void defaultFocusPortKeepsNativeScreenClearAndFloorUsesTheSameTypedPathOperations() throws Exception {
        String main = source("common/src/main/java/dev/openallay/client/gui/GuideNativeFocus.java");
        String floor = source("common/src/targets/1.20.2/java/dev/openallay/client/gui/GuideNativeFocus.java");
        assertTrue(main.contains("public static void clear(Screen screen)"));
        assertTrue(main.contains("screen.clearFocus();"));
        assertFalse(main.contains("getCurrentFocusPath()"));
        assertTrue(floor.contains("public static void clear(Screen screen)"));
        assertTrue(floor.contains("import net.minecraft.client.gui.ComponentPath;"));
        assertTrue(floor.contains("ComponentPath path = screen.getCurrentFocusPath();"));
        assertTrue(floor.contains("if (path != null) {\n            path.applyFocus(false);\n        }"));
        assertFalse(floor.contains("screen.clearFocus()"));
        for (String port : new String[] {main, floor}) {
            for (String forbidden : new String[] {"GuideTextInputFocus.release(", "releaseTextFocus(",
                    "children()", "getDeclaredMethod(", "setAccessible(", "Class.forName(",
                    "setValue(", "setText("}) {
                assertFalse(port.contains(forbidden), forbidden);
            }
        }
    }

    @Test
    void sharedReleaseStillRetiresOnlyThisScreensNativeTextOwnersAfterClearingItsPath() throws Exception {
        String release = source("common/src/main/java/dev/openallay/client/gui/GuideTextInputFocus.java");
        int clear = release.indexOf("GuideNativeFocus.clear(screen);");
        int owners = release.indexOf("for (var child : screen.children())");
        int retire = release.indexOf("GuideWidgetInputs.releaseTextFocus(GuideNativeInput.widgetInput(child));");
        assertTrue(clear >= 0 && owners > clear && retire > owners);
        assertTrue(release.contains("child instanceof EditBox || GuideNativeMultilineText.find(child) != null"));
        assertFalse(release.contains("screen.clearFocus()"));
        assertFalse(release.contains("Minecraft.getInstance()"));
    }

    @Test
    void generalScreenClearsUseOnlyTheNativeFocusPortAndRemovalKeepsItsSeparateOwnerRelease() throws Exception {
        for (String relative : new String[] {"OpenAllayScreen.java", "hud/GuideChatLiteScreen.java",
                "hud/GuideHudEditorScreen.java"}) {
            String screen = source("common/src/main/java/dev/openallay/client/gui/" + relative);
            assertTrue(screen.contains("GuideNativeFocus.clear(this);"), relative);
            assertFalse(screen.contains("clearFocus()"), relative);
            if (!relative.endsWith("GuideHudEditorScreen.java")) {
                assertTrue(screen.contains("GuideTextInputFocus.release(this);"), relative);
            }
        }
        String settings = source("common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java");
        assertTrue(settings.contains("GuideTextInputFocus.release(this);"));
    }

    private static String source(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return Files.readString(root.resolve(relative));
    }
}
