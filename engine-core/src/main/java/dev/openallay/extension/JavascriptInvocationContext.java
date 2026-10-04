package dev.openallay.extension;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Trusted invocation-local authority and evidence sink, never a JavaScript host value.
 * Each Extension receives only its own frozen capability grants. All Extension contexts in
 * this invocation share activity, cancellation, and evidence. Native owner actions must recheck
 * activity and capability immediately before use, then revalidate the exact backend/session.
 */
public final class JavascriptInvocationContext {
    private final State state;
    private final String extensionId;
    private final Set<String> capabilities;
    // Owned by this exact Extension execution context, never a process-wide identity cache.
    private dev.openallay.api.extension.ExtensionInvocation sdkInvocation;

    JavascriptInvocationContext(ToolInvocationContext invocation, CancellationSignal requestCancellation) {
        state = new State(invocation, requestCancellation);
        extensionId = "";
        capabilities = Set.of();
    }

    private JavascriptInvocationContext(State state, String extensionId, Set<String> capabilities) {
        this.state = state;
        this.extensionId = extensionId;
        this.capabilities = Set.copyOf(capabilities);
    }

    JavascriptInvocationContext forExtension(String owner, Set<String> grants) {
        return new JavascriptInvocationContext(state, owner, grants);
    }

    public ToolInvocationContext invocation() { return state.invocation; }

    /** Stable public facade for this admitted execution and Extension, not its correlation ID. */
    public synchronized dev.openallay.api.extension.ExtensionInvocation sdkInvocation() {
        requireActive();
        if (sdkInvocation == null) {
            sdkInvocation = new dev.openallay.extension.universal.UniversalInvocationAdapter(this);
        }
        return sdkInvocation;
    }

    /** The registered owner of this trusted context, not a script-supplied identity. */
    public String extensionId() { return extensionId; }

    /** Becomes cancelled on request cancellation, request close, or execution scope exit. */
    public CancellationSignal cancellation() { return state.cancellation; }

    public boolean hasCapability(String id) {
        requireActive();
        return capabilities.contains(id);
    }

    /** Does not consult Agent Java/JVM settings or another Extension's declarations/grants. */
    public void requireCapability(String id) {
        if (!hasCapability(id)) {
            throw new JavascriptExecutionException("javascript_extension_capability_denied",
                    "This Extension operation requires an explicit player grant: " + id);
        }
    }

    /** Normal script return, not a claim that a domain operation or final Tool completed. */
    public boolean completedSuccessfully() {
        synchronized (state) { return state.completedSuccessfully; }
    }

    void complete() {
        synchronized (state) {
            requireActive();
            state.completedSuccessfully = true;
        }
    }

    /** Records only evidence for an actually completed capture or operation. */
    public void recordEvidence(EvidenceMetadata metadata) {
        synchronized (state) {
            requireActive();
            if (state.evidence == null) {
                throw new JavascriptExecutionException(
                        "javascript_invocation_inactive", "JavaScript invocation is not active");
            }
            state.evidence.accept(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    /** Safe to call on a native owner thread immediately before applying an operation. */
    public void requireActive() {
        synchronized (state) {
            state.requestCancellation.throwIfCancelled();
            if (!state.active) {
                throw new JavascriptExecutionException(
                        "javascript_invocation_closed", "JavaScript invocation is closed");
            }
            state.cancellation.throwIfCancelled();
        }
    }

    boolean requestCancelled() { return state.requestCancellation.isCancelled(); }

    void bindEvidence(Consumer<EvidenceMetadata> sink) {
        synchronized (state) {
            requireActive();
            state.evidence = Objects.requireNonNull(sink, "evidence");
        }
    }

    void revoke() {
        synchronized (state) {
            state.active = false;
            state.evidence = null;
        }
        try { state.cancellation.cancel(); }
        catch (Throwable ignored) { /* Foreign listeners cannot prevent revocation/cleanup. */ }
    }

    private static final class State {
        private final ToolInvocationContext invocation;
        private final CancellationSignal cancellation = new CancellationSignal();
        private final CancellationSignal requestCancellation;
        private Consumer<EvidenceMetadata> evidence;
        private boolean active = true;
        private boolean completedSuccessfully;

        private State(ToolInvocationContext invocation, CancellationSignal requestCancellation) {
            this.invocation = Objects.requireNonNull(invocation, "invocation");
            this.requestCancellation = Objects.requireNonNull(requestCancellation, "requestCancellation");
        }
    }
}
