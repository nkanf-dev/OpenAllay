package dev.openallay.settings;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.client.gui.settings.ModelProfileDraft;
import dev.openallay.client.gui.settings.SettingsSaveCoordinator;
import dev.openallay.client.gui.settings.UiSettingsDraft;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.tool.ToolResult;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/** Pure production service/coordinator evidence. Native Screen scenarios have a separate client runner. */
final class SettingsEditorSaveReceiptTest {
    @Test
    void displaySaveWaitsForBackendAndClientReceiptWithOneOperationOnRepeatedClick() {
        Fixture f = new Fixture();
        GuideDisplayConfig candidate = f.display.current.withAssistantName("Player draft");
        assertTrue(f.save.save(() -> f.service.saveDisplay(candidate), f.client, () -> {}, result -> {
            assertInstanceOf(ToolResult.Success.class, result); f.commits++;
        }));
        long id = f.save.activeId();
        assertFalse(f.save.save(() -> f.service.saveDisplay(candidate), f.client, () -> {}, result -> f.commits++));
        assertEquals(id, f.save.activeId());
        assertEquals(1, f.worker.tasks.size());
        assertEquals(0, f.display.saves);
        assertEquals(0, f.commits);
        assertEquals(GuideDisplayConfig.defaults(), f.service.snapshot().display());
        f.worker.runAll();
        assertEquals(1, f.display.saves);
        assertEquals(candidate, f.service.snapshot().display());
        assertEquals(0, f.commits);
        assertTrue(f.save.busy());
        f.client.runAll();
        assertFalse(f.save.busy());
        assertEquals(1, f.commits);
    }

