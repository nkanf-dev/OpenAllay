package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.model.ServerModelSettingsView;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelSettingsProjectionTest {
    @Test
    void mixesLocalProfilesWithOneReadOnlyConnectionScopedServerModel() {
        ModelProfileDefinition local = new ModelProfileDefinition(
                "local",
                "Local model",
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                "provider/local",
                "LOCAL_KEY",
                256_000,
                4_096,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
        ModelProfilesConfig config = new ModelProfilesConfig(
                ModelProfilesConfig.SCHEMA_VERSION, "local", List.of(local));
        ModelProfileSettingsView locals = ModelProfileSettingsView.from(
                config,
                List.of(new ModelProfileSettingsView.Resolution(local, true, 256_000, null)),
                java.util.Set.of("LOCAL_KEY"),
                null,
                null);

        ModelSettingsProjection projection = ModelSettingsProjection.from(
                locals,
                new ServerModelSettingsView(
                        true, "server/deepseek", 100_000, 8_192, 6_000));

        assertEquals(2, projection.models().size());
        ModelSettingsProjection.ModelCard server = projection.models().getLast();
        assertEquals(ModelSettingsProjection.Origin.SERVER, server.origin());
        assertEquals("server/deepseek", server.displayName());
        assertTrue(server.available());
        assertFalse(server.editable());
        assertFalse(server.testable());
        assertFalse(server.deletable());
    }

    @Test
    void automaticOutputCardUsesEffectiveRuntimeAndDisabledNullStaysUnknown() {
        var automatic = new ModelProfileDefinition("luna", "Luna", true,
                ModelProtocol.OPENAI_CHAT, URI.create("https://provider.example/v1/"),
                "gpt-6-luna", "env:KEY", 1_000_000, null,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var disabled = new ModelProfileDefinition("disabled", "Disabled", false,
                ModelProtocol.OPENAI_CHAT, URI.create("https://provider.example/v1/"),
                "unknown", "env:KEY", null, null,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig(2, "luna", List.of(automatic, disabled));
        var encoded = new dev.openallay.model.config.ModelProfilesConfigWriter().encode(config);
        var loaded = (dev.openallay.tool.ToolResult.Success<dev.openallay.model.config.ModelProfilesConfigLoader.Load>)
                new dev.openallay.model.config.ModelProfilesConfigLoader().load(
                        new java.io.StringReader(encoded), java.util.Map.of("KEY", "fixture-key"));
        var views = ModelProfileSettingsView.from(config, loaded.value().profiles().stream()
                .map(ModelProfileSettingsView.Resolution::from).toList(), java.util.Set.of(), null, null);
        var cards = ModelSettingsProjection.from(views, ServerModelSettingsView.unavailable()).models();
        assertEquals(128_000, cards.getFirst().maxOutputTokens());
        assertEquals(1_000_000, cards.getFirst().contextWindowTokens());
        assertNull(cards.get(1).maxOutputTokens());
        assertNull(cards.get(1).contextWindowTokens());
        assertEquals("model_disabled", cards.get(1).failureCode());
        assertFalse(cards.toString().contains("fixture-key"));
    }
}
