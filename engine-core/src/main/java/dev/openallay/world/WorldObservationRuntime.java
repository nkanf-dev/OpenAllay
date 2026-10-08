package dev.openallay.world;

import dev.openallay.concurrent.NamedThreads;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageReference;
import dev.openallay.script.JavascriptExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns request observations and their image producer pins until transcript custody is published. */
public final class WorldObservationRuntime {
    private final ConcurrentMap<String, Request> requests = new ConcurrentHashMap<>();
    private volatile Function<UUID, ImageAttachmentStore> images = ignored -> null;

    /** Uses the existing actor image store. It does not create another cache or collect images. */
    public void configureImages(Function<UUID, ImageAttachmentStore> resolver) {
        images = java.util.Objects.requireNonNull(resolver, "resolver");
    }

    public void capture(String correlationId, WorldObservationCoordinator coordinator) {
        if (correlationId == null || dev.openallay.util.Java8Strings.isBlank(correlationId)) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        WorldObservationCoordinator candidate = java.util.Objects.requireNonNull(coordinator, "coordinator");
        Request existing = requests.putIfAbsent(correlationId, new Request(candidate));
        if (existing != null && existing.coordinator != candidate) candidate.close();
    }

    public Optional<JavascriptWorldBridge> bridge(String correlationId, CancellationSignal cancellation) {
        return bridge(correlationId, cancellation, ignored -> {});
    }

    public Optional<JavascriptWorldBridge> bridge(String correlationId, CancellationSignal cancellation,
            Consumer<EvidenceMetadata> evidence) {
        return bridge(correlationId, cancellation, evidence, ignored -> {});
    }

    public Optional<JavascriptWorldBridge> bridge(String correlationId, CancellationSignal cancellation,
            Consumer<EvidenceMetadata> evidence, Consumer<ImageReference> capturedImages) {
        Request request = requests.get(correlationId);
        return request == null || request.closed ? Optional.empty()
                : Optional.of(new JavascriptWorldBridge(request.coordinator, cancellation, evidence, capturedImages));
    }

    /** In-memory PNG import off the client/render thread, atomically pinned by this request. */
    public CompletableFuture<ImageReference> captureImport(String correlationId, UUID actor, byte[] png) {
        return captureImport(correlationId, actor, png, () -> true);
    }

    /** Native admission can expire while the worker imports bytes; expired imports drop their own pin. */
    public CompletableFuture<ImageReference> captureImport(String correlationId, UUID actor, byte[] png,
            java.util.function.BooleanSupplier admission) {
        java.util.Objects.requireNonNull(admission, "admission");
        Request request = requests.get(correlationId);
        if (request == null) return dev.openallay.util.Java8Futures.failedFuture(unavailable());
        byte[] bytes = java.util.Objects.requireNonNull(png, "png").clone();
        CompletableFuture<ImageReference> result = new CompletableFuture<>();
        synchronized (request) {
            if (request.closed) return dev.openallay.util.Java8Futures.failedFuture(unavailable());
            request.imports.add(result);
        }
        try {
            NamedThreads.startDaemon("openallay-observation-image", () -> {
                ImageAttachmentStore store = null;
                String owner = "observation-producer:" + UUID.randomUUID();
                boolean retained = false;
                try {
                    if (request.closed || requests.get(correlationId) != request) throw unavailable();
                    store = images.apply(actor);
                    if (store == null) throw new JavascriptExecutionException(
                            "image_store_unavailable", "Native view images are unavailable on this connection");
                    ImageReference reference = store.importImage(actor, owner, bytes);
                    synchronized (request) {
                        if (request.closed || requests.get(correlationId) != request || result.isDone()
                                || !admission.getAsBoolean()) throw unavailable();
                        request.producers.add(new Producer(actor, store, owner, reference));
                        retained = true;
                        result.complete(reference);
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure instanceof JavascriptExecutionException ? failure
                            : new JavascriptExecutionException("view_image_import_failed", "Native view image import failed", failure));
                } finally {
                    synchronized (request) { request.imports.remove(result); }
                    if (!retained && store != null) {
                        try { store.release(actor, owner); } catch (java.io.IOException ignored) {}
                    }
                }
            });
        } catch (Throwable failure) {
            synchronized (request) { request.imports.remove(result); }
            result.completeExceptionally(failure);
        }
        return result;
    }

    public dev.openallay.model.image.ImageInputLimits imageLimits(UUID actor) {
        ImageAttachmentStore store = images.apply(actor);
        if (store == null) throw new JavascriptExecutionException(
                "image_store_unavailable", "Native view images are unavailable on this connection");
        return store.limits();
    }

    /** Complete producer references, not a scan of model-returned JSON. */
    public List<ImageReference> producerReferences(String correlationId) {
        Request request = requests.get(correlationId);
        if (request == null) return dev.openallay.util.Java8Collections.listOf();
        synchronized (request) {
            return dev.openallay.util.Java8Collections.toList(request.producers.stream().map(Producer::reference).distinct());
        }
    }

