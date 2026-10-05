package dev.openallay.settings;

import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.requirement.RequirementEvaluator;
import dev.openallay.requirement.RequirementKind;
import dev.openallay.settings.requirement.PreparedPackageInstall;
import dev.openallay.settings.requirement.RequirementChange;
import dev.openallay.settings.requirement.RequirementReview;
import dev.openallay.settings.requirement.RequirementSettingsEnvironment;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuidePersistenceSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.catalog.ModelCatalog;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.model.metadata.ModelContextResolution;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.ModelMetadataUpdate;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.model.ServerModelSettingsView;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsAggregator;
import dev.openallay.settings.extension.ExtensionSettingsView;
import dev.openallay.settings.history.HistorySettingsView;
import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import dev.openallay.recipe.config.RecipeClientConfig;
import dev.openallay.tool.ToolResult;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** Common owner for native settings actions and immutable UI snapshots. */
public final class ClientSettingsService implements AutoCloseable {
    public interface ModelActions {
        ToolResult<ModelState> save(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata);

        default ToolResult<ModelState> save(
                ModelProfilesConfig candidate,
                String replacementProfileId,
                SecretValue replacement,
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            if (replacement != null) {
                return new ToolResult.Failure<>(
                        "credential_store_unavailable",
                        "Stored credentials are unavailable");
            }
            return save(candidate, metadata);
        }

        ToolResult<ModelState> reload(Map<ModelMetadata.Key, ModelMetadata> metadata);

        ToolResult<ResolvedModelProfile> resolve(
                ModelProfileDefinition candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata);

        default ToolResult<ResolvedModelProfile> resolve(
                ModelProfileDefinition candidate,
                SecretValue replacement,
                Map<ModelMetadata.Key, ModelMetadata> metadata) {
            if (replacement != null) {
                return new ToolResult.Failure<>(
                        "credential_store_unavailable",
                        "Stored credentials are unavailable");
            }
            return resolve(candidate, metadata);
        }

        ToolResult<PreparedModels> prepare(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata);

        CompletableFuture<ModelConnectionResult> probe(
                ResolvedModelProfile profile, CancellationSignal cancellation);

