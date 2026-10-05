package dev.openallay.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** JDK-only workers: no game, model, network, or native resource is needed. */
final class NamedThreadsTest {
    @Test
    void unstartedWorkerIsNamedDaemonAndRunsOnItsOwnThread() throws Exception {
        AtomicReference<Thread> actual = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Thread worker = NamedThreads.unstartedDaemon("test-unstarted", () -> {
            actual.set(Thread.currentThread());
            completed.countDown();
        });
        assertEquals(Thread.State.NEW, worker.getState());
        assertEquals("test-unstarted", worker.getName());
        assertTrue(worker.isDaemon());
        worker.start();
        assertTrue(completed.await(2, TimeUnit.SECONDS));
        worker.join(2000);
        assertEquals(Thread.State.TERMINATED, worker.getState());
        assertEquals(worker, actual.get());
        assertNotSame(Thread.currentThread(), actual.get());
    }

    @Test
    void fixedAndCountedFactoriesKeepIndependentWorkersAndOneSharedCounter() {
        var fixed = NamedThreads.daemonFactory("test-fixed");
        Thread first = fixed.newThread(() -> {});
        Thread second = fixed.newThread(() -> {});
        assertNotSame(first, second);
        assertEquals("test-fixed", first.getName());
        assertEquals("test-fixed", second.getName());
        assertTrue(first.isDaemon());
        assertTrue(second.isDaemon());
        var counted = NamedThreads.daemonFactory("test-counted-", 7);
        assertEquals("test-counted-7", counted.newThread(() -> {}).getName());
        assertEquals("test-counted-8", counted.newThread(() -> {}).getName());
        assertThrows(IllegalArgumentException.class,
                () -> NamedThreads.daemonFactory("test-counted-", -1));
    }

