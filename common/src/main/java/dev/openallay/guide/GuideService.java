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
    private final Set<String> imageImportOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, dev.openallay.model.image.ImageReference> importedImageReferences =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> imageDraftOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, dev.openallay.model.ModelMessage> submittedInputs = new LinkedHashMap<>();
    private final Map<String, SessionState> sessions = new LinkedHashMap<>();
    private final Map<UUID, String> requestSessions = new LinkedHashMap<>();
    private final Map<UUID, CancelledFinalization> pendingCancelledFinalization = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<GuideSnapshot>> listeners =
            new CopyOnWriteArrayList<>();
    private volatile GuideSnapshot snapshot;
    private volatile GuideTelemetrySnapshot telemetry;
    private String selectedSession = "main";
    private long sessionSelectionGeneration;
    private final Map<String, UUID> pendingForks = new LinkedHashMap<>();
    private GuidePersistenceSnapshot persistence;
    private boolean allowHistoryWrites;
    private boolean historyDeletionPending;
    private volatile boolean disconnected;
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
        snapshot = buildSnapshot();
        if (history != null) {
            startHistoryLoad();
        }
    }

    public GuideSnapshot snapshot() {
        return snapshot;
    }

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
        GuideRequestSnapshot latest = selected.requests.isEmpty() ? null : selected.requests.getLast();
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
                .orElse(selected.requests().getLast());
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
        if (attachmentStore == null || disconnected) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection"));
        Objects.requireNonNull(encodedImage, "encodedImage");
        byte[] captured = encodedImage.clone();
        String owner = imageOwnerPrefix + "import:" + UUID.randomUUID();
        imageImportOwners.add(owner);
        return imageOperation(() -> {
            if (disconnected) {
                imageImportOwners.remove(owner);
                throw new java.io.IOException("Image connection is closed");
            }
            var reference = attachmentStore.importImage(actor, owner, captured);
            importedImageReferences.put(owner, reference);
            return reference;
        });
    }

    public CompletableFuture<ToolResult<Boolean>> releaseImportedImage(
            dev.openallay.model.image.ImageReference reference) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Success<>(false));
        return imageOperation(() -> {
            for (var entry : List.copyOf(importedImageReferences.entrySet())) {
                if (entry.getValue().equals(reference)) {
                    attachmentStore.release(actor, entry.getKey());
                    importedImageReferences.remove(entry.getKey(), entry.getValue());
                    imageImportOwners.remove(entry.getKey());
                }
            }
            return true;
        });
    }

    public CompletableFuture<ToolResult<byte[]>> readImage(
            dev.openallay.model.image.ImageReference reference) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection"));
        return imageOperation(() -> attachmentStore.read(actor, reference));
    }

    public CompletableFuture<ToolResult<Boolean>> retainDraftImages(
            String owner, List<dev.openallay.model.image.ImageReference> references) {
        if (owner == null || owner.isBlank()) return CompletableFuture.completedFuture(
                new ToolResult.Failure<>("invalid_image_owner", "Image draft owner is required"));
        List<dev.openallay.model.image.ImageReference> captured = List.copyOf(references);
        if (attachmentStore == null) return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "image_store_unavailable", "Image attachments are unavailable on this connection"));
        imageDraftOwners.add(imageOwnerPrefix + "draft:" + owner);
        return imageOperation(() -> {
            attachmentStore.retain(actor, imageOwnerPrefix + "draft:" + owner, captured);
            // Publish the durable draft pin before removing the short import lease.
            for (var entry : List.copyOf(importedImageReferences.entrySet())) {
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
        boolean images = input.content().stream()
                .anyMatch(dev.openallay.model.ModelContent.Image.class::isInstance);
        if (!images) return new ToolResult.Success<>(true);
        return validateImageCapability(input, snapshot.imageInputCapability(selection));
    }

    private ToolResult<Boolean> validateImageCapability(
            dev.openallay.model.ModelMessage input,
            dev.openallay.model.image.ImageInputCapability capability) {
        if (input.content().stream().noneMatch(dev.openallay.model.ModelContent.Image.class::isInstance)) {
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
        CompletableFuture<Void> retained = retainUserInput(requestId, userInput(requestId));
        return reference -> {
            try { retained.join(); }
            catch (java.util.concurrent.CompletionException failure) {
                throw new java.io.IOException("Unable to retain image attachments", failure);
            }
            return attachmentStore.read(actor, reference);
        };
    }

    /** Retain this session input before normal ask dispatch may release its import lease. */
    private CompletableFuture<Void> retainUserInput(
            UUID requestId, dev.openallay.model.ModelMessage input) {
        if (attachmentStore == null) return CompletableFuture.completedFuture(null);
        String session = requestSessions.get(requestId);
        if (session == null) throw new IllegalArgumentException("Image request owner is unavailable");
        List<dev.openallay.model.image.ImageReference> initial = imageReferences(List.of(input));
        String owner = imageOwnerPrefix + "session:" + session;
        List<dev.openallay.model.image.ImageReference> union = new ArrayList<>(
                retainedImages.getOrDefault(owner, List.of()));
        for (var reference : initial) if (!union.contains(reference)) union.add(reference);
        retainedImages.put(owner, List.copyOf(union));
        return CompletableFuture.runAsync(() -> {
            try { attachmentStore.retain(actor, owner, union); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, IMAGE_IO);
    }

    private void releaseSessionImageReferences(String sessionId) {
        if (attachmentStore == null) return;
        String owner = imageOwnerPrefix + "session:" + sessionId;
        retainedImages.remove(owner);
        CompletableFuture<Void> barrier = history != null && allowHistoryWrites
                ? drainHistoryWrites() : CompletableFuture.completedFuture(null);
        barrier.thenRunAsync(() -> {
            try { attachmentStore.release(actor, owner); }
            catch (java.io.IOException ignored) { /* Preserve files when release cannot be saved. */ }
        }, IMAGE_IO);
    }

    private static List<dev.openallay.model.image.ImageReference> imageReferences(
            List<dev.openallay.model.ModelMessage> messages) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(dev.openallay.model.ModelContent.Image.class::isInstance)
                .map(dev.openallay.model.ModelContent.Image.class::cast)
                .map(dev.openallay.model.ModelContent.Image::reference).distinct().toList();
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

    public CompletableFuture<ToolResult<UUID>> ask(String question) {
        try { return ask(dev.openallay.model.ModelMessage.userInput(question, List.of())); }
        catch (IllegalArgumentException invalid) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "invalid_arguments", "Question must not be blank"));
        }
    }

    public CompletableFuture<ToolResult<UUID>> ask(dev.openallay.model.ModelMessage input) {
        CompletableFuture<ToolResult<UUID>> result = new CompletableFuture<>();
        dispatcher.execute(() -> submit(selectedSession, input, result));
        return result;
    }

    public CompletableFuture<ToolResult<Boolean>> cancel() {
        CompletableFuture<ToolResult<Boolean>> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            if (rejectStateChange(result)) {
                return;
            }
            SessionState session = sessions.get(selectedSession);
            GuideRequestSnapshot active = active(session);
            if (active == null) {
                result.complete(new ToolResult.Success<>(false));
                return;
            }
            boolean preparingContext = session.preparingContextRequest != null
                    && session.preparingContextRequest.equals(active.requestId());
            if (preparingContext) session.unresolvedForkContext.add(active.requestId());
            var note = new dev.openallay.model.ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                    List.of(new dev.openallay.model.ModelContent.Text(
                            "[OpenAllay request ended: agent_cancelled] Agent request was cancelled")));
            List<dev.openallay.model.ModelMessage> current = new ArrayList<>(session.modelContext);
            List<dev.openallay.model.ModelMessage> original = new ArrayList<>(
                    session.originalContext.getOrDefault(active.requestId(), List.of()));
            if (original.isEmpty()) {
                dev.openallay.model.ModelMessage input = submittedInputs.get(active.requestId());
                if (input == null) input = dev.openallay.model.ModelMessage.userText(active.userMessage());
                original.add(input);
                current.add(input);
            }
            current.add(note);
            original.add(note);
            if (!preparingContext) {
                pendingCancelledFinalization.put(active.requestId(), new CancelledFinalization(
                        session.id, Objects.requireNonNull(session.requestSequences.get(active.requestId())),
                        List.copyOf(session.checkpoints)));
            }
            if (preparingContext) {
                // The actual predecessor projection is still loading. Keep it durable and
                // record only this request's known input/end note; no guessed fork boundary.
                session.originalContext.put(active.requestId(), List.copyOf(original));
                if (incrementalHistory) pendingHistoryMutations.add(
                        new GuideHistoryMutation.ReplaceRequestContext(active.requestId(), original));
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
            if (input == null) input = (session == null ? List.<dev.openallay.model.ModelMessage>of()
                    : session.originalContext.getOrDefault(request.requestId(), List.of()))
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
                || pendingCancelledFinalization.containsKey(requestId)) {
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
                    originals.put(inherited.requestId(), source.originalContext.getOrDefault(sourceId, List.of()));
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
        String owner = imageOwnerPrefix + "session:" + mutation.sessionId();
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
                mutation, fork, failure, result, Map.of(), Map.of());
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
        target.usage.restore(fork.session().usage(), fork.session().inheritedUsage());
        target.firstAvailable = fork.session().first();
        target.lastAvailable = fork.session().last();
        target.nextRequestSequence = fork.session().last().sequence() + 1;
        target.messageOrdinalBase = fork.nextMessageOrdinal();
        target.modelContext = fork.messages();
        target.checkpoints.addAll(fork.checkpoints());
        sessions.put(target.id, target);
        mergePage(target, GuideHistoryPageRequest.Direction.NEWEST, 120, fork.page());
        if (history == null) {
            target.originalContext.putAll(originals);
            target.forkBoundaries.putAll(boundaries);
        }
        for (DurableProjection projection : List.of(capturedProjection, durableProjection)) {
            projection.sessions.put(target.id, new SessionProjection(fork.session().ordinal(), target.modelSelection));
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
            if (source.originalContext.getOrDefault(original.requestId(), List.of()).isEmpty()) {
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
                mutation.modelSelection(), inherited.size(), cursors.getFirst(), cursors.getLast(),
                GuideUsageSnapshot.empty(), inherited.stream().map(GuideRequestSnapshot::usageProjection)
                        .reduce(GuideUsageSnapshot.empty(), GuideUsageSnapshot::plus), inheritedMessageCount),
                new GuideHistoryPage(mutation.sessionId(), inherited, cursors.getFirst(), cursors.getLast(), false, false),
                boundary.messages(), boundary.checkpoints().stream().map(checkpoint ->
                        new ContextCheckpoint(UUID.randomUUID(), checkpoint.sourceFromIndex(),
                                checkpoint.sourceToIndexExclusive(), checkpoint.sourceHash(), checkpoint.modelIdentifier(),
                                checkpoint.createdAt(), checkpoint.status(), checkpoint.summary(), checkpoint.failureCode(),
                                checkpoint.failureMessage(), checkpoint.estimatedProjectionTokens())).toList(),
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
            session.requests.forEach(request -> cancelHistoryContextBarrier(request.requestId()));
            session.requests.forEach(request -> requestSessions.remove(request.requestId()));
            session.usageCarriers.keySet().forEach(requestSessions::remove);
            session.usageCarriers.clear();
            capturedProjection.removeSession(sessionId);
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
            releaseSessionImageReferences(sessionId);
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
                        session.requests.stream().map(request ->
                                new GuideSessionExportCollector.SequencedRequest(
                                        Objects.requireNonNull(
                                                session.requestSequences.get(request.requestId()),
                                                "request sequence"),
                                        request)).toList();
                GuideSessionExportCollector collector =
                        new GuideSessionExportCollector(historyScope, history);
                collector.collect(capturedSession, captured,
                                Map.copyOf(session.originalContext),
                                session.nextRequestSequence - 1, capturedAt)
                        .whenComplete((export, failure) -> dispatcher.execute(() -> {
                            if (failure == null) {
                                captureExportImages(export, result);
                            } else {
                                result.complete(new ToolResult.Failure<>(
                                        "history_export_failed",
                                        "Unable to read the complete guide session for export"));
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
                export.requests().stream().flatMap(request -> request.originalContext().stream()).toList());
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
            if (active(session) != null) {
                result.complete(new ToolResult.Failure<>(
                        "agent_busy", "Cannot clear a session while its request is active"));
                return;
            }
            invalidatePageLoad(session, "history_page_cancelled", "History page request was cancelled");
            session.requests.forEach(request -> requestSessions.remove(request.requestId()));
            capturedProjection.clearSession(session.id);
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
            session.modelContext = List.of();
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
            releaseSessionImageReferences(session.id);
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
            select(sessions.get(selectedSession), selection);
            publish();
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

    public CompletableFuture<Void> disconnect() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        dispatcher.execute(() -> {
            List<GuideRequestSnapshot> activeRequests = sessions.values().stream()
                    .map(GuideService::active)
                    .filter(Objects::nonNull)
                    .toList();
            for (GuideRequestSnapshot active : activeRequests) {
                if (active.topology() == GuideTopology.SERVER) {
                    remote.cancel(active.requestId());
                } else if (local != null) {
                    local.cancel(actor, active.sessionId(), active.requestId());
                }
                apply(active.requestId(), new AgentEvent.Failed(
                        "agent_cancelled", "Agent request was cancelled by disconnect"));
            }
            CompletableFuture<Void> durable = history != null && allowHistoryWrites
                    ? drainHistoryWrites().handle((ignored, failure) -> null)
                            .thenCompose(ignored -> history.flush())
                    : CompletableFuture.completedFuture(null);
            sessions.values().forEach(session -> invalidatePageLoad(
                    session, "history_page_cancelled", "History page request was cancelled by disconnect"));
            historyWriteBarriers.stream().map(HistoryWriteBarrier::requestId)
                    .filter(Objects::nonNull).toList().forEach(this::cancelHistoryContextBarrier);
            disconnected = true;
            List<String> transientImageOwners = new ArrayList<>(imageImportOwners);
            transientImageOwners.addAll(imageDraftOwners);
            retainedImages.keySet().stream().filter(owner -> !owner.contains(":export:"))
                    .forEach(transientImageOwners::add);
            // Export snapshots carry their own lease and can finish after this connection closes.
            CompletableFuture<Void> imageCleanup = durable.handle((ignored, failure) -> null)
                    .thenRunAsync(() -> {
                        if (attachmentStore == null) return;
                        for (String owner : transientImageOwners) {
                            try { attachmentStore.release(actor, owner); }
                            catch (java.io.IOException ignored) { /* Retention failure is conservative. */ }
                        }
                    }, IMAGE_IO);
            if (local != null) {
                local.clearActor(actor);
            }
            remote.disconnect();
            requestSessions.clear();
            pendingCancelledFinalization.clear();
            sessions.clear();
            sessions.put("main", new SessionState("main", defaultClientSelection()));
            selectedSession = "main";
            publishWithoutSave();
            imageCleanup.handle((ignored, failure) -> null).thenRun(() -> result.complete(null));
        });
        return result;
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
            deletion = switch (kind) {
                case PARTITION -> history.delete(
                        GuideHistoryDeleteScope.partition(historyScope));
                case ACTOR -> history.delete(GuideHistoryDeleteScope.actor(actor));
                case DATABASE -> history.resetDatabase();
            };
            Objects.requireNonNull(deletion, "history deletion future");
        } catch (RuntimeException failure) {
            finishHistoryAdministration(failure, result);
            return;
        }
        deletion.whenComplete((ignored, failure) -> dispatcher.execute(
                () -> finishHistoryAdministration(failure, result)));
    }

    private GuideFailure historyAdministrationFailure(HistoryAdministrationKind kind) {
        if (history == null || historyScope == null || disconnected) {
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
                || sessions.values().stream().anyMatch(session -> active(session) != null)) {
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
        if (!disconnected) {
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

    private void submit(
            String sessionId, dev.openallay.model.ModelMessage input,
            CompletableFuture<ToolResult<UUID>> result) {
        if (rejectStateChange(result)) {
            return;
        }
        try { dev.openallay.model.ModelMessage.requireUserInput(input); }
        catch (IllegalArgumentException | NullPointerException invalid) {
            result.complete(new ToolResult.Failure<>("invalid_arguments", "Enter text or attach an image"));
            return;
        }
        String question = dev.openallay.agent.AgentRequest.displayText(input);
        SessionState session = sessions.computeIfAbsent(
                sessionId, id -> new SessionState(id, defaultClientSelection()));
        if (active(session) != null) {
            result.complete(new ToolResult.Failure<>(
                    "agent_busy", "This guide session already has an active request"));
            return;
        }
        GuideModelSelection capturedSelection = session.modelSelection;
        ToolResult<Boolean> valid = validateUserInput(input, capturedSelection);
        if (valid instanceof ToolResult.Failure<Boolean> failure) {
            result.complete(new ToolResult.Failure<>(failure.code(), failure.message()));
            return;
        }
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
        session.usage.begin(requestId, capturedSelection, publicModelIdentifier(capturedSelection));
        session.originalContext.put(requestId, List.of());
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
        submittedInputs.put(requestId, input);
        // The managed bytes stay actor/session-owned through the request and durable context.
        retainUserInput(requestId, input);
        publish();

        if (topology == GuideTopology.SERVER) {
            if (incrementalHistory) {
                prepareRemoteContext(session, requestId, question);
            } else {
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
        ToolResult<ToolInvocationContext> captured =
                contexts.capture(requiredContext, requestId.toString());
        if (captured instanceof ToolResult.Failure<ToolInvocationContext> failure) {
            apply(requestId, new AgentEvent.Failed(failure.code(), failure.message()));
            return;
        }
        ToolInvocationContext context =
                ((ToolResult.Success<ToolInvocationContext>) captured).value();
        try {
            local.ask(
                            profileId,
                            actor,
                            sessionId,
                            requestId,
                            userInput(requestId),
                            images(requestId),
                            context,
                            event -> dispatcher.execute(() -> apply(requestId, event)))
                    .whenComplete((ignored, throwable) -> dispatcher.execute(() -> {
                        if (throwable != null) failIfActive(requestId, throwable);
                        // Local runtime completes only after handing off all numeric call receipts.
                        releaseUsageOwner(requestId);
                    }));
        } catch (RuntimeException failure) {
            apply(requestId, new AgentEvent.Failed(
                    "agent_failure", message(failure)));
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

    private void apply(UUID requestId, AgentEvent event) {
        if (disconnected) return;
        if (event instanceof AgentEvent.RequestReleased) {
            releaseUsageOwner(requestId);
            return;
        }
        if (event instanceof AgentEvent.ContextFinalized finalized) {
            CancelledFinalization pending = pendingCancelledFinalization.remove(requestId);
            if (disconnected || pending == null) return;
            SessionState session = sessions.get(pending.sessionId());
            if (session == null) return;
            session.originalContext.put(requestId, finalized.requestMessages());
            if (incrementalHistory) {
                pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceRequestContext(
                        requestId, finalized.requestMessages()));
            }
            if (pending.sequence() == session.nextRequestSequence - 1) {
                session.modelContext = finalized.messages();
                if (incrementalHistory) {
                    pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceContext(
                            pending.sessionId(), finalized.messages()));
                }
            }
            captureForkBoundary(session, requestId, finalized.messages(), pending.checkpoints());
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
        if (index < 0 || target.terminal()) return;
        if (event instanceof AgentEvent.ContextUpdated updated) {
            session.modelContext = updated.messages();
            session.originalContext.put(requestId, updated.requestMessages());
            if (incrementalHistory) {
                pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceContext(
                        sessionId, updated.messages()));
                pendingHistoryMutations.add(new GuideHistoryMutation.ReplaceRequestContext(
                        requestId, updated.requestMessages()));
            }
            publish();
            return;
        }
        if (event instanceof AgentEvent.ContextCompacted compacted) {
            ContextCheckpoint checkpoint = compacted.checkpoint();
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
        if (after.terminal()) {
            contexts.closeRequest(requestId.toString());
            captureForkBoundary(session, requestId);
        }
        if (after.status() == GuideRequestStatus.COMPLETED
                && !after.assistantText().isBlank()) {
            session.messages.add(new GuideMessage(
                    after.requestId(),
                    GuideMessage.Role.ASSISTANT,
                    after.assistantText(),
                    after.terminalAt()));
        }
        publish();
    }

    private void releaseUsageOwner(UUID requestId) {
        String sessionId = requestSessions.get(requestId);
        SessionState session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) return;
        if (!session.usage.release(requestId)) return;
        session.usageCarriers.remove(requestId);
        if (indexOf(session, requestId) < 0) requestSessions.remove(requestId);
    }

    private void failIfActive(UUID requestId, Throwable throwable) {
        GuideRequestSnapshot request = find(requestId);
        if (request != null && !request.terminal()) {
            Throwable failure = unwrap(throwable);
            if (failure instanceof GuideModelProfileException profileFailure) {
                apply(requestId, new AgentEvent.Failed(
                        profileFailure.code(), profileFailure.getMessage()));
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
        GuideRequestSnapshot latest = session.requests.getLast();
        return latest.terminal() ? null : latest;
    }

    private void publish() {
        scheduleHistorySave();
        publishWithoutSave();
    }

    private void publishWithoutSave() {
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
        List<GuideSessionSnapshot> copies = sessions.values().stream()
                .map(session -> new GuideSessionSnapshot(
                        session.id,
                        session.messages,
                        session.requests,
                        session.checkpoints,
                        session.modelSelection,
                        historyWindow(session)))
                .toList();
        GuideModelSelection currentSelection = sessions.get(selectedSession).modelSelection;
        List<GuideClientModelProfile> profiles = local == null ? List.of() : local.profiles();
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
            session.usage.restore(snapshot.usage(), snapshot.inheritedUsage());
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
        PageLoad load = new PageLoad(key, generation, new ArrayList<>(List.of(result)));
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
        List<GuideRequestSnapshot> active = session.requests.stream()
                .filter(request -> !request.terminal()).toList();
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
        List<GuideRequestSnapshot> loadedDurable = session.requests.stream()
                .filter(GuideRequestSnapshot::terminal).toList();
        session.firstLoaded = loadedDurable.isEmpty() ? null : cursor(
                session, loadedDurable.getFirst());
        session.lastLoaded = loadedDurable.isEmpty() ? null : cursor(
                session, loadedDurable.getLast());
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
            for (DurableProjection projection : List.of(durableProjection, capturedProjection)) {
                projection.requests.putIfAbsent(request.requestId(), rowProjection(request));
                for (GuideTimelineEntry entry : request.timeline()) {
                    projection.timeline.putIfAbsent(
                            new TimelineKey(request.requestId(), entry.ordinal()), entry);
                }
                projection.sources.putIfAbsent(request.requestId(), List.copyOf(request.sources()));
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
            return CompletableFuture.failedFuture(new GuideHistoryException(
                    persistence.failure().code(), persistence.failure().message()));
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        historyWriteBarriers.add(new HistoryWriteBarrier(generation, requestId, completion));
        startQueuedHistoryWrite();
        return completion;
    }

    private void cancelHistoryContextBarrier(UUID requestId) {
        List<HistoryWriteBarrier> cancelled = historyWriteBarriers.stream()
                .filter(barrier -> requestId.equals(barrier.requestId())).toList();
        historyWriteBarriers.removeAll(cancelled);
        for (HistoryWriteBarrier barrier : cancelled) {
            barrier.completion().completeExceptionally(new GuideHistoryException(
                    "history_context_cancelled", "Durable guide context preparation was cancelled"));
        }
    }

    private void finishHistoryWriteBarriers(Throwable failure, boolean newerWork) {
        List<HistoryWriteBarrier> completed = historyWriteBarriers.stream()
                .filter(barrier -> failure == null
                        ? barrier.generation() <= persistence.committedGeneration()
                        : barrier.requestId() != null || !newerWork).toList();
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
        List<GuideHistoryMutation> boundaries = mutations.stream()
                .filter(value -> value instanceof GuideHistoryMutation.CaptureRequestBoundary).toList();
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
                List.of(), request.status(), List.of(), request.usage(),
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
        List<GuideTimelineEntry> timeline = request.timeline().stream()
                .map(entry -> entry instanceof GuideTimelineEntry.Tool tool
                        ? new GuideTimelineEntry.Tool(
                                tool.ordinal(),
                                new GuideToolActivity(
                                        tool.activity().invocationId(),
                                        tool.activity().index(),
                                        tool.activity().toolId(),
                                        tool.activity().status(),
                                        null,
                                        tool.activity().invocation().restored(),
                                        null,
                                        tool.activity().presentationMessages(),
                                        tool.activity().sources()))
                        : entry)
                .toList();
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
        if (current instanceof GuideHistoryException historyFailure) {
            return new GuideFailure(
                    historyFailure.code(),
                    historyFailure.getMessage() == null
                            ? "Durable guide history is unavailable"
                            : historyFailure.getMessage());
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
        private GuideUsageTracker usage = new GuideUsageTracker();
        private final List<GuideMessage> messages = new ArrayList<>();
        // Unloaded inherited durable messages own earlier ordinals.
        private int messageOrdinalBase;
        private final List<GuideRequestSnapshot> requests = new ArrayList<>();
        private final List<ContextCheckpoint> checkpoints = new ArrayList<>();
        private List<dev.openallay.model.ModelMessage> modelContext = List.of();
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

    private record CancelledFinalization(String sessionId, long sequence, List<ContextCheckpoint> checkpoints) {
        private CancelledFinalization {
            checkpoints = List.copyOf(checkpoints);
        }
    }
    private record UsageCarrier(long sequence, GuideUsageSnapshot usage) {}

    private record PageKey(
            GuideHistoryPageRequest.Direction direction,
            GuideHistoryCursor cursor,
            int count) {}

    private record PageLoad(
            PageKey key,
            long generation,
            List<CompletableFuture<ToolResult<GuideHistoryPage>>> waiters) {}

    private record SessionProjection(int ordinal, GuideModelSelection selection) {}

    private record TimelineKey(UUID requestId, int ordinal) {}

    private record MessageKey(String sessionId, int ordinal) {}

    private record CheckpointKey(String sessionId, int ordinal) {}

    private record HistoryWrite(long generation, GuideHistoryCommit commit) {}

    private record HistoryWriteBarrier(
            long generation, UUID requestId, CompletableFuture<Void> completion) {}

    private record MutationKey(Class<?> kind, Object owner, int ordinal) {}

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
                if (change instanceof GuideHistoryMutation.UpsertRequest row) {
                    unacknowledgedOwners.put(row.request().requestId(), row.request().sessionId());
                }
            }
            for (GuideHistoryMutation change : changes) {
                if (change instanceof GuideHistoryMutation.DeleteSession deleted) {
                    discardSession(deleted.sessionId(), true);
                } else if (change instanceof GuideHistoryMutation.ClearSession cleared) {
                    discardSession(cleared.sessionId(), false);
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
            return switch (mutation) {
                case GuideHistoryMutation.UpsertSession row -> row.sessionId();
                case GuideHistoryMutation.UpsertMessage row -> row.sessionId();
                case GuideHistoryMutation.ReplaceContext row -> row.sessionId();
                case GuideHistoryMutation.UpsertCheckpoint row -> row.sessionId();
                case GuideHistoryMutation.AppendCheckpoint row -> row.sessionId();
                case GuideHistoryMutation.DeleteSession row -> row.sessionId();
                case GuideHistoryMutation.ClearSession row -> row.sessionId();
                case GuideHistoryMutation.UpsertRequest row -> row.request().sessionId();
                case GuideHistoryMutation.UpsertTimelineEntry row -> sessionOf(row.requestId());
                case GuideHistoryMutation.ReplaceRequestSources row -> sessionOf(row.requestId());
                case GuideHistoryMutation.ReplaceRequestContext row -> sessionOf(row.requestId());
                case GuideHistoryMutation.CaptureRequestBoundary row -> sessionOf(row.requestId());
                case GuideHistoryMutation.ForkSession row -> row.sessionId();
                case GuideHistoryMutation.UpsertPartition ignored -> null;
            };
        }

        private String sessionOf(UUID requestId) {
            String owner = unacknowledgedOwners.get(requestId);
            if (owner != null) return owner;
            GuideRequestSnapshot row = capturedProjection.requests.get(requestId);
            if (row == null) row = durableProjection.requests.get(requestId);
            return row == null ? null : row.sessionId();
        }

        private MutationKey key(GuideHistoryMutation mutation) {
            return switch (mutation) {
                case GuideHistoryMutation.UpsertPartition ignored ->
                        new MutationKey(mutation.getClass(), "partition", 0);
                case GuideHistoryMutation.UpsertSession row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), 0);
                case GuideHistoryMutation.UpsertRequest row ->
                        new MutationKey(mutation.getClass(), row.request().requestId(), 0);
                case GuideHistoryMutation.UpsertMessage row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), row.ordinal());
                case GuideHistoryMutation.UpsertTimelineEntry row ->
                        new MutationKey(mutation.getClass(), row.requestId(), row.entry().ordinal());
                case GuideHistoryMutation.ReplaceRequestSources row ->
                        new MutationKey(mutation.getClass(), row.requestId(), 0);
                case GuideHistoryMutation.ReplaceContext row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), 0);
                case GuideHistoryMutation.ReplaceRequestContext row ->
                        new MutationKey(mutation.getClass(), row.requestId(), 0);
                case GuideHistoryMutation.CaptureRequestBoundary row ->
                        new MutationKey(mutation.getClass(), row.requestId(), 0);
                case GuideHistoryMutation.ForkSession row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), 0);
                case GuideHistoryMutation.AppendCheckpoint row ->
                        new MutationKey(mutation.getClass(), row.checkpoint().checkpointId(), 0);
                case GuideHistoryMutation.UpsertCheckpoint row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), row.ordinal());
                case GuideHistoryMutation.DeleteSession row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), 0);
                case GuideHistoryMutation.ClearSession row ->
                        new MutationKey(mutation.getClass(), row.sessionId(), 0);
            };
        }

        private List<GuideHistoryMutation> take() {
            List<GuideHistoryMutation> batch = List.copyOf(mutations.values());
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
        private final Map<UUID, GuideRequestSnapshot> requests = new LinkedHashMap<>();
        private final Map<TimelineKey, GuideTimelineEntry> timeline = new LinkedHashMap<>();
        private final Map<UUID, List<GuideSource>> sources = new LinkedHashMap<>();
        private final Map<MessageKey, GuideMessage> messages = new LinkedHashMap<>();
        private final Map<CheckpointKey, ContextCheckpoint> checkpoints = new LinkedHashMap<>();
        private final Map<UUID, ContextCheckpoint> checkpointPayloads = new LinkedHashMap<>();
        private final Map<UUID, String> checkpointSessions = new LinkedHashMap<>();

        private void acknowledge(List<GuideHistoryMutation> changes) {
            for (GuideHistoryMutation mutation : changes) {
                switch (mutation) {
                    case GuideHistoryMutation.UpsertPartition row -> selectedSession = row.selectedSession();
                    case GuideHistoryMutation.UpsertSession row -> sessions.put(
                            row.sessionId(), new SessionProjection(row.ordinal(), row.modelSelection()));
                    case GuideHistoryMutation.UpsertRequest row -> requests.put(
                            row.request().requestId(), row.request());
                    case GuideHistoryMutation.UpsertTimelineEntry row -> timeline.put(
                            new TimelineKey(row.requestId(), row.entry().ordinal()), row.entry());
                    case GuideHistoryMutation.ReplaceRequestSources row -> sources.put(
                            row.requestId(), row.sources());
                    case GuideHistoryMutation.UpsertMessage row -> messages.put(
                            new MessageKey(row.sessionId(), row.ordinal()), row.message());
                    case GuideHistoryMutation.UpsertCheckpoint row -> {
                        checkpoints.put(new CheckpointKey(row.sessionId(), row.ordinal()), row.checkpoint());
                        checkpointPayloads.put(row.checkpoint().checkpointId(), row.checkpoint());
                        checkpointSessions.put(row.checkpoint().checkpointId(), row.sessionId());
                    }
                    case GuideHistoryMutation.AppendCheckpoint row -> {
                        checkpointPayloads.put(row.checkpoint().checkpointId(), row.checkpoint());
                        checkpointSessions.put(row.checkpoint().checkpointId(), row.sessionId());
                    }
                    case GuideHistoryMutation.DeleteSession row -> removeSession(row.sessionId());
                    case GuideHistoryMutation.ClearSession row -> clearSession(row.sessionId());
                    case GuideHistoryMutation.ReplaceContext ignored -> { }
                    case GuideHistoryMutation.ReplaceRequestContext ignored -> { }
                    case GuideHistoryMutation.CaptureRequestBoundary ignored -> { }
                    case GuideHistoryMutation.ForkSession ignored -> { }
                }
            }
        }

        private void clear() {
            selectedSession = null;
            sessions.clear();
            requests.clear();
            timeline.clear();
            sources.clear();
            messages.clear();
            checkpoints.clear();
            checkpointPayloads.clear();
            checkpointSessions.clear();
        }

        private void clearSession(String sessionId) {
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
