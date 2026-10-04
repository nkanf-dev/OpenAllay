package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuideDisplayRuntimeTest {
    @TempDir Path temporary;

    @Test
    void successfulSavePublishesCanonicalConfigAndReloadsExternalChanges() throws Exception {
        Path path = temporary.resolve("display.json");
        GuideDisplayRuntime runtime = new GuideDisplayRuntime(path);

        ToolResult<GuideDisplayConfig> saved = runtime.save(new GuideDisplayConfig(
                true, false,
                        GuideDisplayConfig.DEFAULT_ASSISTANT_NAME));

        assertTrue(saved instanceof ToolResult.Success<GuideDisplayConfig>);
        assertTrue(runtime.config().debugMode());
        assertNull(runtime.failure());
        assertTrue(Files.readString(path).contains("\"debugMode\": true"));

        Files.writeString(path, new GuideDisplayConfigWriter().encode(
                GuideDisplayConfig.defaults().withAssistantName("小羽")));
        ToolResult<GuideDisplayConfig> reloaded = runtime.reload();

        assertTrue(reloaded instanceof ToolResult.Success<GuideDisplayConfig>);
        assertFalse(runtime.config().debugMode());
        assertEquals("小羽", runtime.config().assistantName());
        assertNull(runtime.failure());
    }

    @Test
    void invalidReloadRetainsLastValidProjectionAndReportsFailure() throws Exception {
        Path path = temporary.resolve("display.json");
        GuideDisplayRuntime runtime = new GuideDisplayRuntime(path);
        runtime.save(new GuideDisplayConfig(
                true, false,
                        GuideDisplayConfig.DEFAULT_ASSISTANT_NAME));
        Files.writeString(path,
                "{\"debugMode\":false}");

        ToolResult<GuideDisplayConfig> result = runtime.reload();

        ToolResult.Failure<GuideDisplayConfig> failure =
                (ToolResult.Failure<GuideDisplayConfig>) result;
        assertEquals("invalid_display_config", failure.code());
        assertTrue(runtime.config().debugMode());
        assertNotNull(runtime.failure());
        assertEquals("invalid_display_config", runtime.failure().code());
    }

    @Test
    void failedNestedUiSaveRetainsEveryLastValidPreferenceAndExactBytes() throws Exception {
        Path path = temporary.resolve("ui-display.json");
        GuideDisplayConfig saved = GuideDisplayConfig.defaults().withUi(GuideUiConfig.defaults()
                .withHud(GuideUiConfig.Hud.defaults().withEnabled(true).withBackgroundOpacity(.3))
                .withNotifications(GuideUiConfig.Notifications.defaults().withEnabled(true)));
        String original = new GuideDisplayConfigWriter().encode(saved);
        Files.writeString(path, original);
        GuideDisplayRuntime runtime = new GuideDisplayRuntime(path, (target, contents) -> {
            throw new SettingsWriteException();
        });
        GuideDisplayConfig candidate = saved.withUi(saved.ui().withHud(saved.ui().hud().withBackgroundOpacity(.1)));
        assertTrue(runtime.save(candidate) instanceof ToolResult.Failure<?>);
        assertEquals(saved, runtime.config());
        assertEquals(original, Files.readString(path));
        assertEquals("settings_write_failed", runtime.failure().code());
    }

    @Test
    void failedDebugSaveRetainsFileAndProjection() throws Exception {
        Path path = temporary.resolve("display.json");
        String original = new GuideDisplayConfigWriter().encode(GuideDisplayConfig.defaults());
        Files.writeString(path, original);
        GuideDisplayRuntime runtime = new GuideDisplayRuntime(
                path,
                (target, contents) -> {
                    throw new SettingsWriteException();
                });

        ToolResult<GuideDisplayConfig> result =
                runtime.save(new GuideDisplayConfig(
                        true, false,
                        GuideDisplayConfig.DEFAULT_ASSISTANT_NAME));

        ToolResult.Failure<GuideDisplayConfig> failure =
                (ToolResult.Failure<GuideDisplayConfig>) result;
        assertEquals("settings_write_failed", failure.code());
        assertFalse(runtime.config().debugMode());
        assertEquals(original, Files.readString(path));
        assertEquals("settings_write_failed", runtime.failure().code());
    }
}
