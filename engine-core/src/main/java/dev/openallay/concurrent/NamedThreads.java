package dev.openallay.concurrent;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/** Named daemon workers. Each call creates a plain JDK thread; no worker pool is shared. */
public final class NamedThreads {
    private NamedThreads() {}

    public static Thread unstartedDaemon(String name, Runnable task) {
        Thread thread = new Thread(Objects.requireNonNull(task, "task"),
                Objects.requireNonNull(name, "name"));
        thread.setDaemon(true);
        return thread;
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
