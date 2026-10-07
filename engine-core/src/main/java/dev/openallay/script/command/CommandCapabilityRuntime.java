package dev.openallay.script.command;

import dev.openallay.model.CancellationSignal;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.script.JavascriptExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the command-only setting and immutable per-request effective command captures.
 *
 * <p>Captures, not the current toggle, determine active-request behavior.
 */
public final class CommandCapabilityRuntime {
    private static final long DEFAULT_FEEDBACK_QUIET_MILLIS = 200;
    private static final long DEFAULT_FEEDBACK_DEADLINE_MILLIS = 3_000;

    @FunctionalInterface
    public interface Submitter {
        CompletableFuture<Void> submit(
                UUID actorId, String command, CancellationSignal cancellation);
    }

    private final AtomicBoolean enabled = new AtomicBoolean();
    private final long feedbackQuietMillis;
    private final long feedbackDeadlineMillis;
    private final Map<String, Boolean> requestSettings = new ConcurrentHashMap<>();
    private final Map<String, RequestCapability> requests = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> actorLocks = new ConcurrentHashMap<>();
    private final Map<UUID, PendingFeedback> pendingFeedback = new ConcurrentHashMap<>();

    public CommandCapabilityRuntime() {
        this(DEFAULT_FEEDBACK_QUIET_MILLIS, DEFAULT_FEEDBACK_DEADLINE_MILLIS);
    }

    CommandCapabilityRuntime(long feedbackQuietMillis, long feedbackDeadlineMillis) {
        if (feedbackQuietMillis < 1 || feedbackDeadlineMillis < feedbackQuietMillis) {
            throw new IllegalArgumentException("Invalid command feedback timing");
        }
        this.feedbackQuietMillis = feedbackQuietMillis;
        this.feedbackDeadlineMillis = feedbackDeadlineMillis;
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void replace(CommandCapabilityConfig config) {
        enabled.set(Objects.requireNonNull(config, "config").enabled());
    }

    public void capture(
            String correlationId,
            UUID actorId,
            CommandCatalogSnapshot catalog,
            Submitter submitter) {
        requireCorrelation(correlationId);
        freezeRequest(correlationId);
        if (!enabledFor(correlationId)) {
            requests.remove(correlationId);
            return;
        }
        requests.computeIfAbsent(
                correlationId,
                ignored -> new RequestCapability(
                        this, actorId, catalog, submitter, new AtomicLong()));
    }

    /** True only after this request captured a player route, not merely an enabled setting. */
    public boolean availableFor(String correlationId) {
        requireCorrelation(correlationId);
        return requests.containsKey(correlationId);
    }

    public Optional<JavascriptCommandBridge> bridge(
            String correlationId, CancellationSignal cancellation) {
        requireCorrelation(correlationId);
        RequestCapability capability = requests.get(correlationId);
        return capability == null
                ? Optional.empty()
                : Optional.of(new JavascriptCommandBridge(capability, cancellation));
    }

    public Optional<JavascriptCommandBridge> bridge(
            String correlationId,
            CancellationSignal cancellation,
            java.util.function.BiFunction<String, java.time.Instant, EvidenceMetadata> evidence,
            java.util.function.Consumer<EvidenceMetadata> recordEvidence) {
        requireCorrelation(correlationId);
        RequestCapability capability = requests.get(correlationId);
        return capability == null
                ? Optional.empty()
                : Optional.of(new JavascriptCommandBridge(
                        capability, cancellation, evidence, recordEvidence));
    }

    /** Freezes the command-only toggle for a request with no captured full-access authority. */
    public boolean freezeRequest(String correlationId) {
        return freezeRequest(correlationId, false);
    }

    /**
     * Freezes effective authority once: captured client-local full access includes commands.
     *
     * <p>Later settings or repeated captures cannot change this request's decision. The caller
     * supplies an already frozen full-access flag, never the current JVM-wide setting.
     */
    public boolean freezeRequest(String correlationId, boolean unrestrictedJavascript) {
        requireCorrelation(correlationId);
        return requestSettings.computeIfAbsent(
                correlationId, ignored -> unrestrictedJavascript || enabled());
    }

    public boolean enabledFor(String correlationId) {
        requireCorrelation(correlationId);
        return requestSettings.getOrDefault(correlationId, enabled());
    }

    public void closeRequest(String correlationId) {
        if (correlationId != null) {
            requests.remove(correlationId);
            requestSettings.remove(correlationId);
        }
    }

    /**
     * Accepts one non-overlay game message on the owning client thread.
     *
     * <p>The loader adapter supplies the current local player identity. Messages are retained only
     * while one command for that actor is awaiting feedback.
     */
    public void acceptFeedback(UUID actorId, String message) {
        if (actorId == null || message == null || dev.openallay.util.Java8Strings.isBlank(message)) {
            return;
        }
        PendingFeedback pending = pendingFeedback.get(actorId);
        if (pending != null) {
            pending.accept(message);
        }
    }

    static final class RequestCapability {
        private final CommandCapabilityRuntime owner;
        private final UUID actorId;
        private final CommandCatalogSnapshot catalog;
        private final Submitter submitter;
        private final AtomicLong sequences;

        private RequestCapability(
                CommandCapabilityRuntime owner,
                UUID actorId,
                CommandCatalogSnapshot catalog,
                Submitter submitter,
                AtomicLong sequences) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.actorId = Objects.requireNonNull(actorId, "actorId");
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            this.submitter = Objects.requireNonNull(submitter, "submitter");
            this.sequences = Objects.requireNonNull(sequences, "sequences");
        }

        CommandCatalogSnapshot catalog() {
            return catalog;
        }

        CommandExecutionResult submit(String source, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            String command = normalize(source);
            long sequence = sequences.incrementAndGet();
            return owner.executeAndAwait(
                    sequence, actorId, command, submitter, cancellation);
        }

        private static String normalize(String source) {
            if (source == null) {
                throw new JavascriptExecutionException(
                        "command_invalid", "commands.run requires one command string");
            }
            String command = dev.openallay.util.Java8Strings.strip(source);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (dev.openallay.util.Java8Strings.isBlank(command)) {
                throw new JavascriptExecutionException(
                        "command_invalid", "commands.run requires one command string");
            }
            return command;
        }
    }

