package dev.openallay.bridge.server;

import com.google.gson.Gson;
import dev.openallay.bridge.CorrelationRegistry;
import dev.openallay.bridge.protocol.RemoteCancelPayload;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.bridge.protocol.RemoteToolResultChunkPayload;
import dev.openallay.bridge.protocol.RemoteToolRequestClosePayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RemoteToolServer {
    @FunctionalInterface
    public interface ContextProvider {
        CompletableFuture<ToolInvocationContext> capture(
                UUID actorId,
                Set<ContextCapability> capabilities,
                String correlationId,
                CancellationSignal cancellation);
        default ContextProvider bind(UUID actor) { return this; }
    }

    @FunctionalInterface
    public interface ResponseSink {
        void send(UUID actorId, RemoteToolResultChunkPayload chunk);
        default ResponseSink bind(UUID actor) { return this; }
    }

    private final ExportedToolPolicy policy;
    private final ContextProvider contexts;
    private final ResponseSink responses;
    private final CorrelationRegistry correlations;
    private final ToolArgumentCodec arguments;
    private final ToolResultNormalizer normalizer;
    private final Gson gson;
    private final int transportChunkBytes;
    private final java.util.Map<UUID, java.util.Map<String, RequestState>> requestScopes =
            new java.util.HashMap<>();

    public RemoteToolServer(
            ExportedToolPolicy policy,
            ContextProvider contexts,
            ResponseSink responses,
            CorrelationRegistry correlations,
            Gson gson,
            int transportChunkBytes) {
        if (transportChunkBytes <= 0) {
            throw new IllegalArgumentException("transportChunkBytes must be positive");
        }
        this.policy = policy;
        this.contexts = contexts;
        this.responses = responses;
        this.correlations = correlations;
        this.gson = dev.openallay.json.EngineJson.withInstant(gson);
        this.transportChunkBytes = transportChunkBytes;
        arguments = new ToolArgumentCodec(gson);
        normalizer = new ToolResultNormalizer(gson);
    }

    public ToolResult<VoidResult> handle(UUID sender, RemoteToolCallPayload payload) {
        Tool<?, ?> tool = policy.find(payload.toolId()).orElse(null);
        if (tool == null) {
            return new ToolResult.Failure<>("remote_tool_denied", "Tool is not exported as read-only");
        }
        CancellationSignal cancellation = new CancellationSignal();
        if (!correlations.register(sender, payload.correlationId(), cancellation)) {
            return new ToolResult.Failure<>("duplicate_correlation", "Correlation ID is already active");
        }
        String requestScope = requestScope(sender, payload.sessionId());
        RequestState request;
        try { request = registerRequest(sender, requestScope, cancellation); }
        catch (RuntimeException | Error failure) {
            completeOriginal(sender, payload.correlationId(), cancellation);
            cancellation.cancel();
            throw failure;
        }
        try {
            ResponseSink boundResponses = responses.bind(sender);
            ContextProvider boundContexts = contexts.bind(sender);
            boundContexts.capture(
                        sender,
                        tool.descriptor().requiredContext(),
                        requestScope,
                        cancellation)
                .thenCompose(context ->
                        invoke(tool, context, payload.argumentsJson(), cancellation))
                .exceptionally(throwable -> new ToolResult.Failure<>(
                        failureCode(throwable), safeMessage(throwable)))
                .thenAccept(result -> {
                    request.complete(cancellation);
                    finish(sender, payload.correlationId(), cancellation, boundResponses, tool, result);
                });
        } catch (RuntimeException | Error failure) {
            request.complete(cancellation);
            completeOriginal(sender, payload.correlationId(), cancellation);
            cancellation.cancel();
            throw failure;
        }
        return new ToolResult.Success<>(new VoidResult());
    }

    public boolean cancel(UUID sender, RemoteCancelPayload payload) {
        return correlations.cancel(sender, payload.correlationId());
    }

    public void closeRequest(UUID sender, RemoteToolRequestClosePayload payload) {
        String scope = requestScope(sender, payload.requestId());
        RequestState request;
        synchronized (requestScopes) {
            java.util.Map<String, RequestState> scopes = requestScopes.get(sender);
            if (scopes == null) return;
            request = scopes.remove(scope);
            if (scopes.isEmpty()) requestScopes.remove(sender);
        }
        if (request == null) return;
        request.close();
        policy.closeRequestScope(scope);
    }

    public int disconnect(UUID sender) {
        int cancelled = correlations.cancelActor(sender);
        java.util.Map<String, RequestState> scopes;
        synchronized (requestScopes) {
            scopes = requestScopes.remove(sender);
        }
        if (scopes != null) {
            scopes.forEach((scope, request) -> {
                request.close();
                policy.closeRequestScope(scope);
            });
        }
        return cancelled;
    }

    private RequestState registerRequest(UUID actor, String scope, CancellationSignal cancellation) {
        synchronized (requestScopes) {
            RequestState request = requestScopes.computeIfAbsent(actor, ignored -> new java.util.HashMap<>())
                    .computeIfAbsent(scope, ignored -> new RequestState());
            request.register(cancellation);
            return request;
        }
    }

    private static final class RequestState {
        private final java.util.Set<CancellationSignal> pending = new java.util.HashSet<>();
        private boolean closed;

        synchronized void register(CancellationSignal cancellation) {
            if (closed) cancellation.cancel();
            else pending.add(cancellation);
        }

        synchronized void complete(CancellationSignal cancellation) {
            pending.remove(cancellation);
        }

        void close() {
            java.util.List<CancellationSignal> snapshot;
            synchronized (this) {
                closed = true;
                snapshot = dev.openallay.util.Java8Collections.listCopyOf(pending);
                pending.clear();
            }
            snapshot.forEach(CancellationSignal::cancel);
        }
    }

    private boolean completeOriginal(UUID actor, UUID correlation, CancellationSignal original) {
        synchronized (correlations) {
            CorrelationRegistry.Entry entry = correlations.find(actor, correlation).orElse(null);
            return entry != null && entry.cancellation() == original && correlations.complete(actor, correlation);
        }
    }

    private void finish(UUID actor, UUID correlation, CancellationSignal original,
            ResponseSink boundResponses, Tool<?, ?> tool, ToolResult<?> result) {
        if (!completeOriginal(actor, correlation, original)) return;
        // Normalize and call retained sinks outside the registry monitor.
        String json = gson.toJson(normalizer.normalize(result, tool.descriptor().outputType()));
        new ResultChunker().split(correlation, json, transportChunkBytes)
                .forEach(chunk -> boundResponses.send(actor, chunk));
    }

    private CompletableFuture<ToolResult<?>> invoke(
            Tool<?, ?> tool,
            ToolInvocationContext context,
            String argumentsJson,
            CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        com.google.gson.JsonElement parsed = dev.openallay.json.JsonTrees.parse(argumentsJson);
        if (!parsed.isJsonObject()) {
            return CompletableFuture.completedFuture(
                    new ToolResult.Failure<>(
                            "invalid_arguments", "Remote tool arguments must be an object"));
        }
        com.google.gson.JsonObject object = parsed.getAsJsonObject();
        ToolResult<?> decoded = arguments.decode(object, tool.descriptor().inputType());
        if (decoded instanceof ToolResult.Failure<?> failure) {
            return CompletableFuture.completedFuture(failure);
        }
        cancellation.throwIfCancelled();
        return invokeTypedAsync(
                tool,
                context,
                ((ToolResult.Success<?>) decoded).value(),
                cancellation);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletableFuture<ToolResult<?>> invokeTypedAsync(
            Tool<?, ?> raw,
            ToolInvocationContext context,
            Object input,
            CancellationSignal cancellation) {
        return (CompletableFuture<ToolResult<?>>) (CompletableFuture<?>)
                ((Tool<I, O>) raw).invokeAsync(context, (I) input, cancellation);
    }

    private static String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String failureCode(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof dev.openallay.script.JavascriptExecutionException failure) {
            return failure.code();
        }
        if (current instanceof dev.openallay.model.ModelClientException failure) {
            return failure.failure().code();
        }
        return "remote_tool_failure";
    }

    private static String requestScope(UUID actorId, String requestId) {
        return actorId + "/" + requestId;
    }

    @dev.openallay.value.ValueType(VoidResult.ValueSchemaProvider.class)
public static final class VoidResult {
    public VoidResult() {
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VoidResult)) return false;
        VoidResult that = (VoidResult) other;
        return true;
    }
    @Override public int hashCode() {
        int hash = 0;
        return hash;
    }
    @Override public String toString() { return "VoidResult[]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<VoidResult> schema() {
            return new dev.openallay.value.ValueSchema<>(VoidResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<VoidResult>>asList(), arguments -> new VoidResult());
        }
    }
}
}
