package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImageReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class WorldObservationDetachTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 1, 1, 1);

    @Test void oldCustodyCompletionNeverClosesNewRequestWithTheSameCorrelation() throws Exception {
        for (boolean failure : List.of(false, true)) {
            Store store = new Store();
            WorldObservationRuntime runtime = new WorldObservationRuntime();
            runtime.configureImages(actor -> store);
            Coordinator old = new Coordinator();
            runtime.capture("same-correlation", old);
            runtime.captureImport("same-correlation", ACTOR, new byte[] {1}).get(2, TimeUnit.SECONDS);
            CompletableFuture<Void> custody = new CompletableFuture<>();
            CompletableFuture<Void> detached = runtime.detachConnectionState(custody);
            assertTrue(old.closed);
            assertEquals(1, store.owners.size(), "detach must stop native work without dropping its pins");
            Coordinator next = new Coordinator();
            runtime.capture("same-correlation", next);
            runtime.captureImport("same-correlation", ACTOR, new byte[] {1}).get(2, TimeUnit.SECONDS);
            assertEquals(2, store.owners.size());
            if (failure) custody.completeExceptionally(new IllegalStateException("injected custody failure"));
            else custody.complete(null);
            if (failure) assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> detached.get(2, TimeUnit.SECONDS));
            else detached.get(2, TimeUnit.SECONDS);
            assertFalse(next.closed);
            assertTrue(runtime.bridge("same-correlation", new CancellationSignal()).isPresent());
            assertEquals(failure ? 2 : 1, store.owners.size(),
                    "failed custody is not permission to drop the old request's durable producer owner");
            assertEquals(List.of(IMAGE), runtime.producerReferences("same-correlation"));
            runtime.releaseImageProducers("same-correlation").get(2, TimeUnit.SECONDS);
            assertEquals(failure ? 1 : 0, store.owners.size());
            if (failure) {
                assertEquals(0, store.collect(ACTOR));
                assertArrayEquals(new byte[] {1}, store.read(ACTOR, IMAGE),
                        "the actual retained source remains recoverable after the new request closes");
            }
        }
    }

    private static final class Store implements ImageAttachmentStore {
        final Set<String> owners = ConcurrentHashMap.newKeySet();
        volatile boolean bytesPresent;
        public ImageInputLimits limits() { return ImageInputLimits.defaults(); }
        public ImageReference importImage(UUID actor, byte[] bytes) { return IMAGE; }
        public ImageReference importImage(UUID actor, String owner, byte[] bytes) { owners.add(owner); bytesPresent = true; return IMAGE; }
        public byte[] read(UUID actor, ImageReference reference) throws java.io.IOException {
            if (!bytesPresent) throw new java.io.IOException("collected actual bytes");
            return new byte[] {1};
        }
        public void retain(UUID actor, String owner, List<ImageReference> references) { owners.add(owner); }
        public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners) {}
        public void release(UUID actor, String owner) { owners.remove(owner); }
        public int collect(UUID actor) {
            if (owners.isEmpty() && bytesPresent) { bytesPresent = false; return 1; }
            return 0;
        }
    }

    private static final class Coordinator implements WorldObservationCoordinator {
        boolean closed;
        public CompletionStage<BlockObservation> inspect(WorldObservationRequest request, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        public CompletionStage<EntityObservation> entities(WorldObservationRequest request, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        public CompletionStage<WorldEntitySnapshot> entity(String id, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        public void close() { closed = true; }
    }
}
