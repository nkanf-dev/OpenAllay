package dev.tomewisp.settings;

import dev.tomewisp.client.ClientEventDispatcher;
import dev.tomewisp.guide.GuideFailure;
import dev.tomewisp.guide.ui.GuideDisplayConfig;
import dev.tomewisp.model.CancellationSignal;
import dev.tomewisp.model.config.ModelProfileDefinition;
import dev.tomewisp.model.config.ModelProfilesConfig;
import dev.tomewisp.model.config.ResolvedModelProfile;
import dev.tomewisp.model.metadata.ModelMetadata;
import dev.tomewisp.model.metadata.ModelMetadataUpdate;
import dev.tomewisp.settings.model.ModelConnectionResult;
import dev.tomewisp.settings.model.ModelProfileSettingsView;
import dev.tomewisp.tool.ToolResult;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Common owner for native settings actions and immutable UI snapshots. */
public final class ClientSettingsService implements AutoCloseable {
    public interface ModelActions {
        ToolResult<ModelState> save(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata);

        ToolResult<ModelState> reload(Map<ModelMetadata.Key, ModelMetadata> metadata);

        ToolResult<ResolvedModelProfile> resolve(
                ModelProfileDefinition candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata);

        ToolResult<PreparedModels> prepare(
                ModelProfilesConfig candidate,
                Map<ModelMetadata.Key, ModelMetadata> metadata);

        CompletableFuture<ModelConnectionResult> probe(
                ResolvedModelProfile profile, CancellationSignal cancellation);
    }

    public interface MetadataActions {
        CompletableFuture<Void> refresh();

        CompletableFuture<Void> closeAsync();
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

    public record PreparedModels(ModelState state, Runnable publish) {
        public PreparedModels {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(publish, "publish");
        }
    }

    private final Object lock = new Object();
    private final GuideDisplayConfig display;
    private final Set<String> presentEnvironmentNames;
    private final ModelActions models;
    private final MetadataActions metadataActions;
    private final ClientEventDispatcher dispatcher;
    private final Executor worker;
    private final CopyOnWriteArrayList<Consumer<ClientSettingsSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private final AtomicLong operationIds = new AtomicLong();

    private ModelState modelState;
    private long modelGeneration;
    private long metadataGeneration;
    private Map<ModelMetadata.Key, ModelMetadata> metadata = Map.of();
    private GuideFailure metadataFailure;
    private ModelConnectionResult connectionResult;
    private SettingsOperation operation = SettingsOperation.idle();
    private SettingsNotice notice;
    private ClientSettingsSnapshot snapshot;
    private ActiveProbe activeProbe;
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
        this.display = Objects.requireNonNull(display, "display");
        this.modelState = Objects.requireNonNull(initialModels, "initialModels");
        this.presentEnvironmentNames = Collections.unmodifiableSet(
                new TreeSet<>(presentEnvironmentNames));
        this.models = Objects.requireNonNull(models, "models");
        this.metadataActions = Objects.requireNonNull(metadataActions, "metadataActions");
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
                    () -> models.save(candidate, metadataSnapshot),
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

    public CompletableFuture<ModelConnectionResult> testConnection(
            ModelProfileDefinition candidate) {
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
                reservation.id(), candidate, metadataSnapshot, cancellation, outward));
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
        }
        cancelConnectionTest();
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
            Map<ModelMetadata.Key, ModelMetadata> metadataSnapshot,
            CancellationSignal cancellation,
            CompletableFuture<ModelConnectionResult> outward) {
        if (!isCurrent(operationId)) {
            return;
        }
        ToolResult<ResolvedModelProfile> resolved = safely(
                () -> models.resolve(candidate, metadataSnapshot),
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
        synchronized (lock) {
            if (!isCurrentLocked(operationId)) {
                return;
            }
            operation = SettingsOperation.idle();
            if (completed instanceof ToolResult.Success<ModelState> success) {
                modelState = success.value();
                modelGeneration++;
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
        ModelProfilesConfig retryConfig = null;
        long retryGeneration = -1;
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (modelGeneration != expectedGeneration
                    || metadataGeneration != expectedMetadataGeneration
                    || modelState.config() != preparedConfig) {
                retryConfig = modelState.config();
                retryGeneration = modelGeneration;
            } else if (prepared instanceof ToolResult.Success<PreparedModels> success) {
                try {
                    success.value().publish().run();
                    modelState = success.value().state();
                } catch (RuntimeException failure) {
                    metadataFailure = new GuideFailure(
                            "metadata_unavailable", "Model metadata reconciliation is unavailable");
                }
                publishLocked();
            } else {
                ToolResult.Failure<PreparedModels> failure =
                        (ToolResult.Failure<PreparedModels>) prepared;
                metadataFailure = new GuideFailure(failure.code(), failure.message());
                publishLocked();
            }
        }
        if (retryConfig != null) {
            long currentMetadataGeneration;
            synchronized (lock) {
                currentMetadataGeneration = metadataGeneration;
            }
            reconcileMetadata(
                    retryGeneration,
                    currentMetadataGeneration,
                    retryConfig,
                    metadataSnapshot());
        }
    }

    private Reservation reserve(SettingsOperation requested) {
        synchronized (lock) {
            if (closed) {
                return Reservation.rejected("settings_closed");
            }
            if (operation.kind() != SettingsOperation.Kind.IDLE) {
                return Reservation.rejected("settings_busy");
            }
            long id = operationIds.incrementAndGet();
            operation = requested;
            notice = null;
            publishLocked();
            return Reservation.accepted(id);
        }
    }

    private boolean isCurrent(long id) {
        synchronized (lock) {
            return isCurrentLocked(id);
        }
    }

    private boolean isCurrentLocked(long id) {
        return operationIds.get() == id && operation.kind() != SettingsOperation.Kind.IDLE;
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
        return new ClientSettingsSnapshot(
                generation,
                display,
                ModelProfileSettingsView.from(
                        modelState.config(),
                        modelState.profiles(),
                        presentEnvironmentNames,
                        metadataFailure,
                        connectionResult),
                operation,
                notice);
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
}
