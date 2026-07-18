package dev.tomewisp.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tomewisp.TomeWispRuntime;
import dev.tomewisp.devmode.DevelopmentToolInspector;
import dev.tomewisp.guide.ui.GuideDisplayConfig;
import dev.tomewisp.guide.ui.GuideDisplayRuntime;
import dev.tomewisp.knowledge.KnowledgeRegistry;
import dev.tomewisp.platform.PlatformService;
import dev.tomewisp.skill.SkillParser;
import dev.tomewisp.skill.SkillRepository;
import dev.tomewisp.tool.ToolRegistry;
import dev.tomewisp.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientSettingsRuntimeTest {
    @Test
    void missingFilesUseDisabledMemoryDefaultWithoutMaterializingConfiguration(
            @TempDir Path directory) {
        Path profiles = directory.resolve("models.json");
        Path legacy = directory.resolve("model.json");
        Path metadata = directory.resolve("model-metadata.json");

        ToolResult<ClientSettingsRuntime> created = ClientSettingsRuntime.create(
                runtime(),
                profiles,
                legacy,
                metadata,
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                GuideDisplayConfig.defaults());

        if (!(created instanceof ToolResult.Success<ClientSettingsRuntime> success)) {
            throw new AssertionError("expected native settings runtime creation to succeed");
        }
        ClientSettingsRuntime settings = success.value();
        assertEquals("default", settings.settings().snapshot()
                .models().config().defaultProfileId());
        assertFalse(settings.settings().snapshot().models().profiles().getFirst().available());
        assertEquals("model_not_configured", settings.settings().snapshot().notice().code());
        assertFalse(Files.exists(profiles));
        assertFalse(Files.exists(legacy));
        settings.closeAsync().join();
    }

    @Test
    void sharedDisplayRuntimePersistsDebugModeForSettingsAndGuide(@TempDir Path directory)
            throws Exception {
        Path displayPath = directory.resolve("display.json");
        GuideDisplayRuntime display = new GuideDisplayRuntime(displayPath);
        ClientSettingsHistoryBinding history = new ClientSettingsHistoryBinding();
        ToolResult<ClientSettingsRuntime> created = ClientSettingsRuntime.create(
                runtime(),
                directory.resolve("models.json"),
                directory.resolve("model.json"),
                directory.resolve("model-metadata.json"),
                directory.resolve("capabilities.json"),
                directory.resolve("recipes.json"),
                new dev.tomewisp.recipe.config.RecipeClientRuntime(
                        directory.resolve("recipes.json")),
                Map.of(),
                Runnable::run,
                null,
                Clock.systemUTC(),
                display,
                history);
        if (!(created instanceof ToolResult.Success<ClientSettingsRuntime> success)) {
            throw new AssertionError("expected native settings runtime creation to succeed");
        }
        ClientSettingsRuntime settings = success.value();

        assertInstanceOf(ToolResult.Success.class, settings.settings()
                .saveDisplay(new GuideDisplayConfig(1, true)).join());

        assertTrue(display.config().debugMode());
        assertTrue(settings.settings().snapshot().display().debugMode());
        assertTrue(Files.exists(displayPath));
        assertTrue(new GuideDisplayRuntime(displayPath).config().debugMode());
        settings.closeAsync().join();
    }

    private static TomeWispRuntime runtime() {
        ToolRegistry tools = new ToolRegistry();
        return new TomeWispRuntime(
                new FakePlatform(),
                tools,
                new KnowledgeRegistry(),
                new dev.tomewisp.integration.patchouli.PatchouliMultiblockStore(),
                new SkillRepository(new SkillParser(), List.of()),
                new DevelopmentToolInspector(tools),
                null);
    }

    private static final class FakePlatform implements PlatformService {
        @Override
        public String platformName() {
            return "test";
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return true;
        }
    }
}
