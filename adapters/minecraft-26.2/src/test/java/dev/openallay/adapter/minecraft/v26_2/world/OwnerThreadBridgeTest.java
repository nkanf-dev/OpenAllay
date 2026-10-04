package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionEvidence;
import dev.openallay.api.extension.ExtensionException;
import dev.openallay.api.extension.ExtensionInvocation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** JDK executors and fixed SDK only. These are owner protocol tests, not game substitutes. */
@Timeout(10)
class OwnerThreadBridgeTest {
    @Test void ownerGateAndTargetRunOnTheirActualThreads() throws Exception {
        try (TestOwner client = new TestOwner(); TestOwner server = new TestOwner()) {
            List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
            OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, () -> client.isOwner() || server.isOwner());
            String value = bridge.callAfter(client.owner(() -> order.add("client")), server.owner(() -> order.add("server")), () -> {
                assertTrue(server.isOwner());
                assertFalse(client.isOwner());
                order.add("action");
                return "detached";
            });
            assertEquals("detached", value);
            assertEquals(List.of("client", "server", "action"), order);
            assertEquals(2, bridge.dispatches());
            bridge.close();
        }
    }

    @Test void staleClientGateNeverAdmitsServerWork() throws Exception {
        try (TestOwner client = new TestOwner(); TestOwner server = new TestOwner()) {
            OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, () -> client.isOwner() || server.isOwner());
            AtomicInteger calls = new AtomicInteger();
            ExtensionException failure = assertThrows(ExtensionException.class, () -> bridge.callAfter(
                    client.owner(() -> { throw new ExtensionException("stale_session", "Session changed"); }),
                    server.owner(() -> calls.incrementAndGet()), () -> calls.incrementAndGet()));
            assertEquals("stale_session", failure.code());
            assertEquals(0, calls.get());
            assertEquals(1, bridge.dispatches());
            bridge.close();
        }
    }

    @Test void cancellationRevokesQueuedTargetAfterClientGate() throws Exception {
        try (TestOwner client = new TestOwner(); TestOwner server = new TestOwner()) {
            CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), queued = new CountDownLatch(1);
            server.executor.execute(() -> { held.countDown(); await(release); });
            assertTrue(held.await(5, TimeUnit.SECONDS));
            Invocation invocation = new Invocation();
            OwnerThreadBridge bridge = new OwnerThreadBridge(invocation::requireActive, () -> client.isOwner() || server.isOwner());
            invocation.onCancel(bridge::close);
            AtomicInteger actions = new AtomicInteger();
            var target = new OwnerThreadBridge.Owner(task -> { server.executor.execute(task); queued.countDown(); },
                    server::isOwner, () -> {});
            Thread cancel = new Thread(() -> { await(queued); invocation.cancel(); release.countDown(); });
            cancel.start();
            try {
                ExtensionException failure = assertThrows(ExtensionException.class,
                        () -> bridge.callAfter(client.owner(() -> {}), target, actions::incrementAndGet));
                assertEquals("session_closed", failure.code());
                assertTrue(bridge.isClosed());
                server.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
                assertEquals(0, actions.get());
                bridge.close();
            } finally { release.countDown(); cancel.join(5000); }
        }
    }

    @Test void cancellationAfterAdmissionRetainsActualOutcome() throws Exception {
        try (TestOwner server = new TestOwner()) {
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, server::isOwner);
            Thread cancel = new Thread(() -> { await(started); bridge.close(); release.countDown(); });
            cancel.start();
            try {
                String actual = bridge.call(server.owner(() -> {}), () -> {
                    started.countDown(); await(release); return "actual-post-write-readback";
                });
                assertEquals("actual-post-write-readback", actual);
                assertTrue(bridge.isClosed());
                assertEquals("session_closed", assertThrows(ExtensionException.class,
                        () -> bridge.call(server.owner(() -> {}), () -> "never")).code());
            } finally { release.countDown(); cancel.join(5000); }
        }
    }

    @Test void interruptionAfterAdmissionWaitsForActualOutcomeAndRestoresInterrupt() throws Exception {
        try (TestOwner server = new TestOwner()) {
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            Thread worker = Thread.currentThread();
            OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, server::isOwner);
            Thread interrupter = new Thread(() -> { await(started); worker.interrupt(); bridge.close(); release.countDown(); });
            interrupter.start();
            try {
                assertEquals("readback", bridge.call(server.owner(() -> {}), () -> {
                    started.countDown(); await(release); return "readback";
                }));
                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(bridge.isClosed());
            } finally {
                Thread.interrupted();
                release.countDown();
                interrupter.join(5000);
            }
        }
    }

    @Test void noOwnerThreadCanWaitAndNoOtherWorkerCanUseTheSession() throws Exception {
        try (TestOwner server = new TestOwner()) {
            ExtensionException noWait = server.executor.submit(() -> {
                OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, server::isOwner);
                return assertThrows(ExtensionException.class, () -> bridge.call(server.owner(() -> {}), () -> null));
            }).get(5, TimeUnit.SECONDS);
            assertEquals("owner_thread_wait", noWait.code());
            OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, server::isOwner);
            AtomicReference<ExtensionException> wrongWorker = new AtomicReference<>();
            Thread other = new Thread(() -> wrongWorker.set(assertThrows(ExtensionException.class,
                    () -> bridge.call(server.owner(() -> {}), () -> null))));
            other.start(); other.join(5000);
            assertEquals("wrong_worker", wrongWorker.get().code());
            bridge.close(); bridge.close();
        }
    }

    @Test void wrongOwnerAndRevocationRecheckPreventAction() {
        AtomicInteger actions = new AtomicInteger();
        OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, () -> false);
        var wrongOwner = new OwnerThreadBridge.Owner(Runnable::run, () -> false, () -> {});
        assertEquals("wrong_owner", assertThrows(ExtensionException.class,
                () -> bridge.call(wrongOwner, actions::incrementAndGet)).code());
        assertEquals(0, actions.get());
        AtomicBoolean active = new AtomicBoolean(true), executing = new AtomicBoolean();
        OwnerThreadBridge revoked = new OwnerThreadBridge(() -> {
            if (!active.get()) throw new ExtensionException("session_closed", "Invocation ended");
        }, () -> false);
        var owner = new OwnerThreadBridge.Owner(task -> {
            executing.set(true); try { task.run(); } finally { executing.set(false); }
        }, executing::get, () -> active.set(false));
        assertEquals("session_closed", assertThrows(ExtensionException.class,
                () -> revoked.call(owner, actions::incrementAndGet)).code());
        assertEquals(0, actions.get());
        bridge.close(); revoked.close();
    }

    @Test void executorAdmissionFailureIsTypedAndCheckedCausesStayDiagnostic() {
        OwnerThreadBridge bridge = new OwnerThreadBridge(() -> {}, () -> false);
        var reject = new OwnerThreadBridge.Owner(task -> { throw new java.util.concurrent.RejectedExecutionException("diagnostic"); },
                () -> false, () -> {});
        ExtensionException failure = assertThrows(ExtensionException.class, () -> bridge.call(reject, () -> null));
        assertEquals("native_failure", failure.code());
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, failure.getCause());
        assertFalse(failure.summary().contains("diagnostic"));
        bridge.close();
        Exception checked = new Exception("native diagnostic");
        RuntimeException typed = OwnerThreadBridge.propagate(checked);
        assertInstanceOf(ExtensionException.class, typed);
        assertSame(checked, typed.getCause());
    }

    @Test void sdkImmediateCancellationClosesBeforeDispatch() {
        Invocation invocation = new Invocation(); invocation.cancel();
        OwnerThreadBridge bridge = new OwnerThreadBridge(invocation::requireActive, () -> false);
        invocation.onCancel(bridge::close);
        assertTrue(bridge.isClosed());
        assertEquals("session_closed", assertThrows(ExtensionException.class, bridge::checkWorker).code());
        assertEquals(0, bridge.dispatches());
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Owner coordination timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static final class TestOwner implements AutoCloseable {
        private final AtomicReference<Thread> thread = new AtomicReference<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor(action -> {
            Thread owner = new Thread(action, "adapter-owner-protocol-test");
            owner.setDaemon(true); thread.set(owner); return owner;
        });
        boolean isOwner() { return Thread.currentThread() == thread.get(); }
        OwnerThreadBridge.Owner owner(Runnable validate) { return new OwnerThreadBridge.Owner(executor, this::isOwner, validate); }
        @Override public void close() throws InterruptedException { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    private static final class Invocation implements ExtensionInvocation {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
        void cancel() { if (cancelled.compareAndSet(false,true)) listeners.forEach(Runnable::run); }
        @Override public String extensionId() { return "openallay_builder"; }
        @Override public String correlationId() { return "owner-protocol-test"; }
        @Override public Instant capturedAt() { return Instant.EPOCH; }
        @Override public CallerKind callerKind() { return CallerKind.PLAYER; }
        @Override public UUID callerUuid() { return new UUID(1,2); }
        @Override public Optional<String> playerDimension() { return Optional.of("minecraft:overworld"); }
        @Override public void requireActive() { if (cancelled.get()) throw new ExtensionException("session_closed", "Invocation ended"); }
        @Override public boolean isCancelled() { return cancelled.get(); }
        @Override public void onCancel(Runnable listener) { listeners.add(listener); if (cancelled.get()) listener.run(); }
        @Override public boolean hasCapability(String capabilityId) { return "openallay_builder:world_write".equals(capabilityId); }
        @Override public void requireCapability(String capabilityId) { requireActive(); if (!hasCapability(capabilityId)) throw new ExtensionException("capability_required", "World write grant is required"); }
        @Override public boolean completedSuccessfully() { return false; }
        @Override public void recordEvidence(ExtensionEvidence evidence) {}
    }
}