    private CommandExecutionResult executeAndAwait(
            long sequence,
            UUID actorId,
            String command,
            Submitter submitter,
            CancellationSignal cancellation) {
        ReentrantLock actorLock =
                actorLocks.computeIfAbsent(actorId, ignored -> new ReentrantLock(true));
        acquire(actorLock, cancellation);
        try {
            cancellation.throwIfCancelled();
            PendingFeedback pending =
                    new PendingFeedback(feedbackQuietMillis, feedbackDeadlineMillis);
            if (pendingFeedback.putIfAbsent(actorId, pending) != null) {
                throw new JavascriptExecutionException(
                        "command_feedback_busy",
                        "Another command is already awaiting feedback for this player");
            }
            long started = System.nanoTime();
            try {
                awaitSubmission(submitter.submit(actorId, command, cancellation), cancellation);
                pending.beginWait();
                List<String> messages = pending.await(cancellation);
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - started);
                return new CommandExecutionResult(
                        sequence,
                        actorId,
                        command,
                        messages.isEmpty() ? "no_feedback" : "feedback",
                        messages,
                        !messages.isEmpty(),
                        durationMillis);
            } finally {
                pendingFeedback.remove(actorId, pending);
            }
        } finally {
            actorLock.unlock();
        }
    }

    private static void acquire(
            ReentrantLock actorLock, CancellationSignal cancellation) {
        while (true) {
            cancellation.throwIfCancelled();
            try {
                if (actorLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancellation.throwIfCancelled();
                throw new JavascriptExecutionException(
                        "command_submission_failed",
                        "Command scheduling was interrupted",
                        interrupted);
            }
        }
    }

    private static void awaitSubmission(
            CompletableFuture<Void> submitted,
            CancellationSignal cancellation) {
        try {
            while (!submitted.isDone()) {
                cancellation.throwIfCancelled();
                try {
                    submitted.get(50, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException ignored) {
                    // Poll cancellation without blocking a Minecraft-owned thread.
                }
            }
            submitted.join();
        } catch (dev.openallay.model.ModelClientException failure) {
            throw failure;
        } catch (RuntimeException | java.util.concurrent.ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof dev.openallay.model.ModelClientException cancelled) {
                throw cancelled;
            }
            if (cause instanceof JavascriptExecutionException rejected) {
                throw rejected;
            }
            if (failure instanceof JavascriptExecutionException rejected) {
                throw rejected;
            }
            throw new JavascriptExecutionException(
                    "command_submission_failed",
                    "Minecraft rejected command submission",
                    failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JavascriptExecutionException(
                    "command_submission_failed",
                    "Command feedback wait was interrupted",
                    interrupted);
        }
    }

    private static final class PendingFeedback {
        private final List<String> messages = new ArrayList<>();
        private final long quietNanos;
        private final long deadlineDurationNanos;
        private long deadlineNanos;
        private long lastMessageNanos;

        private PendingFeedback(long quietMillis, long deadlineMillis) {
            quietNanos = TimeUnit.MILLISECONDS.toNanos(quietMillis);
            deadlineDurationNanos = TimeUnit.MILLISECONDS.toNanos(deadlineMillis);
        }

        synchronized void beginWait() {
            if (deadlineNanos == 0) {
                deadlineNanos = System.nanoTime() + deadlineDurationNanos;
            }
        }

        synchronized void accept(String message) {
            messages.add(message);
            lastMessageNanos = System.nanoTime();
            notifyAll();
        }

        synchronized List<String> await(CancellationSignal cancellation) {
            while (true) {
                cancellation.throwIfCancelled();
                long now = System.nanoTime();
                if (!messages.isEmpty()
                        && now - lastMessageNanos >= quietNanos) {
                    return dev.openallay.util.Java8Collections.listCopyOf(messages);
                }
                if (now >= deadlineNanos) {
                    return dev.openallay.util.Java8Collections.listCopyOf(messages);
                }
                long remainingNanos = messages.isEmpty()
                        ? deadlineNanos - now
                        : Math.min(
                                deadlineNanos - now,
                                quietNanos - (now - lastMessageNanos));
                long waitMillis = Math.max(
                        1, Math.min(50, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                try {
                    wait(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new JavascriptExecutionException(
                            "command_submission_failed",
                            "Command feedback wait was interrupted",
                            interrupted);
                }
            }
        }
    }

    private static void requireCorrelation(String correlationId) {
        if (correlationId == null || dev.openallay.util.Java8Strings.isBlank(correlationId)) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }
}