    @Test
    void failedDisplayReceiptPreservesLastValidProjectionAndUiPreviewForRetry() {
        Fixture f = new Fixture();
        var draft = new UiSettingsDraft(f.display.current);
        draft.preview(draft.ui().withFullscreen(new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMPACT, false, false, GuideUiConfig.Theme.MINT))
                .withHud(draft.ui().hud().withEnabled(true))
                .withNotifications(draft.ui().notifications().withEnabled(true)));
        GuideDisplayConfig candidate = draft.candidate(f.service.snapshot().display());
        f.display.fail = true;
        f.save.save(() -> f.service.saveDisplay(candidate), f.client, () -> {}, result -> {
            assertEquals("settings_write_failed", assertInstanceOf(ToolResult.Failure.class, result).code());
            f.failures++;
        });
        f.ack();
        assertEquals(GuideDisplayConfig.defaults(), f.service.snapshot().display());
        assertEquals(candidate.ui(), draft.ui());
        assertTrue(draft.dirty());
        assertEquals(1, f.failures);
        assertEquals(0, f.commits);
        f.display.fail = false;
        f.save.save(() -> f.service.saveDisplay(draft.candidate(f.service.snapshot().display())),
                f.client, () -> {}, result -> {
                    assertInstanceOf(ToolResult.Success.class, result);
                    draft.published(f.service.snapshot().display()); f.commits++;
                });
        f.ack();
        assertEquals(candidate, f.service.snapshot().display());
        assertFalse(draft.dirty());
        assertEquals(2, f.display.saves);
        assertEquals(1, f.commits);
    }

    @Test
    void failedModelSaveKeepsOriginalProjectionAndTheSameValidatedKeyCandidateRetriesOnce() {
        Fixture f = new Fixture();
        var modelDraft = ModelProfileDraft.from(profile("renamed"));
        var validated = modelDraft.validate();
        assertInstanceOf(ToolResult.Success.class, validated);
        var replacement = ((ToolResult.Success<ModelProfileDefinition>) validated).value();
        var candidate = new ModelProfilesConfig("renamed", List.of(replacement));
        SecretValue key = SecretValue.of("synthetic-uncommitted-key");
        f.models.fail = true;
        f.save.save(() -> f.service.saveModels(candidate, replacement.id(), key), f.client,
                () -> {}, result -> {
                    assertInstanceOf(ToolResult.Failure.class, result); f.failures++;
                });
        long id = f.save.activeId();
        assertFalse(f.save.save(() -> f.service.saveModels(candidate, replacement.id(), key),
                f.client, () -> {}, result -> f.commits++));
        assertEquals(id, f.save.activeId());
        f.ack();
        assertEquals("alpha", f.service.snapshot().models().config().defaultProfileId());
        assertEquals(1, f.models.saves);
        assertSame(key, f.models.key);
        assertEquals(0, f.commits);
        f.models.fail = false;
        f.save.save(() -> f.service.saveModels(candidate, replacement.id(), key), f.client,
                () -> {}, result -> { assertInstanceOf(ToolResult.Success.class, result); f.commits++; });
        f.ack();
        assertEquals(List.of("renamed"), f.service.snapshot().models().config().profiles().stream()
                .map(ModelProfileDefinition::id).toList());
        assertEquals(2, f.models.saves);
        assertEquals("renamed", f.models.replacementId);
        assertSame(key, f.models.key);
        assertEquals(1, f.commits);
    }

    @Test
    void childDraggedCandidateWithOldParentOffsetsIsSavedFrozenAndRetainedAfterFailureUntilAck() {
        Fixture f = new Fixture();
        var draft = new UiSettingsDraft(f.display.current);
        var oldOffsets = Map.of("offset_x", "17", "offset_y", "29");
        var ui = draft.ui().withFullscreen(new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMPACT, false, false, GuideUiConfig.Theme.MINT))
                .withNotifications(draft.ui().notifications().withEnabled(true));
        var returned = f.display.current.withUi(ui.withHud(ui.hud().withPlacement(
                GuideUiConfig.Anchor.BOTTOM_RIGHT, -144, -72, 352, 208, 1.2)));
        draft.adopt(returned);
        var frozen = draft.candidate(f.service.snapshot().display());
        f.display.fail = true;
        f.save.save(() -> f.service.saveDisplay(frozen), f.client, () -> {}, result -> {
            assertInstanceOf(ToolResult.Failure.class, result); f.failures++;
        });
        f.ack();
        assertEquals(GuideDisplayConfig.defaults(), f.service.snapshot().display());
        assertEquals(-144, draft.ui().hud().offsetX());
        assertEquals(-72, draft.ui().hud().offsetY());
        assertEquals("17", oldOffsets.get("offset_x"), "Removed widget values never own this save");
        assertEquals("29", oldOffsets.get("offset_y"));
        assertEquals(returned.ui().fullscreen(), draft.ui().fullscreen());
        assertEquals(returned.ui().notifications(), draft.ui().notifications());
        assertTrue(draft.dirty());
        f.display.fail = false;
        f.save.save(() -> f.service.saveDisplay(frozen), f.client, () -> {}, result -> {
            assertInstanceOf(ToolResult.Success.class, result);
            draft.published(f.service.snapshot().display()); f.commits++;
        });
        f.worker.runAll();
        assertEquals(frozen, f.service.snapshot().display());
        assertEquals(0, f.commits, "Receipt has not reached the client yet");
        f.client.runAll();
        assertEquals(1, f.commits);
        assertFalse(draft.dirty());
        assertEquals(frozen.ui(), f.display.current.ui());
    }

    @Test
    void detachedParentDisplayCannotOverwriteGeneralPublishedWhileChildEditorIsOpen() {
        Fixture f = new Fixture();
        GuideDisplayConfig parentAtOpen = f.service.snapshot().display();
        var returned = parentAtOpen.withUi(parentAtOpen.ui().withHud(parentAtOpen.ui().hud()
                .withPlacement(GuideUiConfig.Anchor.BOTTOM_RIGHT, -188, -84, 352, 208, 1.2)))
                .withAnimationsEnabled(false);
        GuideDisplayConfig independentlyPublished = parentAtOpen.withAssistantName("Published while editing")
                .withDebugMode(true);
        var generalSave = f.service.saveDisplay(independentlyPublished);
        f.worker.runAll();
        assertInstanceOf(ToolResult.Success.class, generalSave.join());
        assertEquals(GuideDisplayConfig.defaults(), parentAtOpen, "Detached parent retains its old snapshot");
        GuideDisplayConfig frozen = f.service.snapshot().display().withUi(returned.ui())
                .withAnimationsEnabled(returned.animationsEnabled());
        f.save.save(() -> f.service.saveDisplay(frozen), f.client, () -> {}, result -> {
            assertInstanceOf(ToolResult.Success.class, result); f.commits++;
        });
        f.ack();
        assertEquals("Published while editing", f.service.snapshot().display().assistantName());
        assertTrue(f.service.snapshot().display().debugMode());
        assertEquals(returned.ui(), f.service.snapshot().display().ui());
        assertFalse(f.service.snapshot().display().animationsEnabled());
        assertEquals(1, f.commits);
    }

    @Test
    void invalidNameAndModelCandidateAreRejectedBeforeAnyServiceWriteOrProbe() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.service.snapshot().display().withAssistantName("  "));
        assertInstanceOf(ToolResult.Failure.class, ModelProfileDraft.create("new-profile").validate());
        assertEquals(0, f.worker.tasks.size());
        assertEquals(0, f.models.saves);
        assertEquals(0, f.display.saves);
        assertEquals(SettingsOperation.Kind.IDLE, f.service.snapshot().operation().kind());
    }

    private static <T> T forbidden(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError("Unexpected action: " + method.getName()); }));
    }
    private static ModelProfileDefinition profile(String id) {
        return new ModelProfileDefinition(id, id, true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://model.example/v1"), "unknown/model", "env:TEST_KEY", 256_000, 4096,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
    }
    private static ClientSettingsService.ModelState state(ModelProfilesConfig config) {
        return new ClientSettingsService.ModelState(config, config.profiles().stream()
                .map(p -> new ModelProfileSettingsView.Resolution(p, true, true,
                        p.contextWindowTokens(), p.maxOutputTokens(), null)).toList());
    }
    private static final class Fixture {
        final ManualExecutor worker = new ManualExecutor();
        final ManualExecutor client = new ManualExecutor();
        final FakeDisplay display = new FakeDisplay();
        final FakeModels models = new FakeModels();
        final SettingsSaveCoordinator save = new SettingsSaveCoordinator();
        final ClientSettingsService service = new ClientSettingsService(display.current, display, models.current,
                Set.of(), models, models, CapabilitySettingsView.defaults(),
                forbidden(ClientSettingsService.CapabilityActions.class), RecipeSettingsView.defaults(),
                forbidden(ClientSettingsService.RecipeActions.class), new ClientSettingsService.HistoryActions() {
                    public ClientSettingsService.HistoryRuntimeState state() { return ClientSettingsService.HistoryRuntimeState.disconnected(); }
                    public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() { throw new AssertionError("history mutation"); }
                    public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() { throw new AssertionError("history mutation"); }
                    public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() { throw new AssertionError("history mutation"); }
                }, Runnable::run, worker, null);
        int commits; int failures;
        void ack() { worker.runAll(); client.runAll(); }
    }
    private static final class FakeDisplay implements ClientSettingsService.DisplayActions {
        GuideDisplayConfig current = GuideDisplayConfig.defaults(); boolean fail; int saves;
        public ToolResult<GuideDisplayConfig> saveDisplay(GuideDisplayConfig candidate) {
            saves++;
            if (fail) return new ToolResult.Failure<>("settings_write_failed", "Unable to save settings");
            current = candidate; return new ToolResult.Success<>(current);
        }
        public ToolResult<GuideDisplayConfig> reloadDisplay() { throw new AssertionError("reload"); }
    }
    private static final class FakeModels implements ClientSettingsService.ModelActions, ClientSettingsService.MetadataActions {
        ClientSettingsService.ModelState current = state(new ModelProfilesConfig("alpha", List.of(profile("alpha"))));
        boolean fail; int saves; String replacementId; SecretValue key;
        public ToolResult<ClientSettingsService.ModelState> save(ModelProfilesConfig candidate, Map<ModelMetadata.Key, ModelMetadata> metadata) {
            return save(candidate, null, null, metadata);
        }
        public ToolResult<ClientSettingsService.ModelState> save(ModelProfilesConfig candidate, String id, SecretValue replacement, Map<ModelMetadata.Key, ModelMetadata> metadata) {
            saves++; replacementId = id; key = replacement;
            if (fail) return new ToolResult.Failure<>("settings_write_failed", "Unable to save models");
            current = state(candidate); return new ToolResult.Success<>(current);
        }
        public ToolResult<ClientSettingsService.ModelState> reload(Map<ModelMetadata.Key, ModelMetadata> metadata) { throw new AssertionError("reload"); }
        public ToolResult<ResolvedModelProfile> resolve(ModelProfileDefinition candidate, Map<ModelMetadata.Key, ModelMetadata> metadata) { throw new AssertionError("resolve/probe"); }
        public ToolResult<ClientSettingsService.PreparedModels> prepare(ModelProfilesConfig candidate, Map<ModelMetadata.Key, ModelMetadata> metadata) { throw new AssertionError("prepare"); }
        public CompletableFuture<ModelConnectionResult> probe(ResolvedModelProfile profile, CancellationSignal cancellation) { throw new AssertionError("network probe"); }
        public CompletableFuture<Void> refresh() { throw new AssertionError("metadata refresh"); }
        public CompletableFuture<Void> closeAsync() { return CompletableFuture.completedFuture(null); }
    }
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void runAll() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }
}
