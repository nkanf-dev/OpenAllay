package dev.openallay.extension;

import com.google.gson.JsonElement;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/** One admitted execution. Only revocation runs off its JavaScript worker thread. */
public final class JavascriptInvocationScope implements AutoCloseable {
    private final JavascriptInvocationContext context;
    private final List<Participant> participants;
    private final Map<String, JavascriptInvocationContext> extensionContexts = new LinkedHashMap<>();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private Thread worker;
    private final List<AutoCloseable> opened = new ArrayList<>();
    private final Runnable release;
    private final java.util.concurrent.CompletableFuture<Void> released = new java.util.concurrent.CompletableFuture<>();
    private boolean started;
    private boolean closed;

    JavascriptInvocationScope(
            ToolInvocationContext invocation,
            CancellationSignal cancellation,
            List<Participant> participants,
            List<Binding> bindings,
            Map<String, Set<String>> grants,
            Runnable release) {
        context = new JavascriptInvocationContext(invocation, cancellation);
        this.participants = List.copyOf(participants);
        grants.forEach((owner, capabilities) -> extensionContexts.put(
                owner, context.forExtension(owner, capabilities)));
        bindings.forEach(binding -> this.bindings.put(binding.declaration().id(), binding));
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
        worker = Thread.currentThread();
        context.bindEvidence(evidence);
        try {
            for (Participant participant : participants) {
                context.requireActive();
                AutoCloseable scope = participant.declaration().open(extensionContexts.get(participant.owner()));
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

    /** Trusted immutable method declarations only. This list contains no invocation context. */
    public List<JavascriptHostBinding> hostBindings() {
        return bindings.values().stream().map(Binding::declaration).toList();
    }

    /** Called by the controlled Rhino bridge on the opening worker. Never accepts script callbacks. */
    public JsonElement invokeHostMethod(String bindingId, String methodName, List<JsonElement> arguments)
            throws Exception {
        context.requireActive();
        if (!started || closed || Thread.currentThread() != worker) {
            throw new JavascriptExecutionException("javascript_host_access_denied",
                    "Extension host methods require their active JavaScript worker");
        }
        Binding binding = bindings.get(bindingId);
        if (binding == null) throw new JavascriptExecutionException(
                "javascript_module_unavailable", "Extension host binding is unavailable");
        JavascriptHostMethod method = binding.declaration().methods().stream()
                .filter(value -> value.name().equals(methodName)).findFirst().orElseThrow(() ->
                        new JavascriptExecutionException("javascript_host_access_denied",
                                "Extension host method is unavailable"));
        if (arguments.size() != method.parameters().size()) {
            throw new JavascriptExecutionException("javascript_extension_host_invalid",
                    "Host method requires its exact declared argument count");
        }
        for (int index = 0; index < arguments.size(); index++) {
            if (!method.parameters().get(index).accepts(arguments.get(index))) {
                throw new JavascriptExecutionException("javascript_extension_host_invalid",
                        "Host method argument does not match its declared type");
            }
        }
        JavascriptInvocationContext authority = extensionContexts.get(binding.owner());
        method.requiredCapabilities().forEach(authority::requireCapability);
        JsonElement value = method.invoker().invoke(authority, arguments);
        authority.requireActive();
        return value;
    }

    record Participant(String owner, JavascriptInvocationParticipant declaration) {}
    record Binding(String owner, JavascriptHostBinding declaration) {}

    /** Marks a normal JavaScript return before revoking native work and unwinding hooks. */
    public void complete() {
        context.complete();
    }

    /** Completes only after worker-local hooks and registry release have actually unwound. */
    java.util.concurrent.CompletableFuture<Void> releasedFuture() { return released; }

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
            try { release.run(); }
            finally { released.complete(null); }
        }
        if (failed) {
            throw new JavascriptExecutionException(
                    "javascript_participant_cleanup_failed", "JavaScript participant cleanup failed");
        }
    }
}
