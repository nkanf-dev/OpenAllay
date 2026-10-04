package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class WorldObservationProducerTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 1, 1, 10);

    @Test void toolClosureStopsNativeWorkButPreservesProducerUntilCustodyBarrier() {
        MemoryStore store = new MemoryStore();
        FakeCoordinator coordinator = new FakeCoordinator();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.configureImages(actor -> store);
        observations.capture("request", coordinator);
        assertEquals(IMAGE, observations.captureImport("request", ACTOR, new byte[] {1}).join());
        observations.closeObservations("request");
        assertTrue(coordinator.closed);
        assertTrue(observations.bridge("request", new CancellationSignal()).isEmpty());
        assertEquals(List.of(IMAGE), observations.producerReferences("request"));
        assertEquals(1, store.owners.size());
        observations.releaseImageProducers("request").join();
        assertTrue(store.owners.isEmpty());
        assertTrue(observations.producerReferences("request").isEmpty());
    }

    @Test void lateNativeImportAfterRequestCloseReleasesItsOwnPin() throws Exception {
        MemoryStore store = new MemoryStore();
        store.blockImport = true;
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.configureImages(actor -> store);
        observations.capture("request", new FakeCoordinator());
        var imported = observations.captureImport("request", ACTOR, new byte[] {1});
        assertTrue(store.importStarted.await(5, TimeUnit.SECONDS));
        observations.closeObservations("request");
        assertTrue(imported.isCompletedExceptionally());
        observations.releaseImageProducers("request").join();
        store.allowImport.countDown();
        assertTrue(store.released.await(5, TimeUnit.SECONDS));
        assertTrue(store.owners.isEmpty());
    }

    @Test void everyCaptureImportStaysPinnedAndTypedUntilExplicitRelease() {
        MemoryStore store = new MemoryStore();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.configureImages(actor -> store);
        observations.capture("request", new FakeCoordinator());
        observations.captureImport("request", ACTOR, new byte[] {1}).join();
        observations.captureImport("request", ACTOR, new byte[] {2}).join();
        assertEquals(2, store.owners.size());
        assertEquals(List.of(IMAGE), observations.producerReferences("request"));
        observations.releaseImageProducers("request").join();
        assertTrue(store.owners.isEmpty());
    }

    @Test void coordinatorTeardownFailureCannotOrphanImageProducerPins() {
        MemoryStore store = new MemoryStore();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.configureImages(actor -> store);
        observations.capture("request", new FakeCoordinator() {
            @Override public void close() { throw new IllegalStateException("fixture teardown failure"); }
        });
        observations.captureImport("request", ACTOR, new byte[] {1}).join();
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> observations.releaseImageProducers("request").join());
        assertTrue(store.owners.isEmpty());
        assertTrue(observations.producerReferences("request").isEmpty());
    }

    @Test void expiredNativeAdmissionDuringImportReleasesThatImportPin() throws Exception {
        MemoryStore store = new MemoryStore();
        store.blockImport = true;
        java.util.concurrent.atomic.AtomicBoolean admitted = new java.util.concurrent.atomic.AtomicBoolean(true);
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.configureImages(actor -> store);
        observations.capture("request", new FakeCoordinator());
        var imported = observations.captureImport("request", ACTOR, new byte[] {1}, admitted::get);
        assertTrue(store.importStarted.await(5, TimeUnit.SECONDS));
        admitted.set(false);
        store.allowImport.countDown();
        assertThrows(java.util.concurrent.CompletionException.class, imported::join);
        assertTrue(store.released.await(5, TimeUnit.SECONDS));
        assertTrue(store.owners.isEmpty());
        assertTrue(observations.producerReferences("request").isEmpty());
        observations.releaseImageProducers("request").join();
    }

    @Test void associatedCaptureKeepsEarlierSourceTimeAndPinsItsTypedImage() {
        MemoryStore store = new MemoryStore();
        WorldObservationRuntime observations = new WorldObservationRuntime();
        observations.configureImages(actor -> store);
        observations.capture("request", new FakeCoordinator());
        var at = java.time.Instant.EPOCH;
        var evidence = new dev.openallay.context.EvidenceMetadata(dev.openallay.context.DataAuthority.CLIENT_VISIBLE,
                dev.openallay.context.DataCompleteness.PARTIAL, at, "minecraft:client_view", "minecraft:native_render_target",
                "26.2", "fabric", Map.of());
        var empty = new WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0, new com.google.gson.JsonObject(), true, "");
        var camera = new WorldFocusObservation.Camera(1, 2, 3, 90, 20, 70, "first_person", true, false, ACTOR);
        var screen = new WorldFocusObservation.Screen("test.Chest", "Chest", 20, 10, false, true, "game_ui");
        var focus = new WorldFocusObservation(at, ACTOR, "minecraft:overworld", camera,
                new WorldFocusObservation.Target("none", null, null, null), empty, empty, screen,
                new WorldFocusObservation.Menu("test.Menu", 1, 2, "minecraft:generic_9x3", true, true, true, 27, empty, ""),
                new WorldFocusObservation.Hover(2, 3, false, "none", -1, -1, null, ""), evidence);
        var source = new WorldViewCapture("old-menu-frame", at, ACTOR, "minecraft:overworld",
                WorldViewRequest.Target.GAME_UI, true, true, 20, 10, 1, camera, screen, IMAGE, evidence);
        observations.associate("request", new ClientObservationAnchor(UUID.randomUUID(), at, focus, java.util.Optional.of(source)));
        var associated = observations.associatedCapture("request", ACTOR).join();
        assertEquals(at, associated.capturedAt());
        assertEquals("old-menu-frame", associated.captureId());
        assertEquals(WorldViewRequest.Target.ASSOCIATED_UI, associated.target());
        assertEquals(screen, associated.screen());
        assertEquals(IMAGE, associated.image());
        assertEquals(List.of(IMAGE), observations.producerReferences("request"));
        observations.releaseImageProducers("request").join();
        assertTrue(store.owners.isEmpty());
    }

    private static final class MemoryStore implements ImageAttachmentStore {
        final Set<String> owners = ConcurrentHashMap.newKeySet();
        final CountDownLatch importStarted = new CountDownLatch(1);
        final CountDownLatch allowImport = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        boolean blockImport;
        @Override public ImageInputLimits limits() { return ImageInputLimits.defaults(); }
        @Override public ImageReference importImage(UUID actor, byte[] bytes) { return IMAGE; }
        @Override public ImageReference importImage(UUID actor, String owner, byte[] bytes) throws IOException {
            importStarted.countDown();
            if (blockImport) {
                try { if (!allowImport.await(5, TimeUnit.SECONDS)) throw new IOException("fixture import timed out"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
            }
            owners.add(owner);
            return IMAGE;
        }
        @Override public byte[] read(UUID actor, ImageReference reference) { return new byte[] {1}; }
        @Override public void retain(UUID actor, String owner, List<ImageReference> refs) { owners.add(owner); }
        @Override public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> refs) {}
        @Override public void release(UUID actor, String owner) { owners.remove(owner); released.countDown(); }
        @Override public int collect(UUID actor) { return 0; }
    }

    private static class FakeCoordinator implements WorldObservationCoordinator {
        boolean closed;
        @Override public CompletionStage<BlockObservation> inspect(WorldObservationRequest request, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public CompletionStage<EntityObservation> entities(WorldObservationRequest request, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public CompletionStage<WorldEntitySnapshot> entity(String observationId, CancellationSignal cancellation) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public void close() { closed = true; }
    }
}
