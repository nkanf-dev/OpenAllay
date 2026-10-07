package dev.openallay.guide;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryDeleteScope;
import dev.openallay.guide.history.GuideHistoryException;
import dev.openallay.guide.history.GuideHistoryMetadata;
import dev.openallay.guide.history.GuideHistoryPage;
import dev.openallay.guide.history.GuideHistoryPageRequest;
import dev.openallay.guide.history.GuideHistoryCursor;
import dev.openallay.guide.history.GuideHistoryCommit;
import dev.openallay.guide.history.GuideHistoryContextRequest;
import dev.openallay.guide.history.GuideHistoryContextSeed;
import dev.openallay.guide.history.GuideHistoryMutation;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.guide.history.GuideHistoryForkRequest;
import dev.openallay.guide.history.GuideHistoryForkResult;
import dev.openallay.guide.export.GuideSessionExportCollector;
import dev.openallay.guide.export.GuideSessionExportSnapshot;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Single client product state source for commands and screens. */
public final class GuideService implements GuideHistoryAdministration {
    private final UUID actor;
    private final GuideLocalEndpoint local;
    private final GuideRemoteEndpoint remote;
    private final GuideContextProvider contexts;
    private final ClientEventDispatcher dispatcher;
    private final Clock clock;
    private final GuideStateReducer reducer;
    private final GuideLivePresentationSource presentation;
    private final GuideHistoryScope historyScope;
    private final GuideHistoryAccess history;
    private final dev.openallay.model.image.ImageAttachmentStore attachmentStore;
    private static final java.util.concurrent.ExecutorService IMAGE_IO =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "openallay-image-artifacts");
                thread.setDaemon(true);
                return thread;
            });
    private final String imageOwnerPrefix = "connection:" + UUID.randomUUID() + ":";
    private final Map<String, List<dev.openallay.model.image.ImageReference>> retainedImages =
            new LinkedHashMap<>();
    private final Map<UUID, dev.openallay.model.image.ImageInputCapability> requestImageCapabilities =
            new LinkedHashMap<>();
    private final Set<String> imageImportOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, dev.openallay.model.image.ImageReference> importedImageReferences =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> imageDraftOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, dev.openallay.model.ModelMessage> submittedInputs = new LinkedHashMap<>();
    private final Map<String, SessionState> sessions = new LinkedHashMap<>();
    private final Map<UUID, String> requestSessions = new LinkedHashMap<>();
    private final Map<UUID, CancelledFinalization> pendingCancelledFinalization = new LinkedHashMap<>();
    // Dispatcher-owned barriers. Their continuations use immutable image snapshots only.
    private final Map<UUID, CompletableFuture<Void>> observationReleases = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<GuideSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private volatile GuideSnapshot snapshot;
    private volatile Map<String, SessionState> publishedSessions = dev.openallay.util.Java8Collections.mapOf();
    private volatile Map<UUID, Map<String, List<dev.openallay.model.image.ImageReference>>> publishedObservationImages = dev.openallay.util.Java8Collections.mapOf();
    private volatile GuideTelemetrySnapshot telemetry;
    private String selectedSession = "main";
    private String compactSelectedSession = "main";
    private GuideModelSelection compactSelectedModel;
    private long compactSelectionEpoch;
    private long sessionSelectionGeneration;
    private final Map<String, UUID> pendingForks = new LinkedHashMap<>();
    private final Map<String, String> pendingForkImageOwners = new LinkedHashMap<>();
    private GuidePersistenceSnapshot persistence;
    private boolean allowHistoryWrites;
    private boolean historyDeletionPending;
    private volatile boolean closing;
    private volatile boolean disconnected;
    private CompletableFuture<Void> disconnectFuture;
    private final Map<UUID, CompletableFuture<Void>> endpointSettled = new LinkedHashMap<>();
    private boolean incrementalHistory;
    private final DurableProjection durableProjection = new DurableProjection();
    // Captured rows suppress duplicate deltas; only successful writes enter durableProjection.
    private final DurableProjection capturedProjection = new DurableProjection();
    private final List<GuideHistoryMutation> pendingHistoryMutations = new ArrayList<>();
    private final HistoryMutationBuffer queuedHistoryMutations = new HistoryMutationBuffer();
    private HistoryWrite inFlightHistoryWrite;
    private final List<HistoryWriteBarrier> historyWriteBarriers = new ArrayList<>();

    public GuideService(
            UUID actor,
            GuideLocalEndpoint local,
            GuideRemoteEndpoint remote,
            GuideContextProvider contexts,
            ClientEventDispatcher dispatcher,
            Clock clock,
            Gson gson) {
        this(actor, local, remote, contexts, dispatcher, clock, gson, null, null);
    }

    public GuideService(
            UUID actor,
            GuideLocalEndpoint local,
            GuideRemoteEndpoint remote,
            GuideContextProvider contexts,
            ClientEventDispatcher dispatcher,
            Clock clock,
            Gson gson,
            GuideHistoryScope historyScope,
            GuideHistoryAccess history) {
        this(actor, local, remote, contexts, dispatcher, clock, gson, historyScope, history, null);
    }

    public GuideService(
            UUID actor, GuideLocalEndpoint local, GuideRemoteEndpoint remote,
            GuideContextProvider contexts, ClientEventDispatcher dispatcher, Clock clock, Gson gson,
            GuideHistoryScope historyScope, GuideHistoryAccess history,
            dev.openallay.model.image.ImageAttachmentStore attachmentStore) {
        this.attachmentStore = attachmentStore;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.presentation = new GuideLivePresentationSource(actor);
        this.local = local;
        this.remote = Objects.requireNonNull(remote, "remote");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        if ((historyScope == null) != (history == null)) {
            throw new IllegalArgumentException("history scope and access must be configured together");
        }
        if (historyScope != null && !historyScope.actorId().equals(actor)) {
            throw new IllegalArgumentException("history scope belongs to another actor");
        }
        this.historyScope = historyScope;
        this.history = history;
        reducer = new GuideStateReducer(gson);
        sessions.put("main", new SessionState("main", defaultClientSelection()));
        persistence = history == null
                ? GuidePersistenceSnapshot.disabled()
                : GuidePersistenceSnapshot.loading();
        publishedSessions = dev.openallay.util.Java8Collections.mapCopyOf(sessions);
        snapshot = buildSnapshot();
        if (history != null) {
            startHistoryLoad();
        }
    }

    public GuideSnapshot snapshot() {
        return snapshot;
    }

    public UUID presentationGeneration() { return presentation.generation(); }

    /** Cached identity only; deletion/recreation of the same session name gets a new owner. */
    public java.util.Optional<UUID> presentationSessionOwner(String sessionId) {
        SessionState session = publishedSessions.get(sessionId);
        return session == null ? java.util.Optional.empty() : java.util.Optional.of(session.presentationOwner);
    }

    /** Live-only subscription. No initial snapshot, history replay or dispatcher binding gap. */
    public GuideSubscription subscribePresentation(Consumer<GuidePresentationEvent> listener) {
        return presentation.subscribe(listener);
    }

    void invalidatePresentation() { presentation.invalidate(); }

    /** Cached numeric state only; safe for a screen tick without capture, history I/O or tokenization. */
    public GuideTelemetrySnapshot telemetry() {
        GuideTelemetrySnapshot value = telemetry;
        return value == null
                ? GuideTelemetrySnapshot.unknown(snapshot.selectedSession(), snapshot.modelSelection())
                : value;
    }

    private GuideTelemetrySnapshot buildTelemetry() {
        SessionState selected = sessions.get(snapshot.selectedSession());
        if (selected == null) return GuideTelemetrySnapshot.unknown(
                snapshot.selectedSession(), snapshot.modelSelection());
        GuideUsageTracker usage = selected.usage;
        GuideRequestSnapshot latest = selected.requests.isEmpty() ? null : selected.requests.get(selected.requests.size() - 1);
        GuideUsageSnapshot requestUsage = latest == null ? GuideUsageSnapshot.empty() : latest.usageProjection();
        return new GuideTelemetrySnapshot(selected.id, snapshot.modelSelection(),
                latest == null ? null : latest.requestId(), contextEstimate().orElse(null),
                requestUsage, usage.sessionSnapshot(), usage.inheritedSnapshot());
    }

    private String publicModelIdentifier(GuideModelSelection selection) {
        if (selection.kind() != GuideModelSelection.Kind.CLIENT) return null;
        return snapshot.clientProfiles().stream().filter(profile -> profile.id().equals(selection.profileId()))
                .map(GuideClientModelProfile::modelIdentifier).findFirst().orElse(null);
    }

    /** Runtime projection; never reconstructed from checkpoint sums or transcript pages. */
    public java.util.Optional<GuideContextEstimate> contextEstimate() {
        if (disconnected || local == null) return java.util.Optional.empty();
        GuideSessionSnapshot selected = snapshot.sessions().stream()
                .filter(session -> session.sessionId().equals(snapshot.selectedSession()))
                .findFirst().orElse(null);
        if (selected == null || selected.requests().isEmpty()
                || selected.modelSelection().kind() != GuideModelSelection.Kind.CLIENT) {
            return java.util.Optional.empty();
        }
        GuideRequestSnapshot request = selected.requests().stream().filter(value -> !value.terminal())
                .reduce((first, second) -> second)
                .orElse(selected.requests().get(selected.requests().size() - 1));
        if (!request.modelSelection().equals(selected.modelSelection())) {
            return java.util.Optional.empty();
        }
        return local.contextEstimate(selected.modelSelection().profileId(), actor, selected.sessionId())
                .filter(estimate -> estimate.requestId().equals(request.requestId())
                        && Objects.equals(estimate.modelIdentifier(), publicModelIdentifier(selected.modelSelection())));
    }

    public GuideSubscription subscribe(Consumer<GuideSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        dispatcher.execute(() -> {
            listeners.add(listener);
            listener.accept(snapshot);
        });
        return () -> listeners.remove(listener);
    }

    /** Encoded PNG/JPEG bytes only. Decoding and managed file I/O never run on the client owner thread. */
    public CompletableFuture<ToolResult<dev.openallay.model.image.ImageReference>> importImage(
            byte[] encodedImage) {
        if (attachmentStore == null || closing || disconnected) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection"));
        Objects.requireNonNull(encodedImage, "encodedImage");
        byte[] captured = encodedImage.clone();
        String owner = imageOwnerPrefix + "import:" + UUID.randomUUID();
        imageImportOwners.add(owner);
        return imageOperation(() -> {
            if (closing || disconnected) {
                imageImportOwners.remove(owner);
                throw new java.io.IOException("Image connection is closed");
            }
            dev.openallay.model.image.ImageReference reference = attachmentStore.importImage(actor, owner, captured);
            importedImageReferences.put(owner, reference);
            return reference;
        });
    }

    public CompletableFuture<ToolResult<Boolean>> releaseImportedImage(
            dev.openallay.model.image.ImageReference reference) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Success<>(false));
        return imageOperation(() -> {
            for (java.util.Map.Entry<java.lang.String, dev.openallay.model.image.ImageReference> entry : dev.openallay.util.Java8Collections.listCopyOf(importedImageReferences.entrySet())) {
                if (entry.getValue().equals(reference)) {
                    attachmentStore.release(actor, entry.getKey());
                    importedImageReferences.remove(entry.getKey(), entry.getValue());
                    imageImportOwners.remove(entry.getKey());
                }
            }
            return true;
        });
    }

    /** Original typed Tool images, never inferred from normalized result JSON. */
    public List<dev.openallay.model.image.ImageReference> observationImages(UUID requestId, String toolUseId) {
        if (requestId == null || toolUseId == null) return dev.openallay.util.Java8Collections.listOf();
        return publishedObservationImages.getOrDefault(requestId, dev.openallay.util.Java8Collections.mapOf())
                .getOrDefault(toolUseId, dev.openallay.util.Java8Collections.listOf());
    }

    public CompletableFuture<ToolResult<byte[]>> readImage(
            dev.openallay.model.image.ImageReference reference) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection"));
        return imageOperation(() -> attachmentStore.read(actor, reference));
    }

    public CompletableFuture<ToolResult<Boolean>> retainDraftImages(
            String owner, List<dev.openallay.model.image.ImageReference> references) {
        if (owner == null || dev.openallay.util.Java8Strings.isBlank(owner)) return CompletableFuture.completedFuture(
                new ToolResult.Failure<>("invalid_image_owner", "Image draft owner is required"));
        List<dev.openallay.model.image.ImageReference> captured = dev.openallay.util.Java8Collections.listCopyOf(references);
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection"));
        imageDraftOwners.add(imageOwnerPrefix + "draft:" + owner);
        return imageOperation(() -> {
            attachmentStore.retain(actor, imageOwnerPrefix + "draft:" + owner, captured);
            // Publish the durable draft pin before removing the short import lease.
            for (java.util.Map.Entry<java.lang.String, dev.openallay.model.image.ImageReference> entry : dev.openallay.util.Java8Collections.listCopyOf(importedImageReferences.entrySet())) {
                if (captured.contains(entry.getValue())) {
                    attachmentStore.release(actor, entry.getKey());
                    importedImageReferences.remove(entry.getKey(), entry.getValue());
                    imageImportOwners.remove(entry.getKey());
                }
            }
            return true;
        });
    }

    public CompletableFuture<ToolResult<Boolean>> releaseDraftImages(String owner) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Success<>(false));
        imageDraftOwners.remove(imageOwnerPrefix + "draft:" + owner);
        return imageOperation(() -> {
            attachmentStore.release(actor, imageOwnerPrefix + "draft:" + owner);
            return true;
        });
    }

    /** Reject unsupported typed input without changing the selected provider or losing a draft. */
    public ToolResult<Boolean> validateUserInput(
            dev.openallay.model.ModelMessage input, GuideModelSelection selection) {
        try {
            dev.openallay.model.ModelMessage.requireUserInput(input);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return new ToolResult.Failure<>("invalid_question", "Enter text or attach an image");
        }
        if (input.inputObservation().isPresent()
                && !actor.equals(input.inputObservation().orElseThrow().focus().actorId())) {
            return new ToolResult.Failure<>("input_source_actor_mismatch", "Input reference belongs to another player");
        }
        boolean images = dev.openallay.model.image.ModelImages.hasImages(dev.openallay.util.Java8Collections.listOf(input));
        if (!images) return new ToolResult.Success<>(true);
        return validateImageCapability(input, snapshot.imageInputCapability(selection));
    }

    private void captureImageCapability(UUID requestId, GuideModelSelection selection) {
        requestImageCapabilities.put(requestId, snapshot.imageInputCapability(selection));
    }

    private dev.openallay.model.image.ImageInputCapability capturedImageCapability(UUID requestId) {
        return requestImageCapabilities.getOrDefault(requestId,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN);
    }

    private ToolResult<Boolean> validateCapturedUserInput(
            dev.openallay.model.ModelMessage input, UUID requestId) {
        try { dev.openallay.model.ModelMessage.requireUserInput(input); }
        catch (IllegalArgumentException | NullPointerException invalid) {
            return new ToolResult.Failure<>("invalid_question", "Enter text or attach an image");
        }
        if (input.inputObservation().isPresent()
                && !actor.equals(input.inputObservation().orElseThrow().focus().actorId())) {
            return new ToolResult.Failure<>("input_source_actor_mismatch", "Input reference belongs to another player");
        }
        return validateImageCapability(input, requestImageCapabilities.getOrDefault(requestId,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN));
    }

    private ToolResult<Boolean> validateImageCapability(
            dev.openallay.model.ModelMessage input,
            dev.openallay.model.image.ImageInputCapability capability) {
        if (!dev.openallay.model.image.ModelImages.hasImages(dev.openallay.util.Java8Collections.listOf(input))) {
            return new ToolResult.Success<>(true);
        }
        if (capability != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
            return new ToolResult.Failure<>(capability == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                    ? "image_model_unknown" : "image_model_unsupported",
                    capability == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                            ? "Image input is not confirmed for this model. Choose a supported model or set its image capability in model settings."
                            : "This model does not support image input. Choose a supported model; your draft is kept.");
        }
        if (attachmentStore == null) return new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection");
        return new ToolResult.Success<>(true);
    }

    /** Freezes actor scope. No caller can select a filesystem path or another player's image. */
    private dev.openallay.model.image.ImagePayloadResolver images(UUID requestId) {
        if (attachmentStore == null) return dev.openallay.model.image.ImagePayloadResolver.unavailable();
        String session = requestSessions.get(requestId);
        if (session == null) throw new IllegalArgumentException("Image request owner is unavailable");
        retainUserInput(requestId, userInput(requestId));
        // Input receipts and active observation producers already pin these bytes. Provider
        // reads need no blocking owner-thread wait; terminal cleanup uses the async barrier.
        return reference -> attachmentStore.read(actor, reference);
    }

    /** Retain this session input before normal ask dispatch may release its import lease. */
    private CompletableFuture<Void> retainUserInput(
            UUID requestId, dev.openallay.model.ModelMessage input) {
        String sessionId = requestSessions.get(requestId);
        SessionState session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) throw new IllegalArgumentException("Image request owner is unavailable");
        return retainSessionImages(session, imageReferences(dev.openallay.util.Java8Collections.listOf(input)));
    }

    private String sessionImageOwner(SessionState session) {
        return imageOwnerPrefix + "session:" + session.id + ":" + session.imageOwner;
    }

    /** Called on the dispatcher: collect typed originals and all still-live projections. */
    private CompletableFuture<Void> retainPublishedImages(SessionState session) {
        List<dev.openallay.model.ModelMessage> messages = new ArrayList<>(session.modelContext);
        session.originalContext.values().forEach(messages::addAll);
        session.forkBoundaries.values().forEach(boundary -> messages.addAll(boundary.messages()));
        // Checkpoints contain text and source indices, not independent image references.
        return retainSessionImages(session, imageReferences(dev.openallay.util.Java8Collections.listCopyOf(messages)));
    }

    private CompletableFuture<Void> retainSessionImages(SessionState session,
            List<dev.openallay.model.image.ImageReference> references) {
        String owner = sessionImageOwner(session);
        List<dev.openallay.model.image.ImageReference> union = new ArrayList<>(
                retainedImages.getOrDefault(owner, dev.openallay.util.Java8Collections.listOf()));
        for (dev.openallay.model.image.ImageReference reference : references) if (!union.contains(reference)) union.add(reference);
        if (union.isEmpty()) return session.imageCustody;
        List<dev.openallay.model.image.ImageReference> captured = dev.openallay.util.Java8Collections.listCopyOf(union);
        retainedImages.put(owner, captured);
        CompletableFuture<Void> retained = attachmentStore == null
                ? dev.openallay.util.Java8Futures.failedFuture(new java.io.IOException(
                        "Published image attachments are unavailable on this connection"))
                : CompletableFuture.runAsync(() -> {
                    try { attachmentStore.retain(actor, owner, captured); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }, IMAGE_IO);
        session.imageCustody = retained;
        retained.whenComplete((ignored, failure) -> {
            if (failure != null) dispatcher.execute(() -> {
                if (!disconnected && sessions.get(session.id) == session) reportImageCustodyFailure(failure);
            });
        });
        return retained;
    }

    private CompletableFuture<Void> releaseObservationImages(SessionState session, UUID requestId) {
        return observationReleases.computeIfAbsent(requestId, ignored -> {
            List<dev.openallay.model.image.ImageReference> produced = dev.openallay.util.Java8Collections.listCopyOf(contexts.observationImageReferences(requestId.toString()));
            CompletableFuture<Void> retained = retainPublishedImages(session);
            List<dev.openallay.model.image.ImageReference> published = retainedImages.getOrDefault(
                    sessionImageOwner(session), dev.openallay.util.Java8Collections.listOf());
            if (!published.containsAll(produced)) return dev.openallay.util.Java8Futures.failedFuture(new java.io.IOException("Produced observation images have no authoritative transcript custody receipt"));
            return retained.thenCompose(receipt -> Objects.requireNonNull(
                    contexts.releaseObservationImages(requestId.toString()), "observation image release future"));
        });
    }

    private void stopObservations(UUID requestId) {
        try { contexts.closeRequest(requestId.toString()); }
        catch (RuntimeException failure) { reportImageCustodyFailure(failure); }
    }

    private void reportImageCustodyFailure(Throwable failure) {
        persistence = new GuidePersistenceSnapshot(GuidePersistenceSnapshot.State.UNAVAILABLE,
                persistence.submittedGeneration(), persistence.committedGeneration(),
                new GuideFailure("observation_image_handoff_failed",
                        "Observation image custody could not complete; retained assets are preserved: " + message(failure)));
        publishWithoutSave();
    }

    /** Pin receipt before acknowledging a queued message or steer. Caller commits state after completion. */
    private CompletableFuture<Void> transferImageInput(
            String sessionId, UUID receipt, dev.openallay.model.ModelMessage input) {
        List<dev.openallay.model.image.ImageReference> references = imageReferences(dev.openallay.util.Java8Collections.listOf(input));
        if (references.isEmpty()) return CompletableFuture.completedFuture(null);
        if (attachmentStore == null) return dev.openallay.util.Java8Futures.failedFuture(new java.io.IOException("Image attachments are unavailable on this connection"));
        String owner = imageOwnerPrefix + "receipt:" + receipt;
        retainedImages.put(owner, references);
        return CompletableFuture.runAsync(() -> {
            try { attachmentStore.retain(actor, owner, references); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, IMAGE_IO);
    }

    private CompletableFuture<Void> retainPendingInput(
            UUID receipt, dev.openallay.model.ModelMessage input) {
        return transferImageInput(selectedSession, receipt, input);
    }

    private CompletableFuture<Void> releasePendingInput(UUID receipt) {
        return releaseImageInput(receipt);
    }

    private void releaseSessionImageReferences(SessionState session) {
        releaseSessionImageReferences(sessionImageOwner(session), session.imageCustody);
    }

    private void releaseSessionImageReferences(String owner, CompletableFuture<Void> imageCustody) {
        if (attachmentStore == null) return;
        retainedImages.remove(owner);
        CompletableFuture<Void> durable = history != null && allowHistoryWrites
                ? drainHistoryWrites() : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> barrier = CompletableFuture.allOf(imageCustody, durable);
        barrier.thenRunAsync(() -> {
            try { attachmentStore.release(actor, owner); }
            catch (java.io.IOException ignored) { /* Preserve files when release cannot be saved. */ }
        }, IMAGE_IO);
    }

    private CompletableFuture<Void> releaseImageInput(UUID receipt) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(null);
        String owner = imageOwnerPrefix + "receipt:" + receipt;
        retainedImages.remove(owner);
        return CompletableFuture.runAsync(() -> {
            try { attachmentStore.release(actor, owner); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, IMAGE_IO);
    }

    private static List<dev.openallay.model.image.ImageReference> imageReferences(
            List<dev.openallay.model.ModelMessage> messages) {
        return dev.openallay.model.image.ModelImages.uniqueReferences(messages);
    }

    private <T> CompletableFuture<ToolResult<T>> imageOperation(ImageOperation<T> action) {
        CompletableFuture<ToolResult<T>> result = new CompletableFuture<>();
        IMAGE_IO.execute(() -> {
            ToolResult<T> value;
            try { value = new ToolResult.Success<>(action.run()); }
            catch (java.io.IOException | IllegalArgumentException failure) {
                value = new ToolResult.Failure<>("image_attachment_failed",
                        "Unable to read or store the image attachment: " + failure.getMessage());
            }
            ToolResult<T> completed = value;
            dispatcher.execute(() -> result.complete(completed));
        });
        return result;
    }

    @FunctionalInterface
    private interface ImageOperation<T> { T run() throws java.io.IOException; }

    public dev.openallay.model.ModelMessage userInput(UUID requestId) {
        dev.openallay.model.ModelMessage input = submittedInputs.get(requestId);
        if (input == null) throw new IllegalArgumentException("Request input is unavailable");
        return input;
    }

    public boolean compactAvailable() {
        GuideModelSelection selected = snapshot.modelSelection();
        return !disconnected && local != null && selected.kind() == GuideModelSelection.Kind.CLIENT
                && local.compactAvailable(selected.profileId());
    }

    public CompletableFuture<ToolResult<GuideCompactResult>> compactSelectedSession() {
        String selected = snapshot.selectedSession();
        SessionState captured = publishedSessions.get(selected);
        CompletableFuture<ToolResult<GuideCompactResult>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (captured == null || sessions.get(selected) != captured || !selected.equals(selectedSession)) {
                result.complete(new ToolResult.Failure<>("compact_stale", "The selected session changed")); return;
            }
            startManualCompaction(result);
        });
        return result;
    }

    private void startManualCompaction(CompletableFuture<ToolResult<GuideCompactResult>> result) {
        if (rejectStateChange(result)) return;
        fenceManualCompactionSelection();
        SessionState session = sessions.get(selectedSession);
        if (session == null || requestSessionBusy(session)) {
            result.complete(new ToolResult.Failure<>("compact_busy",
                    "Wait for the current request and queued messages to finish before using /compact"));
            return;
        }
        GuideModelSelection selection = session.modelSelection;
        if (local == null || selection.kind() != GuideModelSelection.Kind.CLIENT
                || !local.compactAvailable(selection.profileId())) {
            result.complete(new ToolResult.Failure<>("compact_unavailable",
                    "Manual compaction is available only with a configured client model"));
            return;
        }
        Object endpoint;
        try { endpoint = local.compactIdentity(selection.profileId()); }
        catch (RuntimeException unavailable) {
            result.complete(new ToolResult.Failure<>("compact_unavailable", "The selected model is unavailable"));
            return;
        }
        ManualCompaction control = new ManualCompaction(UUID.randomUUID(), session, selection,
                compactSelectionEpoch, session.contextGeneration, endpoint, result);
        session.manualCompaction = control;
        session.usage.registerControl(control.id, selection, publicModelIdentifier(selection));
        publishWithoutSave();
        CompletableFuture<GuideHistoryContextSeed> source;
        if (local.hasContext(actor, session.id) || !incrementalHistory) {
            source = CompletableFuture.completedFuture(new GuideHistoryContextSeed(
                    session.id, session.modelContext, session.checkpoints, 0));
        } else {
            GuideContextSpec spec;
            try { spec = local.contextSpec(selection.profileId()).orElse(null); }
            catch (RuntimeException unavailable) { spec = null; }
            if (spec == null) {
                finishManualCompaction(control, null, "compact_unavailable", "The selected model budget is unavailable");
                return;
            }
            try {
                source = readHistoryContext(new GuideHistoryContextRequest(historyScope, session.id,
                        spec.budget(), spec.promptAndToolTokens(), spec.canonicalModelId(), spec.estimator()), control.id);
            } catch (RuntimeException failed) {
                finishManualCompaction(control, null, "compact_failed", "Unable to prepare retained context");
                return;
            }
        }
        source.whenComplete((seed, failure) -> dispatcher.execute(() -> {
            if (!manualCompactionCurrent(control)) {
                finishManualCompaction(control, null, "compact_stale", "The selected session or model changed");
                return;
            }
            if (failure != null || seed == null || !session.id.equals(seed.sessionId())) {
                finishManualCompaction(control, null, "compact_failed", "Unable to prepare retained context");
                return;
            }
            control.original = seed.messages();
            CompletableFuture<ToolResult<GuidePreparedCompaction>> preparing;
            try {
                control.preparing = true;
                preparing = local.prepareCompaction(selection.profileId(), actor, session.id, control.id,
                        seed.messages(), control.cancellation, manualImages(seed.messages()),
                        event -> dispatcher.execute(() -> observeManualCompactionUsage(control, event)));
            } catch (RuntimeException failed) {
                control.preparing = false;
                finishManualCompaction(control, null, "compact_failed", "Unable to prepare a conversation summary");
                return;
            }
            preparing.whenComplete((prepared, error) -> dispatcher.execute(() -> {
                control.preparing = false;
                if (error != null || prepared == null) {
                    finishManualCompaction(control, null, control.cancellation.isCancelled()
                            ? "compact_cancelled" : "compact_failed", "Manual compaction did not complete");
                } else {
final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.GuidePreparedCompaction> value; ToolResult.Failure<GuidePreparedCompaction> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = prepared) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<GuidePreparedCompaction>) $oaPattern0_holder.value) != null))) {
                    finishManualCompaction(control, null, $oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
                } else {
                    GuidePreparedCompaction value = ((ToolResult.Success<GuidePreparedCompaction>) prepared).value();
                    if (control.result.isDone()) {
                        value.close();
                        if (control.session.manualCompaction == control) control.session.manualCompaction = null;
                        control.session.usage.finishControl(control.id);
                        control.settled.complete(null);
                        if (!disconnected && sessions.get(control.session.id) == control.session) {
                            publish();
                            drainPending(control.session);
                        }
                    } else saveManualCompaction(control, value);
                }
}
            }));
        }));
    }

    private dev.openallay.model.image.ImagePayloadResolver manualImages(
            List<dev.openallay.model.ModelMessage> source) {
        // The endpoint may have a newer retained projection than the GUI's context copy.
        // Store authorization binds reads to this actor; callers cannot provide paths or URLs.
        if (attachmentStore == null) return dev.openallay.model.image.ImagePayloadResolver.unavailable();
        return reference -> attachmentStore.read(actor, reference);
    }

    private void observeManualCompactionUsage(ManualCompaction control, AgentEvent event) {
        // Real summary calls are billable even when their prepared projection is later cancelled.
        if (!disconnected && sessions.get(control.session.id) == control.session
                && (event instanceof AgentEvent.ModelUsageStarted || event instanceof AgentEvent.ModelUsageObserved)) {
            if (control.session.usage.accept(control.id, event)) publish();
        }
    }

    private boolean manualCompactionCurrent(ManualCompaction control) {
        if (disconnected || sessions.get(control.session.id) != control.session
                || control.session.manualCompaction != control || control.cancellation.isCancelled()
                || !selectedSession.equals(control.session.id)
                || !control.selection.equals(control.session.modelSelection)
                || compactSelectionEpoch != control.selectionEpoch
                || control.session.contextGeneration != control.contextGeneration
                || control.session.nextRequestSequence != control.historySequence
                || control.session.modelContext != control.initialContext) return false;
        try { return local.compactIdentity(control.selection.profileId()) == control.endpoint; }
        catch (RuntimeException unavailable) { return false; }
    }

    private void saveManualCompaction(ManualCompaction control, GuidePreparedCompaction prepared) {
        control.prepared = prepared;
        control.original = prepared.source();
        if (!manualCompactionCurrent(control) || !prepared.current()) {
            finishManualCompaction(control, null, "compact_stale", "The selected session or model changed");
            return;
        }
        if (prepared.outcome().status() == GuideCompactResult.Status.NOT_NEEDED) {
            finishManualCompaction(control, prepared.outcome(), null, null);
            return;
        }
        if (history == null) {
            publishManualCompaction(control);
            return;
        }
        scheduleHistorySave();
        drainHistoryWrites().whenComplete((ignored, failure) -> dispatcher.execute(() -> {
            if (failure != null || !manualCompactionCurrent(control) || !prepared.current()) {
                finishManualCompaction(control, null, failure == null ? "compact_stale" : "compact_failed",
                        failure == null ? "The selected session or model changed" : "Unable to save the prepared summary");
                return;
            }
            GuideHistoryCommit commit = new GuideHistoryCommit(historyScope, dev.openallay.util.Java8Collections.listOf(new GuideHistoryMutation.ReplaceContext(control.session.id, prepared.projection()), new GuideHistoryMutation.AppendCheckpoint(control.session.id,
                            prepared.outcome().checkpoint())));
            control.saving = true;
            try {
                history.commit(commit).whenComplete((saved, error) -> dispatcher.execute(() -> {
                    control.saving = false;
                    if (error != null) {
                        finishManualCompaction(control, null, "compact_failed", "Unable to save the prepared summary");
                    } else if (!manualCompactionCurrent(control) || !prepared.current()) {
                        // A detached write must not turn into the next request's context.
                        restoreManualCompaction(control);
                    } else {
                        durableProjection.acknowledge(commit.mutations());
                        capturedProjection.acknowledge(commit.mutations());
                        publishManualCompaction(control);
                    }
                }));
            } catch (RuntimeException error) {
                control.saving = false;
                finishManualCompaction(control, null, "compact_failed", "Unable to save the prepared summary");
            }
        }));
    }

    private void restoreManualCompaction(ManualCompaction control) {
        if ((!control.detachedOnDisconnect && (disconnected || sessions.get(control.session.id) != control.session))
                || control.session.contextGeneration != control.contextGeneration
                || control.session.nextRequestSequence != control.historySequence
                || control.session.modelContext != control.initialContext) {
            // A clear/delete/new context owns its later durable mutation. Disconnect does not.
            finishManualCompaction(control, null, "compact_stale", "The selected session or model changed");
            return;
        }
        ContextCheckpoint preparedCheckpoint = control.prepared.outcome().checkpoint();
        ContextCheckpoint discarded = new ContextCheckpoint(preparedCheckpoint.checkpointId(),
                preparedCheckpoint.sourceFromIndex(), preparedCheckpoint.sourceToIndexExclusive(),
                preparedCheckpoint.sourceHash(), preparedCheckpoint.modelIdentifier(), preparedCheckpoint.createdAt(),
                ContextCheckpoint.Status.FAILED, null, "compact_stale",
                "The prepared summary was not published because its control snapshot changed",
                control.prepared.outcome().beforeTokens());
        GuideHistoryCommit restore = new GuideHistoryCommit(historyScope, dev.openallay.util.Java8Collections.listOf(new GuideHistoryMutation.ReplaceContext(control.session.id, control.original), new GuideHistoryMutation.AppendCheckpoint(control.session.id, discarded)));
        control.saving = true;
        try {
            history.commit(restore).whenComplete((ignored, failure) -> dispatcher.execute(() -> {
                control.saving = false;
                if (failure == null) {
                    if (!disconnected && sessions.get(control.session.id) == control.session) {
                        durableProjection.acknowledge(restore.mutations());
                        capturedProjection.acknowledge(restore.mutations());
                    }
                } else if (control.detachedOnDisconnect) {
                    control.settled.completeExceptionally(failure);
                } else {
                    retryManualCompactionRestore(control, restore);
                    return;
                }
                finishManualCompaction(control, null, failure == null ? "compact_stale" : "compact_failed",
                        failure == null ? "The selected session or model changed" : "Unable to restore retained context");
            }));
        } catch (RuntimeException failure) {
            control.saving = false;
            if (control.detachedOnDisconnect) {
                control.settled.completeExceptionally(failure);
                finishManualCompaction(control, null, "compact_failed", "Unable to restore retained context");
            } else {
                retryManualCompactionRestore(control, restore);
            }
        }
    }

    private void retryManualCompactionRestore(ManualCompaction control, GuideHistoryCommit restore) {
        control.saving = true;
        pendingHistoryMutations.addAll(restore.mutations());
        scheduleHistorySave();
        drainHistoryWrites().whenComplete((ignored, failure) -> dispatcher.execute(() -> {
            if (failure != null) {
                // An unacknowledged compensation still owns the control lease. Do not allow a
                // successor to read the unpublished summary as if rollback had succeeded.
                control.settled.completeExceptionally(failure);
                control.result.complete(new ToolResult.Failure<>("compact_failed", "Unable to restore retained context"));
                return;
            }
            control.saving = false;
            finishManualCompaction(control, null, "compact_stale", "The selected session or model changed");
        }));
    }

    private void publishManualCompaction(ManualCompaction control) {
        if (!manualCompactionCurrent(control) || !control.prepared.current() || !control.prepared.publish()) {
            if (history != null) restoreManualCompaction(control);
            else finishManualCompaction(control, null, "compact_stale", "The selected session or model changed");
            return;
        }
        control.session.modelContext = control.prepared.projection();
        control.session.contextGeneration++;
        control.session.checkpoints.add(control.prepared.outcome().checkpoint());
        finishManualCompaction(control, control.prepared.outcome(), null, null);
    }

    private void finishManualCompaction(ManualCompaction control, GuideCompactResult outcome,
            String code, String message) {
        if (control.saving) return;
        if (control.prepared != null) control.prepared.close();
        cancelHistoryContextBarrier(control.id);
        if (!control.preparing && control.session.manualCompaction == control) {
            control.session.manualCompaction = null;
        }
        if (!control.preparing) {
            control.session.usage.finishControl(control.id);
            control.settled.complete(null);
        }
        if (outcome != null) control.result.complete(new ToolResult.Success<>(outcome));
        else {
            String publicCode = dev.openallay.util.Java8Collections.setOf("compact_busy", "compact_unavailable", "compact_cancelled", "compact_stale", "compact_failed").contains(code) ? code : "compact_failed";
            control.result.complete(new ToolResult.Failure<>(publicCode, message));
        }
        if (!disconnected && sessions.get(control.session.id) == control.session) {
            publish();
            drainPending(control.session);
        }
    }

    private boolean cancelManualCompaction(SessionState session) {
        ManualCompaction control = session.manualCompaction;
        if (control == null) return false;
        control.cancellation.cancel(java.util.concurrent.ForkJoinPool.commonPool());
        if (!control.saving) finishManualCompaction(control, null, "compact_cancelled", "Manual compaction was cancelled");
        return true;
    }

    private void fenceManualCompactionSelection() {
        SessionState selected = sessions.get(selectedSession);
        GuideModelSelection selection = selected == null ? null : selected.modelSelection;
        if (!Objects.equals(compactSelectedSession, selectedSession)
                || !Objects.equals(compactSelectedModel, selection)) {
            compactSelectedSession = selectedSession;
            compactSelectedModel = selection;
            compactSelectionEpoch++;
            sessions.values().forEach(session -> {
                ManualCompaction control = session.manualCompaction;
                if (control != null && !manualCompactionCurrent(control)) cancelManualCompaction(session);
            });
        }
    }

    private static final class ManualCompaction {
        private final UUID id;
        private final SessionState session;
        private final GuideModelSelection selection;
        private final long selectionEpoch;
        private final long contextGeneration;
        private final Object endpoint;
        private final long historySequence;
        private final List<dev.openallay.model.ModelMessage> initialContext;
        private final CompletableFuture<ToolResult<GuideCompactResult>> result;
        private final dev.openallay.model.CancellationSignal cancellation = new dev.openallay.model.CancellationSignal();
        private List<dev.openallay.model.ModelMessage> original = dev.openallay.util.Java8Collections.listOf();
        private GuidePreparedCompaction prepared;
        private boolean preparing;
        private boolean saving;
        private boolean detachedOnDisconnect;
        private final CompletableFuture<Void> settled = new CompletableFuture<>();
        private ManualCompaction(UUID id, SessionState session, GuideModelSelection selection,
                long selectionEpoch, long contextGeneration, Object endpoint,
                CompletableFuture<ToolResult<GuideCompactResult>> result) {
            this.id = id;
            this.session = session;
            this.selection = selection;
            this.selectionEpoch = selectionEpoch;
            this.contextGeneration = contextGeneration;
            this.endpoint = endpoint;
            this.historySequence = session.nextRequestSequence;
            this.initialContext = session.modelContext;
            this.result = result;
        }
    }

    public CompletableFuture<ToolResult<UUID>> ask(String question) {
        return ask(question == null ? null : dev.openallay.model.ModelMessage.userText(question));
    }

    public CompletableFuture<ToolResult<UUID>> ask(dev.openallay.model.ModelMessage message) {
        String sessionId = snapshot.selectedSession();
        SessionState captured = publishedSessions.get(sessionId);
        CompletableFuture<ToolResult<UUID>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (captured == null || sessions.get(sessionId) != captured) {
                result.complete(new ToolResult.Failure<>("invalid_session", "Guide session was closed")); return;
            }
            if (rejectStateChange(result)) return;
            ToolResult<Boolean> valid = validateUserInput(message, captured.modelSelection);
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = valid) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern1_holder.value) != null))) {
                result.complete(new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message())); return;
            }
            UUID receipt = UUID.randomUUID();
            admitInput(captured, receipt, message, result,
                    () -> submit(sessionId, message, receipt, result));
        });
        return result;
    }

    public CompletableFuture<ToolResult<UUID>> followUp(String text) {
        return followUp(text == null ? null : dev.openallay.model.ModelMessage.userText(text));
    }

    public CompletableFuture<ToolResult<UUID>> followUp(dev.openallay.model.ModelMessage message) {
        return enqueue(message, false);
    }

    /** Actual request identity or accepted FIFO pending identity; neither means task completion. */
    @dev.openallay.value.ValueType(InputReceipt.ValueSchemaProvider.class)
