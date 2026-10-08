package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.client.ClientEventDispatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class GuideServiceManagerSerialExecutorTest {
    @Test
    void completedPredecessorDispatchesInlineOutsideSerialMonitor() {
        AtomicReference<ClientEventDispatcher> serial = new AtomicReference<>();
        List<String> events = new ArrayList<>();
        Executor owner = task -> {
            assertFalse(Thread.holdsLock(serial.get()), "owner dispatch must not hold the serial monitor");
            task.run();
        };
        serial.set(GuideServiceManager.serialExecutor(owner::execute, CompletableFuture.completedFuture(null)));
        serial.get().execute(() -> {
            assertFalse(Thread.holdsLock(serial.get()), "inline user callback must not hold the serial monitor");
            events.add("inline");
        });
        assertEquals(List.of("inline"), events);
    }

    @Test
    void clientQueueCallbackCanEnqueueWhileHistoryPostingWaitsForClientQueue() throws Exception {
        Object clientQueue = new Object();
        ArrayDeque<Runnable> posted = new ArrayDeque<>();
        CountDownLatch clientHoldsQueue = new CountDownLatch(1);
        CountDownLatch historyEnteringQueue = new CountDownLatch(1);
        CountDownLatch clientFinishedEnqueue = new CountDownLatch(1);
        CountDownLatch historyFinishedPost = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Executor owner = task -> {
            historyEnteringQueue.countDown();
            synchronized (clientQueue) { posted.addLast(task); }
        };
        ClientEventDispatcher serial = GuideServiceManager.serialExecutor(
                owner::execute, CompletableFuture.completedFuture(null));
        List<String> events = new ArrayList<>();
        Thread client = daemon("serial-test-client", () -> {
            try {
                synchronized (clientQueue) {
                    clientHoldsQueue.countDown();
                    await(historyEnteringQueue);
                    // The real client drains callbacks while holding its native task queue.
                    serial.execute(() -> events.add("client callback"));
                    clientFinishedEnqueue.countDown();
                }
            } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
        });
        Thread history = daemon("serial-test-history", () -> {
            try {
                await(clientHoldsQueue);
                serial.execute(() -> events.add("history completion"));
                historyFinishedPost.countDown();
            } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
        });
        client.start();
        history.start();
        await(clientFinishedEnqueue);
        await(historyFinishedPost);
        client.join(TimeUnit.SECONDS.toMillis(3));
        history.join(TimeUnit.SECONDS.toMillis(3));
        assertFalse(client.isAlive());
        assertFalse(history.isAlive());
        assertNull(failure.get());
        synchronized (clientQueue) {
            while (!posted.isEmpty()) posted.removeFirst().run();
        }
        assertEquals(List.of("history completion", "client callback"), events,
                "the second admission must return without overtaking the blocked first post");
    }

    @Test
    void delayedGateKeepsAdmittedOrderAndReentrantEnqueueBehindAlreadyAdmittedWork() {
        CompletableFuture<Void> readiness = new CompletableFuture<>();
        List<String> events = new ArrayList<>();
        Executor owner = Runnable::run;
        ClientEventDispatcher serial = GuideServiceManager.serialExecutor(owner::execute, readiness);
        serial.execute(() -> {
            events.add("first");
            serial.execute(() -> events.add("reentrant"));
            events.add("first returned");
        });
        serial.execute(() -> events.add("second"));
        assertTrue(events.isEmpty(), "old connection/history barrier must settle before any post");
        readiness.complete(null);
        serial.execute(() -> events.add("last"));
        assertEquals(List.of("first", "first returned", "second", "reentrant", "last"), events);
    }

    @Test
    void exceptionalAndCancelledReadinessStillReleaseFifoPosts() {
        for (boolean cancellation : List.of(false, true)) {
            CompletableFuture<Void> readiness = new CompletableFuture<>();
            List<Integer> events = new ArrayList<>();
            Executor owner = Runnable::run;
            ClientEventDispatcher serial = GuideServiceManager.serialExecutor(owner::execute, readiness);
            serial.execute(() -> events.add(1));
            serial.execute(() -> events.add(2));
            assertTrue(events.isEmpty());
            if (cancellation) readiness.cancel(false);
            else readiness.completeExceptionally(new IllegalStateException("failed cleanup"));
            serial.execute(() -> events.add(3));
            assertEquals(List.of(1, 2, 3), events);
            assertTrue(readiness.isCompletedExceptionally(), "the original custody failure must remain intact");
        }
    }

    @Test
    void runtimeAndFatalPostingFailuresRemainExceptionalReceiptsAndDoNotPoisonNextAdmission() throws Exception {
        for (Throwable thrown : List.of(new IllegalStateException("posting rejected"), new AssertionError("fatal post"))) {
            List<String> events = new ArrayList<>();
            Executor owner = task -> {
                if (events.isEmpty()) {
                    events.add("rejected");
                    if (thrown instanceof Error error) throw error;
                    throw (RuntimeException) thrown;
                }
                task.run();
            };
            ClientEventDispatcher serial = GuideServiceManager.serialExecutor(
                    owner::execute, CompletableFuture.completedFuture(null));
            // Existing thenRun semantics capture even Error in the scheduling receipt.
            assertDoesNotThrow(() -> serial.execute(() -> events.add("must not run")));
            var tail = serial.getClass().getDeclaredField("tail");
            tail.setAccessible(true);
            CompletableFuture<?> receipt = (CompletableFuture<?>) tail.get(serial);
            var failure = assertThrows(java.util.concurrent.CompletionException.class, receipt::join);
            assertSame(thrown, failure.getCause());
            serial.execute(() -> events.add("continued"));
            assertEquals(List.of("rejected", "continued"), events);
        }
    }

    private static Thread daemon(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(3, TimeUnit.SECONDS), "coordinated executor handoff must complete without deadlock");
    }
}
