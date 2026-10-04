package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.image.ImageReference;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class WorldNativeWaitBudgetTest {
    private static final Duration SCRIPT_BUDGET = Duration.ofMillis(300);
    private static final long NATIVE_WAIT_MILLIS = 650;
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final EvidenceMetadata EVIDENCE = WorldObservationTestFixtures.evidence(
            true, DataCompleteness.COMPLETE, "minecraft:client_focus");
    private static final WorldFocusObservation.Camera CAMERA = new WorldFocusObservation.Camera(
            1, 2, 3, 90, 30, 70, "first_person", true, false, ACTOR);
    private static final WorldFocusObservation.Screen SCREEN = new WorldFocusObservation.Screen(
            "", "", 20, 10, false, false, "gameplay");

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            return world.inspect({from:{x:0,y:0,z:0},to:{x:0,y:0,z:0}}).blocks[0].id; | minecraft:oak_log
            return world.entities({from:{x:0,y:0,z:0},to:{x:0,y:0,z:0}}).entities[0].type; | minecraft:cow
            return world.entity('entity-1').type; | minecraft:cow
            return world.focus().target.kind; | none
            return world.capture().captureId; | frame-1
            """)
    void everyNativeWorldWaitCanExceedTheInterpreterBudget(String source, String expected) {
        try (DelayedCoordinator coordinator = new DelayedCoordinator()) {
            assertEquals(expected, execute(source, coordinator, new CancellationSignal()).value().getAsString());
            assertEquals(1, coordinator.calls.get());
        }
    }

    @Test
    void focusAndCaptureWaitsDoNotHideAnUntrustedLoopAfterTheNativeWork() {
        try (DelayedCoordinator coordinator = new DelayedCoordinator()) {
            assertTrue(execute("world.focus(); world.capture(); return {done:true};",
                    coordinator, new CancellationSignal()).value().getAsJsonObject().get("done").getAsBoolean());
            JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                    () -> execute("world.focus(); world.capture(); while (true) {}",
                            coordinator, new CancellationSignal()));
            assertEquals("javascript_timeout", failure.code());
            assertEquals(4, coordinator.calls.get());
        }
    }

    @Test
    void captureOptionGetterStaysInsideTheInterpreterBudget() {
        try (DelayedCoordinator coordinator = new DelayedCoordinator()) {
            JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                    () -> execute("return world.capture({get target() { while (true) {} }});",
                            coordinator, new CancellationSignal()));
            assertEquals("javascript_timeout", failure.code());
            assertEquals(0, coordinator.calls.get());
        }
    }

    @Test
    void cancellationDuringNativeWaitKeepsTheAgentCancellationCode() throws Exception {
        try (DelayedCoordinator coordinator = new DelayedCoordinator();
                var worker = Executors.newSingleThreadExecutor()) {
            CancellationSignal cancellation = new CancellationSignal();
            var result = worker.submit(() -> assertThrows(ModelClientException.class,
                    () -> execute("return world.focus();", coordinator, cancellation)));
            assertTrue(coordinator.entered.await(2, TimeUnit.SECONDS));
            cancellation.cancel();
            assertEquals("agent_cancelled", result.get(2, TimeUnit.SECONDS).failure().code());
        }
    }

    @Test
    void interruptionDuringNativeWaitKeepsTheWorkerInterruptedAndCancelsExecution() throws Exception {
        try (DelayedCoordinator coordinator = new DelayedCoordinator();
                var worker = Executors.newSingleThreadExecutor()) {
            AtomicBoolean interrupted = new AtomicBoolean();
            var result = worker.submit(() -> {
                try {
                    return assertThrows(JavascriptExecutionException.class,
                            () -> execute("return world.focus();", coordinator, new CancellationSignal()));
                } finally {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    Thread.interrupted();
                }
            });
            assertTrue(coordinator.entered.await(2, TimeUnit.SECONDS));
            coordinator.scriptThread.get().interrupt();
            assertEquals("javascript_cancelled", result.get(2, TimeUnit.SECONDS).code());
            assertTrue(interrupted.get());
        }
    }

    private static dev.openallay.script.JavascriptExecution execute(
            String source, DelayedCoordinator coordinator, CancellationSignal cancellation) {
        JavascriptWorldBridge bridge = new JavascriptWorldBridge(
                coordinator, cancellation, ignored -> {}, ignored -> {});
        return new RhinoJavascriptRuntime(SCRIPT_BUDGET).execute(
                source, Map.of(), Map.of(), Map.of(), cancellation, null, bridge);
    }

    private static final class DelayedCoordinator implements WorldObservationCoordinator {
        private final WorldObservationTestFixtures.Coordinator detached =
                new WorldObservationTestFixtures.Coordinator(true, true);
        private final ScheduledExecutorService owner = Executors.newSingleThreadScheduledExecutor();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final AtomicReference<Thread> scriptThread = new AtomicReference<>();
        private final AtomicInteger calls = new AtomicInteger();
        private final List<CompletableFuture<?>> pending = new java.util.concurrent.CopyOnWriteArrayList<>();

        private <T> CompletionStage<T> delayed(T value) {
            CompletableFuture<T> result = new CompletableFuture<>() {
                @Override public T get(long timeout, TimeUnit unit) throws InterruptedException,
                        java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
                    scriptThread.set(Thread.currentThread());
                    entered.countDown();
                    return super.get(timeout, unit);
                }
            };
            pending.add(result);
            calls.incrementAndGet();
            owner.schedule(() -> result.complete(value), NATIVE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return result;
        }

        @Override public CompletionStage<BlockObservation> inspect(
                WorldObservationRequest request, CancellationSignal cancellation) {
            return delayed(detached.inspect(request, cancellation).toCompletableFuture().join());
        }

        @Override public CompletionStage<EntityObservation> entities(
                WorldObservationRequest request, CancellationSignal cancellation) {
            return delayed(detached.entities(request, cancellation).toCompletableFuture().join());
        }

        @Override public CompletionStage<WorldEntitySnapshot> entity(
                String observationId, CancellationSignal cancellation) {
            return delayed(detached.entity(observationId, cancellation).toCompletableFuture().join());
        }

        @Override public CompletionStage<WorldFocusObservation> focus(CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            WorldFocusObservation.Item empty = new WorldFocusObservation.Item(
                    "minecraft:air", 0, "", 0, 0, new JsonObject(), true, "");
            return delayed(new WorldFocusObservation(
                    EVIDENCE.capturedAt(), ACTOR, WorldObservationTestFixtures.DIMENSION, CAMERA,
                    new WorldFocusObservation.Target("none", null, null, null), empty, empty, SCREEN,
                    new WorldFocusObservation.Menu("test.Menu", 0, 0, "", false, false, true, 0, empty, ""),
                    new WorldFocusObservation.Hover(0, 0, true, "none", -1, -1, null, ""), EVIDENCE));
        }

        @Override public CompletionStage<WorldViewCapture> capture(
                WorldViewRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return delayed(new WorldViewCapture("frame-1", EVIDENCE.capturedAt(), ACTOR,
                    WorldObservationTestFixtures.DIMENSION, request.target(), false, false, 20, 10, 1,
                    CAMERA, SCREEN, new ImageReference("a".repeat(64), "image/png", 20, 10, 100), EVIDENCE));
        }

        @Override public void close() {
            pending.forEach(future -> future.cancel(false));
            owner.shutdownNow();
        }
    }
}