        default CompletableFuture<ToolResult<ModelCatalog>> fetchCatalog(
                ModelCatalogRequest request,
                SecretValue replacement,
                CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "model_catalog_unavailable", "Model catalog discovery is unavailable"));
        }
    }

    public interface MetadataActions {
        CompletableFuture<Void> refresh();

        CompletableFuture<Void> closeAsync();
    }

    public interface CapabilityActions {
        ToolResult<CapabilitySettingsView> saveCapabilities(CapabilityPolicy candidate);

        ToolResult<CapabilitySettingsView> reloadCapabilities();
    }

    public interface RecipeActions {
        ToolResult<RecipeSettingsView> saveRecipes(RecipeClientConfig candidate);

        ToolResult<RecipeSettingsView> reloadRecipes();
    }

    public interface SkillActions {
        default CompletableFuture<ToolResult<PreparedPackageInstall>> prepareCommunity(
                String id, CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "package_preview_unavailable", "Package preview is unavailable"));
        }

        default ToolResult<PreparedPackageInstall> prepareLocalPackage(java.nio.file.Path source) {
            return new ToolResult.Failure<>(
                    "package_preview_unavailable", "Local package preview is unavailable");
        }

        ToolResult<SkillSettingsView> saveOverride(String name, String markdown);

        ToolResult<SkillSettingsView> deleteOverride(String name);

        ToolResult<SkillSettingsView> reloadSkills();

        SkillSettingsView currentView();

        default SkillCommunityView communityView() {
            return SkillCommunityView.unavailable();
        }

        default CompletableFuture<ToolResult<SkillCommunityView>> refreshCommunity(
                CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable", "The Skill community catalog is unavailable"));
        }

        default CompletableFuture<ToolResult<SkillCommunityView>> installCommunity(
                String id, CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable", "The Skill community catalog is unavailable"));
        }

        default ToolResult<SkillCommunityView> importLocalPackage(java.nio.file.Path source) {
            return new ToolResult.Failure<>(
                    "skill_import_unavailable", "Local Skill import is unavailable");
        }
    }

    public interface ExtensionActions {
        default CompletableFuture<ToolResult<PreparedPackageInstall>> prepareCommunity(
                String id, CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "package_preview_unavailable", "Package preview is unavailable"));
        }

        default ToolResult<PreparedPackageInstall> prepareLocalPackage(java.nio.file.Path source) {
            return new ToolResult.Failure<>(
                    "package_preview_unavailable", "Local package preview is unavailable");
        }

        default ToolResult<PreparedPackageInstall> prepareLocalPackage(
                String id, java.nio.file.Path source) {
            return prepareLocalPackage(source);
        }

        ExtensionSettingsView currentView();

        default CompletableFuture<ToolResult<ExtensionSettingsView>> refreshCommunity(
                CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable", "The Extension community catalog is unavailable"));
        }

        default CompletableFuture<ToolResult<ExtensionSettingsView>> installCommunity(
                String id, CancellationSignal cancellation) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable", "The Extension community catalog is unavailable"));
        }

        default ToolResult<ExtensionSettingsView> importLocalPackage(
                String id, java.nio.file.Path source) {
            return importLocalPackage(source);
        }

        default ToolResult<ExtensionSettingsView> importLocalPackage(
                java.nio.file.Path source) {
            return new ToolResult.Failure<>(
                    "extension_import_unavailable", "Local Extension import is unavailable");
        }
    }

    public interface DisplayActions {
        ToolResult<GuideDisplayConfig> saveDisplay(GuideDisplayConfig candidate);

        ToolResult<GuideDisplayConfig> reloadDisplay();
    }

    public interface CommandActions {
        ToolResult<CommandCapabilityConfig> save(CommandCapabilityConfig candidate);
        ToolResult<CommandCapabilityConfig> reload();
    }
    public interface UnrestrictedJavascriptActions {
        ToolResult<UnrestrictedJavascriptConfig> save(UnrestrictedJavascriptConfig candidate);
        ToolResult<UnrestrictedJavascriptConfig> reload();
    }

    public interface HistoryActions {
        HistoryRuntimeState state();

        CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory();

        CompletableFuture<ToolResult<Boolean>> deleteActorHistory();

        CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase();
    }

    public enum HistoryAction {
        DELETE_CURRENT,
        DELETE_ACTOR,
        RESET_DATABASE
    }

    public enum ConfirmationStage {
        FIRST,
        SECOND
    }

    public static final class HistoryConfirmationToken {
        private final HistoryAction action;
        private final ConfirmationStage stage;
        private final long generation;
        private final AtomicBoolean consumed = new AtomicBoolean();

        private HistoryConfirmationToken(
                HistoryAction action, ConfirmationStage stage, long generation) {
            this.action = Objects.requireNonNull(action, "action");
            this.stage = Objects.requireNonNull(stage, "stage");
            if (generation < 0) {
                throw new IllegalArgumentException("confirmation generation must not be negative");
            }
            this.generation = generation;
        }

        public HistoryAction action() {
            return action;
        }

        public ConfirmationStage stage() {
            return stage;
        }

        public long generation() {
            return generation;
        }

        private boolean consume(
                HistoryAction expectedAction,
                ConfirmationStage expectedStage,
                long expectedGeneration) {
            return action == expectedAction
                    && stage == expectedStage
                    && generation == expectedGeneration
                    && consumed.compareAndSet(false, true);
        }

        @Override
        public String toString() {
            return "HistoryConfirmationToken[action=" + action
                    + ", stage=" + stage
                    + ", generation=" + generation + "]";
        }
    }

    public record HistoryRuntimeState(
            boolean configured,
            Optional<GuideSnapshot> guide,
            GuideHistoryActivity activity,
            SettingsDiagnosticsAggregator.HistoryScopeKind scopeKind,
            Long estimatedContextTokens) {
        public HistoryRuntimeState(boolean configured, Optional<GuideSnapshot> guide,
                GuideHistoryActivity activity, SettingsDiagnosticsAggregator.HistoryScopeKind scopeKind) {
            this(configured, guide, activity, scopeKind, null);
        }

        public HistoryRuntimeState {
            guide = Objects.requireNonNull(guide, "guide");
            Objects.requireNonNull(activity, "activity");
            Objects.requireNonNull(scopeKind, "scopeKind");
            if (guide.isEmpty()
                    != (scopeKind == SettingsDiagnosticsAggregator.HistoryScopeKind.NONE)) {
                throw new IllegalArgumentException(
                        "history scope kind must match current Guide availability");
            }
        }

        public static HistoryRuntimeState disconnected() {
            return new HistoryRuntimeState(
                    false,
                    Optional.empty(),
                    GuideHistoryActivity.idle(),
                    SettingsDiagnosticsAggregator.HistoryScopeKind.NONE);
        }
    }

    public record ModelState(
            ModelProfilesConfig config,
            List<ModelProfileSettingsView.Resolution> profiles) {
        public ModelState {
            Objects.requireNonNull(config, "config");
            profiles = List.copyOf(profiles);
            if (profiles.size() != config.profiles().size()) {
                throw new IllegalArgumentException("every configured profile needs a resolution");
            }
            for (int index = 0; index < profiles.size(); index++) {
                if (!profiles.get(index).definition().equals(config.profiles().get(index))) {
                    throw new IllegalArgumentException("resolved profile order must match configuration");
                }
            }
        }
    }

    public record PreparedModels(ModelState state, BooleanSupplier publish) {
        public PreparedModels {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(publish, "publish");
        }
    }

    private final Object lock = new Object();
    private GuideDisplayConfig display;
    private final DisplayActions displayActions;
    private final Set<String> presentEnvironmentNames;
    private final ModelActions models;
    private final MetadataActions metadataActions;
    private final CapabilityActions capabilityActions;
    private final RecipeActions recipeActions;
    private final SkillActions skillActions;
    private final CommandActions commandActions;
    private final UnrestrictedJavascriptActions unrestrictedActions;
    private final ExtensionActions extensionActions;
    private final HistoryActions historyActions;
    private final ClientEventDispatcher dispatcher;
    private final Executor worker;
    private final CopyOnWriteArrayList<Consumer<ClientSettingsSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private final AtomicLong operationIds = new AtomicLong();
    private final SettingsDiagnosticsAggregator diagnostics =
            new SettingsDiagnosticsAggregator();

    private ModelState modelState;
    private ServerModelSettingsView serverModelState = ServerModelSettingsView.unavailable();
    private CapabilitySettingsView capabilityState;
    private RecipeSettingsView recipeState;
    private SkillSettingsView skillState;
    private SkillCommunityView skillCommunityState;
    private ExtensionSettingsView extensionState;
    private CommandCapabilityConfig commandState;
    private UnrestrictedJavascriptConfig unrestrictedState = UnrestrictedJavascriptConfig.defaults();
    private HistoryRuntimeState historyState;
    private java.util.function.Supplier<dev.openallay.knowledge.KnowledgeSourceSnapshot> knowledgeSources =
            dev.openallay.knowledge.KnowledgeSourceSnapshot::notLoaded;
    private dev.openallay.knowledge.KnowledgeSourceSnapshot sourceState =
            dev.openallay.knowledge.KnowledgeSourceSnapshot.notLoaded();
    private boolean knowledgeSourcesBound;
    private long modelGeneration;
    private long metadataGeneration;
    private boolean metadataReconciliationPending;
    private Map<ModelMetadata.Key, ModelMetadata> metadata = Map.of();
    private GuideFailure metadataFailure;
    private ModelConnectionResult connectionResult;
    private SettingsOperation operation = SettingsOperation.idle();
    private SettingsNotice notice;
    private ClientSettingsSnapshot snapshot;
    private ActiveProbe activeProbe;
    private ActiveCatalog activeCatalog;
    private ActivePackagePreparation activePackagePreparation;
    private PreparedPackageInstall preparedPackage;
    private RequirementReview.Token packageReviewToken;
    private boolean closed;

    public ClientSettingsService(
            GuideDisplayConfig display,
            ModelState initialModels,
            Set<String> presentEnvironmentNames,
            ModelActions models,
            MetadataActions metadataActions,
            ClientEventDispatcher dispatcher,
            Executor worker) {
        this(
                display,
                initialModels,
                presentEnvironmentNames,
                models,
                metadataActions,
                CapabilitySettingsView.defaults(),
                defaultCapabilityActions(),
                RecipeSettingsView.defaults(),
                defaultRecipeActions(),
                dispatcher,
                worker,
                null);
    }

    public ClientSettingsService(
            GuideDisplayConfig display,
            ModelState initialModels,
            Set<String> presentEnvironmentNames,
            ModelActions models,
            MetadataActions metadataActions,
            ClientEventDispatcher dispatcher,
            Executor worker,
            SettingsNotice initialNotice) {
        this(
                display,
                initialModels,
                presentEnvironmentNames,
                models,
                metadataActions,
                CapabilitySettingsView.defaults(),
                defaultCapabilityActions(),
                RecipeSettingsView.defaults(),
                defaultRecipeActions(),
                dispatcher,
                worker,
                initialNotice);
    }

    public ClientSettingsService(
            GuideDisplayConfig display,
            ModelState initialModels,
            Set<String> presentEnvironmentNames,
            ModelActions models,
            MetadataActions metadataActions,
            CapabilitySettingsView initialCapabilities,
            CapabilityActions capabilityActions,
            RecipeSettingsView initialRecipes,
            RecipeActions recipeActions,
            ClientEventDispatcher dispatcher,
            Executor worker,
            SettingsNotice initialNotice) {
        this(
                display,
                defaultDisplayActions(),
                initialModels,
                presentEnvironmentNames,
                models,
                metadataActions,
                initialCapabilities,
                capabilityActions,
                initialRecipes,
                recipeActions,
                defaultHistoryActions(),
                dispatcher,
                worker,
                initialNotice);
    }

    public ClientSettingsService(
            GuideDisplayConfig display,
            DisplayActions displayActions,
            ModelState initialModels,
            Set<String> presentEnvironmentNames,
            ModelActions models,
            MetadataActions metadataActions,
            CapabilitySettingsView initialCapabilities,
            CapabilityActions capabilityActions,
            RecipeSettingsView initialRecipes,
            RecipeActions recipeActions,
            HistoryActions historyActions,
            ClientEventDispatcher dispatcher,
            Executor worker,
            SettingsNotice initialNotice) {
        this(
                display,
                displayActions,
                initialModels,
                presentEnvironmentNames,
                models,
                metadataActions,
                initialCapabilities,
                capabilityActions,
                initialRecipes,
                recipeActions,
                SkillSettingsView.empty(),
                defaultSkillActions(),
                ExtensionSettingsView.defaults(),
                CommandCapabilityConfig.defaults(),
                defaultCommandActions(),
                historyActions,
                dispatcher,
                worker,
                initialNotice);
    }

    public ClientSettingsService(
            GuideDisplayConfig display,
            DisplayActions displayActions,
            ModelState initialModels,
            Set<String> presentEnvironmentNames,
            ModelActions models,
            MetadataActions metadataActions,
            CapabilitySettingsView initialCapabilities,
            CapabilityActions capabilityActions,
            RecipeSettingsView initialRecipes,
            RecipeActions recipeActions,
            SkillSettingsView initialSkills,
            SkillActions skillActions,
            ExtensionSettingsView initialExtensions,
            CommandCapabilityConfig initialCommands,
            CommandActions commandActions,
            HistoryActions historyActions,
            ClientEventDispatcher dispatcher,
            Executor worker,
            SettingsNotice initialNotice) {
        this(
                display,
                displayActions,
                initialModels,
                presentEnvironmentNames,
                models,
                metadataActions,
                initialCapabilities,
                capabilityActions,
                initialRecipes,
                recipeActions,
                initialSkills,
                skillActions,
                initialExtensions,
                defaultExtensionActions(initialExtensions),
                initialCommands,
                commandActions,
                historyActions,
                dispatcher,
                worker,
                initialNotice);
    }

    public ClientSettingsService(
            GuideDisplayConfig display,
            DisplayActions displayActions,
            ModelState initialModels,
            Set<String> presentEnvironmentNames,
            ModelActions models,
            MetadataActions metadataActions,
            CapabilitySettingsView initialCapabilities,
            CapabilityActions capabilityActions,
            RecipeSettingsView initialRecipes,
            RecipeActions recipeActions,
            SkillSettingsView initialSkills,
            SkillActions skillActions,
            ExtensionSettingsView initialExtensions,
            ExtensionActions extensionActions,
            CommandCapabilityConfig initialCommands,
            CommandActions commandActions,
            HistoryActions historyActions,
            ClientEventDispatcher dispatcher,
            Executor worker,
            SettingsNotice initialNotice) {
        this(display, displayActions, initialModels, presentEnvironmentNames, models, metadataActions,
                initialCapabilities, capabilityActions, initialRecipes, recipeActions, initialSkills,
                skillActions, initialExtensions, extensionActions, initialCommands, commandActions,
                defaultUnrestrictedJavascriptActions(), UnrestrictedJavascriptConfig.defaults(), historyActions,
                dispatcher, worker, initialNotice);
    }

    public ClientSettingsService(
            GuideDisplayConfig display, DisplayActions displayActions, ModelState initialModels,
            Set<String> presentEnvironmentNames, ModelActions models, MetadataActions metadataActions,
            CapabilitySettingsView initialCapabilities, CapabilityActions capabilityActions,
            RecipeSettingsView initialRecipes, RecipeActions recipeActions, SkillSettingsView initialSkills,
            SkillActions skillActions, ExtensionSettingsView initialExtensions, ExtensionActions extensionActions,
            CommandCapabilityConfig initialCommands, CommandActions commandActions,
            UnrestrictedJavascriptActions unrestrictedActions, UnrestrictedJavascriptConfig initialUnrestricted,
            HistoryActions historyActions, ClientEventDispatcher dispatcher, Executor worker,
            SettingsNotice initialNotice) {
        this.display = Objects.requireNonNull(display, "display");
        this.displayActions = Objects.requireNonNull(displayActions, "displayActions");
        this.modelState = Objects.requireNonNull(initialModels, "initialModels");
        this.presentEnvironmentNames = Collections.unmodifiableSet(
                new TreeSet<>(presentEnvironmentNames));
        this.models = Objects.requireNonNull(models, "models");
        this.metadataActions = Objects.requireNonNull(metadataActions, "metadataActions");
        this.capabilityState = Objects.requireNonNull(initialCapabilities, "initialCapabilities");
        this.capabilityActions = Objects.requireNonNull(capabilityActions, "capabilityActions");
        this.recipeState = Objects.requireNonNull(initialRecipes, "initialRecipes");
        this.recipeActions = Objects.requireNonNull(recipeActions, "recipeActions");
        this.skillState = Objects.requireNonNull(initialSkills, "initialSkills");
        this.skillActions = Objects.requireNonNull(skillActions, "skillActions");
        this.skillCommunityState = Objects.requireNonNull(
                skillActions.communityView(), "initial Skill community view");
        this.extensionState = Objects.requireNonNull(initialExtensions, "initialExtensions");
        this.extensionActions = Objects.requireNonNull(extensionActions, "extensionActions");
        this.commandState = Objects.requireNonNull(initialCommands, "initialCommands");
        this.commandActions = Objects.requireNonNull(commandActions, "commandActions");
        this.unrestrictedActions = Objects.requireNonNull(unrestrictedActions, "unrestrictedActions");
        this.unrestrictedState = Objects.requireNonNull(initialUnrestricted, "initialUnrestricted");
        this.historyActions = Objects.requireNonNull(historyActions, "historyActions");
        this.historyState = safeHistoryState(historyActions);
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.notice = initialNotice;
        this.snapshot = buildSnapshot(0);
    }

    public ClientSettingsSnapshot snapshot() {
        synchronized (lock) {
            return snapshot;
        }
    }

    /** Replaces the connection-scoped server model projection without persisting it. */
    public CompletableFuture<Void> replaceServerModel(CapabilityPayload capability) {
        Objects.requireNonNull(capability, "capability");
        CompletableFuture<Void> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            synchronized (lock) {
                if (!closed) {
                    serverModelState = ServerModelSettingsView.from(capability);
                    publishLocked();
                }
            }
            result.complete(null);
        });
        return result;
    }

    /** Clears all server-origin model state on disconnect. */
    public CompletableFuture<Void> clearServerModel() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            synchronized (lock) {
                if (!closed && serverModelState.available()) {
                    serverModelState = ServerModelSettingsView.unavailable();
                    publishLocked();
                }
            }
            result.complete(null);
        });
        return result;
    }

    public AutoCloseable listen(Consumer<ClientSettingsSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        ClientSettingsSnapshot initial = snapshot();
        dispatcher.execute(() -> {
            if (listeners.contains(listener)) {
                listener.accept(initial);
            }
        });
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<ToolResult<Boolean>> saveModels(ModelProfilesConfig candidate) {
        return saveModels(candidate, null, null);
    }

    public CompletableFuture<ToolResult<Boolean>> saveModels(
            ModelProfilesConfig candidate,
            String replacementProfileId,
            SecretValue replacement) {
        Objects.requireNonNull(candidate, "candidate");
        Reservation reservation = reserve(SettingsOperation.models(
                SettingsOperation.Kind.SAVING_MODELS));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        Map<ModelMetadata.Key, ModelMetadata> metadataSnapshot = metadataSnapshot();
        worker.execute(() -> {
            ToolResult<ModelState> saved = safely(
                    () -> models.save(
                            candidate,
                            replacementProfileId,
                            replacement,
                            metadataSnapshot),
                    "settings_save_failed",
                    "Unable to save model settings");
            dispatcher.execute(() -> finishModels(reservation.id(), saved, result, "models_saved"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> reloadModels(boolean discardDirtyConfirmed) {
        if (!discardDirtyConfirmed) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "settings_discard_confirmation_required",
                    "Reload requires confirmation to discard unsaved changes"));
        }
        Reservation reservation = reserve(SettingsOperation.models(
                SettingsOperation.Kind.RELOADING_MODELS));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        Map<ModelMetadata.Key, ModelMetadata> metadataSnapshot = metadataSnapshot();
        worker.execute(() -> {
            ToolResult<ModelState> loaded = safely(
                    () -> models.reload(metadataSnapshot),
                    "settings_reload_failed",
                    "Unable to reload model settings");
            dispatcher.execute(() -> finishModels(
                    reservation.id(), loaded, result, "models_reloaded"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> saveCapabilities(CapabilityPolicy candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.SAVING_CAPABILITIES));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<CapabilitySettingsView> saved = safely(
                    () -> capabilityActions.saveCapabilities(candidate),
                    "settings_save_failed",
                    "Unable to save capability settings");
            dispatcher.execute(() -> finishCapabilities(
                    reservation.id(), saved, result, "capabilities_saved"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> reloadCapabilities(
            boolean discardDirtyConfirmed) {
        if (!discardDirtyConfirmed) {
            return discardConfirmation();
        }
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.RELOADING_CAPABILITIES));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<CapabilitySettingsView> loaded = safely(
                    capabilityActions::reloadCapabilities,
                    "settings_reload_failed",
                    "Unable to reload capability settings");
            dispatcher.execute(() -> finishCapabilities(
                    reservation.id(), loaded, result, "capabilities_reloaded"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> saveRecipeSettings(
            RecipeClientConfig candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.SAVING_RECIPES));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<RecipeSettingsView> saved = safely(
                    () -> recipeActions.saveRecipes(candidate),
                    "settings_save_failed",
                    "Unable to save recipe settings");
            dispatcher.execute(() -> finishRecipes(
                    reservation.id(), saved, result, "recipes_saved"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> reloadRecipeSettings(
            boolean discardDirtyConfirmed) {
        if (!discardDirtyConfirmed) {
            return discardConfirmation();
        }
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.RELOADING_RECIPES));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<RecipeSettingsView> loaded = safely(
                    recipeActions::reloadRecipes,
                    "settings_reload_failed",
                    "Unable to reload recipe settings");
            dispatcher.execute(() -> finishRecipes(
                    reservation.id(), loaded, result, "recipes_reloaded"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> saveSkillOverride(
            String name, String markdown) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(markdown, "markdown");
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.SAVING_SKILL));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<SkillSettingsView> saved = safely(
                    () -> skillActions.saveOverride(name, markdown),
                    "skill_override_save_failed",
                    "Unable to save the Skill override");
            dispatcher.execute(() -> finishSkills(
                    reservation.id(), saved, result, "skill_override_saved"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> deleteSkillOverride(String name) {
        Objects.requireNonNull(name, "name");
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.DELETING_SKILL_OVERRIDE));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<SkillSettingsView> deleted = safely(
                    () -> skillActions.deleteOverride(name),
                    "skill_override_delete_failed",
                    "Unable to delete the Skill override");
            dispatcher.execute(() -> finishSkills(
                    reservation.id(), deleted, result, "skill_override_deleted"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> reloadSkills(boolean discardDirtyConfirmed) {
        if (!discardDirtyConfirmed) {
            return discardConfirmation();
        }
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.RELOADING_SKILLS));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<SkillSettingsView> loaded = safely(
                    skillActions::reloadSkills,
                    "skill_reload_failed",
                    "Unable to reload Skills");
            dispatcher.execute(() -> finishSkills(
                    reservation.id(), loaded, result, "skills_reloaded"));
        });
        return result;
    }

    public SkillCommunityView skillCommunity() {
        synchronized (lock) {
            return skillCommunityState;
        }
    }

    public CompletableFuture<ToolResult<Boolean>> refreshSkillCommunity() {
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.REFRESHING_SKILL_CATALOG));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        CompletableFuture<ToolResult<SkillCommunityView>> refresh;
        try {
            refresh = Objects.requireNonNull(
                    skillActions.refreshCommunity(new CancellationSignal()),
                    "Skill catalog refresh future");
        } catch (RuntimeException failure) {
            refresh = CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_refresh_failed", "Unable to refresh the Skill community catalog"));
        }
        refresh.whenComplete((completed, thrown) -> dispatcher.execute(() ->
                finishSkillCommunity(
                        reservation.id(),
                        completed,
                        thrown,
                        result,
                        "skill_catalog_refreshed")));
        return result;
    }

    /** Prepares and validates a candidate. Publication requires Continue in its review. */
    public CompletableFuture<ToolResult<Boolean>> installCommunitySkill(String id) {
        Objects.requireNonNull(id, "id");
        return preparePackage(new SettingsOperation(
                SettingsOperation.Kind.INSTALLING_COMMUNITY_SKILL, id, false),
                cancellation -> skillActions.prepareCommunity(id, cancellation));
    }

    public CompletableFuture<ToolResult<Boolean>> importLocalSkillPackage(
            java.nio.file.Path source) {
        Objects.requireNonNull(source, "source");
        return preparePackage(SettingsOperation.domain(SettingsOperation.Kind.IMPORTING_SKILL_PACKAGE),
                cancellation -> CompletableFuture.completedFuture(
                        skillActions.prepareLocalPackage(source)));
    }

    public CompletableFuture<ToolResult<Boolean>> refreshExtensionCommunity() {
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.REFRESHING_EXTENSION_CATALOG));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        CompletableFuture<ToolResult<ExtensionSettingsView>> refresh;
        try {
            refresh = Objects.requireNonNull(
                    extensionActions.refreshCommunity(new CancellationSignal()),
                    "Extension catalog refresh future");
        } catch (RuntimeException failure) {
            refresh = CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_refresh_failed",
                    "Unable to refresh the Extension community catalog"));
        }
        refresh.whenComplete((completed, thrown) -> dispatcher.execute(() ->
                finishExtensionCommunity(
                        reservation.id(),
                        completed,
                        thrown,
                        result,
                        "extension_catalog_refreshed")));
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> installCommunityExtension(String id) {
        Objects.requireNonNull(id, "id");
        return preparePackage(new SettingsOperation(
                SettingsOperation.Kind.INSTALLING_COMMUNITY_EXTENSION, id, false),
                cancellation -> extensionActions.prepareCommunity(id, cancellation));
    }

    public CompletableFuture<ToolResult<Boolean>> importLocalExtensionPackage(
            String id, java.nio.file.Path source) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        return preparePackage(new SettingsOperation(
                SettingsOperation.Kind.IMPORTING_EXTENSION_PACKAGE, id, false),
                cancellation -> CompletableFuture.completedFuture(
                        extensionActions.prepareLocalPackage(id, source)));
    }

    public CompletableFuture<ToolResult<Boolean>> importLocalExtensionPackage(
            java.nio.file.Path source) {
        Objects.requireNonNull(source, "source");
        return preparePackage(SettingsOperation.domain(SettingsOperation.Kind.IMPORTING_EXTENSION_PACKAGE),
                cancellation -> CompletableFuture.completedFuture(
                        extensionActions.prepareLocalPackage(source)));
    }

    private CompletableFuture<ToolResult<Boolean>> preparePackage(
            SettingsOperation requested,
            java.util.function.Function<CancellationSignal,
                    CompletableFuture<ToolResult<PreparedPackageInstall>>> prepare) {
        ActivePackagePreparation active;
        synchronized (lock) {
            if (closed) return CompletableFuture.completedFuture(failed("settings_closed"));
            if (operation.kind() != SettingsOperation.Kind.IDLE) {
                return CompletableFuture.completedFuture(failed("settings_busy"));
            }
            discardPreparedPackageLocked();
            Reservation reservation = reserveLocked(requested);
            active = new ActivePackagePreparation(reservation.id(), new CancellationSignal(),
                    new CompletableFuture<>());
            activePackagePreparation = active;
        }
        worker.execute(() -> {
            synchronized (lock) {
                if (activePackagePreparation != active) return;
            }
            CompletableFuture<ToolResult<PreparedPackageInstall>> pending;
            try {
                pending = Objects.requireNonNull(prepare.apply(active.cancellation()), "package preview future");
            } catch (RuntimeException failure) {
                pending = CompletableFuture.completedFuture(new ToolResult.Failure<>(
                        "package_preview_failed", "Unable to prepare the package preview"));
            }
            pending.whenComplete((completed, thrown) -> dispatcher.execute(() ->
                    finishPackagePreparation(active, completed, thrown)));
        });
        return active.outward();
    }

    private void finishPackagePreparation(ActivePackagePreparation active,
            ToolResult<PreparedPackageInstall> completed, Throwable thrown) {
        synchronized (lock) {
            if (activePackagePreparation != active || closed) {
                if (completed instanceof ToolResult.Success<PreparedPackageInstall> success) {
                    closePreparedPackage(success.value());
                }
                active.outward().complete(new ToolResult.Failure<>(
                        "package_preview_cancelled", "Package preview cancelled"));
                return;
            }
            activePackagePreparation = null;
            operation = SettingsOperation.idle();
            if (thrown == null && completed instanceof ToolResult.Success<PreparedPackageInstall> success) {
                preparedPackage = success.value();
                packageReviewToken = new RequirementReview.Token();
                notice = SettingsNotice.success("package_preview_ready", "Package ready for review");
                publishLocked();
                active.outward().complete(new ToolResult.Success<>(true));
            } else {
                ToolResult.Failure<PreparedPackageInstall> failure =
                        thrown == null && completed instanceof ToolResult.Failure<PreparedPackageInstall> value
                                ? value : new ToolResult.Failure<>(
                                        "package_preview_failed", "Unable to prepare the package preview");
                notice = SettingsNotice.failure(failure.code(), failure.message());
                publishLocked();
                active.outward().complete(new ToolResult.Failure<>(failure.code(), failure.message()));
            }
        }
    }

    /** Cancel an in-flight download/copy. A late result is discarded, never published. */
    public boolean cancelPackagePreparation() {
        synchronized (lock) {
            if (activePackagePreparation == null) return false;
            ActivePackagePreparation active = activePackagePreparation;
            activePackagePreparation = null;
            active.cancellation().cancel();
            operation = SettingsOperation.idle();
            notice = SettingsNotice.success("package_preview_cancelled", "Package preview cancelled");
            publishLocked();
            active.outward().complete(new ToolResult.Failure<>(
                    "package_preview_cancelled", "Package preview cancelled"));
            return true;
        }
    }

    public boolean cancelPackageInstall(RequirementReview.Token token) {
        synchronized (lock) {
            if (token == null || token != packageReviewToken || preparedPackage == null) return false;
            discardPreparedPackageLocked();
            notice = SettingsNotice.success("package_preview_cancelled", "Package preview cancelled");
            publishLocked();
            return true;
        }
    }

    /** Publishes only the captured candidate. No capability or deny-policy writes occur here. */
    public CompletableFuture<ToolResult<Boolean>> continuePackageInstall(RequirementReview.Token token) {
        PreparedPackageInstall candidate;
        Reservation reservation;
        synchronized (lock) {
            if (closed) return CompletableFuture.completedFuture(failed("settings_closed"));
            if (operation.kind() != SettingsOperation.Kind.IDLE) {
                return CompletableFuture.completedFuture(failed("settings_busy"));
            }
            if (token == null || token != packageReviewToken || preparedPackage == null) {
                return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                        "package_preview_stale", "Prepare a fresh package preview"));
            }
            candidate = preparedPackage;
            preparedPackage = null;
            packageReviewToken = null;
            reservation = reserveLocked(SettingsOperation.domain(
                    candidate.kind() == RequirementKind.SKILL
                            ? SettingsOperation.Kind.INSTALLING_COMMUNITY_SKILL
                            : SettingsOperation.Kind.INSTALLING_COMMUNITY_EXTENSION));
        }
        CompletableFuture<ToolResult<Boolean>> outward = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<Boolean> committed;
            try {
                committed = safely(candidate::commit, "package_install_failed", "Unable to install the package");
            } finally {
                closePreparedPackage(candidate);
            }
            ToolResult<Boolean> completed = committed;
            dispatcher.execute(() -> {
                synchronized (lock) {
                    if (!isCurrentLocked(reservation.id())) {
                        outward.complete(new ToolResult.Failure<>(
                                "package_install_stale", "The package operation is no longer current"));
                        return;
                    }
                    operation = SettingsOperation.idle();
                    if (completed instanceof ToolResult.Success<Boolean>) {
                        try {
                            if (candidate.kind() == RequirementKind.SKILL) {
                                skillState = Objects.requireNonNull(skillActions.currentView());
                                skillCommunityState = Objects.requireNonNull(skillActions.communityView());
                            } else {
                                extensionState = Objects.requireNonNull(extensionActions.currentView());
                            }
                            notice = SettingsNotice.success("package_installed", "Package installed");
                        } catch (RuntimeException failure) {
                            notice = SettingsNotice.failure("package_projection_failed",
                                    "Package published, but its settings view could not be refreshed");
                            publishLocked();
                            outward.complete(new ToolResult.Failure<>(notice.code(), notice.message()));
                            return;
                        }
                    } else {
                        var failure = (ToolResult.Failure<Boolean>) completed;
                        notice = SettingsNotice.failure(failure.code(), failure.message());
                    }
                    publishLocked();
                    outward.complete(completed);
                }
            });
        });
        return outward;
    }

    /** Applies exactly one displayed change through its normal settings owner. */
    public CompletableFuture<ToolResult<Boolean>> enablePackageRequirement(
            RequirementReview.Token token, RequirementKind kind, String id,
            boolean unrestrictedConfirmed) {
        synchronized (lock) {
            if (closed) return CompletableFuture.completedFuture(failed("settings_closed"));
            if (operation.kind() != SettingsOperation.Kind.IDLE) {
                return CompletableFuture.completedFuture(failed("settings_busy"));
            }
            if (token == null || token != packageReviewToken || preparedPackage == null) {
                return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                        "package_preview_stale", "Prepare a fresh package preview"));
            }
            RequirementChange change = requirementReviewLocked().orElseThrow().changes().stream()
                    .filter(value -> value.kind() == kind && value.id().equals(id)).findFirst().orElse(null);
            if (change == null) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "requirement_not_enableable", "This requirement has no available settings change"));
            if (change.unrestrictedConsentRequired() && !unrestrictedConfirmed) {
                return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                        "unrestricted_confirmation_required", "Confirm unrestricted JVM access before enabling it"));
            }
            if (kind == RequirementKind.CAPABILITY
                    && RequirementSettingsEnvironment.isUnrestrictedJavascript(id)) {
                return saveUnrestrictedJavascript(true);
            }
            if (kind == RequirementKind.CAPABILITY
                    && id.equals(RequirementSettingsEnvironment.EXPERIMENTAL_COMMANDS)) {
                return saveExperimentalCommands(true);
            }
            Set<String> tools = new TreeSet<>(capabilityState.policy().disabledTools());
            Set<String> skills = new TreeSet<>(capabilityState.policy().disabledSkills());
            if (kind == RequirementKind.CAPABILITY) tools.remove(id);
            else if (kind == RequirementKind.SKILL) skills.remove(id);
            return saveCapabilities(new CapabilityPolicy(tools, skills));
        }
    }

    private Optional<RequirementReview> requirementReviewLocked() {
        if (preparedPackage == null || packageReviewToken == null) return Optional.empty();
        var report = RequirementEvaluator.evaluate(preparedPackage.requirements(),
                RequirementSettingsEnvironment.from(capabilityState, skillState, extensionState,
                        commandState, unrestrictedState));
        return Optional.of(new RequirementReview(packageReviewToken, preparedPackage.kind(),
                preparedPackage.id(), preparedPackage.name(), preparedPackage.version(),
                preparedPackage.sha256(), report,
                RequirementSettingsEnvironment.changes(report, capabilityState),
                preparedPackage.catalogRequirementsDiffer()));
    }

    private void discardPreparedPackageLocked() {
        PreparedPackageInstall previous = preparedPackage;
        preparedPackage = null;
        packageReviewToken = null;
        if (previous != null) closePreparedPackage(previous);
    }

    private static void closePreparedPackage(PreparedPackageInstall candidate) {
        try {
            candidate.close();
        } catch (RuntimeException ignored) {
            // Cleanup never retries or publishes a candidate.
        }
    }

    private record ActivePackagePreparation(long operationId, CancellationSignal cancellation,
            CompletableFuture<ToolResult<Boolean>> outward) {}

    public CompletableFuture<ToolResult<Boolean>> saveDisplay(GuideDisplayConfig candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.SAVING_DISPLAY));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<GuideDisplayConfig> saved = safely(
                    () -> displayActions.saveDisplay(candidate),
                    "settings_save_failed",
                    "Unable to save display settings");
            dispatcher.execute(() -> finishDisplay(
                    reservation.id(), saved, result, "display_saved"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> reloadDisplay() {
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.RELOADING_DISPLAY));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<GuideDisplayConfig> loaded = safely(
                    displayActions::reloadDisplay,
                    "settings_reload_failed",
                    "Unable to reload display settings");
            dispatcher.execute(() -> finishDisplay(
                    reservation.id(), loaded, result, "display_reloaded"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> saveExperimentalCommands(boolean enabled) {
        CommandCapabilityConfig candidate = new CommandCapabilityConfig(
                enabled);
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.SAVING_EXPERIMENTAL_COMMANDS));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<CommandCapabilityConfig> saved = safely(
                    () -> commandActions.save(candidate),
                    "settings_save_failed",
                    "Unable to save experimental command settings");
            dispatcher.execute(() -> finishCommands(
                    reservation.id(), saved, result, "experimental_commands_saved"));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> saveUnrestrictedJavascript(boolean enabled) {
        var candidate = new UnrestrictedJavascriptConfig(enabled);
        Reservation reservation = reserve(SettingsOperation.domain(SettingsOperation.Kind.SAVING_UNRESTRICTED_JAVASCRIPT));
        if (!reservation.accepted()) return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<UnrestrictedJavascriptConfig> saved = safely(() -> unrestrictedActions.save(candidate),
                    "settings_save_failed", "Unable to save unrestricted JavaScript settings");
            dispatcher.execute(() -> finishUnrestricted(reservation.id(), saved, result));
        });
        return result;
    }

    private void finishUnrestricted(long operationId, ToolResult<UnrestrictedJavascriptConfig> completed,
            CompletableFuture<ToolResult<Boolean>> outward) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) return;
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<UnrestrictedJavascriptConfig> success) {
                unrestrictedState = success.value();
                notice = SettingsNotice.success("unrestricted_javascript_saved", "Unrestricted JavaScript settings saved");
                result = new ToolResult.Success<>(true);
            } else {
                var failure = (ToolResult.Failure<UnrestrictedJavascriptConfig>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private static UnrestrictedJavascriptActions defaultUnrestrictedJavascriptActions() {
        return new UnrestrictedJavascriptActions() {
            public ToolResult<UnrestrictedJavascriptConfig> save(UnrestrictedJavascriptConfig c) {
                return new ToolResult.Failure<>("settings_unavailable", "Unrestricted JavaScript settings are unavailable");
            }
            public ToolResult<UnrestrictedJavascriptConfig> reload() {
                return new ToolResult.Failure<>("settings_unavailable", "Unrestricted JavaScript settings are unavailable");
            }
        };
    }

    public CompletableFuture<ToolResult<Boolean>> reloadExperimentalCommands() {
        Reservation reservation = reserve(SettingsOperation.domain(
                SettingsOperation.Kind.RELOADING_EXPERIMENTAL_COMMANDS));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        worker.execute(() -> {
            ToolResult<CommandCapabilityConfig> loaded = safely(
                    commandActions::reload,
                    "settings_reload_failed",
                    "Unable to reload experimental command settings");
            dispatcher.execute(() -> finishCommands(
                    reservation.id(), loaded, result, "experimental_commands_reloaded"));
        });
        return result;
    }

    /** Binds an immutable published status; the supplier must not capture or load Game data. */
    public void bindKnowledgeSources(
            java.util.function.Supplier<dev.openallay.knowledge.KnowledgeSourceSnapshot> sources) {
        synchronized (lock) {
            if (knowledgeSourcesBound) {
                throw new IllegalStateException("Knowledge diagnostics are already bound");
            }
            knowledgeSources = Objects.requireNonNull(sources, "sources");
            knowledgeSourcesBound = true;
            sourceState = safeSourceState();
            publishLocked();
        }
    }

    public void refreshRuntimeState() {
        HistoryRuntimeState refreshed = safeHistoryState(historyActions);
        dev.openallay.knowledge.KnowledgeSourceSnapshot refreshedSources = safeSourceState();
        synchronized (lock) {
            if (closed || refreshed.equals(historyState) && refreshedSources.equals(sourceState)) {
                return;
            }
            historyState = refreshed;
            sourceState = refreshedSources;
            publishLocked();
        }
    }

    private dev.openallay.knowledge.KnowledgeSourceSnapshot safeSourceState() {
        try {
            return Objects.requireNonNull(knowledgeSources.get(), "source snapshot");
        } catch (RuntimeException unavailable) {
            return dev.openallay.knowledge.KnowledgeSourceSnapshot.notLoaded();
        }
    }

    public ToolResult<HistoryConfirmationToken> requestHistoryConfirmation(
            HistoryAction action) {
        Objects.requireNonNull(action, "action");
        synchronized (lock) {
            GuideFailure failure = confirmationFailureLocked(action);
            if (failure != null) {
                return new ToolResult.Failure<>(failure.code(), failure.message());
            }
            return new ToolResult.Success<>(new HistoryConfirmationToken(
                    action, ConfirmationStage.FIRST, snapshot.generation()));
        }
    }

    public ToolResult<HistoryConfirmationToken> confirmHistoryReset(
            HistoryConfirmationToken first) {
        synchronized (lock) {
            GuideFailure failure = confirmationFailureLocked(HistoryAction.RESET_DATABASE);
            if (failure != null) {
                return new ToolResult.Failure<>(failure.code(), failure.message());
            }
            if (first == null || !first.consume(
                    HistoryAction.RESET_DATABASE,
                    ConfirmationStage.FIRST,
                    snapshot.generation())) {
                return confirmationRequired();
            }
            return new ToolResult.Success<>(new HistoryConfirmationToken(
                    HistoryAction.RESET_DATABASE,
                    ConfirmationStage.SECOND,
                    snapshot.generation()));
        }
    }

    public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory(
            HistoryConfirmationToken confirmation) {
        return administerHistory(HistoryAction.DELETE_CURRENT, confirmation);
    }

    public CompletableFuture<ToolResult<Boolean>> deleteActorHistory(
            HistoryConfirmationToken confirmation) {
        return administerHistory(HistoryAction.DELETE_ACTOR, confirmation);
    }

    public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase(
            HistoryConfirmationToken confirmation) {
        return administerHistory(HistoryAction.RESET_DATABASE, confirmation);
    }

    public CompletableFuture<ToolResult<Boolean>> refreshMetadata() {
        Reservation reservation = reserve(SettingsOperation.models(
                SettingsOperation.Kind.REFRESHING_METADATA));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(failed(reservation.failureCode()));
        }
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        CompletableFuture<Void> refresh;
        try {
            refresh = metadataActions.refresh();
        } catch (RuntimeException failure) {
            refresh = CompletableFuture.failedFuture(failure);
        }
        refresh.whenComplete((ignored, failure) -> dispatcher.execute(() -> {
            synchronized (lock) {
                if (!isCurrentLocked(reservation.id())) {
                    return;
                }
                operation = SettingsOperation.idle();
                if (failure == null) {
                    notice = SettingsNotice.success(
                            "metadata_refreshed", "Model metadata refresh completed");
                    result.complete(new ToolResult.Success<>(Boolean.TRUE));
                } else {
                    notice = SettingsNotice.failure(
                            "metadata_unavailable", "Model metadata refresh is unavailable");
                    result.complete(new ToolResult.Failure<>(notice.code(), notice.message()));
                }
                publishLocked();
            }
        }));
        return result;
    }

    public CompletableFuture<ToolResult<ModelCatalog>> fetchModelCatalog(
            ModelCatalogRequest request,
            SecretValue replacement) {
        Objects.requireNonNull(request, "request");
        Reservation reservation = reserve(SettingsOperation.catalog(request.profileId()));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    reservation.failureCode(),
                    reservation.failureCode().equals("settings_closed")
                            ? "Settings are closed"
                            : "Another settings operation is already running"));
        }
        CancellationSignal cancellation = new CancellationSignal();
        CompletableFuture<ToolResult<ModelCatalog>> outward = new CompletableFuture<>();
        synchronized (lock) {
            activeCatalog = new ActiveCatalog(reservation.id(), cancellation, outward);
        }
        worker.execute(() -> startCatalog(
                reservation.id(), request, replacement, cancellation, outward));
        return outward;
    }

    public boolean cancelModelCatalog() {
        ActiveCatalog catalog;
        ToolResult.Failure<ModelCatalog> cancelled = new ToolResult.Failure<>(
                "model_catalog_cancelled", "The model catalog request was cancelled");
        synchronized (lock) {
            catalog = activeCatalog;
            if (catalog == null || !isCurrentLocked(catalog.id())) {
                return false;
            }
            activeCatalog = null;
            operation = SettingsOperation.idle();
            notice = SettingsNotice.failure(cancelled.code(), cancelled.message());
            publishLocked();
        }
        catalog.cancellation().cancel();
        catalog.result().complete(cancelled);
        return true;
    }

    public CompletableFuture<ModelConnectionResult> testConnection(
            ModelProfileDefinition candidate) {
        return testConnection(candidate, null);
    }

    public CompletableFuture<ModelConnectionResult> testConnection(
            ModelProfileDefinition candidate,
            SecretValue replacement) {
        Objects.requireNonNull(candidate, "candidate");
        Reservation reservation = reserve(SettingsOperation.probe(candidate.id()));
        if (!reservation.accepted()) {
            return CompletableFuture.completedFuture(new ModelConnectionResult.Failure(
                    "connection_test_busy", "Another settings operation is already running"));
        }
        CancellationSignal cancellation = new CancellationSignal();
        CompletableFuture<ModelConnectionResult> outward = new CompletableFuture<>();
        synchronized (lock) {
            activeProbe = new ActiveProbe(reservation.id(), cancellation, outward);
        }
        Map<ModelMetadata.Key, ModelMetadata> metadataSnapshot = metadataSnapshot();
        worker.execute(() -> startProbe(
                reservation.id(),
                candidate,
                replacement,
                metadataSnapshot,
                cancellation,
                outward));
        return outward;
    }

    public boolean cancelConnectionTest() {
        ActiveProbe probe;
        ModelConnectionResult.Failure cancelled;
        synchronized (lock) {
            probe = activeProbe;
            if (probe == null || !isCurrentLocked(probe.id())) {
                return false;
            }
            activeProbe = null;
            operation = SettingsOperation.idle();
            cancelled = connectionFailure(
                    "connection_cancelled", "The connection test was cancelled");
            connectionResult = cancelled;
            notice = SettingsNotice.failure(cancelled.code(), cancelled.message());
            publishLocked();
        }
        probe.cancellation().cancel();
        probe.result().complete(cancelled);
        return true;
    }

    public void acceptMetadataUpdate(ModelMetadataUpdate update) {
        Objects.requireNonNull(update, "update");
        long expectedGeneration;
        long expectedMetadataGeneration;
        ModelProfilesConfig config;
        synchronized (lock) {
            if (closed) {
                return;
            }
            metadata = Map.copyOf(update.entries());
            metadataFailure = update.failure();
            expectedMetadataGeneration = ++metadataGeneration;
            expectedGeneration = modelGeneration;
            config = modelState.config();
            publishLocked();
        }
        reconcileMetadata(
                expectedGeneration,
                expectedMetadataGeneration,
                config,
                update.entries());
    }

    public CompletableFuture<Void> closeAsync() {
        synchronized (lock) {
            closed = true;
            cancelPackagePreparation();
            if (preparedPackage != null) {
                discardPreparedPackageLocked();
                publishLocked();
            }
        }
        cancelConnectionTest();
        cancelModelCatalog();
        try {
            return metadataActions.closeAsync().exceptionally(ignored -> null);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void close() {
        closeAsync();
    }

    private void startProbe(
            long operationId,
            ModelProfileDefinition candidate,
            SecretValue replacement,
            Map<ModelMetadata.Key, ModelMetadata> metadataSnapshot,
            CancellationSignal cancellation,
            CompletableFuture<ModelConnectionResult> outward) {
        if (!isCurrent(operationId)) {
            return;
        }
        ToolResult<ResolvedModelProfile> resolved = safely(
                () -> models.resolve(candidate, replacement, metadataSnapshot),
                "invalid_model_config",
                "Unable to prepare the connection test");
        if (resolved instanceof ToolResult.Failure<ResolvedModelProfile> failure) {
            dispatcher.execute(() -> finishProbe(
                    operationId,
                    connectionFailure(failure.code(), safeProbeMessage(failure.code())),
                    outward));
            return;
        }
        ResolvedModelProfile profile = ((ToolResult.Success<ResolvedModelProfile>) resolved).value();
        CompletableFuture<ModelConnectionResult> request;
        try {
            request = models.probe(profile, cancellation);
        } catch (RuntimeException failure) {
            request = CompletableFuture.completedFuture(connectionFailure(
                    "connection_transport_failed", "The model provider could not be reached"));
        }
        request.whenComplete((probeResult, thrown) -> dispatcher.execute(() -> finishProbe(
                operationId,
                thrown == null && probeResult != null
                        ? probeResult
                        : connectionFailure(
                                cancellation.isCancelled()
                                        ? "connection_cancelled"
                                        : "connection_transport_failed",
                                cancellation.isCancelled()
                                        ? "The connection test was cancelled"
                                        : "The model provider could not be reached"),
                outward)));
    }

    private void startCatalog(
            long operationId,
            ModelCatalogRequest request,
            SecretValue replacement,
            CancellationSignal cancellation,
            CompletableFuture<ToolResult<ModelCatalog>> outward) {
        if (!isCurrent(operationId)) {
            return;
        }
        CompletableFuture<ToolResult<ModelCatalog>> pending;
        try {
            pending = Objects.requireNonNull(
                    models.fetchCatalog(request, replacement, cancellation),
                    "model catalog future");
        } catch (RuntimeException failure) {
            pending = CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "model_catalog_transport_failed",
                    "The model provider catalog could not be reached"));
        }
        pending.whenComplete((result, thrown) -> dispatcher.execute(() -> finishCatalog(
                operationId, result, thrown, cancellation, outward)));
    }

    private void finishCatalog(
            long operationId,
            ToolResult<ModelCatalog> completed,
            Throwable thrown,
            CancellationSignal cancellation,
            CompletableFuture<ToolResult<ModelCatalog>> outward) {
        ToolResult<ModelCatalog> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            activeCatalog = null;
            operation = SettingsOperation.idle();
            if (thrown == null && completed instanceof ToolResult.Success<ModelCatalog> success) {
                result = new ToolResult.Success<>(success.value());
                notice = SettingsNotice.success(
                        "model_catalog_loaded", "Model catalog loaded");
            } else {
                ToolResult.Failure<ModelCatalog> failure =
                        thrown == null && completed instanceof ToolResult.Failure<ModelCatalog> value
                                ? value
                                : new ToolResult.Failure<>(
                                        cancellation.isCancelled()
                                                ? "model_catalog_cancelled"
                                                : "model_catalog_transport_failed",
                                        cancellation.isCancelled()
                                                ? "The model catalog request was cancelled"
                                                : "The model provider catalog could not be reached");
                result = new ToolResult.Failure<>(failure.code(), failure.message());
                notice = SettingsNotice.failure(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishProbe(
            long operationId,
            ModelConnectionResult result,
            CompletableFuture<ModelConnectionResult> outward) {
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            activeProbe = null;
            operation = SettingsOperation.idle();
            connectionResult = result;
            if (result instanceof ModelConnectionResult.Success) {
                notice = SettingsNotice.success(
                        "connection_succeeded", "The model connection test succeeded");
            } else {
                ModelConnectionResult.Failure failure = (ModelConnectionResult.Failure) result;
                notice = SettingsNotice.failure(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishModels(
            long operationId,
            ToolResult<ModelState> completed,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        boolean reconcile;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<ModelState> success) {
                modelState = success.value();
                connectionResult = null;
                notice = SettingsNotice.success(successCode, switch (successCode) {
                    case "models_reloaded" -> "Model settings reloaded";
                    default -> "Model settings saved";
                });
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<ModelState> failure =
                        (ToolResult.Failure<ModelState>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            reconcile = metadataReconciliationPending;
            metadataReconciliationPending = false;
            publishLocked();
        }
        if (reconcile) {
            reconcileCurrentMetadata();
        }
        outward.complete(result);
    }

    private void finishCapabilities(
            long operationId,
            ToolResult<CapabilitySettingsView> completed,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<CapabilitySettingsView> success) {
                capabilityState = success.value();
                notice = SettingsNotice.success(
                        successCode,
                        successCode.equals("capabilities_reloaded")
                                ? "Capability settings reloaded"
                                : "Capability settings saved");
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<CapabilitySettingsView> failure =
                        (ToolResult.Failure<CapabilitySettingsView>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishRecipes(
            long operationId,
            ToolResult<RecipeSettingsView> completed,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<RecipeSettingsView> success) {
                recipeState = success.value();
                notice = SettingsNotice.success(
                        successCode,
                        successCode.equals("recipes_reloaded")
                                ? "Recipe settings reloaded"
                                : "Recipe settings saved");
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<RecipeSettingsView> failure =
                        (ToolResult.Failure<RecipeSettingsView>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishSkills(
            long operationId,
            ToolResult<SkillSettingsView> completed,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<SkillSettingsView> success) {
                skillState = success.value();
                notice = SettingsNotice.success(successCode, switch (successCode) {
                    case "skills_reloaded" -> "Skills reloaded";
                    case "skill_override_deleted" -> "Skill override deleted";
                    default -> "Skill override saved";
                });
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<SkillSettingsView> failure =
                        (ToolResult.Failure<SkillSettingsView>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishSkillCommunity(
            long operationId,
            ToolResult<SkillCommunityView> completed,
            Throwable thrown,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (thrown == null && completed instanceof ToolResult.Success<SkillCommunityView>) {
                try {
                    SkillCommunityView updatedCommunity = Objects.requireNonNull(
                            ((ToolResult.Success<SkillCommunityView>) completed).value(),
                            "updated Skill community projection");
                    SkillSettingsView updatedSkills = Objects.requireNonNull(
                            skillActions.currentView(), "current Skill projection");
                    skillCommunityState = updatedCommunity;
                    skillState = updatedSkills;
                    notice = SettingsNotice.success(successCode, switch (successCode) {
                        case "skill_catalog_refreshed" -> "Skill community catalog refreshed";
                        case "skill_package_imported" -> "Skill package imported";
                        default -> "Community Skill installed";
                    });
                    result = new ToolResult.Success<>(Boolean.TRUE);
                } catch (RuntimeException failure) {
                    notice = SettingsNotice.failure(
                            "skill_projection_unavailable",
                            "Unable to reload installed Skills");
                    result = new ToolResult.Failure<>(
                            "skill_projection_unavailable",
                            "Unable to reload installed Skills");
                }
            } else {
                try {
                    skillCommunityState = Objects.requireNonNull(
                            skillActions.communityView(), "current Skill community view");
                } catch (RuntimeException ignored) {
                    // Preserve the last immutable catalog projection when refresh recovery fails.
                }
                ToolResult.Failure<SkillCommunityView> failure =
                        thrown == null && completed instanceof ToolResult.Failure<SkillCommunityView> value
                                ? value
                                : new ToolResult.Failure<>(
                                        "skill_community_operation_failed",
                                        "Unable to update Skills");
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishExtensionCommunity(
            long operationId,
            ToolResult<ExtensionSettingsView> completed,
            Throwable thrown,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (thrown == null
                    && completed instanceof ToolResult.Success<ExtensionSettingsView> success) {
                extensionState = Objects.requireNonNull(
                        success.value(), "updated Extension projection");
                notice = SettingsNotice.success(successCode, switch (successCode) {
                    case "extension_capability_saved" ->
                            "Extension capability settings saved";
                    case "extension_catalog_refreshed" ->
                            "Extension community catalog refreshed";
                    case "extension_package_imported" ->
                            "Extension package staged; restart Minecraft to activate it";
                    default -> "Community Extension staged; restart Minecraft to activate it";
                });
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                try {
                    extensionState = Objects.requireNonNull(
                            extensionActions.currentView(), "current Extension projection");
                } catch (RuntimeException ignored) {
                    // Preserve the last immutable Extension projection on recovery failure.
                }
                ToolResult.Failure<ExtensionSettingsView> failure =
                        thrown == null
                                        && completed
                                                instanceof ToolResult.Failure<
                                                        ExtensionSettingsView> value
                                ? value
                                : new ToolResult.Failure<>(
                                        "extension_community_operation_failed",
                                        "Unable to update Extensions");
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishDisplay(
            long operationId,
            ToolResult<GuideDisplayConfig> completed,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<GuideDisplayConfig> success) {
                display = success.value();
                notice = SettingsNotice.success(
                        successCode,
                        successCode.equals("display_reloaded")
                                ? "Display settings reloaded"
                                : "Display settings saved");
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<GuideDisplayConfig> failure =
                        (ToolResult.Failure<GuideDisplayConfig>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void finishCommands(
            long operationId,
            ToolResult<CommandCapabilityConfig> completed,
            CompletableFuture<ToolResult<Boolean>> outward,
            String successCode) {
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<CommandCapabilityConfig> success) {
                commandState = success.value();
                notice = SettingsNotice.success(
                        successCode,
                        successCode.equals("experimental_commands_reloaded")
                                ? "Experimental command settings reloaded"
                                : "Experimental command settings saved");
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<CommandCapabilityConfig> failure =
                        (ToolResult.Failure<CommandCapabilityConfig>) completed;
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private CompletableFuture<ToolResult<Boolean>> administerHistory(
            HistoryAction action,
            HistoryConfirmationToken confirmation) {
        Objects.requireNonNull(action, "action");
        Reservation reservation;
        synchronized (lock) {
            GuideFailure failure = confirmationFailureLocked(action);
            if (failure != null) {
                return CompletableFuture.completedFuture(
                        new ToolResult.Failure<>(failure.code(), failure.message()));
            }
            ConfirmationStage stage = action == HistoryAction.RESET_DATABASE
                    ? ConfirmationStage.SECOND
                    : ConfirmationStage.FIRST;
            if (confirmation == null || !confirmation.consume(
                    action, stage, snapshot.generation())) {
                return CompletableFuture.completedFuture(confirmationRequired());
            }
            reservation = reserveLocked(historyOperation(action));
        }

        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        CompletableFuture<ToolResult<Boolean>> operationFuture;
        try {
            operationFuture = switch (action) {
                case DELETE_CURRENT -> historyActions.deleteCurrentHistory();
                case DELETE_ACTOR -> historyActions.deleteActorHistory();
                case RESET_DATABASE -> historyActions.resetHistoryDatabase();
            };
            Objects.requireNonNull(operationFuture, "history action future");
        } catch (RuntimeException failure) {
            operationFuture = CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "history_delete_failed", "Unable to update Guide history"));
        }
        operationFuture.whenComplete((completed, thrown) -> dispatcher.execute(() ->
                finishHistory(
                        reservation.id(),
                        completed,
                        thrown,
                        action,
                        result)));
        return result;
    }

    private void finishHistory(
            long operationId,
            ToolResult<Boolean> completed,
            Throwable thrown,
            HistoryAction action,
            CompletableFuture<ToolResult<Boolean>> outward) {
        HistoryRuntimeState refreshed = safeHistoryState(historyActions);
        ToolResult<Boolean> result;
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            historyState = refreshed;
            if (thrown == null && completed instanceof ToolResult.Success<Boolean>) {
                String code = switch (action) {
                    case DELETE_CURRENT -> "history_current_deleted";
                    case DELETE_ACTOR -> "history_actor_deleted";
                    case RESET_DATABASE -> "history_database_reset";
                };
                notice = SettingsNotice.success(code, "Guide history updated");
                result = new ToolResult.Success<>(Boolean.TRUE);
            } else {
                ToolResult.Failure<Boolean> failure =
                        thrown == null && completed instanceof ToolResult.Failure<Boolean> value
                                ? value
                                : new ToolResult.Failure<>(
                                        "history_delete_failed",
                                        "Unable to update Guide history");
                notice = SettingsNotice.failure(failure.code(), failure.message());
                result = new ToolResult.Failure<>(failure.code(), failure.message());
            }
            publishLocked();
        }
        outward.complete(result);
    }

    private void reconcileMetadata(
            long expectedGeneration,
            long expectedMetadataGeneration,
            ModelProfilesConfig config,
            Map<ModelMetadata.Key, ModelMetadata> entries) {
        worker.execute(() -> {
            ToolResult<PreparedModels> prepared = safely(
                    () -> models.prepare(config, Map.copyOf(entries)),
                    "metadata_unavailable",
                    "Model metadata reconciliation is unavailable");
            dispatcher.execute(() -> finishMetadataReconciliation(
                    expectedGeneration, expectedMetadataGeneration, config, prepared));
        });
    }

    private void finishMetadataReconciliation(
            long expectedGeneration,
            long expectedMetadataGeneration,
            ModelProfilesConfig preparedConfig,
            ToolResult<PreparedModels> prepared) {
        boolean retry = false;
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (modelOperationPendingLocked()) {
                // The worker may already have committed while the UI still shows the old config.
                metadataReconciliationPending = true;
                return;
            }
            if (modelGeneration != expectedGeneration
                    || metadataGeneration != expectedMetadataGeneration
                    || modelState.config() != preparedConfig) {
                retry = true;
            } else if (prepared instanceof ToolResult.Success<PreparedModels> success) {
                try {
                    if (success.value().publish().getAsBoolean()) {
                        modelState = success.value().state();
                        publishLocked();
                    } else {
                        // Registry publication, including capabilities, advanced before this callback.
                        retry = true;
                    }
                } catch (RuntimeException failure) {
                    metadataFailure = new GuideFailure(
                            "metadata_unavailable", "Model metadata reconciliation is unavailable");
                    publishLocked();
                }
            } else {
                ToolResult.Failure<PreparedModels> failure =
                        (ToolResult.Failure<PreparedModels>) prepared;
                metadataFailure = new GuideFailure(failure.code(), failure.message());
                publishLocked();
            }
        }
        if (retry) {
            reconcileCurrentMetadata();
        }
    }

    private void reconcileCurrentMetadata() {
        long expectedGeneration;
        long expectedMetadataGeneration;
        ModelProfilesConfig config;
        Map<ModelMetadata.Key, ModelMetadata> entries;
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (modelOperationPendingLocked()) {
                metadataReconciliationPending = true;
                return;
            }
            expectedGeneration = modelGeneration;
            expectedMetadataGeneration = metadataGeneration;
            config = modelState.config();
            entries = metadata;
        }
        reconcileMetadata(expectedGeneration, expectedMetadataGeneration, config, entries);
    }

    private Reservation reserve(SettingsOperation requested) {
        synchronized (lock) {
            if (closed) {
                return Reservation.rejected("settings_closed");
            }
            if (operation.kind() != SettingsOperation.Kind.IDLE) {
                return Reservation.rejected("settings_busy");
            }
            return reserveLocked(requested);
        }
    }

    private Reservation reserveLocked(SettingsOperation requested) {
        long id = operationIds.incrementAndGet();
        operation = Objects.requireNonNull(requested, "requested");
        if (modelOperationPendingLocked()) {
            // Invalidate background work before a worker can commit the new model settings.
            modelGeneration++;
        }
        notice = null;
        publishLocked();
        return Reservation.accepted(id);
    }

    private boolean modelOperationPendingLocked() {
        return operation.kind() == SettingsOperation.Kind.SAVING_MODELS
                || operation.kind() == SettingsOperation.Kind.RELOADING_MODELS;
    }

    private boolean isCurrent(long id) {
        synchronized (lock) {
            return isCurrentLocked(id);
        }
    }

    private boolean isCurrentLocked(long id) {
        return operationIds.get() == id && operation.kind() != SettingsOperation.Kind.IDLE;
    }

    /** Local non-inference projection for the currently typed endpoint and model name. */
    public ModelContextResolution modelContext(java.net.URI endpoint, String model, Integer explicit) {
        return ModelContextResolution.resolve(endpoint, model, explicit, metadataSnapshot(),
                BuiltinModelCatalog.bundled().catalog());
    }

    /** Automatic output uses the model's published maximum; explicit budgets remain manual. */
    public dev.openallay.model.metadata.ModelOutputResolution modelOutput(
            java.net.URI endpoint, String model, Integer explicit) {
        return dev.openallay.model.metadata.ModelOutputResolution.resolve(
                endpoint, model, explicit, metadataSnapshot(), BuiltinModelCatalog.bundled().catalog());
    }

    public dev.openallay.model.metadata.ModelImageCapabilityResolution modelImageCapability(
            java.net.URI endpoint, String model,
            dev.openallay.model.image.ImageInputCapability explicit) {
        return dev.openallay.model.metadata.ModelImageCapabilityResolution.resolve(
                endpoint, model, explicit, metadataSnapshot(), BuiltinModelCatalog.bundled().catalog());
    }

    private Map<ModelMetadata.Key, ModelMetadata> metadataSnapshot() {
        synchronized (lock) {
            return metadata;
        }
    }

    private void publishLocked() {
        snapshot = buildSnapshot(snapshot.generation() + 1);
        ClientSettingsSnapshot published = snapshot;
        dispatcher.execute(() -> listeners.forEach(listener -> listener.accept(published)));
    }

    private ClientSettingsSnapshot buildSnapshot(long generation) {
        ModelProfileSettingsView modelView = ModelProfileSettingsView.from(
                modelState.config(),
                modelState.profiles(),
                presentEnvironmentNames,
                metadataFailure,
                connectionResult);
        HistorySettingsView historyView = historyView(historyState);
        return new ClientSettingsSnapshot(
                generation,
                display,
                modelView,
                serverModelState,
                capabilityState,
                recipeState,
                skillState,
                skillCommunityState,
                extensionState,
                commandState,
                unrestrictedState,
                historyView,
                diagnostics.snapshot(
                        display.debugMode(),
                        new SettingsDiagnosticsAggregator.DiagnosticsInputs(
                                generation,
                                modelView,
                                capabilityState,
                                recipeState,
                                historyState.guide(),
                                historyState.activity(),
                                historyState.scopeKind(),
                                sourceState.sources().stream().map(source ->
                                        new SettingsDiagnosticsAggregator.SourceStatus(
                                                source.sourceId(), source.generation(),
                                                SettingsDiagnosticsAggregator.SourceState.valueOf(source.state().name()),
                                                source.itemCount(), source.failureCode())).toList(),
                                sourceState.loaded(),
                                sourceState.retained(),
                                historyState.estimatedContextTokens())),
                operation,
                notice,
                requirementReviewLocked());
    }

    private GuideFailure confirmationFailureLocked(HistoryAction action) {
        if (closed) {
            return new GuideFailure("settings_closed", "Settings are closed");
        }
        if (operation.kind() != SettingsOperation.Kind.IDLE) {
            return new GuideFailure(
                    "settings_busy", "Another settings operation is already running");
        }
        if (action == HistoryAction.RESET_DATABASE && !display.debugMode()) {
            return new GuideFailure(
                    "history_delete_confirmation_required",
                    "Debug Mode and a fresh second confirmation are required");
        }
        return historyFailure(historyState, action);
    }

    private static GuideFailure historyFailure(
            HistoryRuntimeState state, HistoryAction action) {
        if (!state.configured() || state.guide().isEmpty()) {
            return new GuideFailure(
                    "history_unavailable", "Durable Guide history is unavailable");
        }
        GuideSnapshot guide = state.guide().orElseThrow();
        long active = activeRequestCount(guide);
        if (!state.activity().idleForDeletion()
                || active > 0
                || guide.persistence().state() == GuidePersistenceSnapshot.State.SAVING) {
            return new GuideFailure("history_delete_busy", "Guide history is busy");
        }
        if (guide.persistence().state() == GuidePersistenceSnapshot.State.LOADING) {
            return new GuideFailure(
                    "history_loading", "Durable Guide history is still loading");
        }
        if (action != HistoryAction.RESET_DATABASE
                && guide.persistence().state() != GuidePersistenceSnapshot.State.AVAILABLE) {
            return new GuideFailure(
                    "history_unavailable", "Durable Guide history is unavailable");
        }
        return null;
    }

    private static HistorySettingsView historyView(HistoryRuntimeState state) {
        if (state.guide().isEmpty()) {
            return HistorySettingsView.disconnected();
        }
        GuideSnapshot guide = state.guide().orElseThrow();
        long active = activeRequestCount(guide);
        boolean busy = !state.activity().idleForDeletion()
                || active > 0
                || guide.persistence().state() == GuidePersistenceSnapshot.State.SAVING;
        HistorySettingsView.Health health = !state.configured()
                ? HistorySettingsView.Health.UNAVAILABLE
                : switch (guide.persistence().state()) {
                    case AVAILABLE -> busy
                            ? HistorySettingsView.Health.WORKING
                            : HistorySettingsView.Health.READY;
                    case LOADING, SAVING -> HistorySettingsView.Health.WORKING;
                    case DISABLED -> HistorySettingsView.Health.ATTENTION;
                    case UNAVAILABLE -> HistorySettingsView.Health.UNAVAILABLE;
                };
        boolean normalAvailable = state.configured()
                && !busy
                && guide.persistence().state() == GuidePersistenceSnapshot.State.AVAILABLE;
        boolean resetAvailable = state.configured()
                && !busy
                && guide.persistence().state() != GuidePersistenceSnapshot.State.LOADING;
        return new HistorySettingsView(
                switch (state.scopeKind()) {
                    case NONE -> HistorySettingsView.ConnectionKind.NONE;
                    case SINGLEPLAYER_WORLD ->
                            HistorySettingsView.ConnectionKind.SINGLEPLAYER_WORLD;
                    case MULTIPLAYER_SERVER ->
                            HistorySettingsView.ConnectionKind.MULTIPLAYER_SERVER;
                },
                health,
                state.activity().pendingWrites(),
                state.activity().deleting(),
                active,
                normalAvailable,
                normalAvailable,
                resetAvailable);
    }

    private static long activeRequestCount(GuideSnapshot guide) {
        return guide.sessions().stream()
                .flatMap(session -> session.requests().stream())
                .filter(request -> !request.terminal())
                .count();
    }

    private static SettingsOperation historyOperation(HistoryAction action) {
        return SettingsOperation.domain(switch (action) {
            case DELETE_CURRENT -> SettingsOperation.Kind.DELETING_CURRENT_HISTORY;
            case DELETE_ACTOR -> SettingsOperation.Kind.DELETING_ACTOR_HISTORY;
            case RESET_DATABASE -> SettingsOperation.Kind.RESETTING_HISTORY_DATABASE;
        });
    }

    private static <T> ToolResult<T> confirmationRequired() {
        return new ToolResult.Failure<>(
                "history_delete_confirmation_required",
                "A fresh history confirmation is required");
    }

    private static CompletableFuture<ToolResult<Boolean>> discardConfirmation() {
        return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "settings_discard_confirmation_required",
                "Reload requires confirmation to discard unsaved changes"));
    }

    private static ToolResult<Boolean> failed(String code) {
        return new ToolResult.Failure<>(code, switch (code) {
            case "settings_closed" -> "Settings are closed";
            default -> "Another settings operation is already running";
        });
    }

    private static ModelConnectionResult.Failure connectionFailure(
            String code, String message) {
        return new ModelConnectionResult.Failure(code, message);
    }

    private static String safeProbeMessage(String code) {
        return switch (code) {
            case "model_not_configured" -> "The configured credential is unavailable";
            case "model_disabled" -> "The model profile is disabled";
            default -> "The model profile is invalid";
        };
    }

    private static <T> ToolResult<T> safely(
            Supplier<ToolResult<T>> action,
            String code,
            String message) {
        try {
            return Objects.requireNonNull(action.get(), "settings action result");
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(code, message);
        }
    }

    private static CapabilityActions defaultCapabilityActions() {
        return new CapabilityActions() {
            @Override
            public ToolResult<CapabilitySettingsView> saveCapabilities(
                    CapabilityPolicy candidate) {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Capability settings are unavailable");
            }

            @Override
            public ToolResult<CapabilitySettingsView> reloadCapabilities() {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Capability settings are unavailable");
            }
        };
    }

    private static RecipeActions defaultRecipeActions() {
        return new RecipeActions() {
            @Override
            public ToolResult<RecipeSettingsView> saveRecipes(RecipeClientConfig candidate) {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Recipe settings are unavailable");
            }

            @Override
            public ToolResult<RecipeSettingsView> reloadRecipes() {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Recipe settings are unavailable");
            }
        };
    }

    private static SkillActions defaultSkillActions() {
        return new SkillActions() {
            @Override
            public ToolResult<SkillSettingsView> saveOverride(String name, String markdown) {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Skill settings are unavailable");
            }

            @Override
            public ToolResult<SkillSettingsView> deleteOverride(String name) {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Skill settings are unavailable");
            }

            @Override
            public ToolResult<SkillSettingsView> reloadSkills() {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Skill settings are unavailable");
            }

            @Override
            public SkillSettingsView currentView() {
                return SkillSettingsView.empty();
            }
        };
    }

    private static ExtensionActions defaultExtensionActions(
            ExtensionSettingsView initial) {
        Objects.requireNonNull(initial, "initial");
        return new ExtensionActions() {
            @Override
            public ExtensionSettingsView currentView() {
                return initial;
            }
        };
    }

    private static DisplayActions defaultDisplayActions() {
        return new DisplayActions() {
            @Override
            public ToolResult<GuideDisplayConfig> saveDisplay(GuideDisplayConfig candidate) {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Display settings are unavailable");
            }

            @Override
            public ToolResult<GuideDisplayConfig> reloadDisplay() {
                return new ToolResult.Failure<>(
                        "settings_unavailable", "Display settings are unavailable");
            }
        };
    }

    private static CommandActions defaultCommandActions() {
        return new CommandActions() {
            @Override
            public ToolResult<CommandCapabilityConfig> save(
                    CommandCapabilityConfig candidate) {
                return new ToolResult.Failure<>(
                        "settings_unavailable",
                        "Experimental command settings are unavailable");
            }

            @Override
            public ToolResult<CommandCapabilityConfig> reload() {
                return new ToolResult.Failure<>(
                        "settings_unavailable",
                        "Experimental command settings are unavailable");
            }
        };
    }

    private static HistoryActions defaultHistoryActions() {
        return new HistoryActions() {
            @Override
            public HistoryRuntimeState state() {
                return HistoryRuntimeState.disconnected();
            }

            @Override
            public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() {
                return unavailableHistory();
            }

            @Override
            public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() {
                return unavailableHistory();
            }

            @Override
            public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() {
                return unavailableHistory();
            }
        };
    }

    private static CompletableFuture<ToolResult<Boolean>> unavailableHistory() {
        return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "history_unavailable", "Durable Guide history is unavailable"));
    }

    private static HistoryRuntimeState safeHistoryState(HistoryActions actions) {
        try {
            return Objects.requireNonNull(actions.state(), "history state");
        } catch (RuntimeException failure) {
            return HistoryRuntimeState.disconnected();
        }
    }

    private record Reservation(long id, String failureCode) {
        private static Reservation accepted(long id) {
            return new Reservation(id, null);
        }

        private static Reservation rejected(String code) {
            return new Reservation(-1, code);
        }

        private boolean accepted() {
            return failureCode == null;
        }
    }

    private record ActiveProbe(
            long id,
            CancellationSignal cancellation,
            CompletableFuture<ModelConnectionResult> result) {}

    private record ActiveCatalog(
            long id,
            CancellationSignal cancellation,
            CompletableFuture<ToolResult<ModelCatalog>> result) {}
}