public static final class InputReceipt {
    private final UUID id;
    private final boolean queued;
    public InputReceipt(UUID id, boolean queued) {
 Objects.requireNonNull(id, "id");
        this.id = id;
        this.queued = queued;
    }
    public UUID id() { return id; }
    public boolean queued() { return queued; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InputReceipt)) return false;
        InputReceipt that = (InputReceipt) other;
        return java.util.Objects.equals(id, that.id) && queued == that.queued;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Boolean.hashCode(queued);
        return hash;
    }
    @Override public String toString() { return "InputReceipt[id=" + id + ", queued=" + queued + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<InputReceipt> schema() {
            return new dev.openallay.value.ValueSchema<>(InputReceipt.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<InputReceipt>>asList(new dev.openallay.value.ValueSchema.Component<>(InputReceipt.class, "id", InputReceipt::id), new dev.openallay.value.ValueSchema.Component<>(InputReceipt.class, "queued", InputReceipt::queued)), arguments -> new InputReceipt((UUID) arguments[0], (Boolean) arguments[1]));
        }
    }
}

    /** Explicit captured owner. Idle sends and busy FIFO follow-ups are chosen at final admission. */
    public CompletableFuture<ToolResult<InputReceipt>> followUp(String sessionId, UUID sessionOwner, String text,
            java.util.function.BooleanSupplier admissionFence) {
        return followUp(sessionId, sessionOwner, text == null ? null
                : dev.openallay.model.ModelMessage.userText(text), admissionFence);
    }

    /** Voice and other captured input carry their original reference alongside the same admission. */
    public CompletableFuture<ToolResult<InputReceipt>> followUp(String sessionId, UUID sessionOwner,
            dev.openallay.model.ModelMessage message, java.util.function.BooleanSupplier admissionFence) {
        Objects.requireNonNull(admissionFence, "admissionFence");
        SessionState captured = publishedSessions.get(sessionId);
        CompletableFuture<ToolResult<InputReceipt>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (captured == null || sessions.get(sessionId) != captured
                    || !captured.presentationOwner.equals(sessionOwner)) {
                result.complete(new ToolResult.Failure<>("invalid_session", "Guide session was closed")); return;
            }
            if (closing || disconnected || !admissionFence.getAsBoolean()) {
                result.complete(new ToolResult.Failure<>("message_cancelled", "Message was cancelled")); return;
            }
            if (rejectStateChange(result)) return;
            ToolResult<Boolean> valid = validateUserInput(message, captured.modelSelection);
            final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = valid) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern2_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern2_holder.value) != null))) {
                result.complete(new ToolResult.Failure<>($oaPattern2_holder.bound.code(), $oaPattern2_holder.bound.message())); return;
            }
            UUID receipt = UUID.randomUUID();
            captured.pendingOrder.put(receipt, captured.nextPendingOrder++);
            admitInput(captured, receipt, message, result, () -> {
                boolean busy = captured.workingRequest != null || active(captured) != null
                        || captured.manualCompaction != null || !captured.pending.isEmpty();
                if (!busy) {
                    captured.pendingOrder.remove(receipt);
                    CompletableFuture<ToolResult<UUID>> submitted = new CompletableFuture<>();
                    submit(sessionId, message, receipt, submitted);
                    submitted.thenAccept(admitted -> {
                        final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<java.util.UUID> value; ToolResult.Success<UUID> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = admitted) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern3_holder.bound = (ToolResult.Success<UUID>) $oaPattern3_holder.value) != null))) {
                            result.complete(new ToolResult.Success<>(new InputReceipt($oaPattern3_holder.bound.value(), false)));
                        } else {
                            ToolResult.Failure<UUID> failure = (ToolResult.Failure<UUID>) admitted;
                            result.complete(new ToolResult.Failure<>(failure.code(), failure.message()));
                        }
                    });
                    return;
                }
                GuidePendingMessage pending = new GuidePendingMessage(receipt, GuidePendingMessage.Kind.FOLLOW_UP,
                        message, clock.instant(), null);
                putPendingOrdered(captured, pending);
                captured.pendingReceipts.put(receipt, receipt);
                publishWithoutSave();
                result.complete(new ToolResult.Success<>(new InputReceipt(receipt, true)));
                drainPending(captured);
            }, () -> captured.presentationOwner.equals(sessionOwner) && admissionFence.getAsBoolean());
        });
        return result;
    }

    public CompletableFuture<ToolResult<UUID>> steer(String text) {
        return steer(text == null ? null : dev.openallay.model.ModelMessage.userText(text));
    }

    public CompletableFuture<ToolResult<UUID>> steer(dev.openallay.model.ModelMessage message) {
        return enqueue(message, true);
    }

    private CompletableFuture<ToolResult<UUID>> enqueue(
            dev.openallay.model.ModelMessage message, boolean steer) {
        String sessionId = snapshot.selectedSession();
        SessionState captured = publishedSessions.get(sessionId);
        CompletableFuture<ToolResult<UUID>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) return;
            SessionState session = sessions.get(sessionId);
            if (session == null || session != captured) {
                result.complete(new ToolResult.Failure<>("invalid_session", "Guide session does not exist"));
                return;
            }
            GuideRequestSnapshot running = session.workingRequest == null ? null : find(session.workingRequest);
            GuideModelSelection selection = steer && running != null && !running.terminal()
                    ? running.modelSelection() : session.modelSelection;
            ToolResult<Boolean> valid = steer && running != null && !running.terminal()
                    ? validateCapturedUserInput(message, running.requestId())
                    : validateUserInput(message, selection);
            final class $oaPattern4_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = valid) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern4_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern4_holder.value) != null))) {
                result.complete(new ToolResult.Failure<>($oaPattern4_holder.bound.code(), $oaPattern4_holder.bound.message()));
                return;
            }
            UUID id = UUID.randomUUID();
            session.pendingOrder.put(id, session.nextPendingOrder++);
            boolean canSteer = steer && running != null && !running.terminal();
            GuidePendingMessage pending = new GuidePendingMessage(id,
                    canSteer ? GuidePendingMessage.Kind.STEER : GuidePendingMessage.Kind.FOLLOW_UP,
                    message, clock.instant(), canSteer ? running.requestId() : null);
            admitInput(session, id, message, result, () -> {
                GuidePendingMessage accepted = pending;
                GuideRequestSnapshot current = pending.requestId() == null ? null : find(pending.requestId());
                if (current == null || current.terminal()
                        || !pending.requestId().equals(session.workingRequest)) accepted = pending.followUp();
                putPendingOrdered(session, accepted);
                session.pendingReceipts.put(id, id);
                if (accepted.kind() == GuidePendingMessage.Kind.STEER) {
                    retainUserInput(accepted.requestId(), accepted.message());
                }
                publishWithoutSave();
                if (accepted.kind() == GuidePendingMessage.Kind.STEER) sendSteer(session, accepted);
                else drainPending(session);
                result.complete(new ToolResult.Success<>(id));
            });
        });
        return result;
    }

    public List<GuidePendingMessage> pendingMessages(String sessionId) {
        return snapshot.sessions().stream().filter(session -> session.sessionId().equals(sessionId))
                .map(GuideSessionSnapshot::pendingMessages).findFirst().orElse(dev.openallay.util.Java8Collections.listOf());
    }

    public CompletableFuture<ToolResult<Boolean>> editPending(UUID id, String text) {
        return editPending(id, text == null ? null : dev.openallay.model.ModelMessage.userText(text));
    }

    public CompletableFuture<ToolResult<Boolean>> editPending(
            UUID id, dev.openallay.model.ModelMessage message) {
        String sessionId = snapshot.selectedSession();
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) return;
            SessionState session = sessions.get(sessionId);
            GuidePendingMessage pending = session == null ? null : session.pending.get(id);
            if (pending == null) { result.complete(new ToolResult.Success<>(false)); return; }
            GuideRequestSnapshot request = pending.requestId() == null ? null : find(pending.requestId());
            ToolResult<Boolean> valid = pending.kind() == GuidePendingMessage.Kind.STEER && request != null
                    ? validateCapturedUserInput(message, request.requestId())
                    : validateUserInput(message, session.modelSelection);
            final class $oaPattern5_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = valid) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern5_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern5_holder.value) != null))) {
                result.complete(new ToolResult.Failure<>($oaPattern5_holder.bound.code(), $oaPattern5_holder.bound.message())); return;
            }
            // A new receipt retains an edited image until the original receipt can be replaced.
            UUID transfer = UUID.randomUUID();
            admitInput(session, transfer, message, result, () -> {
                GuidePendingMessage current = session.pending.get(id);
                if (current != pending) {
                    releaseImageInput(transfer);
                    result.complete(new ToolResult.Success<>(false)); return;
                }
                GuidePendingMessage replacement = pending.withMessage(message);
                UUID previousReceipt = session.pendingReceipts.put(id, transfer);
                if (previousReceipt != null) releaseImageInput(previousReceipt);
                session.pending.put(id, replacement);
                if (replacement.kind() == GuidePendingMessage.Kind.STEER) {
                    retainUserInput(replacement.requestId(), message);
                }
                publishWithoutSave();
                if (replacement.kind() == GuidePendingMessage.Kind.STEER) sendSteer(session, replacement);
                else drainPending(session);
                result.complete(new ToolResult.Success<>(true));
            });
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> cancelPending(UUID id) {
        String sessionId = snapshot.selectedSession();
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) return;
            SessionState session = sessions.get(sessionId);
            GuidePendingMessage pending = session == null ? null : session.pending.remove(id);
            if (pending != null && pending.kind() == GuidePendingMessage.Kind.STEER) {
                GuideRequestSnapshot request = find(pending.requestId());
                if (request != null && request.topology() == GuideTopology.SERVER) {
                    remote.cancelSteer(pending.requestId(), id);
                } else if (local != null) {
                    local.cancelSteer(actor, sessionId, pending.requestId(), id);
                }
            }
            if (pending != null) releasePendingReceipt(session, pending.id());
            publishWithoutSave();
            result.complete(new ToolResult.Success<>(pending != null));
        });
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> cancel() {
        String sessionId = snapshot.selectedSession();
        SessionState owner = publishedSessions.get(sessionId);
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            SessionState session = sessions.get(sessionId);
            if (session == null || session != owner) {
                result.complete(new ToolResult.Success<>(false)); return;
            }
            boolean hadPending = !session.pending.isEmpty() || !session.admissions.isEmpty();
            revokePending(session);
            if (cancelManualCompaction(session)) {
                publishWithoutSave();
                result.complete(new ToolResult.Success<>(true)); return;
            }
            GuideRequestSnapshot active = active(session);
            if (active == null) {
                publishWithoutSave();
                result.complete(new ToolResult.Success<>(hadPending || session.workingRequest != null));
                return;
            }
            boolean preparingContext = session.preparingContextRequest != null
                    && session.preparingContextRequest.equals(active.requestId());
            if (preparingContext) session.unresolvedForkContext.add(active.requestId());
            dev.openallay.model.ModelMessage note = new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                    dev.openallay.util.Java8Collections.listOf(new dev.openallay.model.ModelContent.Text(
                            "[OpenAllay request ended: agent_cancelled] Agent request was cancelled")));
            List<dev.openallay.model.ModelMessage> current = new ArrayList<>(session.modelContext);
            List<dev.openallay.model.ModelMessage> original = new ArrayList<>(
                    session.originalContext.getOrDefault(active.requestId(), dev.openallay.util.Java8Collections.listOf()));
            if (original.isEmpty()) {
                original.add(userInput(active.requestId()));
                current.add(userInput(active.requestId()));
            }
            current.add(note);
            original.add(note);
            if (!preparingContext) {
                pendingCancelledFinalization.put(active.requestId(), new CancelledFinalization(
                        session.id, Objects.requireNonNull(session.requestSequences.get(active.requestId())),
                        dev.openallay.util.Java8Collections.listCopyOf(session.checkpoints)));
            }
            if (preparingContext) {
                // Durable predecessor context is still being loaded. Do not replace it with
                // the current question and cancellation note, or archive that as a fork boundary.
                session.originalContext.put(active.requestId(), dev.openallay.util.Java8Collections.listCopyOf(original));
                if (incrementalHistory) {
                    pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceRequestContext(
                            active.requestId(), original));
                }
            } else {
                apply(active.requestId(), new AgentEvent.ContextUpdated(current, original));
            }
            apply(active.requestId(), new AgentEvent.Failed(
                    "agent_cancelled", "Agent request was cancelled"));
            if (!preparingContext) {
                GuideRequestSnapshot cancelled = find(active.requestId());
                session.usageCarriers.put(active.requestId(), new UsageCarrier(
                        Objects.requireNonNull(session.requestSequences.get(active.requestId())), cancelled.usageProjection()));
            }
            if (preparingContext) {
                cancelHistoryContextBarrier(active.requestId());
                session.contextGeneration++;
                session.preparingContextRequest = null;
                releaseRequest(session, active.requestId());
            } else if (active.topology() == GuideTopology.SERVER) {
                remote.cancel(active.requestId());
            } else if (local != null) {
                local.cancel(actor, active.sessionId(), active.requestId());
            }
            result.complete(new ToolResult.Success<>(true));
        });
        return result;
    }

    public CompletableFuture<ToolResult<UUID>> retry(UUID requestId) {
        CompletableFuture<ToolResult<UUID>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            GuideRequestSnapshot request = find(requestId);
            if (request == null || !request.terminal()
                    || (request.status() != GuideRequestStatus.FAILED
                            && request.status() != GuideRequestStatus.CANCELLED
                            && request.status() != GuideRequestStatus.INTERRUPTED)) {
                result.complete(new ToolResult.Failure<>(
                        "retry_unavailable", "Only a failed or cancelled request can be retried"));
                return;
            }
            SessionState session = sessions.get(request.sessionId());
            dev.openallay.model.ModelMessage input = submittedInputs.get(request.requestId());
            if (input == null) input = (session == null ? dev.openallay.util.Java8Collections.<dev.openallay.model.ModelMessage>listOf()
                    : session.originalContext.getOrDefault(request.requestId(), dev.openallay.util.Java8Collections.listOf()))
                    .stream().filter(message -> message.role() == dev.openallay.model.ModelRole.USER)
                    .findFirst().orElse(dev.openallay.model.ModelMessage.userText(request.userMessage()));
            submit(request.sessionId(), input, result);
        });
        return result;
    }

    public CompletableFuture<ToolResult<String>> selectSession(String sessionId) {
        CompletableFuture<ToolResult<String>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            if (!validSession(sessionId)) {
                result.complete(new ToolResult.Failure<>(
                        "invalid_session", "Session ID may contain letters, numbers, dot, underscore, and dash"));
                return;
            }
            if (pendingForks.containsKey(sessionId)) {
                result.complete(new ToolResult.Failure<>("fork_pending", "This session is being created by a fork"));
                return;
            }
            sessions.computeIfAbsent(sessionId,
                    id -> new SessionState(id, defaultClientSelection()));
            selectedSession = sessionId;
            sessionSelectionGeneration++;
            publish();
            result.complete(new ToolResult.Success<>(sessionId));
        });
        return result;
    }

    /** Forks only after a terminal request. Tools and runtime workspaces are never resumed or cloned. */
    public CompletableFuture<ToolResult<String>> forkSession(
            String sourceSessionId, UUID completedRequestId, String newSessionId) {
        CompletableFuture<ToolResult<String>> result = new CompletableFuture<>();
        dispatcher.execute(() -> startFork(sourceSessionId, completedRequestId, newSessionId, result));
        return result;
    }

    public CompletableFuture<ToolResult<String>> forkSession(String sourceSessionId, UUID completedRequestId) {
        return forkSession(sourceSessionId, completedRequestId, "fork-" + UUID.randomUUID());
    }

    /** Uses the latest terminal request, never the newest row of an older GUI page. */
    public CompletableFuture<ToolResult<String>> forkSelectedSession() {
        CompletableFuture<ToolResult<String>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) return;
            SessionState source = sessions.get(selectedSession);
            if (source == null || history == null) {
                forkLatestLoaded(source, result);
                return;
            }
            long epoch = source.forkEpoch;
            long selectionGeneration = sessionSelectionGeneration;
            scheduleHistorySave();
            drainHistoryWrites().thenCompose(ignored -> history.page(new GuideHistoryPageRequest(
                    historyScope, source.id, GuideHistoryPageRequest.Direction.NEWEST, null, 2)))
                    .whenComplete((page, failure) -> dispatcher.execute(() -> {
                        if (failure != null) {
                            GuideFailure known = historyFailure(failure, "history_fork_failed");
                            result.complete(new ToolResult.Failure<>(known.code(), known.message()));
                            return;
                        }
                        if (disconnected || sessions.get(source.id) != source || source.forkEpoch != epoch
                                || !selectedSession.equals(source.id)
                                || sessionSelectionGeneration != selectionGeneration) {
                            result.complete(new ToolResult.Failure<>("fork_source_changed", "The selected session changed"));
                            return;
                        }
                        mergePage(source, GuideHistoryPageRequest.Direction.NEWEST, 2, page);
                        forkLatestLoaded(source, result);
                    }));
        });
        return result;
    }

    private void forkLatestLoaded(SessionState source, CompletableFuture<ToolResult<String>> result) {
        GuideRequestSnapshot last = source == null ? null : source.requests.stream()
                .filter(GuideRequestSnapshot::terminal).reduce((first, second) -> second).orElse(null);
        if (last == null) {
            result.complete(new ToolResult.Failure<>(
                    "fork_boundary_unavailable", "Select a completed request to fork from"));
            return;
        }
        startFork(source.id, last.requestId(), "fork-" + UUID.randomUUID(), result);
    }

    private void startFork(String sourceSessionId, UUID requestId, String targetSessionId,
            CompletableFuture<ToolResult<String>> result) {
        if (rejectStateChange(result)) return;
        if (disconnected || !validSession(targetSessionId)) {
            result.complete(new ToolResult.Failure<>("fork_unavailable", "The fork target is unavailable"));
            return;
        }
        SessionState source = sessions.get(sourceSessionId);
        GuideRequestSnapshot request = source == null ? null : source.requests.stream()
                .filter(value -> value.requestId().equals(requestId)).findFirst().orElse(null);
        GuideHistoryCursor cutoff = request == null ? null : cursor(source, request);
        if (request == null || !request.terminal() || cutoff == null
                || pendingCancelledFinalization.containsKey(requestId)
                || requestId.equals(source.workingRequest)
                || source.manualCompaction != null) {
            result.complete(new ToolResult.Failure<>(
                    "fork_boundary_unavailable", "Fork requires a finalized completed request boundary"));
            return;
        }
        if (sessions.containsKey(targetSessionId) || pendingForks.containsKey(targetSessionId)) {
            result.complete(new ToolResult.Failure<>("fork_target_exists", "The fork target session already exists"));
            return;
        }
        UUID nonce = UUID.randomUUID();
        pendingForks.put(targetSessionId, nonce);
        long sourceEpoch = source.forkEpoch;
        String capturedSelection = selectedSession;
        long capturedSelectionGeneration = sessionSelectionGeneration;
        GuideHistoryMutation.ForkSession mutation = new GuideHistoryMutation.ForkSession(
                source.id, cutoff, targetSessionId, sessions.size(), source.modelSelection);
        if (history == null) {
            try {
                GuideHistoryForkResult fork = forkMemory(source, mutation);
                Map<UUID, List<dev.openallay.model.ModelMessage>> originals = new LinkedHashMap<>();
                Map<UUID, GuideHistoryMutation.CaptureRequestBoundary> boundaries = new LinkedHashMap<>();
                for (GuideRequestSnapshot inherited : fork.page().requests()) {
                    long sequence = fork.page().first().sequence() + originals.size();
                    UUID sourceId = source.requestSequences.entrySet().stream()
                            .filter(entry -> entry.getValue() == sequence).map(Map.Entry::getKey)
                            .findFirst().orElseThrow();
                    originals.put(inherited.requestId(), source.originalContext.getOrDefault(sourceId, dev.openallay.util.Java8Collections.listOf()));
                    GuideHistoryMutation.CaptureRequestBoundary boundary = source.forkBoundaries.get(sourceId);
                    if (boundary != null) boundaries.put(inherited.requestId(),
                            new GuideHistoryMutation.CaptureRequestBoundary(inherited.requestId(),
                                    boundary.messages(), boundary.checkpoints()));
                }
                retainForkImages(mutation, fork, originals, boundaries).whenComplete((ignored, failure) ->
                        dispatcher.execute(() -> completeFork(source, sourceEpoch, capturedSelection,
                                capturedSelectionGeneration, nonce, mutation, fork, failure, result,
                                originals, boundaries)));
            } catch (RuntimeException failure) {
                completeFork(source, sourceEpoch, capturedSelection, capturedSelectionGeneration, nonce, mutation, null, failure, result);
            }
            return;
        }
        if (!allowHistoryWrites) {
            pendingForks.remove(targetSessionId);
            result.complete(new ToolResult.Failure<>("history_unavailable", "Durable guide history is unavailable"));
            return;
        }
        scheduleHistorySave();
        drainHistoryWrites().whenComplete((ignored, writeFailure) -> dispatcher.execute(() -> {
            if (writeFailure != null || disconnected || sessions.get(source.id) != source
                    || source.forkEpoch != sourceEpoch) {
                completeFork(source, sourceEpoch, capturedSelection, capturedSelectionGeneration, nonce, mutation,
                        null, writeFailure == null ? new GuideHistoryException(
                                "fork_source_changed", "The source session changed before the fork") : writeFailure, result);
                return;
            }
            CompletableFuture<GuideHistoryForkResult> operation;
            try {
                operation = history.fork(new GuideHistoryForkRequest(historyScope, mutation));
            } catch (RuntimeException failure) {
                completeFork(source, sourceEpoch, capturedSelection, capturedSelectionGeneration, nonce, mutation, null, failure, result);
                return;
            }
            operation.whenComplete((fork, failure) -> dispatcher.execute(() -> completeFork(
                    source, sourceEpoch, capturedSelection, capturedSelectionGeneration, nonce, mutation, fork, failure, result)));
        }));
    }

    /** Memory-only forks must retain every original image, not only the compacted tail. */
    private CompletableFuture<Void> retainForkImages(GuideHistoryMutation.ForkSession mutation,
            GuideHistoryForkResult fork, Map<UUID, List<dev.openallay.model.ModelMessage>> requestOriginals,
            Map<UUID, GuideHistoryMutation.CaptureRequestBoundary> requestBoundaries) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(null);
        List<dev.openallay.model.ModelMessage> originals = new ArrayList<>(fork.messages());
        requestOriginals.values().forEach(originals::addAll);
        requestBoundaries.values().forEach(boundary -> originals.addAll(boundary.messages()));
        List<dev.openallay.model.image.ImageReference> references = imageReferences(originals);
        String imageOwner = UUID.randomUUID().toString();
        pendingForkImageOwners.put(mutation.sessionId(), imageOwner);
        String owner = imageOwnerPrefix + "session:" + mutation.sessionId() + ":" + imageOwner;
        retainedImages.put(owner, references);
        return CompletableFuture.runAsync(() -> {
            try { attachmentStore.retain(actor, owner, references); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, IMAGE_IO);
    }

    private void completeFork(SessionState source, long sourceEpoch, String capturedSelection,
            long capturedSelectionGeneration, UUID nonce,
            GuideHistoryMutation.ForkSession mutation, GuideHistoryForkResult fork, Throwable failure,
            CompletableFuture<ToolResult<String>> result) {
        completeFork(source, sourceEpoch, capturedSelection, capturedSelectionGeneration, nonce,
                mutation, fork, failure, result, dev.openallay.util.Java8Collections.mapOf(), dev.openallay.util.Java8Collections.mapOf());
    }

    private void completeFork(SessionState source, long sourceEpoch, String capturedSelection,
            long capturedSelectionGeneration, UUID nonce,
            GuideHistoryMutation.ForkSession mutation, GuideHistoryForkResult fork, Throwable failure,
            CompletableFuture<ToolResult<String>> result,
            Map<UUID, List<dev.openallay.model.ModelMessage>> originals,
            Map<UUID, GuideHistoryMutation.CaptureRequestBoundary> boundaries) {
        if (!nonce.equals(pendingForks.get(mutation.sessionId()))) {
            result.complete(new ToolResult.Failure<>("fork_superseded", "The fork was superseded"));
            return;
        }
        pendingForks.remove(mutation.sessionId());
        if (failure != null || fork == null) {
            GuideFailure known = historyFailure(failure, "history_fork_failed");
            result.complete(new ToolResult.Failure<>(known.code(), known.message()));
            return;
        }
        if (!mutation.sessionId().equals(fork.session().sessionId())
                || !mutation.modelSelection().equals(fork.session().modelSelection())) {
            result.complete(new ToolResult.Failure<>("history_scope_mismatch", "The fork result belongs to another session"));
            return;
        }
        if (disconnected || sessions.containsKey(mutation.sessionId())) {
            // The committed branch remains durable. Never attach an old callback to a new live view.
            result.complete(new ToolResult.Failure<>("fork_source_changed", "The live guide changed during the fork"));
            return;
        }
        boolean sourceUnchanged = sessions.get(source.id) == source && source.forkEpoch == sourceEpoch;
        SessionState target = new SessionState(fork.session().sessionId(), fork.session().modelSelection());
        target.totalRequests = fork.session().requestCount();
        target.usage.restore(fork.session().usage(), fork.session().inheritedUsage(), fork.session().controlUsage());
        target.firstAvailable = fork.session().first();
        target.lastAvailable = fork.session().last();
        target.nextRequestSequence = fork.session().last().sequence() + 1;
        target.messageOrdinalBase = fork.nextMessageOrdinal();
        target.modelContext = fork.messages();
        target.checkpoints.addAll(fork.checkpoints());
        sessions.put(target.id, target);
        mergePage(target, GuideHistoryPageRequest.Direction.NEWEST, 120, fork.page());
        if (history == null) {
            // retainForkImages already pinned this immutable memory-only fork before publication.
            String retainedOwner = pendingForkImageOwners.remove(target.id);
            if (retainedOwner != null) target.imageOwner = retainedOwner;
            target.originalContext.putAll(originals);
            target.forkBoundaries.putAll(boundaries);
        }
        for (DurableProjection projection : dev.openallay.util.Java8Collections.listOf(capturedProjection, durableProjection)) {
            projection.sessions.put(target.id, new SessionProjection(fork.session().ordinal(), target.modelSelection));
            projection.controlUsage.put(target.id, fork.session().controlUsage());
            for (int index = 0; index < target.checkpoints.size(); index++) {
                projection.checkpoints.put(new CheckpointKey(target.id, index), target.checkpoints.get(index));
                projection.checkpointPayloads.put(target.checkpoints.get(index).checkpointId(), target.checkpoints.get(index));
                projection.checkpointSessions.put(target.checkpoints.get(index).checkpointId(), target.id);
            }
        }
        if (local != null && target.modelSelection.kind() == GuideModelSelection.Kind.CLIENT) {
            try {
                local.hydrateContext(actor, target.id, fork.messages(),
                        GuideHistoryForkResult.reusableCheckpoints(fork.checkpoints(), fork.messages()));
            } catch (RuntimeException unavailable) {
                // The persisted snapshot is still available for the normal next-request hydration.
                local.clearSession(actor, target.id);
            }
        }
        if (sourceUnchanged && selectedSession.equals(capturedSelection)
                && sessionSelectionGeneration == capturedSelectionGeneration) {
            selectedSession = target.id;
            sessionSelectionGeneration++;
        }
        publish();
        result.complete(new ToolResult.Success<>(target.id));
    }

    private GuideHistoryForkResult forkMemory(SessionState source, GuideHistoryMutation.ForkSession mutation) {
        GuideHistoryMutation.CaptureRequestBoundary boundary = source.forkBoundaries.get(mutation.cutoff().requestId());
        if (boundary == null || boundary.messages().isEmpty()) throw new GuideHistoryException(
                "fork_context_unavailable", "The completed request has no safe model context snapshot");
        List<GuideRequestSnapshot> inherited = new ArrayList<>();
        List<GuideHistoryCursor> cursors = new ArrayList<>();
        for (GuideRequestSnapshot original : source.requests) {
            long sequence = source.requestSequences.get(original.requestId());
            if (sequence > mutation.cutoff().sequence()) continue;
            if (!original.terminal()) throw new GuideHistoryException(
                    "fork_boundary_unavailable", "Fork requires a completed request prefix");
            if (source.originalContext.getOrDefault(original.requestId(), dev.openallay.util.Java8Collections.listOf()).isEmpty()) {
                throw new GuideHistoryException("fork_context_unavailable",
                        "An inherited request has no original model transcript");
            }
            UUID id = UUID.randomUUID();
            GuideRequestSnapshot safe = durableRequest(original);
            inherited.add(new GuideRequestSnapshot(id, mutation.sessionId(), safe.topology(), safe.userMessage(),
                    safe.timeline(), safe.status(), safe.sources(), safe.usage(), safe.retryAfterMillis(),
                    safe.failure(), safe.createdAt(), safe.updatedAt(), safe.terminalAt(), safe.modelSelection(),
                    safe.progress(), safe.usageProjection(), safe.usageOriginRequestId() == null
                            ? safe.requestId() : safe.usageOriginRequestId()));
            cursors.add(new GuideHistoryCursor(sequence, id));
        }
        int inheritedMessageCount = (int) source.messages.stream().filter(message ->
                source.requestSequences.getOrDefault(message.requestId(), Long.MAX_VALUE)
                        <= mutation.cutoff().sequence()).count();
        return new GuideHistoryForkResult(new GuideHistoryMetadata.Session(mutation.sessionId(), mutation.ordinal(),
                mutation.modelSelection(), inherited.size(), cursors.get(0), cursors.get(cursors.size() - 1),
                GuideUsageSnapshot.empty(), inherited.stream().map(GuideRequestSnapshot::usageProjection)
                        .reduce(GuideUsageSnapshot.empty(), GuideUsageSnapshot::plus),
                GuideUsageSnapshot.empty(), inheritedMessageCount),
                new GuideHistoryPage(mutation.sessionId(), inherited, cursors.get(0), cursors.get(cursors.size() - 1), false, false),
                boundary.messages(), dev.openallay.util.Java8Collections.toList(boundary.checkpoints().stream().map(checkpoint ->
                        new ContextCheckpoint(UUID.randomUUID(), checkpoint.sourceFromIndex(),
                                checkpoint.sourceToIndexExclusive(), checkpoint.sourceHash(), checkpoint.modelIdentifier(),
                                checkpoint.createdAt(), checkpoint.status(), checkpoint.summary(), checkpoint.failureCode(),
                                checkpoint.failureMessage(), checkpoint.estimatedProjectionTokens()))),
                inheritedMessageCount);
    }

    private void captureForkBoundary(SessionState session, UUID requestId) {
        captureForkBoundary(session, requestId, session.modelContext, session.checkpoints);
    }

    private void captureForkBoundary(SessionState session, UUID requestId,
            List<dev.openallay.model.ModelMessage> messages, List<ContextCheckpoint> checkpoints) {
        if (session.unresolvedForkContext.contains(requestId)
                || pendingCancelledFinalization.containsKey(requestId)
                || !session.originalContext.containsKey(requestId)
                || session.originalContext.get(requestId).isEmpty() || messages.isEmpty()) return;
        GuideHistoryMutation.CaptureRequestBoundary boundary = new GuideHistoryMutation.CaptureRequestBoundary(
                requestId, messages, checkpoints);
        if (history == null) session.forkBoundaries.put(requestId, boundary);
        if (incrementalHistory) pendingHistoryMutations.add(boundary);
    }

    public CompletableFuture<ToolResult<Boolean>> closeSession(String sessionId) {
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            SessionState session = sessions.get(sessionId);
            if (session == null) {
                result.complete(new ToolResult.Success<>(false));
                return;
            }
            revokePending(session);
            cancelManualCompaction(session);
            GuideRequestSnapshot active = active(session);
            if (active != null) {
                if (active.topology() == GuideTopology.SERVER) {
                    remote.cancel(active.requestId());
                } else if (local != null) {
                    local.cancel(actor, sessionId, active.requestId());
                }
            }
            if (local != null) {
                local.clearSession(actor, sessionId);
            }
            invalidatePageLoad(session, "history_page_cancelled", "History page request was cancelled");
            session.requests.forEach(request -> stopObservations(request.requestId()));
            retainPublishedImages(session);
            session.requests.forEach(request -> releaseObservationImages(session, request.requestId()));
            session.requests.forEach(request -> cancelHistoryContextBarrier(request.requestId()));
            session.requests.forEach(request -> requestSessions.remove(request.requestId()));
            session.requestReceipts.values().forEach(this::releaseImageInput);
            session.requestReceipts.clear();
            session.requests.forEach(request -> requestImageCapabilities.remove(request.requestId()));
            session.usageCarriers.keySet().forEach(requestSessions::remove);
            session.usageCarriers.clear();
            capturedProjection.removeSession(sessionId);
            presentation.discardSession(session.presentationOwner);
            pendingHistoryMutations.add(new GuideHistoryMutation.DeleteSession(sessionId));
            pendingCancelledFinalization.entrySet().removeIf(
                    entry -> entry.getValue().sessionId().equals(sessionId));
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                sessions.put("main", new SessionState("main", defaultClientSelection()));
            }
            if (selectedSession.equals(sessionId)) {
                selectedSession = sessions.containsKey("main")
                        ? "main"
                        : sessions.keySet().iterator().next();
            }
            publish();
            releaseSessionImageReferences(session);
            result.complete(new ToolResult.Success<>(true));
        });
        return result;
    }

    /** Captures the selected session without exposing a history path or mutating its viewport. */
    public CompletableFuture<ToolResult<GuideSessionExportSnapshot>>
            captureSelectedSessionForExport() {
        CompletableFuture<ToolResult<GuideSessionExportSnapshot>> result =
                new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (disconnected) {
                result.complete(new ToolResult.Failure<>(
                        "history_export_unavailable", "Guide session export is unavailable"));
                return;
            }
            SessionState session = sessions.get(selectedSession);
            if (session == null) {
                result.complete(new ToolResult.Failure<>(
                        "invalid_session", "Guide session does not exist"));
                return;
            }
            try {
                String capturedSession = session.id;
                Instant capturedAt = clock.instant();
                List<GuideSessionExportCollector.SequencedRequest> captured =
                        dev.openallay.util.Java8Collections.toList(session.requests.stream().map(request ->
                                new GuideSessionExportCollector.SequencedRequest(
                                        Objects.requireNonNull(
                                                session.requestSequences.get(request.requestId()),
                                                "request sequence"),
                                        request)));
                GuideSessionExportCollector collector =
                        new GuideSessionExportCollector(historyScope, history);
                collector.collect(capturedSession, captured,
                                dev.openallay.util.Java8Collections.mapCopyOf(session.originalContext),
                                session.nextRequestSequence - 1, capturedAt)
                        .whenComplete((export, failure) -> dispatcher.execute(() -> {
                            if (failure == null) {
                                captureExportImages(export, result);
                            } else {
                                GuideFailure known = historyFailure(failure, "history_export_failed");
                                result.complete(new ToolResult.Failure<>(known.code(), known.message()));
                            }
                        }));
            } catch (RuntimeException failure) {
                result.complete(new ToolResult.Failure<>(
                        "history_export_failed",
                        "Unable to read the complete guide session for export"));
            }
        });
        return result;
    }

    private void captureExportImages(
            GuideSessionExportSnapshot export,
            CompletableFuture<ToolResult<GuideSessionExportSnapshot>> result) {
        List<dev.openallay.model.image.ImageReference> references = imageReferences(
                dev.openallay.util.Java8Collections.toList(export.requests().stream().flatMap(request -> request.originalContext().stream())));
        if (references.isEmpty()) {
            result.complete(new ToolResult.Success<>(export));
            return;
        }
        if (attachmentStore == null) {
            result.complete(new ToolResult.Failure<>("image_attachment_unavailable",
                    "The images needed for this export are unavailable"));
            return;
        }
        String owner = imageOwnerPrefix + "export:" + UUID.randomUUID();
        retainedImages.put(owner, references);
        imageOperation(() -> {
            attachmentStore.retain(actor, owner, references);
            return export.withImagePayloadResolver(new dev.openallay.model.image.ImagePayloadResolver() {
                private final java.util.concurrent.atomic.AtomicBoolean closed =
                        new java.util.concurrent.atomic.AtomicBoolean();
                @Override public byte[] read(dev.openallay.model.image.ImageReference reference)
                        throws java.io.IOException {
                    if (closed.get() || !references.contains(reference)) throw new java.io.IOException(
                            "Image is not part of this active captured export");
                    return attachmentStore.read(actor, reference);
                }
                @Override public void close() {
                    if (closed.compareAndSet(false, true)) IMAGE_IO.execute(() -> {
                        try { attachmentStore.release(actor, owner); }
                        catch (java.io.IOException ignored) { /* A failed release preserves the asset. */ }
                    });
                }
            });
        }).whenComplete((value, failure) -> {
            if (failure != null) result.complete(new ToolResult.Failure<>(
                    "history_export_failed", "Unable to retain the images needed for this export"));
            else result.complete(value);
        });
    }

    public CompletableFuture<ToolResult<Boolean>> clearSelectedSession() {
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            SessionState session = sessions.get(selectedSession);
            revokePending(session);
            cancelManualCompaction(session);
            if (active(session) != null || session.workingRequest != null) {
                publishWithoutSave();
                result.complete(new ToolResult.Failure<>(
                        "agent_busy", "Stop the current request before clearing this session"));
                return;
            }
            invalidatePageLoad(session, "history_page_cancelled", "History page request was cancelled");
            retainPublishedImages(session);
            session.requests.forEach(request -> stopObservations(request.requestId()));
            session.requests.forEach(request -> releaseObservationImages(session, request.requestId()));
            String previousImageOwner = sessionImageOwner(session);
            CompletableFuture<Void> previousImageCustody = session.imageCustody;
            session.imageOwner = UUID.randomUUID().toString();
            session.imageCustody = CompletableFuture.completedFuture(null);
            session.requests.forEach(request -> requestSessions.remove(request.requestId()));
            capturedProjection.clearSession(session.id);
            presentation.discardSession(session.presentationOwner);
            session.presentationOwner = UUID.randomUUID();
            session.requests.clear();
            session.usageCarriers.keySet().forEach(requestSessions::remove);
            session.usageCarriers.clear();
            session.usage = new GuideUsageTracker();
            session.messages.clear();
            session.messageOrdinalBase = 0;
            session.checkpoints.clear();
            session.originalContext.clear();
            session.forkBoundaries.clear();
            session.unresolvedForkContext.clear();
            session.forkEpoch++;
            pendingCancelledFinalization.entrySet().removeIf(
                    entry -> entry.getValue().sessionId().equals(session.id));
            session.modelContext = dev.openallay.util.Java8Collections.listOf();
            session.contextGeneration++;
            session.requestSequences.clear();
            session.totalRequests = 0;
            session.nextRequestSequence = 0;
            session.firstAvailable = null;
            session.lastAvailable = null;
            session.firstLoaded = null;
            session.lastLoaded = null;
            session.hasEarlier = false;
            session.hasLater = false;
            pendingHistoryMutations.add(new GuideHistoryMutation.ClearSession(selectedSession));
            if (local != null) {
                local.clearSession(actor, selectedSession);
            }
            publish();
            releaseSessionImageReferences(previousImageOwner, previousImageCustody);
            result.complete(new ToolResult.Success<>(true));
        });
        return result;
    }

    public CompletableFuture<ToolResult<GuideModelMode>> setModelMode(GuideModelMode mode) {
        CompletableFuture<ToolResult<GuideModelMode>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            Objects.requireNonNull(mode, "mode");
            SessionState session = sessions.get(selectedSession);
            GuideModelSelection selection = mode == GuideModelMode.SERVER
                    ? GuideModelSelection.server()
                    : GuideModelSelection.client(session.lastClientProfileId);
            GuideFailure failure = selectionFailure(selection);
            if (failure != null) {
                result.complete(new ToolResult.Failure<>(failure.code(), failure.message()));
                return;
            }
            select(session, selection);
            publish();
            drainPending(session);
            result.complete(new ToolResult.Success<>(mode));
        });
        return result;
    }

    public CompletableFuture<ToolResult<GuideModelSelection>> setModelSelection(
            GuideModelSelection selection) {
        CompletableFuture<ToolResult<GuideModelSelection>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            Objects.requireNonNull(selection, "selection");
            GuideFailure failure = selectionFailure(selection);
            if (failure != null) {
                result.complete(new ToolResult.Failure<>(failure.code(), failure.message()));
                return;
            }
            SessionState session = sessions.get(selectedSession);
            select(session, selection);
            publish();
            drainPending(session);
            result.complete(new ToolResult.Success<>(selection));
        });
        return result;
    }

    public CompletableFuture<Void> refreshCapabilities() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (!remote.serverModelAvailable()) {
                GuideModelSelection fallback = defaultClientSelection();
                sessions.values().stream()
                        .filter(session -> session.modelSelection.kind()
                                == GuideModelSelection.Kind.SERVER)
                        .forEach(session -> select(session, fallback));
            }
            publishWithoutSave();
            result.complete(null);
        });
        return result;
    }

    public CompletableFuture<ToolResult<GuideHistoryPage>> requestHistoryWindow(
            String sessionId,
            GuideHistoryPageRequest.Direction direction,
            GuideHistoryCursor cursor,
            int count) {
        CompletableFuture<ToolResult<GuideHistoryPage>> result = new CompletableFuture<>();
        dispatcher.execute(() -> startHistoryWindow(
                sessionId, direction, cursor, count, result));
        return result;
    }

    public synchronized CompletableFuture<Void> disconnect() {
        if (disconnectFuture != null) return disconnectFuture;
        // Reject new work immediately, but consume final context receipts until actual endpoints settle.
        closing = true;
        invalidatePresentation();
        CompletableFuture<Void> result = new CompletableFuture<>();
        disconnectFuture = result;
        dispatcher.execute(() -> {
            List<ManualCompaction> detachedControls = dev.openallay.util.Java8Collections.toList(sessions.values().stream()
                    .map(session -> session.manualCompaction).filter(Objects::nonNull));
            detachedControls.forEach(control -> control.detachedOnDisconnect = true);
            sessions.values().forEach(this::revokePending);
            sessions.values().forEach(this::cancelManualCompaction);
            List<GuideRequestSnapshot> activeRequests = dev.openallay.util.Java8Collections.toList(sessions.values().stream()
                    .map(GuideService::active).filter(Objects::nonNull));
            for (GuideRequestSnapshot active : activeRequests) {
                SessionState session = sessions.get(active.sessionId());
                if (session != null && session.endpointRequests.contains(active.requestId())) {
                    pendingCancelledFinalization.putIfAbsent(active.requestId(), new CancelledFinalization(
                            session.id, Objects.requireNonNull(session.requestSequences.get(active.requestId())),
                            dev.openallay.util.Java8Collections.listCopyOf(session.checkpoints)));
                }
                stopObservations(active.requestId());
                if (active.topology() == GuideTopology.SERVER) remote.cancel(active.requestId());
                else if (local != null) local.cancel(actor, active.sessionId(), active.requestId());
                apply(active.requestId(), new AgentEvent.Failed(
                        "agent_cancelled", "Agent request was cancelled by disconnect"));
            }
            // The remote endpoint drains already-received events before its release receipt.
            // A vanished server cannot supply a new transcript; captured client producers stay
            // retained if that receipt does not contain their references.
            remote.disconnect();
            List<CompletableFuture<Void>> endpointBarriers = dev.openallay.util.Java8Collections.listCopyOf(endpointSettled.values());
            CompletableFuture.allOf(endpointBarriers.toArray(CompletableFuture[]::new))
                    .whenComplete((ignored, failure) -> dispatcher.execute(() ->
                            finishDisconnect(detachedControls, result)));
        });
        return result;
    }

    private void finishDisconnect(List<ManualCompaction> detachedControls, CompletableFuture<Void> result) {
        List<CompletableFuture<Void>> imageHandoffs = new ArrayList<>();
        for (SessionState session : sessions.values()) {
            session.requests.forEach(request -> stopObservations(request.requestId()));
            imageHandoffs.add(retainPublishedImages(session));
            session.requests.forEach(request -> imageHandoffs.add(
                    releaseObservationImages(session, request.requestId())));
        }
        CompletableFuture<Void> imagesSettled = CompletableFuture.allOf(
                imageHandoffs.toArray(CompletableFuture[]::new));
        CompletableFuture<Void> controlsSettled = CompletableFuture.allOf(detachedControls.stream()
                .map(control -> control.settled).toArray(CompletableFuture[]::new));
        CompletableFuture<Void> ordinaryWrites = history != null && allowHistoryWrites
                ? drainHistoryWrites() : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> durable = CompletableFuture.allOf(ordinaryWrites, controlsSettled)
                .thenCompose(ignored -> history != null && allowHistoryWrites
                        ? history.flush() : CompletableFuture.completedFuture(null));
        sessions.values().forEach(session -> invalidatePageLoad(
                session, "history_page_cancelled", "History page request was cancelled by disconnect"));
        dev.openallay.util.Java8Collections.toList(historyWriteBarriers.stream().map(HistoryWriteBarrier::requestId)
                .filter(Objects::nonNull)).forEach(this::cancelHistoryContextBarrier);
        List<String> transientImageOwners = new ArrayList<>(imageImportOwners);
        transientImageOwners.addAll(imageDraftOwners);
        retainedImages.keySet().stream().filter(owner -> !owner.contains(":export:"))
                .forEach(transientImageOwners::add);
        CompletableFuture<Void> imageCleanup = CompletableFuture.allOf(durable, imagesSettled).thenRunAsync(() -> {
            if (attachmentStore == null) return;
            for (String owner : transientImageOwners) {
                try { attachmentStore.release(actor, owner); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }, IMAGE_IO);
        imageCleanup.whenComplete((ignored, failure) -> dispatcher.execute(() -> {
            if (failure != null) reportImageCustodyFailure(failure);
            disconnected = true;
            if (local != null) local.clearActor(actor);
            requestSessions.clear();
            pendingCancelledFinalization.clear();
            endpointSettled.clear();
            sessions.clear();
            sessions.put("main", new SessionState("main", defaultClientSelection()));
            selectedSession = "main";
            publishWithoutSave();
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        }));
    }

    public CompletableFuture<Void> shutdown() {
        return disconnect();
    }

    @Override
    public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() {
        return administerHistory(HistoryAdministrationKind.PARTITION);
    }

    @Override
    public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() {
        return administerHistory(HistoryAdministrationKind.ACTOR);
    }

    CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() {
        return administerHistory(HistoryAdministrationKind.DATABASE);
    }

    private CompletableFuture<ToolResult<Boolean>> administerHistory(
            HistoryAdministrationKind kind) {
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> startHistoryAdministration(kind, result));
        return result;
    }

    private void startHistoryAdministration(
            HistoryAdministrationKind kind,
            CompletableFuture<ToolResult<Boolean>> result) {
        GuideFailure unavailable;
        try {
            unavailable = historyAdministrationFailure(kind);
        } catch (RuntimeException failure) {
            GuideFailure historyFailure = historyFailure(failure, "history_delete_failed");
            result.complete(new ToolResult.Failure<>(
                    historyFailure.code(), historyFailure.message()));
            return;
        }
        if (unavailable != null) {
            result.complete(new ToolResult.Failure<>(
                    unavailable.code(), unavailable.message()));
            return;
        }
        historyDeletionPending = true;
        CompletableFuture<Void> deletion;
        try {
            {
java.util.concurrent.CompletableFuture<java.lang.Void> $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((kind)) {
case PARTITION:
{
$oaSwitch0_exit_result = history.delete(
                        GuideHistoryDeleteScope.partition(historyScope)); break $oaSwitch0_exit;
}
case ACTOR:
{
$oaSwitch0_exit_result = history.delete(GuideHistoryDeleteScope.actor(actor)); break $oaSwitch0_exit;
}
case DATABASE:
{
$oaSwitch0_exit_result = history.resetDatabase(); break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
deletion = $oaSwitch0_exit_result;
}
            Objects.requireNonNull(deletion, "history deletion future");
        } catch (RuntimeException failure) {
            finishHistoryAdministration(failure, result);
            return;
        }
        deletion.whenComplete((ignored, failure) -> dispatcher.execute(
                () -> finishHistoryAdministration(failure, result)));
    }

    private GuideFailure historyAdministrationFailure(HistoryAdministrationKind kind) {
        if (history == null || historyScope == null || closing || disconnected) {
            return new GuideFailure(
                    "history_unavailable", "Durable guide history is unavailable");
        }
        if (historyDeletionPending
                || !pendingForks.isEmpty()
                || inFlightHistoryWrite != null
                || !queuedHistoryMutations.isEmpty()
                || !pendingHistoryMutations.isEmpty()
                || persistence.state() == GuidePersistenceSnapshot.State.SAVING
                || !history.activity().idleForDeletion()
                || sessions.values().stream().anyMatch(this::requestSessionBusy)) {
            return new GuideFailure("history_delete_busy", "Guide history is busy");
        }
        if (persistence.state() == GuidePersistenceSnapshot.State.LOADING) {
            return new GuideFailure(
                    "history_loading", "Durable guide history is still loading");
        }
        if (kind != HistoryAdministrationKind.DATABASE
                && (!allowHistoryWrites
                        || persistence.state() == GuidePersistenceSnapshot.State.UNAVAILABLE)) {
            return new GuideFailure(
                    "history_unavailable", "Durable guide history is unavailable");
        }
        return null;
    }

    private void finishHistoryAdministration(
            Throwable failure,
            CompletableFuture<ToolResult<Boolean>> result) {
        if (failure != null) {
            historyDeletionPending = false;
            GuideFailure historyFailure = historyFailure(failure, "history_delete_failed");
            result.complete(new ToolResult.Failure<>(
                    historyFailure.code(), historyFailure.message()));
            return;
        }
        // Closing owns the old projection/generation until its actual drain completes.
        // A successful deletion receipt must not rebase those pending custody barriers.
        if (!closing && !disconnected) {
            resetHistoryMemory();
            allowHistoryWrites = true;
            persistence = GuidePersistenceSnapshot.available(0);
            historyDeletionPending = false;
            publishWithoutSave();
        } else {
            historyDeletionPending = false;
        }
        result.complete(new ToolResult.Success<>(Boolean.TRUE));
    }

    private void resetHistoryMemory() {
        if (local != null) {
            try {
                local.clearActor(actor);
            } catch (RuntimeException ignored) {
                // Durable deletion is already committed; stale model memory cannot block reset.
            }
        }
        sessions.values().forEach(this::revokePending);
        sessions.values().forEach(this::cancelManualCompaction);
        requestImageCapabilities.clear();
        requestSessions.clear();
        pendingCancelledFinalization.clear();
        pendingForks.clear();
        sessionSelectionGeneration++;
        sessions.values().forEach(session -> invalidatePageLoad(
                session, "history_page_cancelled", "History page request was cancelled"));
        sessions.clear();
        durableProjection.clear();
        capturedProjection.clear();
        pendingHistoryMutations.clear();
        queuedHistoryMutations.clear();
        sessions.put("main", new SessionState("main", defaultClientSelection()));
        selectedSession = "main";
    }

    private static void invalidatePageLoad(SessionState session, String code, String message) {
        session.windowGeneration++;
        PageLoad load = session.pageLoad;
        session.pageLoad = null;
        session.pageState = GuideHistoryPageState.IDLE;
        session.pageFailure = null;
        if (load != null) {
            load.waiters().forEach(waiter -> waiter.complete(new ToolResult.Failure<>(code, message)));
        }
    }

    private boolean requestSessionBusy(SessionState session) {
        return session.workingRequest != null || active(session) != null || session.manualCompaction != null
                || !session.pending.isEmpty() || !session.admissions.isEmpty();
    }

    private <T> void admitInput(
            SessionState session, UUID receipt, dev.openallay.model.ModelMessage input,
            CompletableFuture<ToolResult<T>> result, Runnable accepted) {
        admitInput(session, receipt, input, result, accepted, () -> true);
    }

    private <T> void admitInput(
            SessionState session, UUID receipt, dev.openallay.model.ModelMessage input,
            CompletableFuture<ToolResult<T>> result, Runnable accepted,
            java.util.function.BooleanSupplier admissionFence) {
        if (closing || disconnected || !admissionFence.getAsBoolean()) {
            session.pendingOrder.remove(receipt);
            result.complete(new ToolResult.Failure<>("message_cancelled", "Message was cancelled")); return;
        }
        long generation = session.queueGeneration;
        session.admissions.put(receipt, generation);
        transferImageInput(session.id, receipt, input).whenComplete((ignored, failure) ->
                dispatcher.execute(() -> {
                    if (!session.admissions.containsKey(receipt)) {
                        releaseImageInput(receipt);
                        result.complete(new ToolResult.Failure<>("message_cancelled", "Message was cancelled"));
                        return;
                    }
                    session.readyAdmissions.put(receipt, () -> {
                        if (failure != null || closing || disconnected || sessions.get(session.id) != session
                                || session.queueGeneration != generation || !admissionFence.getAsBoolean()) {
                            releaseImageInput(receipt);
                            session.pendingOrder.remove(receipt);
                            result.complete(new ToolResult.Failure<>(failure != null
                                    ? "image_attachment_failed" : "message_cancelled",
                                    failure != null ? "Unable to retain the image attachment" : "Message was cancelled"));
                            drainPending(session);
                            return;
                        }
                        try { accepted.run(); }
                        catch (RuntimeException failed) {
                            releaseImageInput(receipt);
                            result.complete(new ToolResult.Failure<>("agent_failure", message(failed)));
                        }
                        if (result.getNow(null) instanceof ToolResult.Failure<?>) releaseImageInput(receipt);
                        drainPending(session);
                    });
                    drainAdmissions(session);
                }));
    }

    private void drainAdmissions(SessionState session) {
        if (session.drainingAdmissions) return;
        session.drainingAdmissions = true;
        try {
            while (!session.admissions.isEmpty()) {
                UUID first = session.admissions.keySet().iterator().next();
                Runnable ready = session.readyAdmissions.remove(first);
                if (ready == null) break;
                session.admissions.remove(first);
                ready.run();
            }
        } finally {
            session.drainingAdmissions = false;
            drainPending(session);
        }
    }

    private void revokePending(SessionState session) {
        session.queueGeneration++;
        session.pendingReceipts.values().forEach(this::releaseImageInput);
        session.admissions.keySet().forEach(this::releaseImageInput);
        session.pending.clear();
        session.pendingOrder.clear();
        session.pendingReceipts.clear();
        List<Runnable> ready = dev.openallay.util.Java8Collections.listCopyOf(session.readyAdmissions.values());
        session.readyAdmissions.clear();
        session.admissions.clear();
        ready.forEach(Runnable::run);
    }

    private void putPendingOrdered(SessionState session, GuidePendingMessage message) {
        session.pending.put(message.id(), message);
        List<GuidePendingMessage> ordered = dev.openallay.util.Java8Collections.toList(session.pending.values().stream()
                .sorted(java.util.Comparator.comparingLong(pending ->
                        session.pendingOrder.getOrDefault(pending.id(), Long.MAX_VALUE))));
        session.pending.clear();
        ordered.forEach(pending -> session.pending.put(pending.id(), pending));
    }

    private void releasePendingReceipt(SessionState session, UUID id) {
        session.pendingOrder.remove(id);
        UUID receipt = session.pendingReceipts.remove(id);
        if (receipt != null) releaseImageInput(receipt);
    }

    private void sendSteer(SessionState session, GuidePendingMessage pending) {
        if (pending.kind() != GuidePendingMessage.Kind.STEER
                || !pending.requestId().equals(session.workingRequest)
                || !session.endpointRequests.contains(pending.requestId())) return;
        GuideRequestSnapshot request = find(pending.requestId());
        if (request == null || request.terminal()) return;
        boolean accepted = false;
        try {
            if (request.topology() == GuideTopology.SERVER) {
                accepted = remote.steer(request.requestId(), pending.id(), pending.message(), images(request.requestId()));
            } else if (local != null) {
                ToolResult<Boolean> result = local.steer(actor, session.id,
                        request.requestId(), pending.id(), pending.message());
                final class $oaPattern6_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Success<Boolean> bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
accepted = (($oaPattern6_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern6_holder.bound = (ToolResult.Success<Boolean>) $oaPattern6_holder.value) != null)) && $oaPattern6_holder.bound.value();
            }
        } catch (RuntimeException ignored) {
            // The player's instruction remains visible and becomes a follow-up after cleanup.
        }
        if (!accepted && session.pending.containsKey(pending.id())) {
            session.pending.put(pending.id(), pending.followUp());
            publishWithoutSave();
        }
    }

    private void releaseRequest(SessionState owner, UUID requestId) {
        if (closing || disconnected || owner == null || sessions.get(owner.id) != owner
                || !requestId.equals(owner.workingRequest) || owner.releasingRequest) return;
        GuideRequestSnapshot request = find(requestId);
        if (request == null && owner.usageCarriers.containsKey(requestId)) {
            request = capturedProjection.requests.get(requestId);
        }
        if (request == null || !request.terminal()) return;
        owner.releasingRequest = true;
        releaseObservationImages(owner, requestId).whenComplete((ignored, failure) -> dispatcher.execute(() -> {
            if (disconnected || sessions.get(owner.id) != owner
                    || !requestId.equals(owner.workingRequest)) return;
            // A failed retain keeps the producer pins. It is a real failure, not an endless FIFO wait.
            if (failure != null) reportImageCustodyFailure(failure);
            finishRequestRelease(owner, requestId);
        }));
    }

    private void finishRequestRelease(SessionState owner, UUID requestId) {
        owner.releasingRequest = false;
        if (!releaseUsageOwner(requestId)) return;
        owner.endpointRequests.remove(requestId);
        owner.usageCarriers.remove(requestId);
        if (indexOf(owner, requestId) < 0) requestSessions.remove(requestId);
        UUID receipt = owner.requestReceipts.remove(requestId);
        if (receipt != null) releaseImageInput(receipt);
        owner.workingRequest = null;
        owner.pending.replaceAll((id, pending) ->
                requestId.equals(pending.requestId()) ? pending.followUp() : pending);
        publishWithoutSave();
        drainPending(owner);
    }

    private void drainPending(SessionState session) {
        if (session.drainingPending || closing || disconnected || historyDeletionPending
                || sessions.get(session.id) != session || session.workingRequest != null
                || !session.admissions.isEmpty() || session.manualCompaction != null
                || active(session) != null) return;
        session.drainingPending = true;
        try {
            while (!session.pending.isEmpty() && session.workingRequest == null
                    && sessions.get(session.id) == session && !disconnected) {
                GuidePendingMessage next = session.pending.values().iterator().next();
                if (next.kind() == GuidePendingMessage.Kind.STEER) {
                    next = next.followUp();
                    session.pending.put(next.id(), next);
                }
                session.pending.remove(next.id());
                CompletableFuture<ToolResult<UUID>> dispatched = new CompletableFuture<>();
                int requestCount = session.requests.size();
                UUID receipt = session.pendingReceipts.get(next.id());
                submit(session.id, next.message(), receipt, dispatched);
                final class $oaPattern7_Holder { dev.openallay.tool.ToolResult<java.util.UUID> value; ToolResult.Failure<UUID> bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if (session.requests.size() == requestCount
                        && (($oaPattern7_holder.value = dispatched.getNow(null)) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern7_holder.bound = (ToolResult.Failure<UUID>) $oaPattern7_holder.value) != null))) {
                    // Capability/attachment validation may have changed while queued. Keep the draft.
                    LinkedHashMap<UUID, GuidePendingMessage> restored = new LinkedHashMap<>();
                    restored.put(next.id(), next.failed(new GuideFailure($oaPattern7_holder.bound.code(), $oaPattern7_holder.bound.message())));
                    restored.putAll(session.pending);
                    session.pending.clear();
                    session.pending.putAll(restored);
                    break;
                }
                session.pendingReceipts.remove(next.id());
                session.pendingOrder.remove(next.id());
            }
        } finally {
            session.drainingPending = false;
            publishWithoutSave();
        }
    }

    private void submit(
            String sessionId, String question, CompletableFuture<ToolResult<UUID>> result) {
        submit(sessionId, question == null ? null : dev.openallay.model.ModelMessage.userText(question), result);
    }

    private void submit(
            String sessionId, dev.openallay.model.ModelMessage input,
            CompletableFuture<ToolResult<UUID>> result) {
        submit(sessionId, input, null, result);
    }

    private void submit(
            String sessionId, dev.openallay.model.ModelMessage input, UUID receipt,
            CompletableFuture<ToolResult<UUID>> result) {
        if (rejectStateChange(result)) return;
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            result.complete(new ToolResult.Failure<>("invalid_session", "Guide session does not exist")); return;
        }
        ToolResult<Boolean> valid = validateUserInput(input, session.modelSelection);
        final class $oaPattern8_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = valid) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern8_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern8_holder.value) != null))) {
            result.complete(new ToolResult.Failure<>($oaPattern8_holder.bound.code(), $oaPattern8_holder.bound.message())); return;
        }
        String question = GuidePendingMessage.displayText(input);
        if (active(session) != null || session.workingRequest != null || session.manualCompaction != null) {
            result.complete(new ToolResult.Failure<>(
                    "agent_busy", "This guide session already has active work"));
            return;
        }
        GuideModelSelection capturedSelection = session.modelSelection;
        GuideTopology topology;
        if (capturedSelection.kind() == GuideModelSelection.Kind.SERVER) {
            if (!remote.serverModelAvailable()) {
                result.complete(new ToolResult.Failure<>(
                        "capability_unavailable", "The connected server does not provide a model"));
                return;
            }
            topology = GuideTopology.SERVER;
        } else {
            GuideFailure failure = selectionFailure(capturedSelection);
            if (failure != null) {
                result.complete(new ToolResult.Failure<>(failure.code(), failure.message()));
                return;
            }
            topology = remote.serverToolsAvailable()
                    ? GuideTopology.CLIENT_WITH_SERVER_TOOLS
                    : GuideTopology.CLIENT_LOCAL;
        }

        UUID requestId = UUID.randomUUID();
        contexts.freezeRequest(requestId.toString(), topology != GuideTopology.SERVER);
        Instant now = clock.instant();
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                requestId, sessionId, topology, question, now, capturedSelection);
        session.requests.add(request);
        presentation.admit(session.presentationOwner, request);
        submittedInputs.put(requestId, input);
        if (receipt != null) session.requestReceipts.put(requestId, receipt);
        captureImageCapability(requestId, capturedSelection);
        session.workingRequest = requestId;
        session.usage.begin(requestId, capturedSelection, publicModelIdentifier(capturedSelection));
        session.originalContext.put(requestId, dev.openallay.util.Java8Collections.listOf());
        long requestSequence = session.nextRequestSequence++;
        session.requestSequences.put(requestId, requestSequence);
        session.totalRequests++;
        GuideHistoryCursor requestCursor = new GuideHistoryCursor(requestSequence, requestId);
        if (session.firstAvailable == null) session.firstAvailable = requestCursor;
        session.lastAvailable = requestCursor;
        if (session.firstLoaded == null) session.firstLoaded = requestCursor;
        session.lastLoaded = requestCursor;
        session.hasLater = false;
        session.messages.add(new GuideMessage(
                requestId, GuideMessage.Role.USER, question, now));
        requestSessions.put(requestId, sessionId);
        // Actor/session image retention requires the exact request-owner mapping to exist first.
        retainUserInput(requestId, input);
        publish();

        if (topology == GuideTopology.SERVER) {
            if (incrementalHistory) {
                prepareRemoteContext(session, requestId, question);
            } else {
                session.endpointRequests.add(requestId);
                endpointSettled.put(requestId, new CompletableFuture<>());
                if (!remote.askWithContext(
                        requestId,
                        sessionId,
                        userInput(requestId),
                        images(requestId),
                        session.modelContext,
                        event -> dispatcher.execute(() -> apply(requestId, event)))) {
                    apply(requestId, new AgentEvent.Failed(
                            "capability_unavailable",
                            "The connected server rejected the model request"));
                    settleEndpoint(requestId);
                    releaseRequest(session, requestId);
                    result.complete(new ToolResult.Failure<>(
                            "capability_unavailable",
                            "The connected server rejected the model request"));
                    return;
                }
            }
        } else if (incrementalHistory && !local.hasContext(actor, sessionId)) {
            prepareLocalContext(
                    session, requestId, capturedSelection.profileId(), question);
        } else {
            dispatchLocal(requestId, sessionId, capturedSelection.profileId(), question);
        }
        result.complete(new ToolResult.Success<>(requestId));
    }

    private void prepareLocalContext(
            SessionState session,
            UUID requestId,
            String profileId,
            String question) {
        GuideContextSpec spec = local.contextSpec(profileId).orElse(null);
        if (spec == null) {
            apply(requestId, new AgentEvent.Failed(
                    "context_budget_unavailable",
                    "The selected model does not provide a valid context budget"));
            return;
        }
        int index = indexOf(session, requestId);
        if (index < 0) return;
        session.requests.set(index, withStatus(
                session.requests.get(index), GuideRequestStatus.CONTEXT_LOADING, clock.instant()));
        long generation = ++session.contextGeneration;
        session.preparingContextRequest = requestId;
        publish();
        CompletableFuture<GuideHistoryContextSeed> loading;
        try {
            loading = Objects.requireNonNull(readHistoryContext(new GuideHistoryContextRequest(
                    historyScope,
                    session.id,
                    spec.budget(),
                    spec.promptAndToolTokens(),
                    spec.canonicalModelId(), spec.estimator()), requestId), "history context future");
        } catch (RuntimeException failure) {
            finishLocalContext(
                    session.id, requestId, profileId, question, generation, null, failure);
            return;
        }
        loading.whenComplete((seed, failure) -> dispatcher.execute(() -> finishLocalContext(
                session.id, requestId, profileId, question, generation, seed, failure)));
    }

    private void prepareRemoteContext(
            SessionState session, UUID requestId, String question) {
        GuideContextSpec spec = remote.contextSpec().orElse(null);
        if (spec == null) {
            apply(requestId, new AgentEvent.Failed(
                    "context_budget_unavailable",
                    "The server model does not provide a valid context budget"));
            return;
        }
        int index = indexOf(session, requestId);
        if (index < 0) return;
        session.requests.set(index, withStatus(
                session.requests.get(index), GuideRequestStatus.CONTEXT_LOADING, clock.instant()));
        long generation = ++session.contextGeneration;
        session.preparingContextRequest = requestId;
        publish();
        CompletableFuture<GuideHistoryContextSeed> loading;
        try {
            loading = Objects.requireNonNull(readHistoryContext(new GuideHistoryContextRequest(
                    historyScope,
                    session.id,
                    spec.budget(),
                    spec.promptAndToolTokens(),
                    spec.canonicalModelId(), spec.estimator()), requestId), "history context future");
        } catch (RuntimeException failure) {
            finishRemoteContext(
                    session.id, requestId, question, generation, null, failure);
            return;
        }
        loading.whenComplete((seed, failure) -> dispatcher.execute(() -> finishRemoteContext(
                session.id, requestId, question, generation, seed, failure)));
    }

    private void finishRemoteContext(
            String sessionId,
            UUID requestId,
            String question,
            long generation,
            GuideHistoryContextSeed seed,
            Throwable failure) {
        if (disconnected) return;
        SessionState session = sessions.get(sessionId);
        GuideRequestSnapshot request = find(requestId);
        if (session == null || request == null || request.terminal()
                || session.contextGeneration != generation
                || !requestId.equals(session.preparingContextRequest)) {
            return;
        }
        session.preparingContextRequest = null;
        if (failure != null || seed == null || !sessionId.equals(seed.sessionId())) {
            apply(requestId, new AgentEvent.Failed(
                    "history_context_failed",
                    "Unable to prepare durable guide context"));
            return;
        }
        boolean accepted;
        try {
            session.endpointRequests.add(requestId);
            endpointSettled.put(requestId, new CompletableFuture<>());
            accepted = remote.askWithContext(
                    requestId, sessionId, userInput(requestId), images(requestId), seed.messages(),
                    event -> dispatcher.execute(() -> apply(requestId, event)));
        } catch (RuntimeException malformed) {
            accepted = false;
        }
        if (!accepted) {
            apply(requestId, new AgentEvent.Failed(
                    "capability_unavailable",
                    "The connected server rejected the model request"));
            settleEndpoint(requestId);
            releaseRequest(session, requestId);
        }
    }

    private void finishLocalContext(
            String sessionId,
            UUID requestId,
            String profileId,
            String question,
            long generation,
            GuideHistoryContextSeed seed,
            Throwable failure) {
        if (disconnected) return;
        SessionState session = sessions.get(sessionId);
        if (session == null
                || session.contextGeneration != generation
                || !requestId.equals(session.preparingContextRequest)
                || find(requestId) == null
                || find(requestId).terminal()) {
            return;
        }
        session.preparingContextRequest = null;
        if (failure != null || seed == null || !sessionId.equals(seed.sessionId())) {
            apply(requestId, new AgentEvent.Failed(
                    "history_context_failed",
                    "Unable to prepare durable guide context"));
            return;
        }
        try {
            local.hydrateContext(actor, sessionId, seed.messages(), seed.checkpoints());
        } catch (RuntimeException malformed) {
            apply(requestId, new AgentEvent.Failed(
                    "history_context_failed",
                    "Unable to prepare durable guide context"));
            return;
        }
        dispatchLocal(requestId, sessionId, profileId, question);
    }

    private void dispatchLocal(
            UUID requestId, String sessionId, String profileId, String question) {
        GuideRequestSnapshot request = find(requestId);
        if (request == null || request.terminal()) return;
        Set<dev.openallay.context.ContextCapability> requiredContext;
        try {
            requiredContext = local.requiredContext(profileId);
        } catch (GuideModelProfileException failure) {
            apply(requestId, new AgentEvent.Failed(failure.code(), failure.getMessage()));
            return;
        }
        contexts.associateInputObservation(requestId.toString(), userInput(requestId).inputObservation());
        ToolResult<ToolInvocationContext> captured =
                contexts.capture(requiredContext, requestId.toString());
        final class $oaPattern9_Holder { dev.openallay.tool.ToolResult<dev.openallay.context.ToolInvocationContext> value; ToolResult.Failure<ToolInvocationContext> bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = captured) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern9_holder.bound = (ToolResult.Failure<ToolInvocationContext>) $oaPattern9_holder.value) != null))) {
            apply(requestId, new AgentEvent.Failed($oaPattern9_holder.bound.code(), $oaPattern9_holder.bound.message()));
            return;
        }
        ToolInvocationContext context =
                ((ToolResult.Success<ToolInvocationContext>) captured).value();
        SessionState owner = sessions.get(sessionId);
        try {
            owner.endpointRequests.add(requestId);
            endpointSettled.put(requestId, new CompletableFuture<>());
            local.ask(
                            profileId,
                            actor,
                            sessionId,
                            requestId,
                            userInput(requestId),
                            images(requestId),
                            context,
                            event -> dispatcher.execute(() -> apply(requestId, event)))
                    .whenComplete((completed, throwable) -> dispatcher.execute(() -> {
                        if (throwable != null) failIfActive(requestId, throwable);
                        else {
                            GuideRequestSnapshot current = find(requestId);
                            if (current != null && !current.terminal()) {
                                apply(requestId, completed != null && completed.successful()
                                        ? new AgentEvent.FinalText(completed.text())
                                        : new AgentEvent.Failed(completed == null ? "agent_failure" : completed.errorCode(),
                                                completed == null ? "Agent endpoint ended without a result" : completed.errorMessage()));
                            }
                        }
                        settleEndpoint(requestId);
                        releaseRequest(owner, requestId);
                    }));
        } catch (RuntimeException failure) {
            apply(requestId, new AgentEvent.Failed(
                    "agent_failure", message(failure)));
            settleEndpoint(requestId);
            releaseRequest(owner, requestId);
        }
    }

    private static GuideRequestSnapshot withStatus(
            GuideRequestSnapshot request, GuideRequestStatus status, Instant now) {
        GuideRequestProgress progress = request.progress().advance(
                status == GuideRequestStatus.CONTEXT_LOADING
                        ? GuideRequestPhase.CONTEXT_LOADING
                        : request.progress().phase(),
                now);
        return new GuideRequestSnapshot(
                request.requestId(), request.sessionId(), request.topology(), request.userMessage(),
                request.timeline(), status, request.sources(), request.usage(),
                request.retryAfterMillis(), request.failure(), request.createdAt(),
                progress.lastProgressAt(), request.terminalAt(), request.modelSelection(), progress,
                request.usageProjection(), request.usageOriginRequestId());
    }

    private void settleEndpoint(UUID requestId) {
        CompletableFuture<Void> settled = endpointSettled.get(requestId);
        if (settled != null) settled.complete(null);
    }

    private void apply(UUID requestId, AgentEvent event) {
        if (disconnected) return;
        if (event instanceof AgentEvent.RequestReleased) {
            settleEndpoint(requestId);
            String releasedSession = requestSessions.get(requestId);
            releaseRequest(releasedSession == null ? null : sessions.get(releasedSession), requestId);
            return;
        }
        final class $oaPattern10_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextFinalized bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextFinalized && (($oaPattern10_holder.bound = (AgentEvent.ContextFinalized) $oaPattern10_holder.value) != null))) {
            CancelledFinalization pending = pendingCancelledFinalization.remove(requestId);
            if (disconnected || pending == null) return;
            SessionState session = sessions.get(pending.sessionId());
            if (session == null) return;
            session.originalContext.put(requestId, $oaPattern10_holder.bound.requestMessages());
            if (incrementalHistory) {
                pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceRequestContext(
                        requestId, $oaPattern10_holder.bound.requestMessages()));
            }
            if (pending.sequence() == session.nextRequestSequence - 1) {
                session.modelContext = $oaPattern10_holder.bound.messages();
                if (incrementalHistory) {
                    pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceContext(
                            pending.sessionId(), $oaPattern10_holder.bound.messages()));
                }
            }
            captureForkBoundary(session, requestId, $oaPattern10_holder.bound.messages(), pending.checkpoints());
            retainPublishedImages(session);
            publish();
            return;
        }
        String sessionId = requestSessions.get(requestId);
        if (sessionId == null) return;
        SessionState session = sessions.get(sessionId);
        if (session == null) return;
        int index = indexOf(session, requestId);
        UsageCarrier carrier = session.usageCarriers.get(requestId);
        if (index < 0 && carrier == null) return;
        GuideRequestSnapshot target = index >= 0 ? session.requests.get(index)
                : capturedProjection.requests.get(requestId);
        if (target == null) return;
        // Numeric source observations may finish after visible cancellation. They update only cost.
        if (event instanceof AgentEvent.ModelUsageObserved || event instanceof AgentEvent.ModelUsageStarted) {
            if (target.usageOriginRequestId() != null) return;
            if (session.usage.accept(requestId, event)) {
                GuideRequestSnapshot charged = target.withUsageProjection(session.usage.requestSnapshot(requestId));
                if (index >= 0) session.requests.set(index, charged);
                if (carrier != null) {
                    session.usageCarriers.put(requestId, new UsageCarrier(carrier.sequence(), charged.usageProjection()));
                }
                if (index < 0 && incrementalHistory) {
                    pendingHistoryMutations.add(new GuideHistoryMutation.UpsertRequest(carrier.sequence(), charged));
                }
                publish();
            }
            return;
        }
        if (index < 0) return;
        final class $oaPattern11_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.SteerRejected bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = event) instanceof dev.openallay.agent.AgentEvent.SteerRejected && (($oaPattern11_holder.bound = (AgentEvent.SteerRejected) $oaPattern11_holder.value) != null))) {
            GuidePendingMessage pending = session.pending.get($oaPattern11_holder.bound.messageId());
            if (pending != null && requestId.equals(pending.requestId())) {
                session.pending.put(pending.id(), pending.followUp());
                publishWithoutSave();
            }
            return;
        }
        boolean pendingSteerChanged = false;
        final class $oaPattern12_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.SteerApplied bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = event) instanceof dev.openallay.agent.AgentEvent.SteerApplied && (($oaPattern12_holder.bound = (AgentEvent.SteerApplied) $oaPattern12_holder.value) != null))) {
            GuidePendingMessage pending = session.pending.get($oaPattern12_holder.bound.messageId());
            if (pending != null && requestId.equals(pending.requestId())) {
                if (pending.message().equals($oaPattern12_holder.bound.message())) {
                    session.pending.remove(pending.id());
                    releasePendingReceipt(session, pending.id());
                }
                else session.pending.put(pending.id(), pending.followUp());
                pendingSteerChanged = true;
            }
        }
        if (target.terminal() && !(closing && event instanceof AgentEvent.ContextUpdated)) {
            if (pendingSteerChanged) publishWithoutSave();
            return;
        }
        final class $oaPattern13_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.StateChanged bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = event) instanceof dev.openallay.agent.AgentEvent.StateChanged && (($oaPattern13_holder.bound = (AgentEvent.StateChanged) $oaPattern13_holder.value) != null))
                && $oaPattern13_holder.bound.state() == dev.openallay.agent.AgentState.PREPARING) {
            dev.openallay.util.Java8Collections.listCopyOf(session.pending.values()).stream()
                    .filter(pending -> requestId.equals(pending.requestId()))
                    .forEach(pending -> sendSteer(session, pending));
        }
        final class $oaPattern14_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextUpdated bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextUpdated && (($oaPattern14_holder.bound = (AgentEvent.ContextUpdated) $oaPattern14_holder.value) != null))) {
            session.modelContext = $oaPattern14_holder.bound.messages();
            session.originalContext.put(requestId, $oaPattern14_holder.bound.requestMessages());
            if (incrementalHistory) {
                pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceContext(
                        sessionId, $oaPattern14_holder.bound.messages()));
                pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceRequestContext(
                        requestId, $oaPattern14_holder.bound.requestMessages()));
            }
            retainPublishedImages(session);
            publish();
            return;
        }
        final class $oaPattern15_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextCompacted bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextCompacted && (($oaPattern15_holder.bound = (AgentEvent.ContextCompacted) $oaPattern15_holder.value) != null))) {
            ContextCheckpoint checkpoint = $oaPattern15_holder.bound.checkpoint();
            int existing = -1;
            for (int ordinal = 0; ordinal < session.checkpoints.size(); ordinal++) {
                if (session.checkpoints.get(ordinal).checkpointId().equals(checkpoint.checkpointId())) {
                    existing = ordinal;
                    break;
                }
            }
            if (existing < 0) session.checkpoints.add(checkpoint);
            else session.checkpoints.set(existing, checkpoint);
            publish();
            return;
        }
        GuideRequestSnapshot before = session.requests.get(index);
        GuideRequestSnapshot after = reducer.apply(before, event, clock.instant());
        if (before == after) {
            return;
        }
        after = after.withUsageProjection(before.usageProjection());
        session.requests.set(index, after);
        presentation.applied(session.presentationOwner, before, after, event);
        if (after.terminal()) {
            stopObservations(requestId);
            captureForkBoundary(session, requestId);
        }
        if (after.status() == GuideRequestStatus.COMPLETED
                && !dev.openallay.util.Java8Strings.isBlank(after.assistantText())) {
            session.messages.add(new GuideMessage(
                    after.requestId(),
                    GuideMessage.Role.ASSISTANT,
                    after.assistantText(),
                    after.terminalAt()));
        }
        publish();
        if (after.terminal() && !session.endpointRequests.contains(requestId)) {
            releaseRequest(session, requestId);
        }
    }

    private boolean releaseUsageOwner(UUID requestId) {
        String sessionId = requestSessions.get(requestId);
        SessionState session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || !session.usage.release(requestId)) return false;
        session.usageCarriers.remove(requestId);
        if (indexOf(session, requestId) < 0) requestSessions.remove(requestId);
        return true;
    }

    private void failIfActive(UUID requestId, Throwable throwable) {
        GuideRequestSnapshot request = find(requestId);
        if (request != null && !request.terminal()) {
            Throwable failure = unwrap(throwable);
            final class $oaPattern16_Holder { java.lang.Throwable value; GuideModelProfileException bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if ((($oaPattern16_holder.value = failure) instanceof dev.openallay.guide.GuideModelProfileException && (($oaPattern16_holder.bound = (GuideModelProfileException) $oaPattern16_holder.value) != null))) {
                apply(requestId, new AgentEvent.Failed(
                        $oaPattern16_holder.bound.code(), $oaPattern16_holder.bound.getMessage()));
            } else {
                apply(requestId, new AgentEvent.Failed("agent_failure", message(failure)));
            }
        }
    }

    private GuideRequestSnapshot find(UUID requestId) {
        String sessionId = requestSessions.get(requestId);
        if (sessionId == null) {
            return null;
        }
        SessionState session = sessions.get(sessionId);
        int index = indexOf(session, requestId);
        return index < 0 ? null : session.requests.get(index);
    }

    private static int indexOf(SessionState session, UUID requestId) {
        if (session == null) {
            return -1;
        }
        for (int index = 0; index < session.requests.size(); index++) {
            if (session.requests.get(index).requestId().equals(requestId)) {
                return index;
            }
        }
        return -1;
    }

    private static GuideRequestSnapshot active(SessionState session) {
        if (session == null || session.requests.isEmpty()) {
            return null;
        }
        GuideRequestSnapshot latest = session.requests.get(session.requests.size() - 1);
        return latest.terminal() ? null : latest;
    }

    private void publish() {
        scheduleHistorySave();
        publishWithoutSave();
    }

    private void publishWithoutSave() {
        fenceManualCompactionSelection();
        publishedSessions = dev.openallay.util.Java8Collections.mapCopyOf(sessions);
        Map<UUID, List<dev.openallay.model.ModelMessage>> originalImages = new LinkedHashMap<>();
        sessions.values().forEach(session -> originalImages.putAll(session.originalContext));
        publishedObservationImages = ToolObservationImageIndex.build(originalImages);
        snapshot = buildSnapshot();
        telemetry = buildTelemetry();
        for (Consumer<GuideSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException ignored) {
                // One screen/command observer cannot break product state delivery.
            }
        }
    }

    private GuideSnapshot buildSnapshot() {
        List<GuideSessionSnapshot> copies = dev.openallay.util.Java8Collections.toList(sessions.values().stream()
                .map(session -> new GuideSessionSnapshot(
                        session.id,
                        session.messages,
                        session.requests,
                        session.checkpoints,
                        session.modelSelection,
                        historyWindow(session),
                        dev.openallay.util.Java8Collections.listCopyOf(session.pending.values()),
                        session.workingRequest)));
        GuideModelSelection currentSelection = sessions.get(selectedSession).modelSelection;
        List<GuideClientModelProfile> profiles = local == null ? dev.openallay.util.Java8Collections.listOf() : local.profiles();
        return new GuideSnapshot(
                actor,
                selectedSession,
                currentSelection.modelMode(),
                profiles.stream().anyMatch(GuideClientModelProfile::available),
                remote.serverModelAvailable(),
                persistence,
                copies,
                clock.instant(),
                currentSelection,
                profiles,
                remote.contextSpec(),
                remote.imageInputCapability(),
                remote.imageInputCapabilitySource());
    }

    private void startHistoryLoad() {
        CompletableFuture<java.util.Optional<GuideHistoryMetadata>> loading;
        try {
            loading = Objects.requireNonNull(
                    history.metadata(historyScope), "history metadata future");
        } catch (RuntimeException failure) {
            completeHistoryLoad(null, failure);
            return;
        }
        loading.whenComplete((loaded, failure) ->
                dispatcher.execute(() -> completeHistoryLoad(loaded, failure)));
    }

    private void completeHistoryLoad(
            java.util.Optional<GuideHistoryMetadata> loaded, Throwable failure) {
        if (disconnected) {
            return;
        }
        if (failure != null) {
            allowHistoryWrites = false;
            persistence = unavailable(failure, "history_load_failed");
            publishWithoutSave();
            return;
        }
        if (loaded == null) {
            allowHistoryWrites = false;
            persistence = unavailable(
                    new GuideHistoryException("history_load_failed", "History load returned no result"),
                    "history_load_failed");
            publishWithoutSave();
            return;
        }
        if (loaded.isPresent()) {
            GuideHistoryMetadata metadata = loaded.orElseThrow();
            if (!metadata.scope().equals(historyScope)) {
                allowHistoryWrites = false;
                persistence = new GuidePersistenceSnapshot(
                        GuidePersistenceSnapshot.State.UNAVAILABLE,
                        0,
                        0,
                        new GuideFailure(
                                "history_scope_mismatch",
                                "Loaded history belongs to another partition"));
                publishWithoutSave();
                return;
            }
            hydrateMetadata(metadata);
        }
        allowHistoryWrites = true;
        incrementalHistory = true;
        persistence = GuidePersistenceSnapshot.available(0);
        publishWithoutSave();
    }

    private void hydrateMetadata(GuideHistoryMetadata metadata) {
        sessions.clear();
        requestSessions.clear();
        pendingCancelledFinalization.clear();
        for (GuideHistoryMetadata.Session snapshot : metadata.sessions()) {
            GuideModelSelection restored = snapshot.modelSelection().kind() == GuideModelSelection.Kind.SERVER
                    ? defaultClientSelection() : snapshot.modelSelection();
            SessionState session = new SessionState(snapshot.sessionId(), restored);
            session.totalRequests = snapshot.requestCount();
            session.messageOrdinalBase = snapshot.messageCount();
            session.usage.restore(snapshot.usage(), snapshot.inheritedUsage(), snapshot.controlUsage());
            session.firstAvailable = snapshot.first();
            session.lastAvailable = snapshot.last();
            session.hasEarlier = snapshot.requestCount() > 0;
            session.hasLater = false;
            session.nextRequestSequence = snapshot.last() == null
                    ? 0 : snapshot.last().sequence() + 1;
            sessions.put(session.id, session);
            SessionProjection projection = new SessionProjection(
                    snapshot.ordinal(), snapshot.modelSelection());
            durableProjection.sessions.put(session.id, projection);
            capturedProjection.sessions.put(session.id, projection);
            durableProjection.controlUsage.put(session.id, snapshot.controlUsage());
            capturedProjection.controlUsage.put(session.id, snapshot.controlUsage());
        }
        selectedSession = metadata.selectedSession();
        durableProjection.selectedSession = selectedSession;
        capturedProjection.selectedSession = selectedSession;
    }

    private void startHistoryWindow(
            String sessionId,
            GuideHistoryPageRequest.Direction direction,
            GuideHistoryCursor cursor,
            int count,
            CompletableFuture<ToolResult<GuideHistoryPage>> result) {
        if (history == null || !allowHistoryWrites || disconnected) {
            result.complete(new ToolResult.Failure<>(
                    "history_unavailable", "Durable guide history is unavailable"));
            return;
        }
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            result.complete(new ToolResult.Failure<>(
                    "invalid_session", "Guide session does not exist"));
            return;
        }
        GuideHistoryPageRequest request;
        try {
            request = new GuideHistoryPageRequest(
                    historyScope, sessionId, direction, cursor, count);
        } catch (IllegalArgumentException failure) {
            result.complete(new ToolResult.Failure<>("invalid_arguments", failure.getMessage()));
            return;
        }
        PageKey key = new PageKey(direction, cursor, count);
        if (session.pageLoad != null && session.pageLoad.key().equals(key)) {
            session.pageLoad.waiters().add(result);
            return;
        }
        if (session.pageLoad != null) {
            session.pageLoad.waiters().forEach(waiter -> waiter.complete(
                    new ToolResult.Failure<>(
                            "history_page_superseded", "A newer history page was requested")));
        }
        long generation = ++session.windowGeneration;
        PageLoad load = new PageLoad(key, generation, new ArrayList<>(dev.openallay.util.Java8Collections.listOf(result)));
        session.pageLoad = load;
        session.pageState = GuideHistoryPageState.LOADING;
        session.pageFailure = null;
        publishWithoutSave();
        CompletableFuture<GuideHistoryPage> loading;
        try {
            loading = Objects.requireNonNull(history.page(request), "history page future");
        } catch (RuntimeException failure) {
            finishHistoryWindow(sessionId, load, null, failure);
            return;
        }
        loading.whenComplete((page, failure) -> dispatcher.execute(
                () -> finishHistoryWindow(sessionId, load, page, failure)));
    }

    private void finishHistoryWindow(
            String sessionId,
            PageLoad load,
            GuideHistoryPage page,
            Throwable failure) {
        if (disconnected) return;
        SessionState session = sessions.get(sessionId);
        if (session == null || session.pageLoad != load
                || session.windowGeneration != load.generation()) {
            return;
        }
        session.pageLoad = null;
        if (failure != null || page == null) {
            GuideFailure pageFailure = historyFailure(
                    failure == null
                            ? new GuideHistoryException(
                                    "history_page_failed", "History page returned no result")
                            : failure,
                    "history_page_failed");
            session.pageState = GuideHistoryPageState.FAILED;
            session.pageFailure = pageFailure;
            load.waiters().forEach(waiter -> waiter.complete(
                    new ToolResult.Failure<>(pageFailure.code(), pageFailure.message())));
            publishWithoutSave();
            return;
        }
        mergePage(session, load.key().direction(), load.key().count(), page);
        session.pageState = GuideHistoryPageState.IDLE;
        session.pageFailure = null;
        load.waiters().forEach(waiter -> waiter.complete(new ToolResult.Success<>(page)));
        publishWithoutSave();
    }

    private void mergePage(
            SessionState session,
            GuideHistoryPageRequest.Direction direction,
            int neighborhoodCount,
            GuideHistoryPage page) {
        List<GuideRequestSnapshot> active = dev.openallay.util.Java8Collections.toList(session.requests.stream()
                .filter(request -> !request.terminal()));
        List<GuideRequestSnapshot> durable = session.requests.stream()
                .filter(GuideRequestSnapshot::terminal).collect(java.util.stream.Collectors.toCollection(
                        ArrayList::new));
        List<GuideRequestSnapshot> incoming = page.requests();
        if (direction == GuideHistoryPageRequest.Direction.NEWEST) {
            durable.clear();
            durable.addAll(incoming);
        } else if (direction == GuideHistoryPageRequest.Direction.BEFORE) {
            durable.addAll(0, incoming);
        } else {
            durable.addAll(incoming);
        }
        LinkedHashMap<UUID, GuideRequestSnapshot> previous = new LinkedHashMap<>();
        session.requests.forEach(request -> previous.put(request.requestId(), request));
        LinkedHashMap<UUID, GuideRequestSnapshot> unique = new LinkedHashMap<>();
        durable.forEach(request -> unique.put(request.requestId(), request));
        List<GuideRequestSnapshot> bounded = new ArrayList<>(unique.values());
        int maximumNeighborhood = Math.addExact(neighborhoodCount, page.requests().size());
        if (bounded.size() > maximumNeighborhood) {
            bounded = direction == GuideHistoryPageRequest.Direction.AFTER
                    ? new ArrayList<>(bounded.subList(
                            bounded.size() - maximumNeighborhood, bounded.size()))
                    : new ArrayList<>(bounded.subList(0, maximumNeighborhood));
        }
        unique.clear();
        bounded.forEach(request -> unique.put(request.requestId(), request));
        active.forEach(request -> unique.put(request.requestId(), request));
        session.requests.clear();
        session.requests.addAll(unique.values());
        previous.keySet().stream()
                .filter(requestId -> !unique.containsKey(requestId) && !session.usageCarriers.containsKey(requestId))
                .forEach(requestSessions::remove);
        page.requests().forEach(request -> requestSessions.put(request.requestId(), session.id));
        if (page.first() != null) {
            long sequence = page.first().sequence();
            for (GuideRequestSnapshot request : page.requests()) {
                session.requestSequences.put(request.requestId(), sequence++);
            }
        }
        List<GuideRequestSnapshot> loadedDurable = dev.openallay.util.Java8Collections.toList(session.requests.stream()
                .filter(GuideRequestSnapshot::terminal));
        session.firstLoaded = loadedDurable.isEmpty() ? null : cursor(
                session, loadedDurable.get(0));
        session.lastLoaded = loadedDurable.isEmpty() ? null : cursor(
                session, loadedDurable.get(loadedDurable.size() - 1));
        session.hasEarlier = session.firstLoaded != null && session.firstAvailable != null
                && session.firstLoaded.sequence() > session.firstAvailable.sequence();
        session.hasLater = session.lastLoaded != null && session.lastAvailable != null
                && session.lastLoaded.sequence() < session.lastAvailable.sequence();
        registerPageBaseline(page);
    }

    private static GuideHistoryCursor cursor(
            SessionState session, GuideRequestSnapshot request) {
        Long sequence = session.requestSequences.get(request.requestId());
        return sequence == null ? null : new GuideHistoryCursor(sequence, request.requestId());
    }

    private void registerPageBaseline(GuideHistoryPage page) {
        for (GuideRequestSnapshot original : page.requests()) {
            GuideRequestSnapshot request = durableRequest(original);
            for (DurableProjection projection : dev.openallay.util.Java8Collections.listOf(durableProjection, capturedProjection)) {
                projection.requests.putIfAbsent(request.requestId(), rowProjection(request));
                for (GuideTimelineEntry entry : request.timeline()) {
                    projection.timeline.putIfAbsent(
                            new TimelineKey(request.requestId(), entry.ordinal()), entry);
                }
                projection.sources.putIfAbsent(request.requestId(), dev.openallay.util.Java8Collections.listCopyOf(request.sources()));
            }
        }
    }

    private GuideHistoryWindowSnapshot historyWindow(SessionState session) {
        if (history == null) {
            return GuideHistoryWindowSnapshot.disabled(session.requests.size());
        }
        return new GuideHistoryWindowSnapshot(
                session.totalRequests,
                session.firstAvailable,
                session.lastAvailable,
                session.firstLoaded,
                session.lastLoaded,
                session.hasEarlier,
                session.hasLater,
                session.pageState,
                session.windowGeneration,
                session.pageFailure);
    }

    private void scheduleHistorySave() {
        if (history == null || !allowHistoryWrites || historyDeletionPending || disconnected) {
            return;
        }
        GuideHistoryCommit captured;
        try {
            captured = incrementalCommit();
        } catch (RuntimeException failure) {
            persistence = new GuidePersistenceSnapshot(
                    GuidePersistenceSnapshot.State.UNAVAILABLE,
                    persistence.submittedGeneration(), persistence.committedGeneration(),
                    historyFailure(failure, "history_write_failed"));
            return;
        }
        if (captured != null) {
            queuedHistoryMutations.addAll(captured.mutations());
            persistence = writePersistence(
                    persistence.submittedGeneration() + 1, persistence.committedGeneration());
        } else if (inFlightHistoryWrite == null && !queuedHistoryMutations.isEmpty()) {
            // A later publish retries retained failed data, even when the live rows are unchanged.
            persistence = writePersistence(
                    persistence.submittedGeneration() + 1, persistence.committedGeneration());
        }
        startQueuedHistoryWrite();
    }

    private GuidePersistenceSnapshot writePersistence(long submitted, long committed) {
        return new GuidePersistenceSnapshot(
                persistence.failure() == null
                        ? GuidePersistenceSnapshot.State.SAVING
                        : GuidePersistenceSnapshot.State.UNAVAILABLE,
                submitted, committed, persistence.failure());
    }

    private void startQueuedHistoryWrite() {
        if (inFlightHistoryWrite != null || queuedHistoryMutations.isEmpty()) {
            return;
        }
        HistoryWrite batch = new HistoryWrite(persistence.submittedGeneration(),
                new GuideHistoryCommit(historyScope, queuedHistoryMutations.take()));
        inFlightHistoryWrite = batch;
        try {
            CompletableFuture<Void> write = Objects.requireNonNull(
                    history.commit(batch.commit()), "history commit future");
            write.whenComplete((ignored, failure) ->
                    dispatcher.execute(() -> finishHistorySave(batch, failure)));
        } catch (RuntimeException failure) {
            finishHistorySave(batch, failure);
        }
    }

    private CompletableFuture<GuideHistoryContextSeed> readHistoryContext(
            GuideHistoryContextRequest request, UUID requestId) {
        return awaitHistoryWrites(requestId).thenCompose(ignored -> history.context(request));
    }

    private CompletableFuture<Void> drainHistoryWrites() {
        return awaitHistoryWrites(null);
    }

    private CompletableFuture<Void> awaitHistoryWrites(UUID requestId) {
        long generation = persistence.submittedGeneration();
        if (generation == persistence.committedGeneration()
                && inFlightHistoryWrite == null && queuedHistoryMutations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (inFlightHistoryWrite == null && persistence.failure() != null) {
            return dev.openallay.util.Java8Futures.failedFuture(new GuideHistoryException(
                    persistence.failure().code(), persistence.failure().message()));
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        historyWriteBarriers.add(new HistoryWriteBarrier(generation, requestId, completion));
        startQueuedHistoryWrite();
        return completion;
    }

    private void cancelHistoryContextBarrier(UUID requestId) {
        List<HistoryWriteBarrier> cancelled = dev.openallay.util.Java8Collections.toList(historyWriteBarriers.stream()
                .filter(barrier -> requestId.equals(barrier.requestId())));
        historyWriteBarriers.removeAll(cancelled);
        for (HistoryWriteBarrier barrier : cancelled) {
            barrier.completion().completeExceptionally(new GuideHistoryException(
                    "history_context_cancelled", "Durable guide context preparation was cancelled"));
        }
    }

    private void finishHistoryWriteBarriers(Throwable failure, boolean newerWork) {
        List<HistoryWriteBarrier> completed = dev.openallay.util.Java8Collections.toList(historyWriteBarriers.stream()
                .filter(barrier -> failure == null
                        ? barrier.generation() <= persistence.committedGeneration()
                        : barrier.requestId() != null || !newerWork));
        historyWriteBarriers.removeAll(completed);
        for (HistoryWriteBarrier barrier : completed) {
            if (failure == null) barrier.completion().complete(null);
            else barrier.completion().completeExceptionally(failure);
        }
    }

    private GuideHistoryCommit incrementalCommit() {
        List<GuideHistoryMutation> mutations = new ArrayList<>(pendingHistoryMutations);
        int sessionOrdinal = 0;
        for (SessionState session : sessions.values()) {
            SessionProjection projected = new SessionProjection(
                    sessionOrdinal++, session.modelSelection);
            if (!projected.equals(capturedProjection.sessions.get(session.id))) {
                mutations.add(new GuideHistoryMutation.UpsertSession(
                        session.id, projected.ordinal(), session.modelSelection));
            }
            GuideUsageSnapshot controls = session.usage.controlSnapshot();
            if (!controls.equals(capturedProjection.controlUsage.get(session.id))) {
                mutations.add(new GuideHistoryMutation.UpsertSessionUsage(session.id, controls));
            }
            for (GuideRequestSnapshot original : session.requests) {
                Long sequence = session.requestSequences.get(original.requestId());
                if (sequence == null) continue;
                GuideRequestSnapshot request = durableRequest(original);
                GuideRequestSnapshot row = rowProjection(request);
                if (!row.equals(capturedProjection.requests.get(request.requestId()))) {
                    mutations.add(new GuideHistoryMutation.UpsertRequest(sequence, row));
                }
                for (GuideTimelineEntry entry : request.timeline()) {
                    TimelineKey key = new TimelineKey(request.requestId(), entry.ordinal());
                    if (!entry.equals(capturedProjection.timeline.get(key))) {
                        mutations.add(new GuideHistoryMutation.UpsertTimelineEntry(
                                request.requestId(), entry));
                    }
                }
                if (!request.sources().equals(capturedProjection.sources.get(request.requestId()))) {
                    mutations.add(new GuideHistoryMutation.ReplaceRequestSources(
                            request.requestId(), request.sources()));
                }
            }
            for (int index = 0; index < session.messages.size(); index++) {
                int ordinal = Math.addExact(session.messageOrdinalBase, index);
                MessageKey key = new MessageKey(session.id, ordinal);
                GuideMessage message = session.messages.get(index);
                if (!message.equals(capturedProjection.messages.get(key))) {
                    mutations.add(new GuideHistoryMutation.UpsertMessage(
                            session.id, ordinal, message));
                }
            }
            for (int ordinal = 0; ordinal < session.checkpoints.size(); ordinal++) {
                ContextCheckpoint checkpoint = session.checkpoints.get(ordinal);
                if (!checkpoint.equals(capturedProjection.checkpointPayloads.get(checkpoint.checkpointId()))) {
                    mutations.add(new GuideHistoryMutation.AppendCheckpoint(session.id, checkpoint));
                }
            }
        }
        if (mutations.isEmpty() && selectedSession.equals(capturedProjection.selectedSession)) {
            return null;
        }
        List<GuideHistoryMutation> boundaries = dev.openallay.util.Java8Collections.toList(mutations.stream()
                .filter(value -> value instanceof GuideHistoryMutation.CaptureRequestBoundary));
        mutations.removeAll(boundaries);
        mutations.addAll(boundaries);
        mutations.add(0, new GuideHistoryMutation.UpsertPartition(
                selectedSession, clock.instant()));
        GuideHistoryCommit commit = new GuideHistoryCommit(historyScope, mutations);
        capturedProjection.acknowledge(commit.mutations());
        pendingHistoryMutations.clear();
        return commit;
    }

    private static GuideRequestSnapshot rowProjection(GuideRequestSnapshot request) {
        return new GuideRequestSnapshot(
                request.requestId(), request.sessionId(), request.topology(), request.userMessage(),
                dev.openallay.util.Java8Collections.listOf(), request.status(), dev.openallay.util.Java8Collections.listOf(), request.usage(),
                request.retryAfterMillis(), request.failure(), request.createdAt(),
                request.updatedAt(), request.terminalAt(), request.modelSelection(),
                GuideRequestSnapshot.legacyProgress(request.status(), request.retryAfterMillis(),
                        request.createdAt(), request.updatedAt()),
                request.usageProjection(), request.usageOriginRequestId());
    }

    private void finishHistorySave(HistoryWrite batch, Throwable failure) {
        if (inFlightHistoryWrite != batch) {
            return;
        }
        inFlightHistoryWrite = null;
        boolean newerWork = !queuedHistoryMutations.isEmpty();
        if (failure == null) {
            durableProjection.acknowledge(batch.commit().mutations());
            queuedHistoryMutations.acknowledged();
            persistence = newerWork
                    ? writePersistence(persistence.submittedGeneration(), batch.generation())
                    : GuidePersistenceSnapshot.available(batch.generation());
        } else {
            // The failed batch still owns its bytes. Newer replacements may coalesce the same unit,
            // but never acknowledge it. Clear/delete barriers retain their original order.
            queuedHistoryMutations.prepend(batch.commit().mutations());
            persistence = new GuidePersistenceSnapshot(
                    GuidePersistenceSnapshot.State.UNAVAILABLE,
                    persistence.submittedGeneration(), persistence.committedGeneration(),
                    historyFailure(failure, "history_write_failed"));
        }
        if (!disconnected) publishWithoutSave();
        finishHistoryWriteBarriers(failure, newerWork);
        if (newerWork) startQueuedHistoryWrite();
    }

    private static GuideRequestSnapshot durableRequest(GuideRequestSnapshot request) {
        List<GuideTimelineEntry> timeline = dev.openallay.util.Java8Collections.toList(request.timeline().stream()
                .map(entry -> {
final class $oaPattern17_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Tool bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
return (($oaPattern17_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.Tool && (($oaPattern17_holder.bound = (GuideTimelineEntry.Tool) $oaPattern17_holder.value) != null))
                        ? new GuideTimelineEntry.Tool(
                                $oaPattern17_holder.bound.ordinal(),
                                new GuideToolActivity(
                                        $oaPattern17_holder.bound.activity().invocationId(),
                                        $oaPattern17_holder.bound.activity().index(),
                                        $oaPattern17_holder.bound.activity().toolId(),
                                        $oaPattern17_holder.bound.activity().status(),
                                        null,
                                        $oaPattern17_holder.bound.activity().invocation().restored(),
                                        null,
                                        $oaPattern17_holder.bound.activity().presentationMessages(),
                                        $oaPattern17_holder.bound.activity().sources()))
                        : entry;
}));
        return new GuideRequestSnapshot(
                request.requestId(),
                request.sessionId(),
                request.topology(),
                request.userMessage(),
                timeline,
                request.status(),
                request.sources(),
                request.usage(),
                request.retryAfterMillis(),
                request.failure(),
                request.createdAt(),
                request.updatedAt(),
                request.terminalAt(),
                request.modelSelection(),
                GuideRequestSnapshot.legacyProgress(request.status(), request.retryAfterMillis(),
                        request.createdAt(), request.updatedAt()),
                request.usageProjection(), request.usageOriginRequestId());
    }

    private <T> boolean rejectStateChange(CompletableFuture<ToolResult<T>> result) {
        if (closing || disconnected) {
            result.complete(new ToolResult.Failure<>("guide_disconnected", "This Guide connection is closing"));
            return true;
        }
        if (historyDeletionPending) {
            result.complete(new ToolResult.Failure<>(
                    "history_delete_busy", "Guide history is busy"));
            return true;
        }
        if (persistence.state() != GuidePersistenceSnapshot.State.LOADING) {
            return false;
        }
        result.complete(new ToolResult.Failure<>(
                "history_loading", "Durable guide history is still loading"));
        return true;
    }

    private enum HistoryAdministrationKind {
        PARTITION,
        ACTOR,
        DATABASE
    }

    private static GuidePersistenceSnapshot unavailable(Throwable failure, String fallbackCode) {
        return new GuidePersistenceSnapshot(
                GuidePersistenceSnapshot.State.UNAVAILABLE,
                0,
                0,
                historyFailure(failure, fallbackCode));
    }

    private static GuideFailure historyFailure(Throwable failure, String fallbackCode) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        final class $oaPattern18_Holder { java.lang.Throwable value; GuideHistoryException bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = current) instanceof dev.openallay.guide.history.GuideHistoryException && (($oaPattern18_holder.bound = (GuideHistoryException) $oaPattern18_holder.value) != null))) {
            return new GuideFailure(
                    $oaPattern18_holder.bound.code(),
                    $oaPattern18_holder.bound.getMessage() == null
                            ? "Durable guide history is unavailable"
                            : $oaPattern18_holder.bound.getMessage());
        }
        return new GuideFailure(fallbackCode, "Durable guide history is unavailable");
    }

    private GuideModelSelection defaultClientSelection() {
        return GuideModelSelection.client(local == null ? "default" : local.defaultProfileId());
    }

    private GuideFailure selectionFailure(GuideModelSelection selection) {
        if (selection.kind() == GuideModelSelection.Kind.SERVER) {
            return remote.serverModelAvailable()
                    ? null
                    : new GuideFailure(
                            "capability_unavailable",
                            "The connected server does not provide a model");
        }
        if (local == null) {
            return new GuideFailure(
                    "model_not_configured", "No usable client model is configured");
        }
        GuideClientModelProfile profile = local.profile(selection.profileId()).orElse(null);
        if (profile == null) {
            return new GuideFailure(
                    "model_not_configured", "The selected client model profile does not exist");
        }
        return profile.available() ? null : profile.failure();
    }

    private static void select(SessionState session, GuideModelSelection selection) {
        session.modelSelection = selection;
        if (selection.kind() == GuideModelSelection.Kind.CLIENT) {
            session.lastClientProfileId = selection.profileId();
        }
    }

    private static boolean validSession(String value) {
        return value != null && value.matches("[a-zA-Z0-9_.-]+");
    }

    GuideHistoryScope historyScope() {
        return historyScope;
    }

    private static String message(Throwable failure) {
        Throwable current = unwrap(failure);
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class SessionState {
        private final String id;
        private UUID presentationOwner = UUID.randomUUID();
        private GuideUsageTracker usage = new GuideUsageTracker();
        private final List<GuideMessage> messages = new ArrayList<>();
        // Unloaded inherited durable messages own earlier ordinals.
        private int messageOrdinalBase;
        private final List<GuideRequestSnapshot> requests = new ArrayList<>();
        private final Map<UUID, GuidePendingMessage> pending = new LinkedHashMap<>();
        private final Map<UUID, UUID> pendingReceipts = new LinkedHashMap<>();
        private final Map<UUID, Long> pendingOrder = new LinkedHashMap<>();
        private long nextPendingOrder;
        private final Set<UUID> endpointRequests = new java.util.HashSet<>();
        private UUID workingRequest;
        private boolean releasingRequest;
        private String imageOwner = UUID.randomUUID().toString();
        private CompletableFuture<Void> imageCustody = CompletableFuture.completedFuture(null);
        private ManualCompaction manualCompaction;
        private final Map<UUID, UUID> requestReceipts = new LinkedHashMap<>();
        private final Map<UUID, Long> admissions = new LinkedHashMap<>();
        private final Map<UUID, Runnable> readyAdmissions = new LinkedHashMap<>();
        private boolean drainingAdmissions;
        private long queueGeneration;
        private boolean drainingPending;
        private final List<ContextCheckpoint> checkpoints = new ArrayList<>();
        private List<dev.openallay.model.ModelMessage> modelContext = dev.openallay.util.Java8Collections.listOf();
        private final Map<UUID, List<dev.openallay.model.ModelMessage>> originalContext =
                new LinkedHashMap<>();
        private final Map<UUID, GuideHistoryMutation.CaptureRequestBoundary> forkBoundaries = new LinkedHashMap<>();
        private final Set<UUID> unresolvedForkContext = new java.util.HashSet<>();
        private long forkEpoch;
        private GuideModelSelection modelSelection;
        private String lastClientProfileId;
        private long totalRequests;
        private long nextRequestSequence;
        private final Map<UUID, Long> requestSequences = new LinkedHashMap<>();
        // Only numeric projections of visibly cancelled in-flight owners; no retained bodies.
        private final Map<UUID, UsageCarrier> usageCarriers = new LinkedHashMap<>();
        private GuideHistoryCursor firstAvailable;
        private GuideHistoryCursor lastAvailable;
        private GuideHistoryCursor firstLoaded;
        private GuideHistoryCursor lastLoaded;
        private boolean hasEarlier;
        private boolean hasLater;
        private GuideHistoryPageState pageState = GuideHistoryPageState.IDLE;
        private GuideFailure pageFailure;
        private long windowGeneration;
        private PageLoad pageLoad;
        private long contextGeneration;
        private UUID preparingContextRequest;

        private SessionState(String id, GuideModelSelection modelSelection) {
            if (!validSession(id)) {
                throw new IllegalArgumentException("invalid sessionId");
            }
            this.id = id;
            this.modelSelection = Objects.requireNonNull(modelSelection, "modelSelection");
            this.lastClientProfileId = modelSelection.kind() == GuideModelSelection.Kind.CLIENT
                    ? modelSelection.profileId() : "default";
        }
    }

    @dev.openallay.value.ValueType(CancelledFinalization.ValueSchemaProvider.class)
private static final class CancelledFinalization {
    private final String sessionId;
    private final long sequence;
    private final List<ContextCheckpoint> checkpoints;
    private CancelledFinalization(String sessionId, long sequence, List<ContextCheckpoint> checkpoints) {

            checkpoints = dev.openallay.util.Java8Collections.listCopyOf(checkpoints);

        this.sessionId = sessionId;
        this.sequence = sequence;
        this.checkpoints = checkpoints;
    }
    public String sessionId() { return sessionId; }
    public long sequence() { return sequence; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CancelledFinalization)) return false;
        CancelledFinalization that = (CancelledFinalization) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && sequence == that.sequence && java.util.Objects.equals(checkpoints, that.checkpoints);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Long.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        return hash;
    }
    @Override public String toString() { return "CancelledFinalization[sessionId=" + sessionId + ", sequence=" + sequence + ", checkpoints=" + checkpoints + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CancelledFinalization> schema() {
            return new dev.openallay.value.ValueSchema<>(CancelledFinalization.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CancelledFinalization>>asList(new dev.openallay.value.ValueSchema.Component<>(CancelledFinalization.class, "sessionId", CancelledFinalization::sessionId), new dev.openallay.value.ValueSchema.Component<>(CancelledFinalization.class, "sequence", CancelledFinalization::sequence), new dev.openallay.value.ValueSchema.Component<>(CancelledFinalization.class, "checkpoints", CancelledFinalization::checkpoints)), arguments -> new CancelledFinalization((String) arguments[0], (Long) arguments[1], (List) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(UsageCarrier.ValueSchemaProvider.class)
private static final class UsageCarrier {
    private final long sequence;
    private final GuideUsageSnapshot usage;
    private UsageCarrier(long sequence, GuideUsageSnapshot usage) {
        this.sequence = sequence;
        this.usage = usage;
    }
    public long sequence() { return sequence; }
    public GuideUsageSnapshot usage() { return usage; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UsageCarrier)) return false;
        UsageCarrier that = (UsageCarrier) other;
        return sequence == that.sequence && java.util.Objects.equals(usage, that.usage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        return hash;
    }
    @Override public String toString() { return "UsageCarrier[sequence=" + sequence + ", usage=" + usage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UsageCarrier> schema() {
            return new dev.openallay.value.ValueSchema<>(UsageCarrier.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UsageCarrier>>asList(new dev.openallay.value.ValueSchema.Component<>(UsageCarrier.class, "sequence", UsageCarrier::sequence), new dev.openallay.value.ValueSchema.Component<>(UsageCarrier.class, "usage", UsageCarrier::usage)), arguments -> new UsageCarrier((Long) arguments[0], (GuideUsageSnapshot) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(PageKey.ValueSchemaProvider.class)
private static final class PageKey {
    private final GuideHistoryPageRequest.Direction direction;
    private final GuideHistoryCursor cursor;
    private final int count;
    private PageKey(GuideHistoryPageRequest.Direction direction, GuideHistoryCursor cursor, int count) {
        this.direction = direction;
        this.cursor = cursor;
        this.count = count;
    }
    public GuideHistoryPageRequest.Direction direction() { return direction; }
    public GuideHistoryCursor cursor() { return cursor; }
    public int count() { return count; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PageKey)) return false;
        PageKey that = (PageKey) other;
        return java.util.Objects.equals(direction, that.direction) && java.util.Objects.equals(cursor, that.cursor) && count == that.count;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(direction);
        hash = 31 * hash + java.util.Objects.hashCode(cursor);
        hash = 31 * hash + Integer.hashCode(count);
        return hash;
    }
    @Override public String toString() { return "PageKey[direction=" + direction + ", cursor=" + cursor + ", count=" + count + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PageKey> schema() {
            return new dev.openallay.value.ValueSchema<>(PageKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PageKey>>asList(new dev.openallay.value.ValueSchema.Component<>(PageKey.class, "direction", PageKey::direction), new dev.openallay.value.ValueSchema.Component<>(PageKey.class, "cursor", PageKey::cursor), new dev.openallay.value.ValueSchema.Component<>(PageKey.class, "count", PageKey::count)), arguments -> new PageKey((GuideHistoryPageRequest.Direction) arguments[0], (GuideHistoryCursor) arguments[1], (Integer) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(PageLoad.ValueSchemaProvider.class)
private static final class PageLoad {
    private final PageKey key;
    private final long generation;
    private final List<CompletableFuture<ToolResult<GuideHistoryPage>>> waiters;
    private PageLoad(PageKey key, long generation, List<CompletableFuture<ToolResult<GuideHistoryPage>>> waiters) {
        this.key = key;
        this.generation = generation;
        this.waiters = waiters;
    }
    public PageKey key() { return key; }
    public long generation() { return generation; }
    public List<CompletableFuture<ToolResult<GuideHistoryPage>>> waiters() { return waiters; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PageLoad)) return false;
        PageLoad that = (PageLoad) other;
        return java.util.Objects.equals(key, that.key) && generation == that.generation && java.util.Objects.equals(waiters, that.waiters);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(waiters);
        return hash;
    }
    @Override public String toString() { return "PageLoad[key=" + key + ", generation=" + generation + ", waiters=" + waiters + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PageLoad> schema() {
            return new dev.openallay.value.ValueSchema<>(PageLoad.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PageLoad>>asList(new dev.openallay.value.ValueSchema.Component<>(PageLoad.class, "key", PageLoad::key), new dev.openallay.value.ValueSchema.Component<>(PageLoad.class, "generation", PageLoad::generation), new dev.openallay.value.ValueSchema.Component<>(PageLoad.class, "waiters", PageLoad::waiters)), arguments -> new PageLoad((PageKey) arguments[0], (Long) arguments[1], (List) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(SessionProjection.ValueSchemaProvider.class)
private static final class SessionProjection {
    private final int ordinal;
    private final GuideModelSelection selection;
    private SessionProjection(int ordinal, GuideModelSelection selection) {
        this.ordinal = ordinal;
        this.selection = selection;
    }
    public int ordinal() { return ordinal; }
    public GuideModelSelection selection() { return selection; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionProjection)) return false;
        SessionProjection that = (SessionProjection) other;
        return ordinal == that.ordinal && java.util.Objects.equals(selection, that.selection);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(selection);
        return hash;
    }
    @Override public String toString() { return "SessionProjection[ordinal=" + ordinal + ", selection=" + selection + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SessionProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(SessionProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SessionProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(SessionProjection.class, "ordinal", SessionProjection::ordinal), new dev.openallay.value.ValueSchema.Component<>(SessionProjection.class, "selection", SessionProjection::selection)), arguments -> new SessionProjection((Integer) arguments[0], (GuideModelSelection) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(TimelineKey.ValueSchemaProvider.class)
private static final class TimelineKey {
    private final UUID requestId;
    private final int ordinal;
    private TimelineKey(UUID requestId, int ordinal) {
        this.requestId = requestId;
        this.ordinal = ordinal;
    }
    public UUID requestId() { return requestId; }
    public int ordinal() { return ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TimelineKey)) return false;
        TimelineKey that = (TimelineKey) other;
        return java.util.Objects.equals(requestId, that.requestId) && ordinal == that.ordinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        return hash;
    }
    @Override public String toString() { return "TimelineKey[requestId=" + requestId + ", ordinal=" + ordinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TimelineKey> schema() {
            return new dev.openallay.value.ValueSchema<>(TimelineKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TimelineKey>>asList(new dev.openallay.value.ValueSchema.Component<>(TimelineKey.class, "requestId", TimelineKey::requestId), new dev.openallay.value.ValueSchema.Component<>(TimelineKey.class, "ordinal", TimelineKey::ordinal)), arguments -> new TimelineKey((UUID) arguments[0], (Integer) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(MessageKey.ValueSchemaProvider.class)
private static final class MessageKey {
    private final String sessionId;
    private final int ordinal;
    private MessageKey(String sessionId, int ordinal) {
        this.sessionId = sessionId;
        this.ordinal = ordinal;
    }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MessageKey)) return false;
        MessageKey that = (MessageKey) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        return hash;
    }
    @Override public String toString() { return "MessageKey[sessionId=" + sessionId + ", ordinal=" + ordinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MessageKey> schema() {
            return new dev.openallay.value.ValueSchema<>(MessageKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MessageKey>>asList(new dev.openallay.value.ValueSchema.Component<>(MessageKey.class, "sessionId", MessageKey::sessionId), new dev.openallay.value.ValueSchema.Component<>(MessageKey.class, "ordinal", MessageKey::ordinal)), arguments -> new MessageKey((String) arguments[0], (Integer) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(CheckpointKey.ValueSchemaProvider.class)
private static final class CheckpointKey {
    private final String sessionId;
    private final int ordinal;
    private CheckpointKey(String sessionId, int ordinal) {
        this.sessionId = sessionId;
        this.ordinal = ordinal;
    }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CheckpointKey)) return false;
        CheckpointKey that = (CheckpointKey) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        return hash;
    }
    @Override public String toString() { return "CheckpointKey[sessionId=" + sessionId + ", ordinal=" + ordinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CheckpointKey> schema() {
            return new dev.openallay.value.ValueSchema<>(CheckpointKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CheckpointKey>>asList(new dev.openallay.value.ValueSchema.Component<>(CheckpointKey.class, "sessionId", CheckpointKey::sessionId), new dev.openallay.value.ValueSchema.Component<>(CheckpointKey.class, "ordinal", CheckpointKey::ordinal)), arguments -> new CheckpointKey((String) arguments[0], (Integer) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(HistoryWrite.ValueSchemaProvider.class)
private static final class HistoryWrite {
    private final long generation;
    private final GuideHistoryCommit commit;
    private HistoryWrite(long generation, GuideHistoryCommit commit) {
        this.generation = generation;
        this.commit = commit;
    }
    public long generation() { return generation; }
    public GuideHistoryCommit commit() { return commit; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HistoryWrite)) return false;
        HistoryWrite that = (HistoryWrite) other;
        return generation == that.generation && java.util.Objects.equals(commit, that.commit);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(commit);
        return hash;
    }
    @Override public String toString() { return "HistoryWrite[generation=" + generation + ", commit=" + commit + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<HistoryWrite> schema() {
            return new dev.openallay.value.ValueSchema<>(HistoryWrite.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<HistoryWrite>>asList(new dev.openallay.value.ValueSchema.Component<>(HistoryWrite.class, "generation", HistoryWrite::generation), new dev.openallay.value.ValueSchema.Component<>(HistoryWrite.class, "commit", HistoryWrite::commit)), arguments -> new HistoryWrite((Long) arguments[0], (GuideHistoryCommit) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(HistoryWriteBarrier.ValueSchemaProvider.class)
private static final class HistoryWriteBarrier {
    private final long generation;
    private final UUID requestId;
    private final CompletableFuture<Void> completion;
    private HistoryWriteBarrier(long generation, UUID requestId, CompletableFuture<Void> completion) {
        this.generation = generation;
        this.requestId = requestId;
        this.completion = completion;
    }
    public long generation() { return generation; }
    public UUID requestId() { return requestId; }
    public CompletableFuture<Void> completion() { return completion; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HistoryWriteBarrier)) return false;
        HistoryWriteBarrier that = (HistoryWriteBarrier) other;
        return generation == that.generation && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(completion, that.completion);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(completion);
        return hash;
    }
    @Override public String toString() { return "HistoryWriteBarrier[generation=" + generation + ", requestId=" + requestId + ", completion=" + completion + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<HistoryWriteBarrier> schema() {
            return new dev.openallay.value.ValueSchema<>(HistoryWriteBarrier.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<HistoryWriteBarrier>>asList(new dev.openallay.value.ValueSchema.Component<>(HistoryWriteBarrier.class, "generation", HistoryWriteBarrier::generation), new dev.openallay.value.ValueSchema.Component<>(HistoryWriteBarrier.class, "requestId", HistoryWriteBarrier::requestId), new dev.openallay.value.ValueSchema.Component<>(HistoryWriteBarrier.class, "completion", HistoryWriteBarrier::completion)), arguments -> new HistoryWriteBarrier((Long) arguments[0], (UUID) arguments[1], (CompletableFuture) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(MutationKey.ValueSchemaProvider.class)
private static final class MutationKey {
    private final Class<?> kind;
    private final Object owner;
    private final int ordinal;
    private MutationKey(Class<?> kind, Object owner, int ordinal) {
        this.kind = kind;
        this.owner = owner;
        this.ordinal = ordinal;
    }
    public Class<?> kind() { return kind; }
    public Object owner() { return owner; }
    public int ordinal() { return ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MutationKey)) return false;
        MutationKey that = (MutationKey) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(owner, that.owner) && ordinal == that.ordinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + Integer.hashCode(ordinal);
        return hash;
    }
    @Override public String toString() { return "MutationKey[kind=" + kind + ", owner=" + owner + ", ordinal=" + ordinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MutationKey> schema() {
            return new dev.openallay.value.ValueSchema<>(MutationKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MutationKey>>asList(new dev.openallay.value.ValueSchema.Component<>(MutationKey.class, "kind", MutationKey::kind), new dev.openallay.value.ValueSchema.Component<>(MutationKey.class, "owner", MutationKey::owner), new dev.openallay.value.ValueSchema.Component<>(MutationKey.class, "ordinal", MutationKey::ordinal)), arguments -> new MutationKey((Class) arguments[0], (Object) arguments[1], (Integer) arguments[2]));
        }
    }
}

    /** Latest unacknowledged value per unit, with clear/delete as ordered session barriers. */
    private final class HistoryMutationBuffer {
        private final LinkedHashMap<MutationKey, GuideHistoryMutation> mutations = new LinkedHashMap<>();
        // Only rows not yet acknowledged need a separate owner lookup for deletion barriers.
        private final Map<UUID, String> unacknowledgedOwners = new LinkedHashMap<>();

        private boolean isEmpty() { return mutations.isEmpty(); }

        private void clear() {
            mutations.clear();
            unacknowledgedOwners.clear();
        }

        private void addAll(List<GuideHistoryMutation> changes) {
            for (GuideHistoryMutation change : changes) {
                final class $oaPattern19_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertRequest bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = change) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertRequest && (($oaPattern19_holder.bound = (GuideHistoryMutation.UpsertRequest) $oaPattern19_holder.value) != null))) {
                    unacknowledgedOwners.put($oaPattern19_holder.bound.request().requestId(), $oaPattern19_holder.bound.request().sessionId());
                }
            }
            for (GuideHistoryMutation change : changes) {
                final class $oaPattern20_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.DeleteSession bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = change) instanceof dev.openallay.guide.history.GuideHistoryMutation.DeleteSession && (($oaPattern20_holder.bound = (GuideHistoryMutation.DeleteSession) $oaPattern20_holder.value) != null))) {
                    discardSession($oaPattern20_holder.bound.sessionId(), true);
                } else {
final class $oaPattern21_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ClearSession bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = change) instanceof dev.openallay.guide.history.GuideHistoryMutation.ClearSession && (($oaPattern21_holder.bound = (GuideHistoryMutation.ClearSession) $oaPattern21_holder.value) != null))) {
                    discardSession($oaPattern21_holder.bound.sessionId(), false);
                }
}
                mutations.put(key(change), change);
            }
        }

        private void discardSession(String sessionId, boolean delete) {
            mutations.entrySet().removeIf(entry -> {
                GuideHistoryMutation mutation = entry.getValue();
                if (!sessionId.equals(sessionOf(mutation))) return false;
                return delete || !(mutation instanceof GuideHistoryMutation.UpsertSession
                        || mutation instanceof GuideHistoryMutation.DeleteSession);
            });
        }

        private String sessionOf(GuideHistoryMutation mutation) {
            Objects.requireNonNull(mutation);
            final class $oaPattern22_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertSession bound; }
final $oaPattern22_Holder $oaPattern22_holder = new $oaPattern22_Holder();
if ((($oaPattern22_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertSession && (($oaPattern22_holder.bound = (GuideHistoryMutation.UpsertSession) $oaPattern22_holder.value) != null))) {
                return $oaPattern22_holder.bound.sessionId();
            } else {
final class $oaPattern23_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertSessionUsage bound; }
final $oaPattern23_Holder $oaPattern23_holder = new $oaPattern23_Holder();
if ((($oaPattern23_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertSessionUsage && (($oaPattern23_holder.bound = (GuideHistoryMutation.UpsertSessionUsage) $oaPattern23_holder.value) != null))) {
                return $oaPattern23_holder.bound.sessionId();
            } else {
final class $oaPattern24_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertMessage bound; }
final $oaPattern24_Holder $oaPattern24_holder = new $oaPattern24_Holder();
if ((($oaPattern24_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertMessage && (($oaPattern24_holder.bound = (GuideHistoryMutation.UpsertMessage) $oaPattern24_holder.value) != null))) {
                return $oaPattern24_holder.bound.sessionId();
            } else {
final class $oaPattern25_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceContext bound; }
final $oaPattern25_Holder $oaPattern25_holder = new $oaPattern25_Holder();
if ((($oaPattern25_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext && (($oaPattern25_holder.bound = (GuideHistoryMutation.ReplaceContext) $oaPattern25_holder.value) != null))) {
                return $oaPattern25_holder.bound.sessionId();
            } else {
final class $oaPattern26_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertCheckpoint bound; }
final $oaPattern26_Holder $oaPattern26_holder = new $oaPattern26_Holder();
if ((($oaPattern26_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertCheckpoint && (($oaPattern26_holder.bound = (GuideHistoryMutation.UpsertCheckpoint) $oaPattern26_holder.value) != null))) {
                return $oaPattern26_holder.bound.sessionId();
            } else {
final class $oaPattern27_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.AppendCheckpoint bound; }
final $oaPattern27_Holder $oaPattern27_holder = new $oaPattern27_Holder();
if ((($oaPattern27_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint && (($oaPattern27_holder.bound = (GuideHistoryMutation.AppendCheckpoint) $oaPattern27_holder.value) != null))) {
                return $oaPattern27_holder.bound.sessionId();
            } else {
final class $oaPattern28_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.DeleteSession bound; }
final $oaPattern28_Holder $oaPattern28_holder = new $oaPattern28_Holder();
if ((($oaPattern28_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.DeleteSession && (($oaPattern28_holder.bound = (GuideHistoryMutation.DeleteSession) $oaPattern28_holder.value) != null))) {
                return $oaPattern28_holder.bound.sessionId();
            } else {
final class $oaPattern29_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ClearSession bound; }
final $oaPattern29_Holder $oaPattern29_holder = new $oaPattern29_Holder();
if ((($oaPattern29_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ClearSession && (($oaPattern29_holder.bound = (GuideHistoryMutation.ClearSession) $oaPattern29_holder.value) != null))) {
                return $oaPattern29_holder.bound.sessionId();
            } else {
final class $oaPattern30_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertRequest bound; }
final $oaPattern30_Holder $oaPattern30_holder = new $oaPattern30_Holder();
if ((($oaPattern30_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertRequest && (($oaPattern30_holder.bound = (GuideHistoryMutation.UpsertRequest) $oaPattern30_holder.value) != null))) {
                return $oaPattern30_holder.bound.request().sessionId();
            } else {
final class $oaPattern31_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertTimelineEntry bound; }
final $oaPattern31_Holder $oaPattern31_holder = new $oaPattern31_Holder();
if ((($oaPattern31_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertTimelineEntry && (($oaPattern31_holder.bound = (GuideHistoryMutation.UpsertTimelineEntry) $oaPattern31_holder.value) != null))) {
                return sessionOf($oaPattern31_holder.bound.requestId());
            } else {
final class $oaPattern32_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceRequestSources bound; }
final $oaPattern32_Holder $oaPattern32_holder = new $oaPattern32_Holder();
if ((($oaPattern32_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestSources && (($oaPattern32_holder.bound = (GuideHistoryMutation.ReplaceRequestSources) $oaPattern32_holder.value) != null))) {
                return sessionOf($oaPattern32_holder.bound.requestId());
            } else {
final class $oaPattern33_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceRequestContext bound; }
final $oaPattern33_Holder $oaPattern33_holder = new $oaPattern33_Holder();
if ((($oaPattern33_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext && (($oaPattern33_holder.bound = (GuideHistoryMutation.ReplaceRequestContext) $oaPattern33_holder.value) != null))) {
                return sessionOf($oaPattern33_holder.bound.requestId());
            } else {
final class $oaPattern34_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.CaptureRequestBoundary bound; }
final $oaPattern34_Holder $oaPattern34_holder = new $oaPattern34_Holder();
if ((($oaPattern34_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.CaptureRequestBoundary && (($oaPattern34_holder.bound = (GuideHistoryMutation.CaptureRequestBoundary) $oaPattern34_holder.value) != null))) {
                return sessionOf($oaPattern34_holder.bound.requestId());
            } else {
final class $oaPattern35_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ForkSession bound; }
final $oaPattern35_Holder $oaPattern35_holder = new $oaPattern35_Holder();
if ((($oaPattern35_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ForkSession && (($oaPattern35_holder.bound = (GuideHistoryMutation.ForkSession) $oaPattern35_holder.value) != null))) {
                return $oaPattern35_holder.bound.sessionId();
            } else {
final class $oaPattern36_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertPartition bound; }
final $oaPattern36_Holder $oaPattern36_holder = new $oaPattern36_Holder();
if ((($oaPattern36_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertPartition && (($oaPattern36_holder.bound = (GuideHistoryMutation.UpsertPartition) $oaPattern36_holder.value) != null))) {
                return null;
            }
}
}
}
}
}
}
}
}
}
}
}
}
}
}
            throw new IncompatibleClassChangeError();
        }

        private String sessionOf(UUID requestId) {
            String owner = unacknowledgedOwners.get(requestId);
            if (owner != null) return owner;
            GuideRequestSnapshot row = capturedProjection.requests.get(requestId);
            if (row == null) row = durableProjection.requests.get(requestId);
            return row == null ? null : row.sessionId();
        }

        private MutationKey key(GuideHistoryMutation mutation) {
            Objects.requireNonNull(mutation);
            final class $oaPattern37_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertPartition bound; }
final $oaPattern37_Holder $oaPattern37_holder = new $oaPattern37_Holder();
if ((($oaPattern37_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertPartition && (($oaPattern37_holder.bound = (GuideHistoryMutation.UpsertPartition) $oaPattern37_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), "partition", 0);
            } else {
final class $oaPattern38_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertSession bound; }
final $oaPattern38_Holder $oaPattern38_holder = new $oaPattern38_Holder();
if ((($oaPattern38_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertSession && (($oaPattern38_holder.bound = (GuideHistoryMutation.UpsertSession) $oaPattern38_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern38_holder.bound.sessionId(), 0);
            } else {
final class $oaPattern39_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertSessionUsage bound; }
final $oaPattern39_Holder $oaPattern39_holder = new $oaPattern39_Holder();
if ((($oaPattern39_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertSessionUsage && (($oaPattern39_holder.bound = (GuideHistoryMutation.UpsertSessionUsage) $oaPattern39_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern39_holder.bound.sessionId(), 0);
            } else {
final class $oaPattern40_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertRequest bound; }
final $oaPattern40_Holder $oaPattern40_holder = new $oaPattern40_Holder();
if ((($oaPattern40_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertRequest && (($oaPattern40_holder.bound = (GuideHistoryMutation.UpsertRequest) $oaPattern40_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern40_holder.bound.request().requestId(), 0);
            } else {
final class $oaPattern41_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertMessage bound; }
final $oaPattern41_Holder $oaPattern41_holder = new $oaPattern41_Holder();
if ((($oaPattern41_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertMessage && (($oaPattern41_holder.bound = (GuideHistoryMutation.UpsertMessage) $oaPattern41_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern41_holder.bound.sessionId(), $oaPattern41_holder.bound.ordinal());
            } else {
final class $oaPattern42_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertTimelineEntry bound; }
final $oaPattern42_Holder $oaPattern42_holder = new $oaPattern42_Holder();
if ((($oaPattern42_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertTimelineEntry && (($oaPattern42_holder.bound = (GuideHistoryMutation.UpsertTimelineEntry) $oaPattern42_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern42_holder.bound.requestId(), $oaPattern42_holder.bound.entry().ordinal());
            } else {
final class $oaPattern43_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceRequestSources bound; }
final $oaPattern43_Holder $oaPattern43_holder = new $oaPattern43_Holder();
if ((($oaPattern43_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestSources && (($oaPattern43_holder.bound = (GuideHistoryMutation.ReplaceRequestSources) $oaPattern43_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern43_holder.bound.requestId(), 0);
            } else {
final class $oaPattern44_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceContext bound; }
final $oaPattern44_Holder $oaPattern44_holder = new $oaPattern44_Holder();
if ((($oaPattern44_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext && (($oaPattern44_holder.bound = (GuideHistoryMutation.ReplaceContext) $oaPattern44_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern44_holder.bound.sessionId(), 0);
            } else {
final class $oaPattern45_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceRequestContext bound; }
final $oaPattern45_Holder $oaPattern45_holder = new $oaPattern45_Holder();
if ((($oaPattern45_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext && (($oaPattern45_holder.bound = (GuideHistoryMutation.ReplaceRequestContext) $oaPattern45_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern45_holder.bound.requestId(), 0);
            } else {
final class $oaPattern46_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.CaptureRequestBoundary bound; }
final $oaPattern46_Holder $oaPattern46_holder = new $oaPattern46_Holder();
if ((($oaPattern46_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.CaptureRequestBoundary && (($oaPattern46_holder.bound = (GuideHistoryMutation.CaptureRequestBoundary) $oaPattern46_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern46_holder.bound.requestId(), 0);
            } else {
final class $oaPattern47_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ForkSession bound; }
final $oaPattern47_Holder $oaPattern47_holder = new $oaPattern47_Holder();
if ((($oaPattern47_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ForkSession && (($oaPattern47_holder.bound = (GuideHistoryMutation.ForkSession) $oaPattern47_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern47_holder.bound.sessionId(), 0);
            } else {
final class $oaPattern48_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.AppendCheckpoint bound; }
final $oaPattern48_Holder $oaPattern48_holder = new $oaPattern48_Holder();
if ((($oaPattern48_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint && (($oaPattern48_holder.bound = (GuideHistoryMutation.AppendCheckpoint) $oaPattern48_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern48_holder.bound.checkpoint().checkpointId(), 0);
            } else {
final class $oaPattern49_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertCheckpoint bound; }
final $oaPattern49_Holder $oaPattern49_holder = new $oaPattern49_Holder();
if ((($oaPattern49_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertCheckpoint && (($oaPattern49_holder.bound = (GuideHistoryMutation.UpsertCheckpoint) $oaPattern49_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern49_holder.bound.sessionId(), $oaPattern49_holder.bound.ordinal());
            } else {
final class $oaPattern50_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.DeleteSession bound; }
final $oaPattern50_Holder $oaPattern50_holder = new $oaPattern50_Holder();
if ((($oaPattern50_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.DeleteSession && (($oaPattern50_holder.bound = (GuideHistoryMutation.DeleteSession) $oaPattern50_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern50_holder.bound.sessionId(), 0);
            } else {
final class $oaPattern51_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ClearSession bound; }
final $oaPattern51_Holder $oaPattern51_holder = new $oaPattern51_Holder();
if ((($oaPattern51_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ClearSession && (($oaPattern51_holder.bound = (GuideHistoryMutation.ClearSession) $oaPattern51_holder.value) != null))) {
                return new MutationKey(mutation.getClass(), $oaPattern51_holder.bound.sessionId(), 0);
            }
}
}
}
}
}
}
}
}
}
}
}
}
}
}
            throw new IncompatibleClassChangeError();
        }

        private List<GuideHistoryMutation> take() {
            List<GuideHistoryMutation> batch = dev.openallay.util.Java8Collections.listCopyOf(mutations.values());
            mutations.clear();
            return batch;
        }

        private void prepend(List<GuideHistoryMutation> failed) {
            List<GuideHistoryMutation> later = take();
            addAll(failed);
            addAll(later);
        }

        private void acknowledged() {
            unacknowledgedOwners.entrySet().removeIf(entry ->
                    !mutations.containsKey(new MutationKey(
                            GuideHistoryMutation.UpsertRequest.class, entry.getKey(), 0)));
        }
    }

    private static final class DurableProjection {
        private String selectedSession;
        private final Map<String, SessionProjection> sessions = new LinkedHashMap<>();
        private final Map<String, GuideUsageSnapshot> controlUsage = new LinkedHashMap<>();
        private final Map<UUID, GuideRequestSnapshot> requests = new LinkedHashMap<>();
        private final Map<TimelineKey, GuideTimelineEntry> timeline = new LinkedHashMap<>();
        private final Map<UUID, List<GuideSource>> sources = new LinkedHashMap<>();
        private final Map<MessageKey, GuideMessage> messages = new LinkedHashMap<>();
        private final Map<CheckpointKey, ContextCheckpoint> checkpoints = new LinkedHashMap<>();
        private final Map<UUID, ContextCheckpoint> checkpointPayloads = new LinkedHashMap<>();
        private final Map<UUID, String> checkpointSessions = new LinkedHashMap<>();

        private void acknowledge(List<GuideHistoryMutation> changes) {
            for (GuideHistoryMutation mutation : changes) {
                Objects.requireNonNull(mutation);
                final class $oaPattern52_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertPartition bound; }
final $oaPattern52_Holder $oaPattern52_holder = new $oaPattern52_Holder();
if ((($oaPattern52_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertPartition && (($oaPattern52_holder.bound = (GuideHistoryMutation.UpsertPartition) $oaPattern52_holder.value) != null))) {
                    selectedSession = $oaPattern52_holder.bound.selectedSession();
                } else {
final class $oaPattern53_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertSession bound; }
final $oaPattern53_Holder $oaPattern53_holder = new $oaPattern53_Holder();
if ((($oaPattern53_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertSession && (($oaPattern53_holder.bound = (GuideHistoryMutation.UpsertSession) $oaPattern53_holder.value) != null))) {
                    sessions.put(
                            $oaPattern53_holder.bound.sessionId(), new SessionProjection($oaPattern53_holder.bound.ordinal(), $oaPattern53_holder.bound.modelSelection()));
                } else {
final class $oaPattern54_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertSessionUsage bound; }
final $oaPattern54_Holder $oaPattern54_holder = new $oaPattern54_Holder();
if ((($oaPattern54_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertSessionUsage && (($oaPattern54_holder.bound = (GuideHistoryMutation.UpsertSessionUsage) $oaPattern54_holder.value) != null))) {
                    controlUsage.put($oaPattern54_holder.bound.sessionId(), $oaPattern54_holder.bound.controlUsage());
                } else {
final class $oaPattern55_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertRequest bound; }
final $oaPattern55_Holder $oaPattern55_holder = new $oaPattern55_Holder();
if ((($oaPattern55_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertRequest && (($oaPattern55_holder.bound = (GuideHistoryMutation.UpsertRequest) $oaPattern55_holder.value) != null))) {
                    requests.put($oaPattern55_holder.bound.request().requestId(), $oaPattern55_holder.bound.request());
                } else {
final class $oaPattern56_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertTimelineEntry bound; }
final $oaPattern56_Holder $oaPattern56_holder = new $oaPattern56_Holder();
if ((($oaPattern56_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertTimelineEntry && (($oaPattern56_holder.bound = (GuideHistoryMutation.UpsertTimelineEntry) $oaPattern56_holder.value) != null))) {
                    timeline.put(new TimelineKey($oaPattern56_holder.bound.requestId(), $oaPattern56_holder.bound.entry().ordinal()), $oaPattern56_holder.bound.entry());
                } else {
final class $oaPattern57_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceRequestSources bound; }
final $oaPattern57_Holder $oaPattern57_holder = new $oaPattern57_Holder();
if ((($oaPattern57_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestSources && (($oaPattern57_holder.bound = (GuideHistoryMutation.ReplaceRequestSources) $oaPattern57_holder.value) != null))) {
                    sources.put($oaPattern57_holder.bound.requestId(), $oaPattern57_holder.bound.sources());
                } else {
final class $oaPattern58_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertMessage bound; }
final $oaPattern58_Holder $oaPattern58_holder = new $oaPattern58_Holder();
if ((($oaPattern58_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertMessage && (($oaPattern58_holder.bound = (GuideHistoryMutation.UpsertMessage) $oaPattern58_holder.value) != null))) {
                    messages.put(new MessageKey($oaPattern58_holder.bound.sessionId(), $oaPattern58_holder.bound.ordinal()), $oaPattern58_holder.bound.message());
                } else {
final class $oaPattern59_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.UpsertCheckpoint bound; }
final $oaPattern59_Holder $oaPattern59_holder = new $oaPattern59_Holder();
if ((($oaPattern59_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.UpsertCheckpoint && (($oaPattern59_holder.bound = (GuideHistoryMutation.UpsertCheckpoint) $oaPattern59_holder.value) != null))) {
                    checkpoints.put(new CheckpointKey($oaPattern59_holder.bound.sessionId(), $oaPattern59_holder.bound.ordinal()), $oaPattern59_holder.bound.checkpoint());
                    checkpointPayloads.put($oaPattern59_holder.bound.checkpoint().checkpointId(), $oaPattern59_holder.bound.checkpoint());
                    checkpointSessions.put($oaPattern59_holder.bound.checkpoint().checkpointId(), $oaPattern59_holder.bound.sessionId());
                } else {
final class $oaPattern60_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.AppendCheckpoint bound; }
final $oaPattern60_Holder $oaPattern60_holder = new $oaPattern60_Holder();
if ((($oaPattern60_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint && (($oaPattern60_holder.bound = (GuideHistoryMutation.AppendCheckpoint) $oaPattern60_holder.value) != null))) {
                    checkpointPayloads.put($oaPattern60_holder.bound.checkpoint().checkpointId(), $oaPattern60_holder.bound.checkpoint());
                    checkpointSessions.put($oaPattern60_holder.bound.checkpoint().checkpointId(), $oaPattern60_holder.bound.sessionId());
                } else {
final class $oaPattern61_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.DeleteSession bound; }
final $oaPattern61_Holder $oaPattern61_holder = new $oaPattern61_Holder();
if ((($oaPattern61_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.DeleteSession && (($oaPattern61_holder.bound = (GuideHistoryMutation.DeleteSession) $oaPattern61_holder.value) != null))) {
                    removeSession($oaPattern61_holder.bound.sessionId());
                } else {
final class $oaPattern62_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ClearSession bound; }
final $oaPattern62_Holder $oaPattern62_holder = new $oaPattern62_Holder();
if ((($oaPattern62_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ClearSession && (($oaPattern62_holder.bound = (GuideHistoryMutation.ClearSession) $oaPattern62_holder.value) != null))) {
                    clearSession($oaPattern62_holder.bound.sessionId());
                } else {
final class $oaPattern63_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceContext bound; }
final $oaPattern63_Holder $oaPattern63_holder = new $oaPattern63_Holder();
if ((($oaPattern63_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext && (($oaPattern63_holder.bound = (GuideHistoryMutation.ReplaceContext) $oaPattern63_holder.value) != null))) {
                } else {
final class $oaPattern64_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ReplaceRequestContext bound; }
final $oaPattern64_Holder $oaPattern64_holder = new $oaPattern64_Holder();
if ((($oaPattern64_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext && (($oaPattern64_holder.bound = (GuideHistoryMutation.ReplaceRequestContext) $oaPattern64_holder.value) != null))) {
                } else {
final class $oaPattern65_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.CaptureRequestBoundary bound; }
final $oaPattern65_Holder $oaPattern65_holder = new $oaPattern65_Holder();
if ((($oaPattern65_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.CaptureRequestBoundary && (($oaPattern65_holder.bound = (GuideHistoryMutation.CaptureRequestBoundary) $oaPattern65_holder.value) != null))) {
                } else {
final class $oaPattern66_Holder { dev.openallay.guide.history.GuideHistoryMutation value; GuideHistoryMutation.ForkSession bound; }
final $oaPattern66_Holder $oaPattern66_holder = new $oaPattern66_Holder();
if ((($oaPattern66_holder.value = mutation) instanceof dev.openallay.guide.history.GuideHistoryMutation.ForkSession && (($oaPattern66_holder.bound = (GuideHistoryMutation.ForkSession) $oaPattern66_holder.value) != null))) {
                } else {
                    throw new IncompatibleClassChangeError();
                }
}
}
}
}
}
}
}
}
}
}
}
}
}
}
            }
        }

        private void clear() {
            selectedSession = null;
            sessions.clear();
            controlUsage.clear();
            requests.clear();
            timeline.clear();
            sources.clear();
            messages.clear();
            checkpoints.clear();
            checkpointPayloads.clear();
            checkpointSessions.clear();
        }

        private void clearSession(String sessionId) {
            controlUsage.remove(sessionId);
            Set<UUID> ids = requests.values().stream()
                    .filter(row -> row.sessionId().equals(sessionId))
                    .map(GuideRequestSnapshot::requestId)
                    .collect(java.util.stream.Collectors.toSet());
            ids.forEach(requests::remove);
            ids.forEach(sources::remove);
            timeline.keySet().removeIf(key -> ids.contains(key.requestId()));
            messages.keySet().removeIf(key -> key.sessionId().equals(sessionId));
            checkpoints.keySet().removeIf(key -> key.sessionId().equals(sessionId));
            Set<UUID> removedCheckpoints = checkpointSessions.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(sessionId)).map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            removedCheckpoints.forEach(checkpointPayloads::remove);
            removedCheckpoints.forEach(checkpointSessions::remove);
        }

        private void removeSession(String sessionId) {
            sessions.remove(sessionId);
            clearSession(sessionId);
        }
    }
}
