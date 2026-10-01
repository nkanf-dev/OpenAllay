package dev.openallay.bridge.client;

import com.google.gson.Gson;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.BridgeProtocol;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.ClientToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillCatalogManifest;
import dev.openallay.tool.RegisteredTool;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Hosts one frozen local capability catalog for each active server-model request.
 * Loader code owns client-thread marshalling in the supplied context provider.
 */
public final class ClientToolExecutionEndpoint {
    public static final String EXPERIMENTAL_COMMANDS_CAPABILITY =
            "openallay:capability/experimental_commands";
    @FunctionalInterface
    public interface ContextProvider {
        CompletableFuture<ToolInvocationContext> capture(
                Set<ContextCapability> capabilities,
                String correlationId,
                CancellationSignal cancellation);
    }

    @FunctionalInterface
    public interface ResponseSink {
        void send(ClientToolResultChunkPayload chunk);
    }

    private final ContextProvider contexts;
    private final ResponseSink responses;
    private final Gson gson;
    private final ToolArgumentCodec arguments;
    private final ToolResultNormalizer normalizer;
    private final int transportChunkBytes;
    private final Executor worker;
    private final java.util.function.UnaryOperator<String> skillTextTransform;
    private final Map<UUID, RequestState> requests = new ConcurrentHashMap<>();

    public ClientToolExecutionEndpoint(
            ContextProvider contexts,
            ResponseSink responses,
            Gson gson,
            int transportChunkBytes) {
        this(contexts, responses, gson, transportChunkBytes, java.util.function.UnaryOperator.identity());
    }

    public ClientToolExecutionEndpoint(
            ContextProvider contexts,
            ResponseSink responses,
            Gson gson,
            int transportChunkBytes,
            java.util.function.UnaryOperator<String> skillTextTransform) {
        this(
                contexts,
                responses,
                gson,
                transportChunkBytes,
                command -> Thread.ofVirtual()
                        .name("openallay-client-tool-worker")
                        .start(command),
                skillTextTransform);
    }

    ClientToolExecutionEndpoint(
            ContextProvider contexts,
            ResponseSink responses,
            Gson gson,
            int transportChunkBytes,
            Executor worker) {
        this(contexts, responses, gson, transportChunkBytes, worker,
                java.util.function.UnaryOperator.identity());
    }

    ClientToolExecutionEndpoint(
            ContextProvider contexts,
            ResponseSink responses,
            Gson gson,
            int transportChunkBytes,
            Executor worker,
            java.util.function.UnaryOperator<String> skillTextTransform) {
        if (transportChunkBytes <= 0) {
            throw new IllegalArgumentException("transportChunkBytes must be positive");
        }
        this.contexts = java.util.Objects.requireNonNull(contexts, "contexts");
        this.responses = java.util.Objects.requireNonNull(responses, "responses");
        this.gson = java.util.Objects.requireNonNull(gson, "gson");
        this.transportChunkBytes = transportChunkBytes;
        this.worker = java.util.Objects.requireNonNull(worker, "worker");
        this.skillTextTransform = java.util.Objects.requireNonNull(skillTextTransform, "skillTextTransform");
        arguments = new ToolArgumentCodec(gson);
        normalizer = new ToolResultNormalizer(gson);
    }