    @Test
    void blockingOwnerDoesNotPreventIndependentCancellationWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> cancelled = new AtomicReference<>();
        Thread owner = NamedThreads.startDaemon("test-owner", () -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        Thread cancellation = null;
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            cancellation = NamedThreads.startDaemon("test-cancellation", () -> {
                cancelled.set(Thread.currentThread());
                release.countDown();
            });
            cancellation.join(2000);
            owner.join(2000);
            assertEquals(Thread.State.TERMINATED, cancellation.getState());
            assertEquals(Thread.State.TERMINATED, owner.getState());
            assertEquals(cancellation, cancelled.get());
            assertNotSame(owner, cancellation);
            assertTrue(cancellation.isDaemon());
        } finally {
            release.countDown();
            owner.interrupt();
            owner.join(2000);
            if (cancellation != null) cancellation.join(2000);
        }
    }

    @Test
    void serialExecutorsShareCounterButKeepOneWorkerEachAndRejectAfterShutdown() throws Exception {
        var factory = NamedThreads.daemonFactory("test-serial-", 0);
        var first = Executors.newSingleThreadExecutor(factory);
        var second = Executors.newSingleThreadExecutor(factory);
        try {
            java.util.concurrent.Callable<Thread> currentThread = Thread::currentThread;
            Thread firstWorker = first.submit(currentThread).get(2, TimeUnit.SECONDS);
            Thread repeatedWorker = first.submit(currentThread).get(2, TimeUnit.SECONDS);
            Thread secondWorker = second.submit(currentThread).get(2, TimeUnit.SECONDS);
            assertEquals(firstWorker, repeatedWorker);
            assertNotSame(firstWorker, secondWorker);
            assertEquals("test-serial-0", firstWorker.getName());
            assertEquals("test-serial-1", secondWorker.getName());
            assertTrue(firstWorker.isDaemon());
            assertTrue(secondWorker.isDaemon());
            first.shutdown();
            assertThrows(java.util.concurrent.RejectedExecutionException.class,
                    () -> first.execute(() -> {}));
        } finally {
            first.shutdownNow();
            second.shutdownNow();
            assertTrue(first.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(second.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void foreignCreatorAndLaterWorkerLoaderDoNotReplaceFrameworkOwner() throws Exception {
        Thread caller = Thread.currentThread();
        ClassLoader callerBefore = caller.getContextClassLoader();
        ClassLoader foreignCreator = new ClassLoader(null) {};
        ClassLoader foreignWorker = new ClassLoader(null) {};
        AtomicReference<ClassLoader> observed = new AtomicReference<>();
        AtomicReference<Class<?>> resolvedParent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker;
        try {
            caller.setContextClassLoader(foreignCreator);
            worker = NamedThreads.unstartedDaemon("test-loader-owner", () -> {
                observed.set(Thread.currentThread().getContextClassLoader());
                try {
                    resolvedParent.set(Thread.currentThread().getContextClassLoader()
                            .loadClass("dev.openallay.model.ModelClientException"));
                } catch (ClassNotFoundException missing) {
                    failure.set(missing);
                }
            });
            assertSame(foreignCreator, worker.getContextClassLoader());
        } finally {
            caller.setContextClassLoader(callerBefore);
        }
        // The execution boundary, not construction-time inheritance, owns class loading.
        worker.setContextClassLoader(foreignWorker);
        worker.start();
        worker.join(2000);
        assertEquals(Thread.State.TERMINATED, worker.getState());
        assertSame(NamedThreads.class.getClassLoader(), observed.get());
        assertSame(dev.openallay.model.ModelClientException.class, resolvedParent.get());
        assertNull(failure.get());
        assertSame(foreignWorker, worker.getContextClassLoader());
        assertSame(callerBefore, caller.getContextClassLoader());
        assertEquals("test-loader-owner", worker.getName());
        assertTrue(worker.isDaemon());
    }

    @Test
    void finallyRestoresLoaderBeforeUncaughtErrorIsReported() throws Exception {
        ClassLoader before = new ClassLoader(null) {};
        ClassLoader changedByTask = new ClassLoader(null) {};
        AtomicReference<ClassLoader> atFailure = new AtomicReference<>();
        AtomicReference<Throwable> reported = new AtomicReference<>();
        AssertionError expected = new AssertionError("worker failure");
        Thread worker = NamedThreads.unstartedDaemon("test-loader-finally", () -> {
            assertSame(NamedThreads.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(changedByTask);
            throw expected;
        });
        worker.setContextClassLoader(before);
        worker.setUncaughtExceptionHandler((thread, failure) -> {
            atFailure.set(thread.getContextClassLoader());
            reported.set(failure);
        });
        worker.start();
        worker.join(2000);
        assertEquals(Thread.State.TERMINATED, worker.getState());
        assertSame(expected, reported.get());
        assertSame(before, atFailure.get());
        assertSame(before, worker.getContextClassLoader());
    }

    @Test
    void bothFactoriesUseFrameworkOwnerEvenWhenExecutorCreatesFromForeignThread() throws Exception {
        var fixed = NamedThreads.daemonFactory("test-owned-fixed");
        var counted = NamedThreads.daemonFactory("test-owned-counted-", 4);
        Thread caller = Thread.currentThread();
        ClassLoader callerBefore = caller.getContextClassLoader();
        ClassLoader foreign = new ClassLoader(null) {};
        var first = Executors.newSingleThreadExecutor(fixed);
        var second = Executors.newSingleThreadExecutor(counted);
        var third = Executors.newSingleThreadExecutor(counted);
        try {
            caller.setContextClassLoader(foreign);
            java.util.concurrent.Callable<Thread> inspect = () -> {
                assertSame(NamedThreads.class.getClassLoader(),
                        Thread.currentThread().getContextClassLoader());
                return Thread.currentThread();
            };
            Thread a = first.submit(inspect).get(2, TimeUnit.SECONDS);
            Thread b = second.submit(inspect).get(2, TimeUnit.SECONDS);
            Thread c = third.submit(inspect).get(2, TimeUnit.SECONDS);
            assertNotSame(a, b);
            assertNotSame(b, c);
            assertEquals("test-owned-fixed", a.getName());
            assertEquals("test-owned-counted-4", b.getName());
            assertEquals("test-owned-counted-5", c.getName());
            assertTrue(a.isDaemon());
            assertTrue(b.isDaemon());
            assertTrue(c.isDaemon());
        } finally {
            caller.setContextClassLoader(callerBefore);
            first.shutdownNow();
            second.shutdownNow();
            third.shutdownNow();
            assertTrue(first.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(second.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(third.awaitTermination(2, TimeUnit.SECONDS));
        }
        assertSame(callerBefore, caller.getContextClassLoader());
    }
}
