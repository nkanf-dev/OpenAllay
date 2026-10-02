package dev.openallay.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModelProfileSettingsStoreTest {
    @TempDir Path temporary;

    @Test
    void atomicallyWritesBeforePublishingPreparedRuntime() throws Exception {
        Path target = temporary.resolve("models.json");
        AtomicBoolean published = new AtomicBoolean();
        ModelProfileSettingsStore store = new ModelProfileSettingsStore(target);

        ToolResult<ModelProfileSettingsStore.Saved> result = store.save(
                config("new-model"),
                Map.of("MODEL_KEY", "secret-value"),
                Map.of(),
                loaded -> () -> {
                    assertTrue(Files.exists(target));
                    assertTrue(read(target).contains("new-model"));
                    assertFalse(read(target).contains("secret-value"));
                    published.set(true);
                });

        ModelProfileSettingsStore.Saved saved = success(result).value();
        assertTrue(published.get());
        assertEquals("new-model", saved.config().profiles().getFirst().model());
        assertTrue(saved.profiles().getFirst().available());
    }

    @Test
    void persistenceFailurePreservesFileAndNeverPublishesPreparedRuntime() throws Exception {
        Path target = temporary.resolve("models.json");
        Files.writeString(target, "old settings\n");
        AtomicBoolean published = new AtomicBoolean();
        ModelProfileSettingsStore store = new ModelProfileSettingsStore(
                target, (ignoredPath, ignoredContents) -> {
                    throw new SettingsWriteException();
                });

        ToolResult<ModelProfileSettingsStore.Saved> result = store.save(
                config("new-model"),
                Map.of("MODEL_KEY", "secret-value"),
                Map.<ModelMetadata.Key, ModelMetadata>of(),
                ignored -> () -> published.set(true));

        ToolResult.Failure<ModelProfileSettingsStore.Saved> failure = failure(result);
        assertEquals("settings_write_failed", failure.code());
        assertEquals("Unable to save settings", failure.message());
        assertEquals("old settings\n", Files.readString(target));
        assertFalse(published.get());
    }

    @Test
    void missingEnvironmentValueMayPersistAsVisibleUnavailableProfile() throws Exception {
        Path target = temporary.resolve("models.json");
        ModelProfileSettingsStore store = new ModelProfileSettingsStore(target);

        ModelProfileSettingsStore.Saved saved = success(store.save(
                config("new-model"), Map.of(), Map.of(), ignored -> () -> {})).value();

        assertFalse(saved.profiles().getFirst().available());
        assertEquals("model_not_configured", saved.profiles().getFirst().failure().code());
        assertTrue(Files.exists(target));
    }

    @Test
    void automaticOutputSaveOmitsOwnershipAndReloadUsesNewMetadataForFutureRuntime() throws Exception {
        var automatic = new ModelProfileDefinition("main", "Luna", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://openrouter.ai/api/v1/"), "gpt-6-luna", "env:MODEL_KEY",
                1_000_000, null, Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig("main", List.of(automatic));
        Path target = temporary.resolve("models.json");
        var store = new ModelProfileSettingsStore(target);
        var saved = success(store.save(config, Map.of("MODEL_KEY", "fixture-key"), Map.of(),
                loaded -> () -> assertEquals(128_000, loaded.profiles().getFirst()
                        .runtimeConfig().maxOutputTokens()))).value();
        assertEquals(config, saved.config());
        assertFalse(read(target).contains("maxOutputTokens"));
        var captured = saved.profiles().getFirst().runtimeConfig();
        var metadata = new ModelMetadata("openrouter", "gpt-6-luna", "canonical",
                1_050_000, 64_000, java.time.Instant.EPOCH);
        var reload = (ToolResult.Success<ModelProfilesConfigLoader.Load>) new ModelProfilesConfigLoader()
                .load(target, Map.of("MODEL_KEY", "fixture-key"), Map.of(metadata.key(), metadata));
        assertEquals(64_000, reload.value().profiles().getFirst().runtimeConfig().maxOutputTokens());
        assertEquals(1_000_000, reload.value().profiles().getFirst().runtimeConfig().contextWindowTokens());
        assertEquals(128_000, captured.maxOutputTokens());
        assertEquals(config, reload.value().config());
    }

    @Test
    void automaticOutputWriteFailureKeepsExistingFileAndPreparedRuntimeUnpublished() throws Exception {
        Path target = temporary.resolve("models.json");
        Files.writeString(target, "old settings\n");
        var profile = new ModelProfileDefinition("main", "Luna", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1/"), "gpt-6-luna", "env:MODEL_KEY",
                1_000_000, null, Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig("main", List.of(profile));
        AtomicBoolean published = new AtomicBoolean();
        var store = new ModelProfileSettingsStore(target, (ignoredPath, ignoredContents) -> {
            throw new SettingsWriteException();
        });
        var result = store.save(config, Map.of("MODEL_KEY", "fixture-key"), Map.of(), loaded -> {
            assertEquals(128_000, loaded.profiles().getFirst().runtimeConfig().maxOutputTokens());
            return () -> published.set(true);
        });
        assertEquals("settings_write_failed", failure(result).code());
        assertEquals("old settings\n", read(target));
        assertFalse(published.get());
    }

    @Test
    void automaticImageSaveLeavesDiscoveryUnpinnedAndReloadOnlyChangesFutureCapability() throws Exception {
        var automatic = new ModelProfileDefinition("main", "Main", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://openrouter.ai/api/v1/"), "vendor/model", "env:MODEL_KEY",
                100000, 10000, Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig("main", List.of(automatic));
        var metadata = new ModelMetadata("openrouter", "vendor/model", "canonical", 100000, 10000,
                java.time.Instant.EPOCH, dev.openallay.model.image.ImageInputCapability.SUPPORTED);
        Path target = temporary.resolve("images.json");
        var store = new ModelProfileSettingsStore(target);
        var saved = success(store.save(config, Map.of("MODEL_KEY", "fixture-key"),
                Map.of(metadata.key(), metadata), prepared -> () -> {
                    assertTrue(Files.exists(target));
                    assertFalse(read(target).contains("imageInputCapabilityOverride"));
                    assertEquals(dev.openallay.model.image.ImageInputCapability.SUPPORTED,
                            prepared.profiles().getFirst().imageCapability().capability());
                })).value();
        var captured = saved.profiles().getFirst();
        var replacement = new ModelMetadata("openrouter", "vendor/model", "canonical", 100000, 10000,
                java.time.Instant.ofEpochSecond(1), dev.openallay.model.image.ImageInputCapability.UNSUPPORTED);
        var reloaded = (ToolResult.Success<ModelProfilesConfigLoader.Load>) new ModelProfilesConfigLoader()
                .load(target, Map.of("MODEL_KEY", "fixture-key"), Map.of(replacement.key(), replacement));
        assertEquals(dev.openallay.model.image.ImageInputCapability.UNSUPPORTED,
                reloaded.value().profiles().getFirst().imageCapability().capability());
        assertEquals(dev.openallay.model.image.ImageInputCapability.SUPPORTED,
                captured.imageCapability().capability());
        assertEquals(captured.imageCapability(), captured.runtimeConfig().imageCapability());
    }

    @Test
    void failedImageOverrideSaveDoesNotPublishPreparedCapabilityOrReplaceSettings() throws Exception {
        var manual = new ModelProfileDefinition("main", "Main", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://gateway.example/v1/"), "private-alias", "env:MODEL_KEY",
                100000, 10000, Duration.ofSeconds(30), Duration.ofSeconds(300), null,
                ModelReasoningEffort.AUTO, dev.openallay.model.tokenizer.ModelTokenEncoding.AUTO,
                dev.openallay.model.image.ImageInputCapability.SUPPORTED);
        Path target = temporary.resolve("images.json");
        Files.writeString(target, "old settings\n");
        AtomicBoolean published = new AtomicBoolean();
        var store = new ModelProfileSettingsStore(target, (ignoredPath, ignoredContents) -> {
            throw new SettingsWriteException();
        });
        var result = store.save(new ModelProfilesConfig("main", List.of(manual)),
                Map.of("MODEL_KEY", "fixture-key"), Map.of(), prepared -> {
                    assertEquals(dev.openallay.model.image.ImageInputCapability.SUPPORTED,
                            prepared.profiles().getFirst().imageCapability().capability());
                    return () -> published.set(true);
                });
        assertEquals("settings_write_failed", failure(result).code());
        assertFalse(published.get());
        assertEquals("old settings\n", read(target));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ModelProfilesConfig config(String model) {
        ModelProfileDefinition profile = new ModelProfileDefinition(
                "main",
                "Main",
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                model,
                "MODEL_KEY",
                256_000,
                8_192,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
        return new ModelProfilesConfig(
                profile.id(), List.of(profile));
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<ModelProfileSettingsStore.Saved> success(
            ToolResult<ModelProfileSettingsStore.Saved> result) {
        return (ToolResult.Success<ModelProfileSettingsStore.Saved>)
                assertInstanceOf(ToolResult.Success.class, result);
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<ModelProfileSettingsStore.Saved> failure(
            ToolResult<ModelProfileSettingsStore.Saved> result) {
        return (ToolResult.Failure<ModelProfileSettingsStore.Saved>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }
}