    /** Immutable custody metadata for a connection detach; never samples native game state. */
    public java.util.Map<String, List<ImageReference>> producerReferenceSnapshot() {
        java.util.Map<String, List<ImageReference>> snapshot = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<java.lang.String, dev.openallay.world.WorldObservationRuntime.Request> entry : dev.openallay.util.Java8Collections.listCopyOf(requests.entrySet())) {
            synchronized (entry.getValue()) {
                snapshot.put(entry.getKey(), dev.openallay.util.Java8Collections.toList(entry.getValue().producers.stream()
                        .map(Producer::reference).distinct()));
            }
        }
        return dev.openallay.util.Java8Collections.mapCopyOf(snapshot);
    }

    /** Stops native work but keeps producer pins until the published transcript owns its images. */
    public void closeObservations(String correlationId) {
        Request request = requests.get(correlationId);
        if (request != null) close(request);
    }

    /** Root calls after image custody/persistence, or on a terminal path with no published images. */
    public CompletableFuture<Void> releaseImageProducers(String correlationId) {
        Request request = requests.remove(correlationId);
        return request == null ? CompletableFuture.completedFuture(null) : releaseImageProducers(request);
    }

    private CompletableFuture<Void> releaseImageProducers(Request request) {
        Throwable closeFailure = null;
        try { close(request); } catch (Throwable failure) { closeFailure = failure; }
        Throwable teardownFailure = closeFailure;
        CompletableFuture<Void> result = new CompletableFuture<>();
        List<Producer> producers;
        synchronized (request) {
            producers = dev.openallay.util.Java8Collections.listCopyOf(request.producers);
            request.producers.clear();
        }
        Runnable release = () -> {
            Throwable failure = teardownFailure;
            for (Producer producer : producers) {
                try { producer.store.release(producer.actor, producer.owner); }
                catch (Throwable rejected) { if (failure == null) failure = rejected; }
            }
            if (failure == null) result.complete(null);
            else result.completeExceptionally(failure);
        };
        try { NamedThreads.startDaemon("openallay-observation-release", release); }
        catch (Throwable rejected) { release.run(); }
        return result;
    }

    /** Explicit complete teardown; Tool scope closure instead uses closeObservations. */
    public void closeRequest(String correlationId) { releaseImageProducers(correlationId); }

    public void clearConnectionState() {
        dev.openallay.util.Java8Collections.listCopyOf(requests.keySet()).forEach(this::closeRequest);
    }

    /** Detach exact old requests now. Later custody completion cannot close a replacement. */
    public CompletableFuture<Void> detachConnectionState(CompletableFuture<Void> custody) {
        java.util.Objects.requireNonNull(custody, "custody");
        List<Request> detached = new ArrayList<>();
        for (java.util.Map.Entry<java.lang.String, dev.openallay.world.WorldObservationRuntime.Request> entry : dev.openallay.util.Java8Collections.listCopyOf(requests.entrySet())) {
            if (requests.remove(entry.getKey(), entry.getValue())) {
                detached.add(entry.getValue());
                try { close(entry.getValue()); }
                catch (RuntimeException ignored) { /* Release still owns this exact request. */ }
            }
        }
        // Failure is not a custody receipt. Keep the old durable producer owners for recovery;
        // neither a disconnect nor a later actor may silently collect their only remaining bytes.
        return custody.thenCompose(ignored -> CompletableFuture.allOf(detached.stream()
                .map(this::releaseImageProducers).toArray(CompletableFuture[]::new)));
    }

    private static void close(Request request) {
        List<CompletableFuture<ImageReference>> imports;
        synchronized (request) {
            request.closed = true;
            imports = dev.openallay.util.Java8Collections.listCopyOf(request.imports);
        }
        imports.forEach(result -> result.completeExceptionally(unavailable()));
        List<CompletableFuture<WorldViewCapture>> associations;
        synchronized (request) { associations = dev.openallay.util.Java8Collections.listCopyOf(request.associations); }
        associations.forEach(result -> result.completeExceptionally(unavailable()));
        request.coordinator.close();
    }

    /** Immutable earlier source. Association never re-labels it as a newly rendered current UI. */
    public void associate(String correlationId, ClientObservationAnchor anchor) {
        java.util.Objects.requireNonNull(anchor, "anchor");
        Request request = requests.get(correlationId);
        if (request == null) throw unavailable();
        synchronized (request) {
            if (request.closed) throw unavailable();
            request.anchor = anchor;
        }
    }

    public CompletableFuture<WorldViewCapture> associatedCapture(String correlationId, UUID actor) {
        Request request = requests.get(correlationId);
        if (request == null || request.closed) return dev.openallay.util.Java8Futures.failedFuture(unavailable());
        ClientObservationAnchor anchor = request.anchor;
        if (anchor == null || !anchor.image().isPresent()) return dev.openallay.util.Java8Futures.failedFuture(new JavascriptExecutionException("associated_view_unavailable", "No retained pre-Guide UI frame is associated with this request"));
        WorldViewCapture source = anchor.image().orElseThrow(() -> new java.util.NoSuchElementException("No value present"));
        if (!actor.equals(source.actorId())) return dev.openallay.util.Java8Futures.failedFuture(unavailable());
        CompletableFuture<WorldViewCapture> result = new CompletableFuture<>();
        synchronized (request) {
            if (request.closed) return dev.openallay.util.Java8Futures.failedFuture(unavailable());
            request.associations.add(result);
        }
        result.whenComplete((value, failure) -> {
            synchronized (request) { request.associations.remove(result); }
        });
        try {
            NamedThreads.startDaemon("openallay-associated-view", () -> {
                ImageAttachmentStore store = null;
                String owner = "observation-association:" + UUID.randomUUID();
                boolean retained = false;
                try {
                    store = images.apply(actor);
                    if (store == null) throw unavailable();
                    store.retain(actor, owner, dev.openallay.util.Java8Collections.listOf(source.image()));
                    synchronized (request) {
                        if (request.closed || request.anchor != anchor || result.isDone()
                                || requests.get(correlationId) != request) throw unavailable();
                        request.producers.add(new Producer(actor, store, owner, source.image()));
                        retained = true;
                    }
                    EvidenceMetadata original = source.evidence();
                    java.util.TreeMap<java.lang.String, java.lang.String> details = new java.util.TreeMap<>(original.details());
                    details.put("openallay:association", anchor.associationId().toString());
                    details.put("openallay:target", WorldViewRequest.Target.ASSOCIATED_UI.name());
                    EvidenceMetadata evidence = new EvidenceMetadata(original.authority(), original.completeness(),
                            original.capturedAt(), original.sourceId(), original.provenance(), original.gameVersion(),
                            original.loader(), details);
                    result.complete(new WorldViewCapture(source.captureId(), source.capturedAt(), source.actorId(),
                            source.dimension(), WorldViewRequest.Target.ASSOCIATED_UI, source.includedHud(),
                            source.includedGameUi(), source.sourceWidth(), source.sourceHeight(), source.guiScale(),
                            source.camera(), source.screen(), source.image(), evidence));
                } catch (Throwable failure) { result.completeExceptionally(failure); }
                finally {
                    if (!retained && store != null) {
                        try { store.release(actor, owner); } catch (java.io.IOException ignored) {}
                    }
                }
            });
        } catch (Throwable failure) { result.completeExceptionally(failure); }
        return result;
    }

    private static JavascriptExecutionException unavailable() {
        return new JavascriptExecutionException("world_observation_cancelled", "Observation request is no longer available");
    }

    @dev.openallay.value.ValueType(Producer.ValueSchemaProvider.class)
