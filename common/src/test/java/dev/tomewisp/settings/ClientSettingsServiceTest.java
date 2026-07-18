package dev.tomewisp.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tomewisp.guide.GuideFailure;
import dev.tomewisp.guide.ui.GuideDisplayConfig;
import dev.tomewisp.model.CancellationSignal;
import dev.tomewisp.model.config.ModelConfig;
import dev.tomewisp.model.config.ModelProfileDefinition;
import dev.tomewisp.model.config.ModelProfilesConfig;
import dev.tomewisp.model.config.ModelProtocol;
import dev.tomewisp.model.config.ResolvedModelProfile;
import dev.tomewisp.model.config.SecretValue;
import dev.tomewisp.model.metadata.ModelMetadata;
import dev.tomewisp.model.metadata.ModelMetadataUpdate;
import dev.tomewisp.settings.model.ModelConnectionResult;
import dev.tomewisp.settings.model.ModelProfileSettingsView;
import dev.tomewisp.tool.ToolResult;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ClientSettingsServiceTest {
    @Test
    void initialSnapshotPreservesProfileOrderAndOnlyCredentialPresence() {
        FakeModels models = new FakeModels(state(config("alpha", "beta")));
        ClientSettingsService service = service(models, Set.of("BETA_KEY", "ALPHA_KEY"));

        ClientSettingsSnapshot snapshot = service.snapshot();

        assertEquals(0, snapshot.generation());
        assertEquals(List.of("alpha", "beta"), snapshot.models().profiles().stream()
                .map(profile -> profile.definition().id())
                .toList());
        assertTrue(snapshot.models().profiles().getFirst().credentialPresent());
        assertTrue(snapshot.models().profiles().getLast().credentialPresent());
        assertFalse(snapshot.toString().contains("secret-alpha"));
        assertEquals(SettingsOperation.Kind.IDLE, snapshot.operation().kind());
    }

    @Test
    void probeAndMutationShareOneForegroundSlotAndCancellationIsImmediate() {
        FakeModels models = new FakeModels(state(config("alpha")));
        models.probe = new CompletableFuture<>();
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));

        CompletableFuture<ModelConnectionResult> pending =
                service.testConnection(profile("alpha"));

        assertFailure(service.saveModels(config("beta")).join(), "settings_busy");
        assertEquals("connection_test_busy", assertInstanceOf(
                ModelConnectionResult.Failure.class,
                service.testConnection(profile("beta")).join()).code());
        assertTrue(service.cancelConnectionTest());
        assertEquals("connection_cancelled", assertInstanceOf(
                ModelConnectionResult.Failure.class, pending.join()).code());
        assertTrue(models.probeCancelled.get());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
    }

    @Test
    void ordinaryListenerDetachDoesNotCancelConfirmedSave() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = service(
                models, Set.of("ALPHA_KEY", "BETA_KEY"), worker);
        List<ClientSettingsSnapshot> seen = new ArrayList<>();
        AutoCloseable listener = service.listen(seen::add);

        CompletableFuture<ToolResult<Boolean>> pending = service.saveModels(config("beta"));
        listener.close();
        worker.runNext();

        assertSuccess(pending.join());
        assertEquals("beta", service.snapshot().models().config().defaultProfileId());
        assertEquals(2, seen.size());
    }

    @Test
    void failedSaveRetainsLastValidProjection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        models.saveFailure = new ToolResult.Failure<>(
                "settings_write_failed", "Unable to save settings");
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY", "BETA_KEY"));

        ToolResult<Boolean> result = service.saveModels(config("beta")).join();

        assertFailure(result, "settings_write_failed");
        assertEquals("alpha", service.snapshot().models().config().defaultProfileId());
        assertEquals("settings_write_failed", service.snapshot().notice().code());
    }

    @Test
    void lateMetadataPreparationCannotOverwriteNewerProfileGeneration() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = new ClientSettingsService(
                GuideDisplayConfig.defaults(),
                models.current,
                Set.of("ALPHA_KEY", "BETA_KEY"),
                models,
                models,
                dispatcher::execute,
                worker);

        service.acceptMetadataUpdate(new ModelMetadataUpdate(Map.of(), null));
        worker.runNext(); // Prepare alpha; its completion waits in the dispatcher.
        service.saveModels(config("beta"));
        worker.runNext(); // Save beta; its completion is queued after alpha's completion.
        dispatcher.runLast(); // Deliver beta first to force the stale-generation race.
        dispatcher.runAll();
        worker.runAll();
        dispatcher.runAll();

        assertEquals("beta", service.snapshot().models().config().defaultProfileId());
        assertEquals("beta", models.publishedDefault);
        assertFalse(models.publishedDefaultsAfterSave.contains("alpha"));
    }

    @Test
    void olderMetadataUpdateCannotOverwriteNewerCacheGeneration() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = new ClientSettingsService(
                GuideDisplayConfig.defaults(),
                models.current,
                Set.of("ALPHA_KEY"),
                models,
                models,
                dispatcher::execute,
                worker);

        service.acceptMetadataUpdate(metadata(100_000));
        worker.runNext();
        service.acceptMetadataUpdate(metadata(200_000));
        worker.runNext();
        dispatcher.runAll();
        worker.runAll();
        dispatcher.runAll();

        assertFalse(models.publishedMetadataWindows.contains(100_000));
        assertTrue(models.publishedMetadataWindows.contains(200_000));
    }

    @Test
    void closeCancelsProbeAndClosesMetadataOwner() {
        ManualExecutor worker = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"), worker);
        models.probe = new CompletableFuture<>();

        CompletableFuture<ModelConnectionResult> probe =
                service.testConnection(profile("alpha"));
        service.closeAsync().join();

        assertEquals("connection_cancelled", assertInstanceOf(
                ModelConnectionResult.Failure.class, probe.join()).code());
        assertTrue(models.closed.get());
    }

    @Test
    void closeDoesNotCancelConfirmedSave() {
        ManualExecutor worker = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = service(
                models, Set.of("ALPHA_KEY", "BETA_KEY"), worker);

        CompletableFuture<ToolResult<Boolean>> save = service.saveModels(config("beta"));
        service.closeAsync().join();
        worker.runAll();

        assertSuccess(save.join());
        assertEquals("beta", service.snapshot().models().config().defaultProfileId());
    }

    private static ClientSettingsService service(FakeModels models, Set<String> environment) {
        return service(models, environment, Runnable::run);
    }

    private static ClientSettingsService service(
            FakeModels models, Set<String> environment, Executor worker) {
        return new ClientSettingsService(
                GuideDisplayConfig.defaults(),
                models.current,
                environment,
                models,
                models,
                Runnable::run,
                worker);
    }

    private static ModelProfilesConfig config(String... ids) {
        return new ModelProfilesConfig(
                ModelProfilesConfig.SCHEMA_VERSION,
                ids[0],
                java.util.Arrays.stream(ids).map(ClientSettingsServiceTest::profile).toList());
    }

    private static ModelProfileDefinition profile(String id) {
        return new ModelProfileDefinition(
                id,
                id.toUpperCase(),
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                "vendor/" + id,
                id.toUpperCase() + "_KEY",
                256_000,
                1_024,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
    }

    private static ModelMetadataUpdate metadata(int contextWindow) {
        ModelMetadata value = new ModelMetadata(
                "openrouter",
                "vendor/alpha",
                "vendor/alpha",
                contextWindow,
                4_096,
                Instant.EPOCH);
        return new ModelMetadataUpdate(Map.of(value.key(), value), null);
    }

    private static ClientSettingsService.ModelState state(ModelProfilesConfig config) {
        return new ClientSettingsService.ModelState(
                config,
                config.profiles().stream()
                        .map(ClientSettingsServiceTest::resolved)
                        .map(ModelProfileSettingsView.Resolution::from)
                        .toList());
    }

    private static ResolvedModelProfile resolved(ModelProfileDefinition definition) {
        return new ResolvedModelProfile(
                definition,
                new ModelConfig(
                        true,
                        definition.protocol(),
                        definition.baseUri(),
                        definition.model(),
                        SecretValue.of("secret-" + definition.id()),
                        definition.contextWindowTokens(),
                        definition.maxOutputTokens(),
                        definition.connectTimeout(),
                        definition.requestTimeout()),
                null);
    }

    private static void assertSuccess(ToolResult<Boolean> result) {
        assertInstanceOf(ToolResult.Success.class, result);
    }

    private static void assertFailure(ToolResult<?> result, String code) {
        assertEquals(code, assertInstanceOf(ToolResult.Failure.class, result).code());
    }

    private static final class FakeModels
            implements ClientSettingsService.ModelActions,
                    ClientSettingsService.MetadataActions {
        private ClientSettingsService.ModelState current;
        private ToolResult.Failure<ClientSettingsService.ModelState> saveFailure;
        private CompletableFuture<ModelConnectionResult> probe = CompletableFuture.completedFuture(
                new ModelConnectionResult.Success(
                        "alpha", ModelProtocol.OPENAI_CHAT, "https://provider.example",
                        Instant.EPOCH, 1));
        private final AtomicBoolean probeCancelled = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private String publishedDefault;
        private boolean saved;
        private final List<String> publishedDefaultsAfterSave = new ArrayList<>();
        private final List<Integer> publishedMetadataWindows = new ArrayList<>();

        private FakeModels(ClientSettingsService.ModelState current) {
            this.current = current;
            this.publishedDefault = current.config().defaultProfileId();
        }

        @Override
        public ToolResult<ClientSettingsService.ModelState> save(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            if (saveFailure != null) {
                return saveFailure;
            }
            current = state(candidate);
            publishedDefault = candidate.defaultProfileId();
            saved = true;
            return new ToolResult.Success<>(current);
        }

        @Override
        public ToolResult<ClientSettingsService.ModelState> reload(
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            return new ToolResult.Success<>(current);
        }

        @Override
        public ToolResult<ResolvedModelProfile> resolve(
                ModelProfileDefinition candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            return new ToolResult.Success<>(resolved(candidate));
        }

        @Override
        public ToolResult<ClientSettingsService.PreparedModels> prepare(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            ClientSettingsService.ModelState prepared = state(candidate);
            Integer metadataWindow = metadata.values().stream()
                    .findFirst()
                    .map(ModelMetadata::contextWindowTokens)
                    .orElse(null);
            return new ToolResult.Success<>(new ClientSettingsService.PreparedModels(
                    prepared,
                    () -> {
                        publishedDefault = candidate.defaultProfileId();
                        if (saved) {
                            publishedDefaultsAfterSave.add(publishedDefault);
                        }
                        if (metadataWindow != null) {
                            publishedMetadataWindows.add(metadataWindow);
                        }
                    }));
        }

        @Override
        public CompletableFuture<ModelConnectionResult> probe(
                ResolvedModelProfile profile, CancellationSignal cancellation) {
            cancellation.onCancel(() -> probeCancelled.set(true));
            return probe;
        }

        @Override
        public CompletableFuture<Void> refresh() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> closeAsync() {
            closed.set(true);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.removeFirst().run();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }

        private void runLast() {
            tasks.removeLast().run();
        }
    }
}
