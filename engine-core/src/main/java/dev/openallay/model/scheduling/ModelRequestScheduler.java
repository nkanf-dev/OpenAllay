package dev.openallay.model.scheduling;

import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelRateLimitException;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUpstreamException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ModelRequestScheduler implements ModelClient {
    private static final Duration DEFAULT_TRANSPORT_RETRY_DELAY = Duration.ofSeconds(1);
    private static final int DEFAULT_TRANSPORT_RETRIES = 2;
    private static final ScheduledThreadPoolExecutor RETRY_TIMERS = retryTimers();
    private final ModelClient delegate;
    private final Duration transportRetryDelay;
    private final int transportRetries;
    private final Map<String, ArrayDeque<Pending>> queues = new HashMap<>();
    private final ArrayDeque<String> rotation = new ArrayDeque<>();
    private final Set<String> inRotation = new HashSet<>();
    private final List<GateWaiter> gateWaiters = new ArrayList<>();
    private long gateUntilNanos;
    private boolean wakeScheduled;

    public ModelRequestScheduler(ModelClient delegate) {
        this(delegate, DEFAULT_TRANSPORT_RETRY_DELAY, DEFAULT_TRANSPORT_RETRIES);
    }

    ModelRequestScheduler(ModelClient delegate, Duration transportRetryDelay, int transportRetries) {
        this.delegate = dev.openallay.model.ObservingModelClient.observe(delegate);
        this.transportRetryDelay = java.util.Objects.requireNonNull(
                transportRetryDelay, "transportRetryDelay");
        if (transportRetryDelay.isNegative() || transportRetries < 0) {
            throw new IllegalArgumentException("transport retry policy is invalid");
        }
        this.transportRetries = transportRetries;
    }

    @Override
    public boolean observesUsage() { return true; }

    @Override
    public CompletableFuture<ModelTurn> complete(
            ModelRequest request,
            Consumer<ModelEvent> events,
            CancellationSignal cancellation) {
        Pending pending = new Pending(request, events, cancellation);
        pending.result.whenComplete((turn, failure) -> release(pending));
        pending.exchangeCancellation.onCancel(() -> {
            if (cancellation.isCancelled()) {
                cancelQueued(pending);
            }
        });
        enqueue(pending, false);
        return pending.result;
    }

    public synchronized int queuedRequests() {
        return queues.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    /**
     * Completes when a request can capture fresh context without knowingly
     * waiting behind an existing endpoint cooldown.
     */
    public CompletableFuture<Void> awaitReady(CancellationSignal cancellation) {
        GateWaiter waiter;
        synchronized (this) {
            long remaining = gateUntilNanos - System.nanoTime();
            if (remaining <= 0) {
                return CompletableFuture.completedFuture(null);
            }
            waiter = new GateWaiter(cancellation, new CompletableFuture<>());
            gateWaiters.add(waiter);
            scheduleWakeLocked(remaining);
        }
        GateWaiter registered = waiter;
        cancellation.onCancel(() -> cancelGateWaiter(registered));
        return waiter.result;
    }

    private void enqueue(Pending pending, boolean front) {
        List<Pending> dispatch;
        synchronized (this) {
            if (pending.cancellation.isCancelled() || pending.result.isDone()) {
                return;
            }
            ArrayDeque<Pending> queue =
                    queues.computeIfAbsent(pending.request.sessionKey(), ignored -> new ArrayDeque<>());
            if (front) {
                queue.addFirst(pending);
            } else {
                queue.addLast(pending);
            }
            addRotation(pending.request.sessionKey());
            dispatch = drainLocked();
        }
        dispatch.forEach(this::dispatch);
    }

    private List<Pending> drainLocked() {
        long remaining = gateUntilNanos - System.nanoTime();
        if (remaining > 0) {
            scheduleWakeLocked(remaining);
            return List.of();
        }
        gateUntilNanos = 0;
        List<Pending> dispatch = new ArrayList<>();
        while (!rotation.isEmpty()) {
            String session = rotation.removeFirst();
            inRotation.remove(session);
            ArrayDeque<Pending> queue = queues.get(session);
            if (queue == null || queue.isEmpty()) {
                queues.remove(session);
                continue;
            }
            Pending pending = queue.removeFirst();
            if (queue.isEmpty()) {
                queues.remove(session);
            } else {
                addRotation(session);
            }
            if (!pending.cancellation.isCancelled() && !pending.result.isDone()) {
                dispatch.add(pending);
            }
        }
        return dispatch;
    }

    private void dispatch(Pending pending) {
        if (pending.cancellation.isCancelled() || pending.result.isDone()) {
            return;
        }
        if (pending.recoveryDeadlineNanos != 0
                && pending.recoveryDeadlineNanos - System.nanoTime() <= 0) {
            expireRecovery(pending);
            return;
        }
        pending.attempt++;
        long attemptStartedNanos = System.nanoTime();
        pending.attemptDeadlineNanos = 0;
        java.util.concurrent.atomic.AtomicBoolean responseProgress =
                new java.util.concurrent.atomic.AtomicBoolean();
        pending.events.accept(new ModelEvent.AttemptStarted(pending.attempt, null));
        if (pending.cancellation.isCancelled() || pending.result.isDone()) {
            cancelQueued(pending);
            return;
        }
        delegate.complete(
                        pending.request,
                        event -> {
                            if (event instanceof ModelEvent.UsageObserved
                                    || event instanceof ModelEvent.UsageStarted) {
                                // Receipts remain request-scoped after cancellation/deadline expiry.
                                // They are not response progress and must not disable safe retries.
                                pending.events.accept(event);
                                return;
                            }
                            if (pending.recoveryDeadlineNanos != 0
                                    && pending.recoveryDeadlineNanos - System.nanoTime() <= 0) {
                                expireRecovery(pending);
                                return;
                            }
                            if (event instanceof ModelEvent.AttemptStarted started) {
                                if (started.attemptTimeoutMillis() != null) {
                                    pending.attemptDeadlineNanos = attemptStartedNanos
                                            + TimeUnit.MILLISECONDS.toNanos(started.attemptTimeoutMillis());
                                }
                            } else {
                                responseProgress.set(true);
                            }
                            if (!pending.cancellation.isCancelled()
                                    && !pending.result.isDone()) {
                                pending.events.accept(
                                        event instanceof ModelEvent.AttemptStarted started
                                                ? new ModelEvent.AttemptStarted(
                                                        pending.attempt,
                                                        attemptBudgetMillis(pending, started.attemptTimeoutMillis()))
                                                : event);
                            }
                        },
                        pending.exchangeCancellation)
                .whenComplete((turn, throwable) -> {
                    if (pending.result.isDone()) {
                        return;
                    }
                    if (pending.cancellation.isCancelled()) {
                        cancelQueued(pending);
                        return;
                    }
                    if (pending.recoveryDeadlineNanos != 0
                            && pending.recoveryDeadlineNanos - System.nanoTime() <= 0) {
                        expireRecovery(pending);
                        return;
                    }
                    Throwable cause = unwrap(throwable);
                    if (cause instanceof ModelRateLimitException limited
                            && !responseProgress.get()
                            && !pending.cancellation.isCancelled()) {
                        Duration delay = limited.retryAfter() == null
                                ? fallbackDelay(pending.attempt)
                                : limited.retryAfter();
                        pending.events.accept(
                                new ModelEvent.RateLimited(delay.toMillis(), pending.attempt));
                        closeGate(delay);
                        enqueue(pending, true);
                    } else if (retryableTransport(cause, responseProgress.get(), pending)
                            && !pending.cancellation.isCancelled()
                            && scheduleTransportRetry(pending, cause)) {
                        // The same model round remains pending. Never re-enter the agent/tool loop.
                    } else if (throwable != null) {
                        pending.result.completeExceptionally(cause);
                    } else {
                        pending.result.complete(turn);
                    }
                });
    }

    private boolean retryableTransport(Throwable cause, boolean responseProgress, Pending pending) {
        return !responseProgress
                && cause instanceof ModelClientException modelFailure
                && (modelFailure.failure().code().equals("model_transport_error")
                        || cause instanceof ModelUpstreamException)
                && pending.attempt <= transportRetries;
    }

    private boolean scheduleTransportRetry(Pending pending, Throwable cause) {
        Duration delay = transportRetryDelay.multipliedBy(Math.max(1, pending.attempt));
        if (cause instanceof ModelUpstreamException upstream
                && upstream.retryAfter() != null && upstream.retryAfter().compareTo(delay) > 0) {
            delay = upstream.retryAfter();
        }
        synchronized (this) {
            if (pending.result.isDone() || pending.cancellation.isCancelled()) {
                return false;
            }
            long deadline = pending.recoveryDeadlineNanos == 0
                    ? pending.attemptDeadlineNanos : pending.recoveryDeadlineNanos;
            long remaining = deadline - System.nanoTime();
            // Do not shorten Retry-After or grant another full timeout to recover.
            // If recovery cannot fit, preserve the actual upstream/transport failure.
            if (deadline != 0 && (remaining <= 0
                    || delay.compareTo(Duration.ofNanos(remaining)) >= 0)) {
                return false;
            }
            if (pending.recoveryDeadlineNanos == 0 && deadline != 0) {
                pending.recoveryDeadlineNanos = deadline;
                pending.deadlineWake = scheduleRecovery(() -> expireRecovery(pending), remaining);
            }
            long delayNanos;
            try {
                delayNanos = delay.toNanos();
            } catch (ArithmeticException excessiveDelay) {
                return false;
            }
            pending.retryWake = scheduleRecovery(() -> enqueue(pending, true), delayNanos);
            return true;
        }
    }

    private static Long attemptBudgetMillis(Pending pending, Long reportedMillis) {
        if (pending.recoveryDeadlineNanos == 0) {
            return reportedMillis;
        }
        long remaining = Math.max(0, TimeUnit.NANOSECONDS.toMillis(
                pending.recoveryDeadlineNanos - System.nanoTime()));
        return reportedMillis == null ? remaining : Math.min(reportedMillis, remaining);
    }

    private static void expireRecovery(Pending pending) {
        // Revoke the exchange before publishing a future whose consumers may block.
        // Transport cleanup must not depend on CompletableFuture callback order.
        pending.exchangeCancellation.cancel(notifications -> Thread.ofVirtual()
                .name("openallay-model-recovery-cancel").start(notifications));
        boolean cancelled = pending.cancellation.isCancelled();
        pending.result.completeExceptionally(new ModelClientException(new ModelFailure(
                cancelled ? "agent_cancelled" : "model_timeout",
                cancelled ? "Agent request was cancelled" : "Model request timed out", null)));
    }

    private static ScheduledFuture<?> scheduleRecovery(Runnable action, long delayNanos) {
        // Never let a decoder, cancellation listener, or consumer occupy the shared timer.
        return RETRY_TIMERS.schedule(
                () -> Thread.ofVirtual().name("openallay-model-recovery-task").start(action),
                delayNanos, TimeUnit.NANOSECONDS);
    }

    private static ScheduledThreadPoolExecutor retryTimers() {
        ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("openallay-model-recovery").factory());
        timers.setRemoveOnCancelPolicy(true);
        return timers;
    }

    private void release(Pending pending) {
        synchronized (this) {
            removeQueuedLocked(pending);
            if (pending.retryWake != null) {
                pending.retryWake.cancel(false);
            }
            if (pending.deadlineWake != null) {
                pending.deadlineWake.cancel(false);
            }
        }
        // Deadline expiry cancels only this exchange, not the caller's abort signal.
        pending.exchangeCancellation.cancel();
    }

    private void closeGate(Duration delay) {
        synchronized (this) {
            long candidate = System.nanoTime() + Math.max(0, delay.toNanos());
            gateUntilNanos = Math.max(gateUntilNanos, candidate);
            scheduleWakeLocked(Math.max(0, gateUntilNanos - System.nanoTime()));
        }
    }

    private void scheduleWakeLocked(long delayNanos) {
        if (wakeScheduled) {
            return;
        }
        wakeScheduled = true;
        Thread.ofVirtual().name("openallay-model-rate-wait").start(() -> {
            try {
                long remaining = delayNanos;
                while (remaining > 0) {
                    long millis = Math.max(1, Math.min(1000, Duration.ofNanos(remaining).toMillis()));
                    Thread.sleep(millis);
                    synchronized (ModelRequestScheduler.this) {
                        remaining = gateUntilNanos - System.nanoTime();
                    }
                }
                List<Pending> dispatch;
                List<GateWaiter> ready;
                synchronized (ModelRequestScheduler.this) {
                    wakeScheduled = false;
                    dispatch = drainLocked();
                    ready = gateUntilNanos == 0 ? List.copyOf(gateWaiters) : List.of();
                    if (!ready.isEmpty()) {
                        gateWaiters.clear();
                    }
                }
                ready.forEach(waiter -> {
                    if (!waiter.cancellation.isCancelled()) {
                        waiter.result.complete(null);
                    }
                });
                dispatch.forEach(this::dispatch);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void cancelQueued(Pending pending) {
        pending.result.completeExceptionally(new ModelClientException(
                new ModelFailure("agent_cancelled", "Agent request was cancelled", null)));
    }

    private void removeQueuedLocked(Pending pending) {
        ArrayDeque<Pending> queue = queues.get(pending.request.sessionKey());
        if (queue != null) {
            queue.remove(pending);
            if (queue.isEmpty()) {
                queues.remove(pending.request.sessionKey());
                rotation.remove(pending.request.sessionKey());
                inRotation.remove(pending.request.sessionKey());
            }
        }
    }

    private void cancelGateWaiter(GateWaiter waiter) {
        synchronized (this) {
            gateWaiters.remove(waiter);
        }
        waiter.result.completeExceptionally(new ModelClientException(
                new ModelFailure("agent_cancelled", "Agent request was cancelled", null)));
    }

    private void addRotation(String session) {
        if (inRotation.add(session)) {
            rotation.addLast(session);
        }
    }

    private static Duration fallbackDelay(int attempt) {
        long seconds = Math.min(60, 1L << Math.min(6, Math.max(0, attempt - 1)));
        return Duration.ofSeconds(seconds);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while ((current instanceof CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class Pending {
        private final ModelRequest request;
        private final Consumer<ModelEvent> events;
        private final CancellationSignal cancellation;
        private final CompletableFuture<ModelTurn> result = new CompletableFuture<>();
        private final CancellationSignal exchangeCancellation;
        private int attempt;
        private volatile long attemptDeadlineNanos;
        private volatile long recoveryDeadlineNanos;
        private ScheduledFuture<?> retryWake;
        private ScheduledFuture<?> deadlineWake;

        private Pending(
                ModelRequest request,
                Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            this.request = request;
            this.events = events;
            this.cancellation = cancellation;
            this.exchangeCancellation = cancellation.linkedChild();
        }
    }

    private record GateWaiter(
            CancellationSignal cancellation, CompletableFuture<Void> result) {}
}