private static final class Producer {
    private final UUID actor;
    private final ImageAttachmentStore store;
    private final String owner;
    private final ImageReference reference;
    private Producer(UUID actor, ImageAttachmentStore store, String owner, ImageReference reference) {
        this.actor = actor;
        this.store = store;
        this.owner = owner;
        this.reference = reference;
    }
    public UUID actor() { return actor; }
    public ImageAttachmentStore store() { return store; }
    public String owner() { return owner; }
    public ImageReference reference() { return reference; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Producer)) return false;
        Producer that = (Producer) other;
        return java.util.Objects.equals(actor, that.actor) && java.util.Objects.equals(store, that.store) && java.util.Objects.equals(owner, that.owner) && java.util.Objects.equals(reference, that.reference);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actor);
        hash = 31 * hash + java.util.Objects.hashCode(store);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        return hash;
    }
    @Override public String toString() { return "Producer[actor=" + actor + ", store=" + store + ", owner=" + owner + ", reference=" + reference + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Producer> schema() {
            return new dev.openallay.value.ValueSchema<>(Producer.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Producer>>asList(new dev.openallay.value.ValueSchema.Component<>(Producer.class, "actor", Producer::actor), new dev.openallay.value.ValueSchema.Component<>(Producer.class, "store", Producer::store), new dev.openallay.value.ValueSchema.Component<>(Producer.class, "owner", Producer::owner), new dev.openallay.value.ValueSchema.Component<>(Producer.class, "reference", Producer::reference)), arguments -> new Producer((UUID) arguments[0], (ImageAttachmentStore) arguments[1], (String) arguments[2], (ImageReference) arguments[3]));
        }
    }
}

    private static final class Request {
        final WorldObservationCoordinator coordinator;
        final List<Producer> producers = new ArrayList<>();
        final List<CompletableFuture<ImageReference>> imports = new ArrayList<>();
        final List<CompletableFuture<WorldViewCapture>> associations = new ArrayList<>();
        volatile boolean closed;
        volatile ClientObservationAnchor anchor;
        Request(WorldObservationCoordinator coordinator) { this.coordinator = coordinator; }
    }
}
