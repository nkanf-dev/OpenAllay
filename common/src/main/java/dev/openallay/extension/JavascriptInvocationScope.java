package dev.openallay.extension;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One admitted execution. Only revocation runs off its JavaScript worker thread. */
public final class JavascriptInvocationScope implements AutoCloseable {
    private final JavascriptInvocationContext context;
    private final List<JavascriptInvocationParticipant> participants;
    private final List<AutoCloseable> opened = new ArrayList<>();
    private final Runnable release;
    private boolean started;
    private boolean closed;

    JavascriptInvocationScope(
            ToolInvocationContext invocation,
            CancellationSignal cancellation,
            List<JavascriptInvocationParticipant> participants,
            Runnable release) {
        context = new JavascriptInvocationContext(invocation, cancellation);
        this.participants = List.copyOf(participants);
        this.release = release;
        // The request signal can outlive this execution. Do not retain its evidence or hooks.
        WeakReference<JavascriptInvocationContext> reference = new WeakReference<>(context);
        cancellation.onCancel(() -> {
            JavascriptInvocationContext current = reference.get();
            if (current != null) current.revoke();
        });
    }

    public CancellationSignal cancellation() {
        return context.cancellation();
    }

    public void requireActive() {
        context.requireActive();
    }

    /** Opens hooks on the same worker that will execute the script and close the scopes. */
    public void open(Consumer<EvidenceMetadata> evidence) {
        if (started) throw new IllegalStateException("Invocation scopes already opened");
        started = true;
        context.bindEvidence(evidence);
        try {
            for (JavascriptInvocationParticipant participant : participants) {
                context.requireActive();
                AutoCloseable scope = participant.open(context);
                if (scope == null) throw new IllegalStateException("Missing invocation scope");
                opened.add(scope);
                context.requireActive();
            }
        } catch (Throwable failure) {
            try {
                close();
            } catch (Throwable ignored) {
                // Preserve the stable setup failure, not a foreign cleanup message.
            }
            if (context.requestCancelled()) context.requireActive();
            throw new JavascriptExecutionException(
                    "javascript_participant_setup_failed", "JavaScript participant setup failed");
        }
    }

    /** Marks a normal JavaScript return before revoking native work and unwinding hooks. */
    public void complete() {
        context.complete();
    }

    /** Stops queued native actions without running worker-local cleanup on the closing thread. */
    void revoke() {
        context.revoke();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        context.revoke();
        boolean failed = false;
        try {
            for (int index = opened.size() - 1; index >= 0; index--) {
                try {
                    opened.get(index).close();
                } catch (Throwable failure) {
                    failed = true;
                }
            }
        } finally {
            opened.clear();
            release.run();
        }
        if (failed) {
            throw new JavascriptExecutionException(
                    "javascript_participant_cleanup_failed", "JavaScript participant cleanup failed");
        }
    }
}
