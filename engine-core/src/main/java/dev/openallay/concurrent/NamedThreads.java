package dev.openallay.concurrent;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/** Named daemon workers with the framework class-loading owner. No worker pool is shared. */
public final class NamedThreads {
    private NamedThreads() {}

    public static Thread unstartedDaemon(String name, Runnable task) {
        Objects.requireNonNull(task, "task");
        Thread thread = new Thread(() -> runOwned(task), Objects.requireNonNull(name, "name"));
        thread.setDaemon(true);
        return thread;
    }

    /** Do not inherit a provider callback's loader or retain an Extension's isolated loader. */
    private static void runOwned(Runnable task) {
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        try {
            current.setContextClassLoader(NamedThreads.class.getClassLoader());
            task.run();
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    public static Thread startDaemon(String name, Runnable task) {
        Thread thread = unstartedDaemon(name, task);
        thread.start();
        return thread;
    }

    /** A fixed name is retained for every worker, including replacement executor workers. */
    public static ThreadFactory daemonFactory(String name) {
        Objects.requireNonNull(name, "name");
        return task -> unstartedDaemon(name, task);
    }

    /** The factory owns one atomic name counter shared by all executors using that factory. */
    public static ThreadFactory daemonFactory(String prefix, long start) {
        Objects.requireNonNull(prefix, "prefix");
        if (start < 0) throw new IllegalArgumentException("start must not be negative");
        AtomicLong counter = new AtomicLong(start);
        return task -> unstartedDaemon(prefix + counter.getAndIncrement(), task);
    }
}
