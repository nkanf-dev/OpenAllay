package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Native resize ownership source contracts. Real selected native math has separate tests. */
final class GuideNativeWindowResizeContractsTest {
    @Test void sdlBindingCompletesTheNativeFramebufferNotificationAfterTheMutation() throws Exception {
        String binding = source("common/src/targets/26.3/java/dev/openallay/client/gui/MinecraftClientWindow.java");
        String resize = resizeMethod(binding);
        int mutate = resize.indexOf("minecraft.getWindow().setWindowed(width, height);");
        int notify = resize.indexOf("minecraft.framebufferSizeChanged();");
        assertTrue(mutate >= 0 && notify > mutate, "Notify after SDL refreshes framebuffer dimensions");
        assertEquals(1, occurrences(resize, "framebufferSizeChanged()"));
        for (String forbidden : List.of("setGuiScale(", "resizeGui(", "resize(", "enableScissor(",
                "Thread.sleep", "catch (", "getDeclaredMethod", "isGameLoadFinished", "Math.max")) {
            assertFalse(resize.contains(forbidden), forbidden);
        }
        String families = source("gradle/minecraft-targets.gradle");
        assertTrue(families.contains("'26.3': '26.3'"), "The actual SDL target selects this native leaf");
        String selector = source("gradle/minecraft-source-family.gradle");
        assertTrue(selector.contains("sourceSets"), "Use existing file-level native source replacement");
    }

    @Test void nonSdlNativeBindingsPreserveTheirExistingWindowContract() throws Exception {
        for (String family : List.of("main", "26.1", "1.21.11", "1.21.8", "1.21.1")) {
            String prefix = family.equals("main") ? "common/src/main/java/" : "common/src/targets/" + family + "/java/";
            String resize = resizeMethod(source(prefix + "dev/openallay/client/gui/MinecraftClientWindow.java"));
            assertTrue(resize.contains("minecraft.getWindow().setWindowed(width, height);"), family);
            assertFalse(resize.contains("framebufferSizeChanged"), family);
            assertFalse(resize.contains("resizeGui"), family);
        }
    }

    @Test void bothDevelopmentDriversUseTheNativeOwnerForEveryWindowMutation() throws Exception {
        for (String driver : List.of("GuideClientE2EController", "GuideGraphicalRegressionProbe")) {
            String source = source("common/src/main/java/dev/openallay/guide/e2e/" + driver + ".java");
            assertFalse(source.contains("getWindow().setWindowed("), driver);
            assertEquals(driver.equals("GuideClientE2EController") ? 2 : 6,
                    occurrences(source, "MinecraftClientWindow.setWindowed(client, "), driver);
        }
    }

    private static String resizeMethod(String source) {
        String signature = "public static void setWindowed(Minecraft minecraft, int width, int height) {";
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Typed native resize entry is required");
        return source.substring(start, source.indexOf("\n    }", start));
    }
    private static int occurrences(String source, String fragment) {
        return (source.length() - source.replace(fragment, "").length()) / fragment.length();
    }
    private static String source(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return Files.readString(root.resolve(relative));
    }
}
