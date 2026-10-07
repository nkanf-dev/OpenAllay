package dev.openallay.settings;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuidePersistenceSnapshot;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.capability.CapabilityCatalogSnapshot;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.recipe.config.RecipeClientConfig;
import dev.openallay.recipe.RecipeVisibilityPolicy;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.catalog.ModelCatalog;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.model.metadata.ModelMetadataUpdate;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsAggregator;
import dev.openallay.settings.extension.ExtensionSettingsView;
import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.skill.SkillMetadata;
import dev.openallay.skill.SkillSource;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.tool.ToolResult;
import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.requirement.RequirementStatus;
import dev.openallay.settings.requirement.PreparedPackageInstall;
import dev.openallay.settings.requirement.RequirementReview;
import dev.openallay.settings.requirement.RequirementSettingsEnvironment;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ClientSettingsServiceTest {
    private static final class ForeignConnectionResult implements ModelConnectionResult {}

    @Test
    void foreignProbeResultCompletesExceptionallyAndPublishesOnlyKnownCleanup() {
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));
        ModelConnectionResult previous = service.testConnection(profile("alpha")).join();
        List<ClientSettingsSnapshot> published = new ArrayList<>();
        service.listen(published::add);
        models.probe = new CompletableFuture<>();
        CompletableFuture<ModelConnectionResult> outward = service.testConnection(profile("alpha"));
        java.util.concurrent.atomic.AtomicReference<Throwable> observed = new java.util.concurrent.atomic.AtomicReference<>();
        outward.whenComplete((value, failure) -> observed.set(failure));
        models.probe.complete(new ForeignConnectionResult());
        java.util.concurrent.CompletionException joined = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class, outward::join);
        IncompatibleClassChangeError foreign = assertInstanceOf(IncompatibleClassChangeError.class, joined.getCause());
        org.junit.jupiter.api.Assertions.assertSame(foreign, observed.get());
        assertEquals("Unknown model connection result subtype", foreign.getMessage());
        assertTrue(outward.isCompletedExceptionally());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
        org.junit.jupiter.api.Assertions.assertSame(previous, service.snapshot().models().connectionResult());
        for (ClientSettingsSnapshot snapshot : published) {
            org.junit.jupiter.api.Assertions.assertSame(previous, snapshot.models().connectionResult());
        }
        assertFalse(service.cancelConnectionTest());
        models.probe = CompletableFuture.completedFuture(new ModelConnectionResult.Failure("synthetic", "Synthetic failure"));
        assertEquals("synthetic", assertInstanceOf(ModelConnectionResult.Failure.class,
                service.testConnection(profile("alpha")).join()).code());
    }

    @Test
    void cancelledForeignProbeCompletionCannotPublishOrReplaceCancellation() {
        FakeModels models = new FakeModels(state(config("alpha")));
        models.probe = new CompletableFuture<>();
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));
        CompletableFuture<ModelConnectionResult> outward = service.testConnection(profile("alpha"));
        assertTrue(service.cancelConnectionTest());
        ModelConnectionResult cancelled = outward.join();
        long generation = service.snapshot().generation();
        models.probe.complete(new ForeignConnectionResult());
        org.junit.jupiter.api.Assertions.assertSame(cancelled, outward.join());
        org.junit.jupiter.api.Assertions.assertSame(cancelled, service.snapshot().models().connectionResult());
        assertEquals(generation, service.snapshot().generation());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
    }

    @Test
    void closedForeignProbeCompletionCannotPublishOrLeaveFuturePending() {
        FakeModels models = new FakeModels(state(config("alpha")));
        models.probe = new CompletableFuture<>();
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));
        CompletableFuture<ModelConnectionResult> outward = service.testConnection(profile("alpha"));
        service.closeAsync().join();
        ModelConnectionResult cancelled = outward.join();
        long generation = service.snapshot().generation();
        models.probe.complete(new ForeignConnectionResult());
        org.junit.jupiter.api.Assertions.assertSame(cancelled, outward.join());
        assertEquals(generation, service.snapshot().generation());
        assertTrue(models.closed.get());
    }

    @Test
    void connectionSnapshotConstructorAndSchemaRejectForeignButKeepKnownAndNotTested() {
        FakeModels models = new FakeModels(state(config("alpha")));
        ModelProfileSettingsView baseline = service(models, Set.of("ALPHA_KEY")).snapshot().models();
        assertEquals(null, baseline.connectionResult());
        ForeignConnectionResult foreign = new ForeignConnectionResult();
        org.junit.jupiter.api.Assertions.assertThrows(IncompatibleClassChangeError.class, () ->
                new ModelProfileSettingsView(baseline.config(), baseline.profiles(), baseline.metadataFailure(), foreign));
        org.junit.jupiter.api.Assertions.assertThrows(IncompatibleClassChangeError.class, () ->
                dev.openallay.value.ValueSchemas.of(ModelProfileSettingsView.class).construct(new Object[] {
                        baseline.config(), baseline.profiles(), baseline.metadataFailure(), foreign}));
        ModelConnectionResult known = new ModelConnectionResult.Failure("synthetic", "Synthetic failure");
        ModelProfileSettingsView accepted = new ModelProfileSettingsView(
                baseline.config(), baseline.profiles(), baseline.metadataFailure(), known);
        org.junit.jupiter.api.Assertions.assertSame(known, accepted.connectionResult());
        assertEquals(accepted, dev.openallay.value.ValueSchemas.of(ModelProfileSettingsView.class).construct(new Object[] {
                baseline.config(), baseline.profiles(), baseline.metadataFailure(), known}));
    }

    @Test
    void actualEngineJsonAggregateConstructorRejectsDecodedForeignConnectionResult() {
        FakeModels models = new FakeModels(state(config("alpha")));
        ModelProfileSettingsView baseline = service(models, Set.of("ALPHA_KEY")).snapshot().models();
        ForeignConnectionResult foreign = new ForeignConnectionResult();
        com.google.gson.Gson gson = dev.openallay.json.EngineJson.derive(dev.openallay.json.EngineJson.create(), builder ->
                builder.registerTypeAdapter(ModelConnectionResult.class, new com.google.gson.TypeAdapter<ModelConnectionResult>() {
                    public void write(com.google.gson.stream.JsonWriter out, ModelConnectionResult value) throws java.io.IOException {
                        out.beginObject().name("synthetic").value(true).endObject();
                    }
                    public ModelConnectionResult read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        in.skipValue(); return foreign;
                    }
                }));
        com.google.gson.JsonObject document = dev.openallay.json.EngineJson.create().toJsonTree(baseline).getAsJsonObject();
        document.add("connectionResult", new com.google.gson.JsonObject());
        org.junit.jupiter.api.Assertions.assertThrows(IncompatibleClassChangeError.class,
                () -> gson.fromJson(document, ModelProfileSettingsView.class));
    }

    @Test
    void serverModelCapabilityIsConnectionScopedAndNeverEntersLocalProfiles() {
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));
        CapabilityPayload advertised = new CapabilityPayload(
                List.of(),
                true,
                100_000,
                8_192,
                6_000,
                "server/deepseek");

        service.replaceServerModel(advertised).join();

        assertTrue(service.snapshot().serverModel().available());
        assertEquals("server/deepseek", service.snapshot().serverModel().canonicalModelId());
        assertEquals(List.of("alpha"), service.snapshot().models().config().profiles().stream()
                .map(ModelProfileDefinition::id)
                .toList());

        service.clearServerModel().join();

        assertFalse(service.snapshot().serverModel().available());
        assertEquals(List.of("alpha"), service.snapshot().models().config().profiles().stream()
                .map(ModelProfileDefinition::id)
                .toList());
    }

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
        assertEquals(4, snapshot.diagnostics().cards().size());
        assertTrue(snapshot.diagnostics().debug().isEmpty());
    }

    @Test
    void debugDisplayAddsOnlyTheSeparateTechnicalDiagnosticsProjection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = new ClientSettingsService(
                new GuideDisplayConfig(true, true,
                        GuideDisplayConfig.DEFAULT_ASSISTANT_NAME),
                models.current,
                Set.of("ALPHA_KEY"),
                models,
                models,
                Runnable::run,
                Runnable::run);

        ClientSettingsSnapshot snapshot = service.snapshot();

        assertEquals(4, snapshot.diagnostics().cards().size());
        assertTrue(snapshot.diagnostics().debug().isPresent());
        assertFalse(snapshot.diagnostics().toString().contains("secret-alpha"));
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
    void modelCatalogSharesForegroundSlotAndExplicitCancellationSuppressesLateResult() {
        FakeModels models = new FakeModels(state(config("alpha")));
        models.catalog = new CompletableFuture<>();
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));
        ModelCatalogRequest request = catalogRequest();

        CompletableFuture<ToolResult<ModelCatalog>> pending =
                service.fetchModelCatalog(request, SecretValue.of("typed-secret"));

        assertEquals(SettingsOperation.Kind.FETCHING_MODEL_CATALOG,
                service.snapshot().operation().kind());
        assertTrue(service.snapshot().operation().cancellable());
        assertFailure(service.saveModels(config("beta")).join(), "settings_busy");
        assertTrue(service.cancelModelCatalog());
        assertEquals("model_catalog_cancelled",
                assertInstanceOf(ToolResult.Failure.class, pending.join()).code());
        assertTrue(models.catalogCancelled.get());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());

        models.catalog.complete(new ToolResult.Success<>(
                new ModelCatalog(List.of("late/model"))));
        assertEquals("model_catalog_cancelled", service.snapshot().notice().code());
        assertFalse(service.snapshot().toString().contains("typed-secret"));
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
    void ordinaryFifoMetadataCompletionCannotRollBackAlreadySavedRuntime() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = new ClientSettingsService(
                GuideDisplayConfig.defaults(),
                models.current,
                Set.of(),
                models,
                models,
                dispatcher::execute,
                worker);
        List<String> publicationTrace = new ArrayList<>();
        publicationTrace.add("initial: " + List.copyOf(models.publishedDefaults));

        service.acceptMetadataUpdate(new ModelMetadataUpdate(Map.of(), null));
        publicationTrace.add("metadata accepted: " + List.copyOf(models.publishedDefaults));
        worker.runNext(); // Prepare alpha without publishing; queue its completion first.
        publicationTrace.add("metadata prepared: " + List.copyOf(models.publishedDefaults));
        assertEquals(List.of("alpha"), models.publishedDefaults);

        CompletableFuture<ToolResult<Boolean>> save = service.saveModels(config("beta"));
        publicationTrace.add("save accepted: " + List.copyOf(models.publishedDefaults));
        worker.runNext(); // The real backend writes and publishes beta before queueing finishModels.
        publicationTrace.add("save worker completed: " + List.copyOf(models.publishedDefaults));
        assertEquals(List.of("alpha", "beta"), models.publishedDefaults);
        assertEquals("beta", models.current.config().defaultProfileId());
        assertEquals("alpha", service.snapshot().models().config().defaultProfileId());
        assertFalse(save.isDone());

        dispatcher.runAll(); // Ordinary FIFO: alpha metadata completion, then beta save completion.
        publicationTrace.add("FIFO dispatched: " + List.copyOf(models.publishedDefaults));
        worker.runAll();
        dispatcher.runAll();
        publicationTrace.add("queues drained: " + List.copyOf(models.publishedDefaults));
        assertTrue(save.isDone());
        assertSuccess(save.join());

        // current changes only on save (disk proxy); publishedDefault is the runtime registry proxy.
        assertAll(
                () -> assertEquals("beta", models.current.config().defaultProfileId(),
                        "The synthetic saved configuration must remain beta"),
                () -> assertEquals("beta", service.snapshot().models().config().defaultProfileId(),
                        "The final settings/UI projection must show beta"),
                () -> assertEquals("beta", models.publishedDefault,
                        "Runtime publication must match the saved configuration: " + publicationTrace),
                () -> assertFalse(models.publishedDefaultsAfterSave.contains("alpha"),
                        "Prepared alpha must never publish after beta was saved: " + publicationTrace));
    }

    @Test
    void ordinaryFifoMetadataCompletionCannotRollBackAlreadyReloadedRuntime() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = new ClientSettingsService(
                GuideDisplayConfig.defaults(), models.current, Set.of(), models, models,
                dispatcher::execute, worker);
        service.acceptMetadataUpdate(new ModelMetadataUpdate(Map.of(), null));
        worker.runNext();
        models.current = state(config("beta")); // Simulate a changed settings file before reload.

        var reload = service.reloadModels(true);
        worker.runNext();
        assertEquals("beta", models.publishedDefault);
        assertEquals("alpha", service.snapshot().models().config().defaultProfileId());
        dispatcher.runAll();
        worker.runAll();
        dispatcher.runAll();

        assertSuccess(reload.join());
        assertEquals("beta", service.snapshot().models().config().defaultProfileId());
        assertEquals("beta", models.publishedDefault);
        assertFalse(models.publishedDefaultsAfterSave.contains("alpha"));
    }

    @Test
    void metadataArrivingDuringSaveReconcilesOnlyAfterCommittedConfigIsReady() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = new ClientSettingsService(
                GuideDisplayConfig.defaults(), models.current, Set.of(), models, models,
                dispatcher::execute, worker);
        var save = service.saveModels(config("beta"));
        service.acceptMetadataUpdate(metadata(200_000));
        worker.runLast(); // Prepare from the still-displayed alpha while save is pending.
        worker.runNext(); // Commit beta, but leave its owner completion queued.
        dispatcher.runAll();
        assertTrue(models.publishedMetadataWindows.isEmpty());
        worker.runAll();
        dispatcher.runAll();

        assertSuccess(save.join());
        assertEquals("beta", models.publishedDefault);
        assertFalse(models.publishedDefaultsAfterSave.contains("alpha"));
        assertTrue(models.publishedMetadataWindows.contains(200_000),
                "The newest metadata must be retried, not disabled by the pending save");
    }

    @Test
    void metadataDeferredByFailedSaveStillReconcilesTheLastValidConfig() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        models.saveFailure = new ToolResult.Failure<>("settings_write_failed", "Unable to save settings");
        ClientSettingsService service = new ClientSettingsService(
                GuideDisplayConfig.defaults(), models.current, Set.of(), models, models,
                dispatcher::execute, worker);
        service.acceptMetadataUpdate(metadata(200_000));
        worker.runNext();
        var save = service.saveModels(config("beta"));
        dispatcher.runAll(); // Metadata callback arrives while the save worker is still pending.
        assertTrue(models.publishedMetadataWindows.isEmpty());
        worker.runAll();
        dispatcher.runAll();
        worker.runAll();
        dispatcher.runAll();

        assertFailure(save.join(), "settings_write_failed");
        assertEquals("alpha", models.publishedDefault);
        assertTrue(models.publishedMetadataWindows.contains(200_000));
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

    @Test
    void recipeChildSaveWritesOnlyRecipeDomain() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        ClientSettingsService service = service(models, domains, Runnable::run);
        RecipeClientConfig candidate = new RecipeClientConfig(
                RecipeVisibilityPolicy.ALL_KNOWN,
                "viewer:rei",
                Set.of("viewer:jei"));

        assertSuccess(service.saveRecipeSettings(candidate).join());

        assertEquals(1, domains.recipeSaves);
        assertEquals(0, domains.capabilitySaves);
        assertEquals(0, models.saveCalls);
        assertEquals(candidate, service.snapshot().recipes().config());
    }

    @Test
    void capabilityDependencyFailureRetainsPriorProjection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        domains.capabilityFailure = new ToolResult.Failure<>(
                "capability_dependency_conflict", "Skill requires a disabled Tool");
        ClientSettingsService service = service(models, domains, Runnable::run);
        CapabilitySettingsView prior = service.snapshot().capabilities();

        ToolResult<Boolean> result = service.saveCapabilities(new CapabilityPolicy(
                Set.of("test:fact"), Set.of())).join();

        assertFailure(result, "capability_dependency_conflict");
        assertEquals(prior, service.snapshot().capabilities());
        assertEquals(0, domains.recipeSaves);
    }

    @Test
    void capabilityAndRecipeActionsShareForegroundSlot() {
        ManualExecutor worker = new ManualExecutor();
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        ClientSettingsService service = service(models, domains, worker);

        CompletableFuture<ToolResult<Boolean>> pending = service.saveCapabilities(
                CapabilityPolicy.defaults());

        assertFailure(
                service.saveRecipeSettings(RecipeClientConfig.defaults()).join(),
                "settings_busy");
        worker.runAll();
        assertSuccess(pending.join());
        assertEquals(1, domains.capabilitySaves);
        assertEquals(0, domains.recipeSaves);
    }

    @Test
    void domainReloadRequiresDiscardConfirmationAndUpdatesOnlyThatView() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        ClientSettingsService service = service(models, domains, Runnable::run);

        assertFailure(service.reloadCapabilities(false).join(),
                "settings_discard_confirmation_required");
        assertSuccess(service.reloadRecipeSettings(true).join());

        assertEquals(0, domains.capabilityReloads);
        assertEquals(1, domains.recipeReloads);
        assertEquals("recipes_reloaded", service.snapshot().notice().code());
    }

    @Test
    void communitySkillInstallPublishesTheReloadedSkillProjection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeSkills skills = new FakeSkills();
        ClientSettingsService service = service(
                models,
                domains,
                display,
                skills,
                new FakeHistory(),
                Runnable::run);

        ToolResult<Boolean> installed = service.installCommunitySkill("demo").join();

        assertSuccess(installed);
        assertTrue(service.snapshot().skills().skills().isEmpty());
        RequirementReview preview = service.snapshot().requirementReview().orElseThrow();
        assertSuccess(service.continuePackageInstall(preview.token()).join());
        assertEquals("demo", service.snapshot().skills().skills().getFirst().metadata().name());
        assertEquals("package_installed", service.snapshot().notice().code());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
        assertTrue(service.snapshot().skillCommunity().packages().getFirst().installed());
    }

    @Test
    void commandOnlySaveRunsOnWorkerAndPublishesOnlySuccessfulResult() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeExtensions extensions = new FakeExtensions();
        FakeDomains domains = new FakeDomains();
        ManualExecutor worker = new ManualExecutor();
        ClientSettingsService service = service(models, domains,
                new FakeDisplay(GuideDisplayConfig.defaults()), new FakeSkills(), extensions,
                new FakeHistory(), worker);
        ExtensionSettingsView before = service.snapshot().extensions();
        CompletableFuture<ToolResult<Boolean>> pending = service.saveExperimentalCommands(true);

        assertEquals(0, domains.commandSaves);
        assertFalse(pending.isDone());
        assertFalse(service.snapshot().experimentalCommands().enabled());
        assertEquals(SettingsOperation.Kind.SAVING_EXPERIMENTAL_COMMANDS, service.snapshot().operation().kind());
        assertFailure(service.saveExperimentalCommands(false).join(), "settings_busy");
        assertEquals(before, service.snapshot().extensions());
        worker.runNext();
        assertSuccess(pending.join());
        assertTrue(service.snapshot().experimentalCommands().enabled());
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        assertEquals(before, service.snapshot().extensions());
        assertEquals("experimental_commands_saved", service.snapshot().notice().code());
        assertEquals(1, domains.commandSaves);
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());

        domains.commandFailure = new ToolResult.Failure<>("settings_write_failed", "Unable to save settings");
        CompletableFuture<ToolResult<Boolean>> failed = service.saveExperimentalCommands(false);
        assertFalse(failed.isDone());
        worker.runNext();
        assertFailure(failed.join(), "settings_write_failed");
        assertTrue(service.snapshot().experimentalCommands().enabled());
        assertEquals(before, service.snapshot().extensions());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
        domains.commandFailure = null;
        CompletableFuture<ToolResult<Boolean>> disabled = service.saveExperimentalCommands(false);
        worker.runNext();
        assertSuccess(disabled.join());
        assertFalse(service.snapshot().experimentalCommands().enabled());
        assertEquals(before, service.snapshot().extensions());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
    }

    @Test
    void fullAccessUpdatesCommandRequirementWithoutChangingStoredCommandOnlyChoice() {
        FakeDomains domains = new FakeDomains();
        FakeSkills skills = new FakeSkills();
        skills.requirements = new RequirementSet(
                Set.of(RequirementSettingsEnvironment.EXPERIMENTAL_COMMANDS), Set.of(), Set.of());
        ManualExecutor worker = new ManualExecutor();
        ClientSettingsService service = requirementService(domains, skills, worker);
        var preparing = service.installCommunitySkill("demo");
        worker.runAll();
        assertSuccess(preparing.join());
        var preview = service.snapshot().requirementReview().orElseThrow();
        assertEquals(RequirementStatus.DISABLED, preview.report().entries().getFirst().status());

        var fullAccess = service.saveUnrestrictedJavascript(true);
        assertFalse(fullAccess.isDone());
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        worker.runAll();
        assertSuccess(fullAccess.join());
        assertFalse(service.snapshot().experimentalCommands().enabled());
        assertEquals(RequirementStatus.SATISFIED,
                service.snapshot().requirementReview().orElseThrow().report().entries().getFirst().status());
        assertTrue(service.snapshot().requirementReview().orElseThrow().changes().isEmpty());
        assertFailure(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                RequirementSettingsEnvironment.EXPERIMENTAL_COMMANDS, false).join(), "requirement_not_enableable");
        assertEquals(0, skills.prepared.commits);

        var normal = service.saveUnrestrictedJavascript(false);
        worker.runAll();
        assertSuccess(normal.join());
        assertFalse(service.snapshot().experimentalCommands().enabled());
        assertEquals(RequirementStatus.DISABLED,
                service.snapshot().requirementReview().orElseThrow().report().entries().getFirst().status());
        assertEquals(List.of(new dev.openallay.settings.requirement.RequirementChange(
                        RequirementKind.CAPABILITY, RequirementSettingsEnvironment.EXPERIMENTAL_COMMANDS, false)),
                service.snapshot().requirementReview().orElseThrow().changes());
    }

    @Test
    void communityExtensionInstallPublishesRestartRequiredImmutableProjection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeExtensions extensions = new FakeExtensions();
        ClientSettingsService service = service(
                models,
                domains,
                display,
                new FakeSkills(),
                extensions,
                new FakeHistory(),
                Runnable::run);

        ToolResult<Boolean> staged =
                service.installCommunityExtension("community:demo").join();

        assertSuccess(staged);
        assertSuccess(service.continuePackageInstall(
                service.snapshot().requirementReview().orElseThrow().token()).join());
        assertEquals(
                ExtensionSettingsView.State.RESTART_REQUIRED,
                service.snapshot().extensions().extensions().stream()
                        .filter(extension -> extension.id().equals("community:demo"))
                        .findFirst()
                        .orElseThrow()
                        .state());
        assertEquals("package_installed", service.snapshot().notice().code());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
    }

    @Test
    void localExtensionImportDoesNotRequireACommunitySelection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        ClientSettingsService service = service(
                models,
                domains,
                display,
                new FakeSkills(),
                new FakeExtensions(),
                new FakeHistory(),
                Runnable::run);

        ToolResult<Boolean> staged =
                service.importLocalExtensionPackage(Path.of("local-extension.jar")).join();

        assertSuccess(staged);
        assertTrue(service.snapshot().requirementReview().isPresent());
        assertSuccess(service.continuePackageInstall(
                service.snapshot().requirementReview().orElseThrow().token()).join());
        assertEquals("package_installed", service.snapshot().notice().code());
        assertEquals(SettingsOperation.Kind.IDLE, service.snapshot().operation().kind());
    }

    @Test
    void unmetRequirementsContinuePublishesOnlyCandidateWithoutGrants() {
        FakeDomains domains = new FakeDomains();
        FakeSkills skills = new FakeSkills();
        skills.requirements = new RequirementSet(
                Set.of(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT, "other:unknown"),
                Set.of("missing:extension"), Set.of("missing-skill"));
        ClientSettingsService service = requirementService(domains, skills, Runnable::run);

        assertSuccess(service.importLocalSkillPackage(Path.of("demo.zip")).join());
        var review = service.snapshot().requirementReview().orElseThrow();
        assertEquals(List.of(RequirementStatus.DISABLED, RequirementStatus.UNKNOWN,
                        RequirementStatus.MISSING, RequirementStatus.MISSING),
                review.report().entries().stream().map(entry -> entry.status()).toList());
        assertEquals(0, skills.prepared.commits);
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        assertSuccess(service.continuePackageInstall(review.token()).join());
        assertEquals(1, skills.prepared.commits);
        assertEquals(1, skills.prepared.closes);
        assertEquals(0, domains.capabilitySaves);
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        assertFalse(service.snapshot().experimentalCommands().enabled());
        assertTrue(service.snapshot().requirementReview().isEmpty());
        assertFailure(service.continuePackageInstall(review.token()).join(), "package_preview_stale");
    }

    @Test
    void cancelAndReplacementInvalidateExactCandidateTokens() {
        FakeSkills skills = new FakeSkills();
        ClientSettingsService service = requirementService(new FakeDomains(), skills, Runnable::run);
        assertSuccess(service.installCommunitySkill("first").join());
        var first = service.snapshot().requirementReview().orElseThrow();
        FakePreparedPackage discarded = skills.prepared;
        assertSuccess(service.installCommunitySkill("second").join());
        var second = service.snapshot().requirementReview().orElseThrow();
        assertEquals(1, discarded.closes);
        assertEquals(0, discarded.commits);
        assertFailure(service.continuePackageInstall(first.token()).join(), "package_preview_stale");
        assertFalse(service.cancelPackageInstall(first.token()));
        assertTrue(service.cancelPackageInstall(second.token()));
        assertEquals(0, skills.prepared.commits);
        assertEquals(1, skills.prepared.closes);
        assertTrue(service.snapshot().skills().skills().isEmpty());
    }

    @Test
    void cancelledPreparationDiscardsLateResultWithoutReplacingNewPreview() {
        FakeSkills skills = new FakeSkills();
        skills.pendingPreparation = new CompletableFuture<>();
        ClientSettingsService service = requirementService(new FakeDomains(), skills, Runnable::run);
        var pending = service.installCommunitySkill("late");
        FakePreparedPackage late = skills.prepared;
        var lateFuture = skills.pendingPreparation;
        assertTrue(service.cancelPackagePreparation());
        assertTrue(skills.preparationCancellation.isCancelled());
        assertFailure(pending.join(), "package_preview_cancelled");
        skills.pendingPreparation = null;
        assertSuccess(service.installCommunitySkill("new").join());
        var current = service.snapshot().requirementReview().orElseThrow();
        lateFuture.complete(new ToolResult.Success<>(late));
        assertEquals(1, late.closes);
        assertEquals(0, late.commits);
        assertEquals(current.token(), service.snapshot().requirementReview().orElseThrow().token());
        assertEquals("new", service.snapshot().requirementReview().orElseThrow().id());
    }

    @Test
    void closeCancelsPreparationAndReleasesReadyCandidate() {
        FakeSkills skills = new FakeSkills();
        ClientSettingsService service = requirementService(new FakeDomains(), skills, Runnable::run);
        assertSuccess(service.installCommunitySkill("ready").join());
        var token = service.snapshot().requirementReview().orElseThrow().token();
        service.close();
        assertEquals(1, skills.prepared.closes);
        assertEquals(0, skills.prepared.commits);
        assertFailure(service.continuePackageInstall(token).join(), "settings_closed");

        FakeSkills pendingSkills = new FakeSkills();
        pendingSkills.pendingPreparation = new CompletableFuture<>();
        ClientSettingsService pendingService = requirementService(new FakeDomains(), pendingSkills, Runnable::run);
        var pending = pendingService.installCommunitySkill("late");
        pendingService.close();
        pendingSkills.pendingPreparation.complete(new ToolResult.Success<>(pendingSkills.prepared));
        assertFailure(pending.join(), "package_preview_cancelled");
        assertEquals(1, pendingSkills.prepared.closes);
        assertEquals(0, pendingSkills.prepared.commits);
    }

    @Test
    void unrestrictedEnableRequiresSeparateConsentAndDoesNotInstall() {
        FakeSkills skills = new FakeSkills();
        skills.requirements = new RequirementSet(
                Set.of(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT), Set.of(), Set.of());
        ClientSettingsService service = requirementService(new FakeDomains(), skills, Runnable::run);
        assertSuccess(service.installCommunitySkill("demo").join());
        var preview = service.snapshot().requirementReview().orElseThrow();
        assertEquals(1, preview.changes().size());
        assertTrue(preview.changes().getFirst().unrestrictedConsentRequired());
        assertFailure(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT, false).join(),
                "unrestricted_confirmation_required");
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        assertSuccess(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT, true).join());
        var refreshed = service.snapshot().requirementReview().orElseThrow();
        assertEquals(preview.token(), refreshed.token());
        assertTrue(refreshed.report().allSatisfied());
        assertTrue(refreshed.changes().isEmpty());
        assertEquals(0, skills.prepared.commits);
        assertTrue(service.cancelPackageInstall(preview.token()));
        assertTrue(service.snapshot().unrestrictedJavascript().enabled());
    }

    @Test
    void publishedBuilderAliasRequiresConsentAndEnablesOnlyTheExistingUnrestrictedOwner() {
        FakeDomains domains = new FakeDomains();
        var priorPolicy = domains.capabilities.policy();
        FakeSkills skills = new FakeSkills();
        skills.requirements = new RequirementSet(
                Set.of(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT_ALIAS,
                        "thirdparty:unrestricted-javascript"), Set.of("missing:extension"), Set.of());
        ClientSettingsService service = requirementService(domains, skills, Runnable::run);
        assertSuccess(service.installCommunitySkill("demo").join());
        var preview = service.snapshot().requirementReview().orElseThrow();
        assertEquals(1, preview.changes().size());
        assertEquals(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT_ALIAS,
                preview.changes().getFirst().id());
        assertTrue(preview.changes().getFirst().unrestrictedConsentRequired());
        assertFailure(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT_ALIAS, false).join(),
                "unrestricted_confirmation_required");
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        assertEquals(0, domains.unrestrictedSaves);
        assertEquals(0, domains.capabilitySaves);
        assertEquals(priorPolicy, service.snapshot().capabilities().policy());
        assertFailure(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                "thirdparty:unrestricted-javascript", true).join(), "requirement_not_enableable");
        assertSuccess(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT_ALIAS, true).join());
        assertTrue(service.snapshot().unrestrictedJavascript().enabled());
        assertEquals(1, domains.unrestrictedSaves);
        assertEquals(0, domains.capabilitySaves);
        assertFalse(service.snapshot().experimentalCommands().enabled());
        assertEquals(priorPolicy, service.snapshot().capabilities().policy());
        var refreshed = service.snapshot().requirementReview().orElseThrow();
        assertEquals(preview.token(), refreshed.token());
        assertTrue(refreshed.changes().isEmpty());
        var statuses = refreshed.report().entries().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.id(), entry -> entry.status()));
        assertEquals(dev.openallay.requirement.RequirementStatus.SATISFIED, statuses.get(
                RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT_ALIAS));
        assertEquals(dev.openallay.requirement.RequirementStatus.UNKNOWN,
                statuses.get("thirdparty:unrestricted-javascript"));
        assertEquals(dev.openallay.requirement.RequirementStatus.MISSING,
                statuses.get("missing:extension"));
        assertEquals(0, skills.prepared.commits);
        service.close();
    }

    @Test
    void failedExactEnableRetainsSettingsAndContinueRemainsAvailable() {
        FakeDomains domains = new FakeDomains();
        domains.capabilities = new CapabilitySettingsView(
                new CapabilityPolicy(Set.of("test:tool"), Set.of()),
                new CapabilityCatalogSnapshot(List.of(new dev.openallay.capability.CapabilitySettingsEntry(
                        "test:owner", "test:tool", dev.openallay.capability.CapabilityKind.TOOL,
                        "settings.test.title", "settings.test.description", null, true, false))), Set.of(), Set.of());
        domains.capabilityFailure = new ToolResult.Failure<>("settings_write_failed", "Disk unavailable");
        FakeSkills skills = new FakeSkills();
        skills.requirements = new RequirementSet(Set.of("test:tool", "unknown:tool"), Set.of(), Set.of());
        ClientSettingsService service = requirementService(domains, skills, Runnable::run);
        assertSuccess(service.installCommunitySkill("demo").join());
        var preview = service.snapshot().requirementReview().orElseThrow();
        assertEquals(List.of("test:tool"), preview.changes().stream().map(change -> change.id()).toList());
        assertFailure(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                "unknown:tool", true).join(), "requirement_not_enableable");
        assertFailure(service.enablePackageRequirement(preview.token(), RequirementKind.CAPABILITY,
                "test:tool", false).join(), "settings_write_failed");
        assertTrue(service.snapshot().capabilities().policy().disabledTools().contains("test:tool"));
        assertEquals(preview.token(), service.snapshot().requirementReview().orElseThrow().token());
        assertEquals("settings_write_failed", service.snapshot().notice().code());
        assertSuccess(service.continuePackageInstall(preview.token()).join());
        assertEquals(1, skills.prepared.commits);
    }

    @Test
    void absentUnrestrictedSettingsOwnerCannotPretendEnableSucceeded() {
        FakeModels models = new FakeModels(state(config("alpha")));
        ClientSettingsService service = service(models, Set.of("ALPHA_KEY"));
        assertFailure(service.saveUnrestrictedJavascript(true).join(), "settings_unavailable");
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
    }

    @Test
    void cancellingReviewDoesNotUndoAnAlreadyConfirmedRequirementChange() {
        ManualExecutor worker = new ManualExecutor();
        FakeSkills skills = new FakeSkills();
        skills.requirements = new RequirementSet(
                Set.of(RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT), Set.of(), Set.of());
        ClientSettingsService service = requirementService(new FakeDomains(), skills, worker);
        var preparing = service.installCommunitySkill("demo");
        worker.runAll();
        assertSuccess(preparing.join());
        var review = service.snapshot().requirementReview().orElseThrow();
        var enabled = service.enablePackageRequirement(review.token(), RequirementKind.CAPABILITY,
                RequirementSettingsEnvironment.UNRESTRICTED_JAVASCRIPT, true);
        assertTrue(service.cancelPackageInstall(review.token()));
        assertFalse(service.snapshot().unrestrictedJavascript().enabled());
        worker.runAll();
        assertSuccess(enabled.join());
        assertTrue(service.snapshot().unrestrictedJavascript().enabled());
        assertTrue(service.snapshot().requirementReview().isEmpty());
        assertEquals(0, skills.prepared.commits);
        assertEquals(1, skills.prepared.closes);
    }

    @Test
    void failedPublishConsumesTokenAndDisposesCandidate() {
        FakeSkills skills = new FakeSkills();
        skills.commitFailure = new ToolResult.Failure<>("publish_failed", "Unable to replace package");
        ClientSettingsService service = requirementService(new FakeDomains(), skills, Runnable::run);
        assertSuccess(service.installCommunitySkill("demo").join());
        var preview = service.snapshot().requirementReview().orElseThrow();
        assertFailure(service.continuePackageInstall(preview.token()).join(), "publish_failed");
        assertEquals(1, skills.prepared.closes);
        assertTrue(service.snapshot().skills().skills().isEmpty());
        assertTrue(service.snapshot().requirementReview().isEmpty());
        assertFailure(service.continuePackageInstall(preview.token()).join(), "package_preview_stale");
    }

    @Test
    void confirmedPublishSurvivesScreenDetachButCannotBeConfirmedTwice() {
        ManualExecutor worker = new ManualExecutor();
        FakeSkills skills = new FakeSkills();
        ClientSettingsService service = requirementService(new FakeDomains(), skills, worker);
        var preparing = service.installCommunitySkill("demo");
        worker.runAll();
        assertSuccess(preparing.join());
        var preview = service.snapshot().requirementReview().orElseThrow();
        var publishing = service.continuePackageInstall(preview.token());
        assertFalse(service.cancelPackageInstall(preview.token()));
        assertFailure(service.continuePackageInstall(preview.token()).join(), "settings_busy");
        service.close();
        worker.runAll();
        assertSuccess(publishing.join());
        assertEquals(1, skills.prepared.commits);
    }

    private static ClientSettingsService requirementService(
            FakeDomains domains, FakeSkills skills, Executor worker) {
        return service(new FakeModels(state(config("alpha"))), domains,
                new FakeDisplay(GuideDisplayConfig.defaults()), skills, new FakeHistory(), worker);
    }

    @Test
    void displaySavePublishesDebugProjectionOnlyAfterBackendSuccess() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeHistory history = new FakeHistory();
        ClientSettingsService service = service(
                models, domains, display, history, Runnable::run);

        assertSuccess(service.saveDisplay(new GuideDisplayConfig(
                true, true,
                GuideDisplayConfig.DEFAULT_ASSISTANT_NAME)).join());

        assertTrue(service.snapshot().display().debugMode());
        assertTrue(service.snapshot().diagnostics().debug().isPresent());
        assertEquals(1, display.saves);
        assertFalse(service.snapshot().toString().contains(
                history.state.guide().orElseThrow().actorId().toString()));
    }

    @Test
    void failedDisplaySaveRetainsNormalProjection() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        display.failure = new ToolResult.Failure<>(
                "settings_write_failed", "Unable to save settings");
        ClientSettingsService service = service(
                models, domains, display, new FakeHistory(), Runnable::run);

        assertFailure(service.saveDisplay(new GuideDisplayConfig(
                true, true,
                GuideDisplayConfig.DEFAULT_ASSISTANT_NAME)).join(),
                "settings_write_failed");

        assertFalse(service.snapshot().display().debugMode());
        assertTrue(service.snapshot().diagnostics().debug().isEmpty());
    }

    @Test
    void wholeDatabaseResetRequiresDebugModeAndFreshSecondConfirmation() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(new GuideDisplayConfig(
                true, true,
                GuideDisplayConfig.DEFAULT_ASSISTANT_NAME));
        FakeHistory history = new FakeHistory();
        ClientSettingsService service = service(
                models, domains, display, history, Runnable::run);

        ClientSettingsService.HistoryConfirmationToken first = successValue(
                service.requestHistoryConfirmation(
                        ClientSettingsService.HistoryAction.RESET_DATABASE));
        assertFailure(service.resetHistoryDatabase(first).join(),
                "history_delete_confirmation_required");
        ClientSettingsService.HistoryConfirmationToken second = successValue(
                service.confirmHistoryReset(first));

        ClientSettingsService.HistoryConfirmationToken staleFirst = successValue(
                service.requestHistoryConfirmation(
                        ClientSettingsService.HistoryAction.RESET_DATABASE));
        assertSuccess(service.saveDisplay(new GuideDisplayConfig(
                true, true,
                GuideDisplayConfig.DEFAULT_ASSISTANT_NAME)).join());
        assertFailure(service.confirmHistoryReset(staleFirst),
                "history_delete_confirmation_required");
        assertFailure(service.resetHistoryDatabase(second).join(),
                "history_delete_confirmation_required");

        ClientSettingsService.HistoryConfirmationToken freshFirst = successValue(
                service.requestHistoryConfirmation(
                        ClientSettingsService.HistoryAction.RESET_DATABASE));
        ClientSettingsService.HistoryConfirmationToken freshSecond = successValue(
                service.confirmHistoryReset(freshFirst));
        assertSuccess(service.resetHistoryDatabase(freshSecond).join());
        assertFailure(service.resetHistoryDatabase(freshSecond).join(),
                "history_delete_confirmation_required");
        assertEquals(1, history.databaseResets);
    }

    @Test
    void databaseResetIsUnrequestableWhileDebugModeIsOff() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeHistory history = new FakeHistory();
        ClientSettingsService service = service(
                models, domains, display, history, Runnable::run);

        assertFailure(service.requestHistoryConfirmation(
                ClientSettingsService.HistoryAction.RESET_DATABASE),
                "history_delete_confirmation_required");
        assertEquals(0, history.databaseResets);
    }

    @Test
    void historyConfirmationIsActionBoundAndOneUse() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeHistory history = new FakeHistory();
        ClientSettingsService service = service(
                models, domains, display, history, Runnable::run);
        ClientSettingsService.HistoryConfirmationToken current = successValue(
                service.requestHistoryConfirmation(
                        ClientSettingsService.HistoryAction.DELETE_CURRENT));

        assertFailure(service.deleteActorHistory(current).join(),
                "history_delete_confirmation_required");
        assertSuccess(service.deleteCurrentHistory(current).join());
        assertFailure(service.deleteCurrentHistory(current).join(),
                "history_delete_confirmation_required");
        assertEquals(1, history.currentDeletes);
        assertEquals(0, history.actorDeletes);
    }

    @Test
    void historyBusyStateRejectsConfirmationWithoutCallingBackend() {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeHistory history = new FakeHistory();
        history.state = new ClientSettingsService.HistoryRuntimeState(
                true,
                history.state.guide(),
                new GuideHistoryActivity(1, false),
                history.state.scopeKind());
        ClientSettingsService service = service(
                models, domains, display, history, Runnable::run);

        assertFailure(service.requestHistoryConfirmation(
                ClientSettingsService.HistoryAction.DELETE_CURRENT),
                "history_delete_busy");
        assertEquals(0, history.currentDeletes);
    }

    @Test
    void listenerDetachDuringConfirmedHistoryDeleteDoesNotCancelCommit() throws Exception {
        FakeModels models = new FakeModels(state(config("alpha")));
        FakeDomains domains = new FakeDomains();
        FakeDisplay display = new FakeDisplay(GuideDisplayConfig.defaults());
        FakeHistory history = new FakeHistory();
        history.currentResult = new CompletableFuture<>();
        ClientSettingsService service = service(
                models, domains, display, history, Runnable::run);
        List<ClientSettingsSnapshot> seen = new ArrayList<>();
        AutoCloseable listener = service.listen(seen::add);
        ClientSettingsService.HistoryConfirmationToken confirmation = successValue(
                service.requestHistoryConfirmation(
                        ClientSettingsService.HistoryAction.DELETE_CURRENT));

        CompletableFuture<ToolResult<Boolean>> deleting =
                service.deleteCurrentHistory(confirmation);
        listener.close();
        history.currentResult.complete(new ToolResult.Success<>(Boolean.TRUE));

        assertSuccess(deleting.join());
        assertEquals(1, history.currentDeletes);
        assertEquals(2, seen.size());
        assertEquals("history_current_deleted", service.snapshot().notice().code());
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

    private static ClientSettingsService service(
            FakeModels models, FakeDomains domains, Executor worker) {
        return new ClientSettingsService(
                GuideDisplayConfig.defaults(),
                models.current,
                Set.of("ALPHA_KEY"),
                models,
                models,
                domains.capabilities,
                domains,
                domains.recipes,
                domains,
                Runnable::run,
                worker,
                null);
    }

    private static ClientSettingsService service(
            FakeModels models,
            FakeDomains domains,
            FakeDisplay display,
            FakeSkills skills,
            FakeExtensions extensions,
            FakeHistory history,
            Executor worker) {
        ClientSettingsService.CommandActions commands =
                new ClientSettingsService.CommandActions() {
                    @Override
                    public ToolResult<CommandCapabilityConfig> save(
                            CommandCapabilityConfig candidate) {
                        domains.commandSaves++;
                        if (domains.commandFailure != null) return domains.commandFailure;
                        return new ToolResult.Success<>(candidate);
                    }

                    @Override
                    public ToolResult<CommandCapabilityConfig> reload() {
                        return new ToolResult.Success<>(CommandCapabilityConfig.defaults());
                    }
                };
        return new ClientSettingsService(
                display.current,
                display,
                models.current,
                Set.of("ALPHA_KEY"),
                models,
                models,
                domains.capabilities,
                domains,
                domains.recipes,
                domains,
                SkillSettingsView.empty(),
                skills,
                extensions.currentView(),
                extensions,
                CommandCapabilityConfig.defaults(),
                commands,
                history,
                Runnable::run,
                worker,
                null);
    }

    private static ClientSettingsService service(
            FakeModels models,
            FakeDomains domains,
            FakeDisplay display,
            FakeHistory history,
            Executor worker) {
        return new ClientSettingsService(
                display.current,
                display,
                models.current,
                Set.of("ALPHA_KEY"),
                models,
                models,
                domains.capabilities,
                domains,
                domains.recipes,
                domains,
                history,
                Runnable::run,
                worker,
                null);
    }

    private static ClientSettingsService service(
            FakeModels models,
            FakeDomains domains,
            FakeDisplay display,
            FakeSkills skills,
            FakeHistory history,
            Executor worker) {
        ClientSettingsService.CommandActions commands =
                new ClientSettingsService.CommandActions() {
                    @Override
                    public ToolResult<CommandCapabilityConfig> save(
                            CommandCapabilityConfig candidate) {
                        domains.commandSaves++;
                        if (domains.commandFailure != null) return domains.commandFailure;
                        return new ToolResult.Success<>(candidate);
                    }

                    @Override
                    public ToolResult<CommandCapabilityConfig> reload() {
                        return new ToolResult.Success<>(CommandCapabilityConfig.defaults());
                    }
                };
        return new ClientSettingsService(
                display.current,
                display,
                models.current,
                Set.of("ALPHA_KEY"),
                models,
                models,
                domains.capabilities,
                domains,
                domains.recipes,
                domains,
                SkillSettingsView.empty(),
                skills,
                ExtensionSettingsView.defaults(),
                new FakeExtensions(),
                CommandCapabilityConfig.defaults(),
                commands,
                new ClientSettingsService.UnrestrictedJavascriptActions() {
                    public ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> save(
                            dev.openallay.script.UnrestrictedJavascriptConfig candidate) {
                        domains.unrestrictedSaves++;
                        return new ToolResult.Success<>(candidate);
                    }
                    public ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> reload() {
                        return new ToolResult.Success<>(dev.openallay.script.UnrestrictedJavascriptConfig.defaults());
                    }
                },
                dev.openallay.script.UnrestrictedJavascriptConfig.defaults(),
                history,
                Runnable::run,
                worker,
                null);
    }

    private static ModelProfilesConfig config(String... ids) {
        return new ModelProfilesConfig(
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

    private static ModelCatalogRequest catalogRequest() {
        ModelProfileDefinition profile = profile("alpha");
        return new ModelCatalogRequest(
                profile.id(),
                profile.protocol(),
                profile.baseUri(),
                profile.credentialRef(),
                profile.connectTimeout(),
                profile.requestTimeout());
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

    private static <T> T successValue(ToolResult<T> result) {
        assertInstanceOf(ToolResult.Success.class, result);
        return ((ToolResult.Success<T>) result).value();
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
        private CompletableFuture<ToolResult<ModelCatalog>> catalog =
                CompletableFuture.completedFuture(new ToolResult.Success<>(
                        new ModelCatalog(List.of("vendor/model"))));
        private final AtomicBoolean catalogCancelled = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private String publishedDefault;
        private final List<String> publishedDefaults = new ArrayList<>();
        private int saveCalls;
        private boolean saved;
        private final List<String> publishedDefaultsAfterSave = new ArrayList<>();
        private final List<Integer> publishedMetadataWindows = new ArrayList<>();

        private FakeModels(ClientSettingsService.ModelState current) {
            this.current = current;
            this.publishedDefault = current.config().defaultProfileId();
            this.publishedDefaults.add(publishedDefault);
        }

        @Override
        public ToolResult<ClientSettingsService.ModelState> save(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            saveCalls++;
            if (saveFailure != null) {
                return saveFailure;
            }
            current = state(candidate);
            publishedDefault = candidate.defaultProfileId();
            publishedDefaults.add(publishedDefault);
            saved = true;
            return new ToolResult.Success<>(current);
        }

        @Override
        public ToolResult<ClientSettingsService.ModelState> reload(
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            publishedDefault = current.config().defaultProfileId();
            publishedDefaults.add(publishedDefault);
            saved = true;
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
                        publishedDefaults.add(publishedDefault);
                        if (saved) {
                            publishedDefaultsAfterSave.add(publishedDefault);
                        }
                        if (metadataWindow != null) {
                            publishedMetadataWindows.add(metadataWindow);
                        }
                        return true;
                    }));
        }

        @Override
        public CompletableFuture<ModelConnectionResult> probe(
                ResolvedModelProfile profile, CancellationSignal cancellation) {
            cancellation.onCancel(() -> probeCancelled.set(true));
            return probe;
        }

        @Override
        public CompletableFuture<ToolResult<ModelCatalog>> fetchCatalog(
                ModelCatalogRequest request,
                SecretValue replacement,
                CancellationSignal cancellation) {
            cancellation.onCancel(() -> catalogCancelled.set(true));
            return catalog;
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

    private static final class FakeDomains
            implements ClientSettingsService.CapabilityActions,
                    ClientSettingsService.RecipeActions {
        private CapabilitySettingsView capabilities = new CapabilitySettingsView(
                CapabilityPolicy.defaults(),
                new CapabilityCatalogSnapshot(List.of()),
                Set.of(),
                Set.of());
        private RecipeSettingsView recipes = RecipeSettingsView.defaults();
        private ToolResult.Failure<CapabilitySettingsView> capabilityFailure;
        private ToolResult.Failure<CommandCapabilityConfig> commandFailure;
        private int commandSaves;
        private int capabilitySaves;
        private int unrestrictedSaves;
        private int capabilityReloads;
        private int recipeSaves;
        private int recipeReloads;

        @Override
        public ToolResult<CapabilitySettingsView> saveCapabilities(CapabilityPolicy candidate) {
            capabilitySaves++;
            if (capabilityFailure != null) {
                return capabilityFailure;
            }
            capabilities = new CapabilitySettingsView(
                    candidate, capabilities.catalog(), Set.of(), Set.of());
            return new ToolResult.Success<>(capabilities);
        }

        @Override
        public ToolResult<CapabilitySettingsView> reloadCapabilities() {
            capabilityReloads++;
            return new ToolResult.Success<>(capabilities);
        }

        @Override
        public ToolResult<RecipeSettingsView> saveRecipes(RecipeClientConfig candidate) {
            recipeSaves++;
            recipes = new RecipeSettingsView(candidate, List.of(), Set.of(), false);
            return new ToolResult.Success<>(recipes);
        }

        @Override
        public ToolResult<RecipeSettingsView> reloadRecipes() {
            recipeReloads++;
            return new ToolResult.Success<>(recipes);
        }
    }

    private static final class FakeDisplay implements ClientSettingsService.DisplayActions {
        private GuideDisplayConfig current;
        private ToolResult.Failure<GuideDisplayConfig> failure;
        private int saves;

        private FakeDisplay(GuideDisplayConfig current) {
            this.current = current;
        }

        @Override
        public ToolResult<GuideDisplayConfig> saveDisplay(GuideDisplayConfig candidate) {
            saves++;
            if (failure != null) {
                return failure;
            }
            current = candidate;
            return new ToolResult.Success<>(candidate);
        }

        @Override
        public ToolResult<GuideDisplayConfig> reloadDisplay() {
            return new ToolResult.Success<>(current);
        }
    }

    private static final class FakeSkills implements ClientSettingsService.SkillActions {
        private SkillSettingsView current = SkillSettingsView.empty();
        private RequirementSet requirements = RequirementSet.EMPTY;
        private CompletableFuture<ToolResult<PreparedPackageInstall>> pendingPreparation;
        private CancellationSignal preparationCancellation;
        private FakePreparedPackage prepared;
        private ToolResult.Failure<Boolean> commitFailure;

        @Override
        public CompletableFuture<ToolResult<PreparedPackageInstall>> prepareCommunity(
                String id, CancellationSignal cancellation) {
            preparationCancellation = cancellation;
            prepared = new FakePreparedPackage(RequirementKind.SKILL, id, requirements, () -> {
                if (commitFailure != null) return commitFailure;
                installCommunity(id, new CancellationSignal());
                return new ToolResult.Success<>(true);
            });
            return pendingPreparation != null ? pendingPreparation
                    : CompletableFuture.completedFuture(new ToolResult.Success<>(prepared));
        }

        @Override
        public ToolResult<PreparedPackageInstall> prepareLocalPackage(Path source) {
            return prepareCommunity("demo", new CancellationSignal()).join();
        }

        private SkillCommunityView community = new SkillCommunityView(
                true,
                Optional.of(Instant.EPOCH),
                List.of(new SkillCommunityView.Package(
                        "demo",
                        "Demo Skill",
                        "A demo vertical workflow.",
                        "Test Publisher",
                        "1.0.0",
                        false,
                        false,
                        true,
                        "https://example.test/demo",
                        "https://example.test/demo.zip",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                Optional.empty());

        @Override
        public ToolResult<SkillSettingsView> saveOverride(String name, String markdown) {
            return new ToolResult.Failure<>("unsupported", "unsupported");
        }

        @Override
        public ToolResult<SkillSettingsView> deleteOverride(String name) {
            return new ToolResult.Failure<>("unsupported", "unsupported");
        }

        @Override
        public ToolResult<SkillSettingsView> reloadSkills() {
            return new ToolResult.Success<>(current);
        }

        @Override
        public SkillSettingsView currentView() {
            return current;
        }

        @Override
        public SkillCommunityView communityView() {
            return community;
        }

        @Override
        public CompletableFuture<ToolResult<SkillCommunityView>> installCommunity(
                String id,
                CancellationSignal cancellation) {
            current = new SkillSettingsView(List.of(new SkillSettingsView.Skill(
                    new SkillMetadata(
                            id,
                            "Installed from the community",
                            Optional.empty(),
                            Optional.empty(),
                            Map.of(),
                            Set.of(),
                            Set.of(),
                            List.of(),
                            "community:" + id,
                            SkillSource.Origin.LOCAL),
                    "Use this Skill.",
                    "---\nname: " + id
                            + "\ndescription: Installed from the community\n---\nUse this Skill.",
                    false)), List.of());
            SkillCommunityView.Package prior = community.packages().getFirst();
            community = new SkillCommunityView(
                    true,
                    community.generatedAt(),
                    List.of(new SkillCommunityView.Package(
                            prior.id(),
                            prior.displayName(),
                            prior.description(),
                            prior.publisher(),
                            prior.availableVersion(),
                            true,
                            false,
                            true,
                            prior.source(),
                            prior.archive(),
                            prior.sha256())),
                    Optional.empty());
            return CompletableFuture.completedFuture(new ToolResult.Success<>(community));
        }

        @Override
        public ToolResult<SkillCommunityView> importLocalPackage(Path source) {
            return new ToolResult.Success<>(community);
        }
    }

    private static final class FakeExtensions
            implements ClientSettingsService.ExtensionActions {
        private ExtensionSettingsView current;
        @Override
        public CompletableFuture<ToolResult<PreparedPackageInstall>> prepareCommunity(
                String id, CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(prepareLocalPackage(Path.of("demo.jar")));
        }

        @Override
        public ToolResult<PreparedPackageInstall> prepareLocalPackage(Path source) {
            return new ToolResult.Success<>(new FakePreparedPackage(
                    RequirementKind.EXTENSION, "community:demo", RequirementSet.EMPTY, () -> {
                        stage();
                        return new ToolResult.Success<>(true);
                    }));
        }

        private FakeExtensions() {
            ExtensionSettingsView base = ExtensionSettingsView.defaults();
            ExtensionSettingsView.Extension community = extension(
                    ExtensionSettingsView.State.COMMUNITY, true);
            current = new ExtensionSettingsView(
                    base.roots(),
                    base.bundledModules(),
                    base.adapters(),
                    List.of(base.extensions().getFirst(), community),
                    new ExtensionSettingsView.Catalog(
                            true,
                            true,
                            Optional.of(Instant.EPOCH),
                            Optional.empty()));
        }

        @Override
        public ExtensionSettingsView currentView() {
            return current;
        }

        @Override
        public CompletableFuture<ToolResult<ExtensionSettingsView>> installCommunity(
                String id, CancellationSignal cancellation) {
            return stage();
        }

        @Override
        public ToolResult<ExtensionSettingsView> importLocalPackage(Path source) {
            return stage().join();
        }

        private CompletableFuture<ToolResult<ExtensionSettingsView>> stage() {
            ExtensionSettingsView base = ExtensionSettingsView.defaults();
            current = new ExtensionSettingsView(
                    base.roots(),
                    base.bundledModules(),
                    base.adapters(),
                    List.of(
                            base.extensions().getFirst(),
                            extension(ExtensionSettingsView.State.RESTART_REQUIRED, false)),
                    current.catalog());
            return CompletableFuture.completedFuture(new ToolResult.Success<>(current));
        }

        private static ExtensionSettingsView.Extension extension(
                ExtensionSettingsView.State state, boolean installable) {
            return new ExtensionSettingsView.Extension(
                    "community:demo",
                    "Demo",
                    "1.0.0",
                    "Community",
                    "Demo Extension",
                    state,
                    List.of("fabric"),
                    "[26.2,26.3)",
                    "[0.2,0.3)",
                    "community",
                    new ExtensionSettingsView.Contributions(
                            List.of(), List.of(), List.of(), List.of(), List.of()),
                    state == ExtensionSettingsView.State.RESTART_REQUIRED
                            ? "restart_required"
                            : "",
                    new ExtensionSettingsView.PackageInfo(
                            true,
                            "1.0.0",
                            "https://example.test/demo.jar",
                            "a".repeat(64),
                            false,
                            installable));
        }
    }

    private static final class FakePreparedPackage implements PreparedPackageInstall {
        private final RequirementKind kind;
        private final String id;
        private final RequirementSet requirements;
        private final java.util.function.Supplier<ToolResult<Boolean>> publisher;
        private int commits;
        private int closes;

        private FakePreparedPackage(RequirementKind kind, String id, RequirementSet requirements,
                java.util.function.Supplier<ToolResult<Boolean>> publisher) {
            this.kind = kind;
            this.id = id;
            this.requirements = requirements;
            this.publisher = publisher;
        }
        public RequirementKind kind() { return kind; }
        public String id() { return id; }
        public String name() { return id; }
        public String version() { return "1.0.0"; }
        public String sha256() { return "a".repeat(64); }
        public RequirementSet requirements() { return requirements; }
        public ToolResult<Boolean> commit() { commits++; return publisher.get(); }
        public void close() { closes++; }
    }

    private static final class FakeHistory implements ClientSettingsService.HistoryActions {
        private ClientSettingsService.HistoryRuntimeState state = availableHistory();
        private CompletableFuture<ToolResult<Boolean>> currentResult =
                CompletableFuture.completedFuture(new ToolResult.Success<>(Boolean.TRUE));
        private int currentDeletes;
        private int actorDeletes;
        private int databaseResets;

        @Override
        public ClientSettingsService.HistoryRuntimeState state() {
            return state;
        }

        @Override
        public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() {
            currentDeletes++;
            return currentResult;
        }

        @Override
        public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() {
            actorDeletes++;
            return CompletableFuture.completedFuture(new ToolResult.Success<>(Boolean.TRUE));
        }

        @Override
        public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() {
            databaseResets++;
            return CompletableFuture.completedFuture(new ToolResult.Success<>(Boolean.TRUE));
        }

        private static ClientSettingsService.HistoryRuntimeState availableHistory() {
            GuideSnapshot guide = new GuideSnapshot(
                    UUID.fromString("21876009-3d3e-4092-952a-dd7212046fe8"),
                    "main",
                    GuideModelMode.CLIENT,
                    true,
                    false,
                    GuidePersistenceSnapshot.available(0),
                    List.of(new GuideSessionSnapshot(
                            "main", List.<GuideMessage>of(), List.of())),
                    Instant.EPOCH);
            return new ClientSettingsService.HistoryRuntimeState(
                    true,
                    Optional.of(guide),
                    GuideHistoryActivity.idle(),
                    SettingsDiagnosticsAggregator.HistoryScopeKind.MULTIPLAYER_SERVER);
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