    public ToolResult<OpenedRequest> open(
            UUID requestId, String sessionId, ToolRuntimeCatalog frozenTools) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            return new ToolResult.Failure<>("invalid_session", "Invalid Agent session ID");
        }
        java.util.Objects.requireNonNull(frozenTools, "frozenTools");
        ToolRuntimeCatalog requestTools = ToolRuntimeCatalog.from(
                frozenTools.registrations().stream()
                        .map(registration -> registration.tool() instanceof LoadSkillTool skill
                                ? new RegisteredTool(registration.providerId(),
                                        skill.withOwner("client").withTextTransform(skillTextTransform))
                                : registration)
                        .toList(),
                Set.of());
        SkillCatalogManifest skillDocuments = requestTools.find("openallay:load_skill")
                .filter(LoadSkillTool.class::isInstance)
                .map(LoadSkillTool.class::cast)
                .map(LoadSkillTool::catalogManifest)
                .orElse(SkillCatalogManifest.EMPTY);
        java.util.ArrayList<String> exported = new java.util.ArrayList<>(
                requestTools.descriptors().stream()
                .filter(descriptor -> descriptor.access() == ToolAccess.READ_ONLY
                        || descriptor.access() == ToolAccess.EXPERIMENTAL_ACTION)
                .map(descriptor -> descriptor.id())
                .sorted()
                .toList());
        requestTools.find("openallay:run_javascript")
                .filter(dev.openallay.tool.builtin.RunJavascriptTool.class::isInstance)
                .map(dev.openallay.tool.builtin.RunJavascriptTool.class::cast)
                .filter(tool -> tool.freezeCommandCapability(requestId.toString()))
                .ifPresent(ignored -> exported.add(EXPERIMENTAL_COMMANDS_CAPABILITY));
        RequestState state = new RequestState(
                sessionId,
                requestTools,
                exported.stream()
                        .filter(id -> !id.equals(EXPERIMENTAL_COMMANDS_CAPABILITY))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        if (requests.putIfAbsent(requestId, state) != null) {
            return new ToolResult.Failure<>(
                    "duplicate_request", "Client Tool request ID is already active");
        }
        return new ToolResult.Success<>(
                new OpenedRequest(requestId, sessionId, List.copyOf(exported), skillDocuments));
    }

    public ToolResult<VoidResult> handle(ClientToolCallPayload payload) {
        RequestState request = requests.get(payload.requestId());
        if (request == null) {
            sendFailure(payload, "client_tool_unavailable", "Client Tool request is no longer active");
            return new ToolResult.Failure<>(
                    "client_tool_unavailable", "Client Tool request is no longer active");
        }
        if (!request.sessionId.equals(payload.sessionId())) {
            sendFailure(payload, "client_tool_rejected", "Client Tool session does not match");
            return new ToolResult.Failure<>(
                    "client_tool_rejected", "Client Tool session does not match");
        }
        Tool<?, ?> tool = request.exported.contains(payload.toolId())
                ? request.tools.find(payload.toolId()).orElse(null)
                : null;
        if (tool == null
                || (tool.descriptor().access() != ToolAccess.READ_ONLY
                        && tool.descriptor().access() != ToolAccess.EXPERIMENTAL_ACTION)) {
            sendFailure(payload, "client_tool_unavailable", "Client Tool is absent or disabled");
            return new ToolResult.Failure<>(
                    "client_tool_unavailable", "Client Tool is absent or disabled");
        }
        CancellationSignal cancellation = new CancellationSignal();
        Registration registration = request.register(payload.invocationId(), cancellation);
        if (registration == Registration.CLOSED) {
            sendFailure(payload, "client_tool_unavailable", "Client Tool request is no longer active");
            return new ToolResult.Failure<>(
                    "client_tool_unavailable", "Client Tool request is no longer active");
        }
        if (registration == Registration.DUPLICATE) {
            return new ToolResult.Failure<>(
                    "duplicate_invocation", "Client Tool invocation ID is already active");
        }
        CompletableFuture<ToolInvocationContext> capture;
        try {
            capture = contexts.capture(
                    tool.descriptor().requiredContext(),
                    payload.requestId().toString(),
                    cancellation);
        } catch (RuntimeException failure) {
            finish(payload, request, tool, new ToolResult.Failure<>(
                    "client_tool_context_failed", "Client Tool context capture failed"));
            return new ToolResult.Success<>(new VoidResult());
        }
        if (capture == null) {
            finish(payload, request, tool, new ToolResult.Failure<>(
                    "client_tool_context_failed", "Client Tool context capture failed"));
            return new ToolResult.Success<>(new VoidResult());
        }
        capture.thenComposeAsync(
                        context -> invokeAsync(
                                tool, context, payload.argumentsJson(), cancellation),
                        worker)
                .exceptionally(ignored -> new ToolResult.Failure<>(
                        "client_tool_context_failed", "Client Tool context capture failed"))
                .thenAccept(result -> finish(payload, request, tool, result));
        return new ToolResult.Success<>(new VoidResult());
    }

    public boolean cancel(ClientToolCancelPayload payload) {
        RequestState request = requests.get(payload.requestId());
        if (request == null) {
            return false;
        }
        CancellationSignal cancellation = request.remove(payload.invocationId());
        return cancellation != null && cancellation.cancel();
    }

    public boolean close(UUID requestId) {
        RequestState request = requests.remove(requestId);
        if (request == null) {
            return false;
        }
        request.close();
        request.tools.registrations().stream()
                .map(registration -> registration.tool())
                .filter(RequestScopeParticipant.class::isInstance)
                .map(RequestScopeParticipant.class::cast)
                .forEach(participant -> participant.closeRequestScope(requestId.toString()));
        return true;
    }

    public int disconnect() {
        List<UUID> active = List.copyOf(requests.keySet());
        active.forEach(this::close);
        return active.size();
    }

    public int activeRequests() {
        return requests.size();
    }

    private CompletableFuture<ToolResult<?>> invokeAsync(
            Tool<?, ?> tool,
            ToolInvocationContext context,
            String argumentsJson,
            CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        com.google.gson.JsonElement parsed;
        try {
            parsed = com.google.gson.JsonParser.parseString(argumentsJson);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "invalid_arguments", "Client Tool arguments are not valid JSON"));
        }
        if (!parsed.isJsonObject()) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "invalid_arguments", "Client Tool arguments must be an object"));
        }
        ToolResult<?> decoded = arguments.decode(
                parsed.getAsJsonObject(), tool.descriptor().inputType());
        if (decoded instanceof ToolResult.Failure<?> failure) {
            return CompletableFuture.completedFuture(failure);
        }
        cancellation.throwIfCancelled();
        try {
            return invokeTypedAsync(
                    tool, context, ((ToolResult.Success<?>) decoded).value(), cancellation);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "tool_failure", "Client Tool execution failed"));
        }
    }

    private void finish(
            ClientToolCallPayload payload,
            RequestState request,
            Tool<?, ?> tool,
            ToolResult<?> result) {
        CancellationSignal cancellation = request.remove(payload.invocationId());
        if (cancellation == null
                || cancellation.isCancelled()
                || requests.get(payload.requestId()) != request) {
            return;
        }
        try {
            sendNormalized(
                    payload.requestId(), payload.invocationId(),
                    normalizer.normalize(result, tool.descriptor().outputType()));
        } catch (RuntimeException failure) {
            sendFailure(payload, "client_tool_result_invalid", "Client Tool result was invalid");
        }
    }

    private void sendFailure(ClientToolCallPayload payload, String code, String message) {
        sendNormalized(
                payload.requestId(),
                payload.invocationId(),
                normalizer.normalize(new ToolResult.Failure<>(code, message), Object.class));
    }

    private void sendNormalized(
            UUID requestId, UUID invocationId, com.google.gson.JsonObject normalized) {
        new ResultChunker().split(
                        invocationId, gson.toJson(normalized), transportChunkBytes)
                .stream()
                .map(chunk -> ClientToolResultChunkPayload.from(requestId, chunk))
                .forEach(responses::send);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletableFuture<ToolResult<?>> invokeTypedAsync(
            Tool<?, ?> raw,
            ToolInvocationContext context,
            Object input,
            CancellationSignal cancellation) {
        if (raw instanceof LoadSkillTool skill) {
            // The server projection owns retained plaintext. The client has no delivery receipts.
            return CompletableFuture.completedFuture(
                    skill.invokeFresh(context, (LoadSkillTool.Input) input));
        }
        return ((Tool<I, O>) raw)
                .invokeAsync(context, (I) input, cancellation)
                .thenApply(result -> result);
    }

    public record OpenedRequest(
            UUID requestId, String sessionId, List<String> clientToolIds,
            SkillCatalogManifest skillDocuments) {
        public OpenedRequest {
            java.util.Objects.requireNonNull(requestId, "requestId");
            clientToolIds = List.copyOf(clientToolIds);
            java.util.Objects.requireNonNull(skillDocuments, "skillDocuments");
        }
    }

    public record VoidResult() {}

    private enum Registration {
        REGISTERED,
        DUPLICATE,
        CLOSED
    }

    private static final class RequestState {
        private final String sessionId;
        private final ToolRuntimeCatalog tools;
        private final Set<String> exported;
        private final Map<UUID, CancellationSignal> pending = new HashMap<>();
        private boolean closed;

        private RequestState(
                String sessionId, ToolRuntimeCatalog tools, Set<String> exported) {
            this.sessionId = sessionId;
            this.tools = tools;
            this.exported = exported;
        }

        private synchronized Registration register(
                UUID invocationId, CancellationSignal cancellation) {
            if (closed) {
                return Registration.CLOSED;
            }
            if (pending.putIfAbsent(invocationId, cancellation) != null) {
                return Registration.DUPLICATE;
            }
            return Registration.REGISTERED;
        }

        private synchronized CancellationSignal remove(UUID invocationId) {
            return pending.remove(invocationId);
        }

        private void close() {
            List<CancellationSignal> cancellations;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                cancellations = List.copyOf(pending.values());
                pending.clear();
            }
            cancellations.forEach(CancellationSignal::cancel);
        }
    }
}
