package dev.openallay.extension;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Trusted invocation-local authority and evidence sink. This is not a JavaScript host binding.
 * Extensions must check {@link #requireActive()} immediately before each native owner-thread
 * action, and separately revalidate their exact backend/session identity.
 */
public final class JavascriptInvocationContext {
    private final ToolInvocationContext invocation;
    private final CancellationSignal cancellation = new CancellationSignal();
    private final CancellationSignal requestCancellation;
    private Consumer<EvidenceMetadata> evidence;
    private boolean active = true;
    private boolean completedSuccessfully;

    JavascriptInvocationContext(ToolInvocationContext invocation, CancellationSignal requestCancellation) {
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.requestCancellation = Objects.requireNonNull(requestCancellation, "requestCancellation");
    }

    public ToolInvocationContext invocation() {
        return invocation;
    }

    /** Becomes cancelled on request cancellation, request close, or execution scope exit. */
    public CancellationSignal cancellation() {
        return cancellation;
    }

    /**
     * Whether the JavaScript execution returned normally while this scope was still active.
     * Readable after revocation for cleanup. This does not assert domain operation completion,
     * final Tool validation, or that every script claim has evidence.
     */
    public synchronized boolean completedSuccessfully() {
        return completedSuccessfully;
    }

    synchronized void complete() {
        requireActive();
        completedSuccessfully = true;
    }

    /** Records only evidence for an actually completed capture or operation. */
    public synchronized void recordEvidence(EvidenceMetadata metadata) {
        requireActive();
        if (evidence == null) {
            throw new JavascriptExecutionException(
                    "javascript_invocation_inactive", "JavaScript invocation is not active");
        }
        evidence.accept(Objects.requireNonNull(metadata, "metadata"));
    }

    /** Rejects cancelled/closed work. This method is safe to call on a native owner thread. */
    public synchronized void requireActive() {
        requestCancellation.throwIfCancelled();
        if (!active) {
            throw new JavascriptExecutionException(
                    "javascript_invocation_closed", "JavaScript invocation is closed");
        }
        cancellation.throwIfCancelled();
    }

    boolean requestCancelled() {
        return requestCancellation.isCancelled();
    }

    synchronized void bindEvidence(Consumer<EvidenceMetadata> sink) {
        requireActive();
        evidence = Objects.requireNonNull(sink, "evidence");
    }

    void revoke() {
        synchronized (this) {
            active = false;
            evidence = null;
        }
        // Foreign cancellation listeners cannot prevent revocation or subsequent cleanup.
        try {
            cancellation.cancel();
        } catch (Throwable ignored) {
            // No Extension exception text crosses the Tool result boundary.
        }
    }
}
