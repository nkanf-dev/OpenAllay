package dev.openallay.bridge.server;

import dev.openallay.concurrent.NamedThreads;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.ClientToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.json.EngineJson;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.RetainedSkillContext;
import dev.openallay.skill.SkillCatalogManifest;
import dev.openallay.skill.SkillCatalogSnapshot;
import dev.openallay.skill.SkillInstructionContext;
import dev.openallay.tool.ModelFacingToolOutput;
import dev.openallay.tool.RegisteredTool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns server-side correlations for client-resident Tools used by a server-hosted Agent.
 * Each opened executor freezes one actor/request capability intersection.
 * Advertised trusted Tools run on that client; other Tools run on the server.
 */
public final class PlayerClientToolRouter {
    private static final java.util.concurrent.ScheduledExecutorService TIMEOUTS =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "openallay-client-tool-timeouts");
                thread.setDaemon(true);
                return thread;
            });

    public interface Transport {
        boolean call(UUID actorId, ClientToolCallPayload payload);

        void cancel(UUID actorId, ClientToolCancelPayload payload);
    }

    /** The Agent request owner imports, pins and grants resolver access before completion. */
    @FunctionalInterface
    public interface ResultPreparation {
        CompletableFuture<Void> prepare(UUID actorId, UUID requestId, String sessionId,
                List<dev.openallay.model.image.ImageReference> references,
                List<dev.openallay.bridge.protocol.ServerAgentImageAttachment> attachments,
                java.util.function.BooleanSupplier invocationCurrent);
    }

    private volatile ResultPreparation resultPreparation =
            (actor, request, session, references, attachments, current) -> references.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(new IllegalStateException("Server image admission is unavailable"));
    private volatile int resultByteLimit = dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES;
    private volatile java.util.function.BiPredicate<UUID, UUID> resultAdmission = (actor, request) -> true;
    private final java.util.concurrent.Executor resultWorker;

    public void configureResultPreparation(ResultPreparation preparation, int maximumBytes) {
        configureResultPreparation(preparation, maximumBytes, (actor, request) -> true);
    }

    public void configureResultPreparation(ResultPreparation preparation, int maximumBytes,
            java.util.function.BiPredicate<UUID, UUID> admission) {
        resultAdmission = java.util.Objects.requireNonNull(admission, "admission");
        if (maximumBytes <= 0
                || maximumBytes > dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES) {
            throw new IllegalArgumentException("Invalid Tool result envelope limit");
        }
        resultPreparation = java.util.Objects.requireNonNull(preparation, "preparation");
        resultByteLimit = maximumBytes;
    }

    private final ToolRuntimeCatalog trustedTools;
    private final Gson gson;
    private final Transport transport;
    private final ToolResultNormalizer normalizer;
    private final ToolArgumentCodec argumentsCodec;
    private final Duration resultTimeout;
    private final Map<RequestKey, RequestExecutor> active = new ConcurrentHashMap<>();

    public PlayerClientToolRouter(ToolRegistry tools, Gson gson, Transport transport) {
        this(tools, gson, transport, Duration.ofMinutes(5));
    }

    public PlayerClientToolRouter(
            ToolRegistry tools, Gson gson, Transport transport, Duration resultTimeout) {
        this(tools, gson, transport, resultTimeout,
                command -> NamedThreads.startDaemon("openallay-client-tool-result", command));
    }

    /** Explicit executor lets tests drive assembly and image admission without native threads. */
    public PlayerClientToolRouter(
            ToolRegistry tools, Gson gson, Transport transport, Duration resultTimeout,
            java.util.concurrent.Executor resultWorker) {
        this.resultWorker = java.util.Objects.requireNonNull(resultWorker, "resultWorker");
        java.util.Objects.requireNonNull(tools, "tools");
        Set<String> nonReadOnly = tools.descriptors().stream()
                .filter(descriptor -> descriptor.access() != ToolAccess.READ_ONLY
                        && descriptor.access() != ToolAccess.EXPERIMENTAL_ACTION)
                .map(descriptor -> descriptor.id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        trustedTools = ToolRuntimeCatalog.from(tools.registrations(), nonReadOnly);
        this.gson = EngineJson.withInstant(java.util.Objects.requireNonNull(gson, "gson"));
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
        this.resultTimeout = java.util.Objects.requireNonNull(resultTimeout, "resultTimeout");
        if (resultTimeout.isZero() || resultTimeout.isNegative()) {
            throw new IllegalArgumentException("resultTimeout must be positive");
        }
        normalizer = new ToolResultNormalizer(gson);
        argumentsCodec = new ToolArgumentCodec(gson);
    }

    public ToolResult<AgentToolExecutor> open(
            UUID actorId,
            UUID requestId,
            String sessionId,
            List<String> advertisedClientToolIds,
            SkillCatalogSnapshot requestSkills) {
        return open(actorId, requestId, sessionId, advertisedClientToolIds, requestSkills,
                SkillCatalogManifest.EMPTY);
    }

    public ToolResult<AgentToolExecutor> open(
            UUID actorId,
            UUID requestId,
            String sessionId,
            List<String> advertisedClientToolIds,
            SkillCatalogSnapshot requestSkills,
            SkillCatalogManifest clientSkillDocuments) {
        java.util.Objects.requireNonNull(actorId, "actorId");
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(requestSkills, "requestSkills");
        java.util.Objects.requireNonNull(clientSkillDocuments, "clientSkillDocuments");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            return new ToolResult.Failure<>("invalid_session", "Invalid Agent session ID");
        }
        Set<String> accepted = List.copyOf(advertisedClientToolIds).stream()
                .filter(toolId -> trustedTools.find(toolId).isPresent())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        // Rebind document guidance only. The trusted Tool IDs and placement policy do not change.
        ToolRuntimeCatalog requestTools = ToolRuntimeCatalog.from(
                trustedTools.registrations().stream()
                        .map(registration -> registration.tool() instanceof LoadSkillTool
                                ? new RegisteredTool(
                                        registration.providerId(), new LoadSkillTool(requestSkills, "server"))
                                : registration)
                        .toList(),
                Set.of());
        RequestKey key = new RequestKey(actorId, requestId);
        RequestExecutor executor = new RequestExecutor(
                key, sessionId, accepted, requestTools, clientSkillDocuments);
        if (active.putIfAbsent(key, executor) != null) {
            return new ToolResult.Failure<>(
                    "duplicate_request", "Client Tool request ID is already active");
        }
        return new ToolResult.Success<>(executor);
    }

    public boolean receive(UUID actorId, ClientToolResultChunkPayload chunk) {
        RequestExecutor executor = active.get(new RequestKey(actorId, chunk.requestId()));
        return executor != null && executor.receive(chunk);
    }

    /** Converts a correlated transport failure into Tool results for every pending invocation. */
    public int fail(UUID actorId, UUID requestId, String code, String message) {
        RequestExecutor executor = active.get(new RequestKey(actorId, requestId));
        return executor == null ? 0 : executor.failPending(code, message);
    }

    public boolean close(UUID actorId, UUID requestId) {
        RequestExecutor executor = active.remove(new RequestKey(actorId, requestId));
        if (executor == null) {
            return false;
        }
        executor.closed = true;
        executor.cancelPending();
        List.copyOf(executor.retained.keySet()).forEach(executor::closeRequestScope);
        executor.closeRequestScope(actorId + "/" + requestId);
        return true;
    }

    public int disconnect(UUID actorId) {
        List<RequestKey> owned = active.keySet().stream()
                .filter(key -> key.actorId.equals(actorId))
                .toList();
        owned.forEach(key -> close(key.actorId, key.requestId));
        return owned.size();
    }

    public int activeRequests() {
        return active.size();
    }

    public int activeResultAssemblies(UUID actorId, UUID requestId) {
        RequestExecutor executor = active.get(new RequestKey(actorId, requestId));
        return executor == null ? 0 : executor.reassembler.activeAssemblies();
    }

    private final class RequestExecutor implements AgentToolExecutor {
        private final RequestKey key;
        private final String sessionId;
        private final Set<String> clientTools;
        private final ToolRuntimeCatalog requestTools;
        private AgentToolExecutor local;
        private final SkillInstructionContext clientSkillContext;
        private final Map<String, RetainedSkillContext> retained = new ConcurrentHashMap<>();
        private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
        private volatile boolean closed;
        private final ResultPreparation preparation = resultPreparation;
        private final java.util.function.BiPredicate<UUID, UUID> admission = resultAdmission;
        private final ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler(
                dev.openallay.bridge.protocol.BridgeProtocol.PARTIAL_ASSEMBLY_TIMEOUT,
                resultByteLimit, dev.openallay.bridge.protocol.BridgeProtocol.TRANSPORT_CHUNK_BYTES);

        private RequestExecutor(
                RequestKey key,
                String sessionId,
                Set<String> clientTools,
                ToolRuntimeCatalog requestTools,
                SkillCatalogManifest clientSkillDocuments) {
            this.key = key;
            this.sessionId = sessionId;
            this.clientTools = Set.copyOf(clientTools);
            this.requestTools = requestTools;
            this.local = new LocalAgentToolExecutor(requestTools, gson);
            this.clientSkillContext = clientTools.contains("openallay:load_skill")
                    ? new SkillInstructionContext(clientSkillDocuments) : null;
        }

        @Override
        public List<ModelToolDefinition> definitions() {
            return local.definitions();
        }

        @Override
        public Set<ContextCapability> requiredContext() {
            // Tools without an advertised client placement still use the server snapshot.
            return local.requiredContext();
        }

        @Override
        public Optional<String> canonicalToolId(String modelToolName) {
            return local.canonicalToolId(modelToolName);
        }

        @Override
        public CompletableFuture<AgentToolResult> execute(
                String modelToolName,
                JsonObject arguments,
                ToolInvocationContext context,
                CancellationSignal cancellation) {
            String toolId = canonicalToolId(modelToolName).orElse(UNKNOWN_TOOL_ID);
            if (closed) {
                return completedFailure(toolId, "client_tool_unavailable", "Client Tool request is no longer active");
            }
            if (toolId.equals(UNKNOWN_TOOL_ID)) {
                return completedFailure(
                        toolId, "tool_unavailable", "Tool is unavailable in this request");
            }
            if (!clientTools.contains(toolId)) {
                return local.execute(modelToolName, arguments, context, cancellation);
            }
            LoadSkillTool.Input skillInput = null;
            if (clientSkillContext != null && toolId.equals("openallay:load_skill")) {
                if (cancellation.isCancelled()) {
                    return CompletableFuture.failedFuture(new ModelClientException(new ModelFailure(
                            "agent_cancelled", "Client Tool invocation was cancelled", null)));
                }
                ToolResult<LoadSkillTool.Input> decoded = argumentsCodec.decode(
                        arguments, LoadSkillTool.Input.class);
                if (decoded instanceof ToolResult.Failure<LoadSkillTool.Input> failure) {
                    return completedFailure(toolId, failure.code(), failure.message());
                }
                skillInput = ((ToolResult.Success<LoadSkillTool.Input>) decoded).value();
                RetainedSkillContext bound = retained.get(context.correlationId());
                LoadSkillTool.Output reused = bound == null
                        || (SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT.equals(skillInput.name())
                                && !context.unrestrictedJavascript())
                        ? null : clientSkillContext.reuse(skillInput, bound);
                if (reused != null) {
                    return CompletableFuture.completedFuture(new AgentToolResult(
                            toolId, normalizer.normalize(new ToolResult.Success<>(reused),
                                    LoadSkillTool.Output.class), false));
                }
            }
            UUID invocationId = UUID.randomUUID();
            CompletableFuture<AgentToolResult> result = new CompletableFuture<>();
            Pending value = new Pending(toolId, result, skillInput);
            pending.put(invocationId, value);
            cancellation.onCancel(() -> cancelInvocation(invocationId, value));
            ClientToolCallPayload payload = new ClientToolCallPayload(
                    key.requestId,
                    invocationId,
                    sessionId,
                    toolId,
                    arguments.toString());
            synchronized (value) {
                if (pending.get(invocationId) != value || cancellation.isCancelled()) {
                    return result;
                }
                boolean sent;
                try {
                    sent = transport.call(key.actorId, payload);
                } catch (RuntimeException failure) {
                    sent = false;
                }
                value.dispatched = sent;
                if (!sent && pending.remove(invocationId, value)) {
                    result.complete(failure(
                            toolId,
                            "client_tool_bridge_unavailable",
                            "Player client Tool connection is unavailable"));
                } else if (sent && pending.get(invocationId) == value) {
                    ScheduledFuture<?> deadline = TIMEOUTS.schedule(
                            () -> timeoutInvocation(invocationId, value),
                            resultTimeout.toMillis(),
                            TimeUnit.MILLISECONDS);
                    value.setDeadline(deadline);
                }
            }
            return result;
        }

        private boolean receive(ClientToolResultChunkPayload chunk) {
            Pending value = pending.get(chunk.invocationId());
            if (value == null || !current(chunk.invocationId(), value)
                    || !admission.test(key.actorId, key.requestId)) return false;
            try {
                resultWorker.execute(() -> assemble(chunk, value));
                return true;
            } catch (RuntimeException unavailable) {
                failResult(chunk.invocationId(), value, "client_tool_result_invalid",
                        "Player client Tool result preparation is unavailable");
                return false;
            }
        }

        private boolean current(UUID invocation, Pending value) {
            return !closed && active.get(key) == this && pending.get(invocation) == value;
        }

        private void assemble(ClientToolResultChunkPayload chunk, Pending value) {
            // Owner admission can take the service owner lock; never take it under Pending.
            if (!admission.test(key.actorId, key.requestId)) return;
            try {
                String json;
                synchronized (value) {
                    if (!current(chunk.invocationId(), value) || value.accepting) return;
                    Optional<String> complete = reassembler.accept(chunk.asRemoteChunk());
                    if (complete.isEmpty()) return;
                    value.accepting = true;
                    json = complete.orElseThrow();
                }
                var message = new dev.openallay.bridge.protocol.BridgeJsonCodec(gson).decode(
                        json, dev.openallay.bridge.protocol.ToolExecutionMessage.class);
                JsonObject normalized = message.result();
                ValidatedResult validated = validateNormalized(requestTools, value.toolId, normalized);
                if (validated == null
                        || (value.skillInput != null
                                && validated.normalized().get("status").getAsString().equals("success")
                                && !validSkillResult(value.skillInput, normalized, validated.normalized()))) {
                    throw new IllegalArgumentException("Invalid typed client Tool result");
                }
                message.requireImages(validated.images());
                if (!current(chunk.invocationId(), value)) return;
                preparation.prepare(key.actorId, key.requestId, sessionId, validated.images(),
                                message.imageAttachments(), () -> current(chunk.invocationId(), value))
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                failResult(chunk.invocationId(), value, "client_tool_image_failed",
                                        "Player client Tool images were invalid or unavailable");
                                return;
                            }
                            synchronized (value) {
                                if (!current(chunk.invocationId(), value)
                                        || !pending.remove(chunk.invocationId(), value)) return;
                                value.cancelDeadline();
                            }
                            // Completion may synchronously take Agent/service owner locks.
                            boolean failed = validated.normalized().get("status").getAsString().equals("failure");
                            value.result.complete(new AgentToolResult(value.toolId,
                                    validated.normalized(), failed, null, validated.images()));
                        });
            } catch (RuntimeException invalid) {
                failResult(chunk.invocationId(), value, "client_tool_result_invalid",
                        "Player client Tool result was invalid");
            }
        }

        private void failResult(UUID invocation, Pending value, String code, String message) {
            synchronized (value) {
                if (!pending.remove(invocation, value)) return;
                value.cancelDeadline();
                reassembler.cancel(invocation);
            }
            value.result.complete(failure(value.toolId, code, message));
        }

        @Override
        public List<ModelMessage> refreshContext(List<ModelMessage> messages) {
            return clientSkillContext == null ? local.refreshContext(messages)
                    : clientSkillContext.refresh(messages);
        }

        @Override
        public List<ModelMessage> refreshContext(
                List<ModelMessage> messages, RetainedSkillContext retainedSkills) {
            return clientSkillContext == null ? local.refreshContext(messages, retainedSkills)
                    : clientSkillContext.refresh(messages, retainedSkills);
        }

        @Override
        public void prepareContext(String correlationId, List<ModelMessage> messages) {
            prepareContext(correlationId, messages,
                    retained.computeIfAbsent(correlationId, ignored -> new RetainedSkillContext()));
        }

        @Override
        public void prepareContext(
                String correlationId, List<ModelMessage> messages, RetainedSkillContext retainedSkills) {
            if (clientSkillContext == null) {
                local.prepareContext(correlationId, messages, retainedSkills);
            } else {
                clientSkillContext.reconcile(messages, retainedSkills);
            }
            retained.put(correlationId, retainedSkills);
        }

        @Override
        public void prepareSystem(String systemPrompt, RetainedSkillContext retainedSkills) {
            if (clientSkillContext == null) local.prepareSystem(systemPrompt, retainedSkills);
            else clientSkillContext.prepareSystem(systemPrompt, retainedSkills);
        }

        @Override
        public String skillManifest(String correlationId) {
            if (clientSkillContext == null) return local.skillManifest(correlationId);
            RetainedSkillContext bound = retained.get(correlationId);
            return bound == null ? "" : clientSkillContext.manifest(bound);
        }

        @Override
        public String skillSystemPrompt(String prompt) {
            return clientSkillContext == null ? local.skillSystemPrompt(prompt) : prompt;
        }

        @Override
        public void closeRequestScope(String correlationId) {
            retained.remove(correlationId);
            local.closeRequestScope(correlationId);
        }

        private boolean validSkillResult(
                LoadSkillTool.Input input, JsonObject raw, JsonObject validated) {
            if (!raw.get("value").isJsonObject()
                    || !exactSkillOutput(raw.getAsJsonObject("value"))) return false;
            LoadSkillTool.Output output = gson.fromJson(
                    validated.get("value"), LoadSkillTool.Output.class);
            // A client execution must return real text. Only the server can issue reuse receipts.
            return output.state() != LoadSkillTool.LoadState.ALREADY_LOADED
                    && clientSkillContext.validate(input, output);
        }

        private int failPending(String code, String message) {
            List<Map.Entry<UUID, Pending>> values = List.copyOf(pending.entrySet());
            values.forEach(entry -> {
                Pending value = entry.getValue();
                synchronized (value) {
                    if (!pending.remove(entry.getKey(), value)) {
                        return;
                    }
                    value.cancelDeadline();
                    reassembler.cancel(entry.getKey());
                    value.result.complete(failure(value.toolId, code, message));
                }
            });
            return values.size();
        }

        private void cancelPending() {
            List<Map.Entry<UUID, Pending>> values = List.copyOf(pending.entrySet());
            values.forEach(entry -> cancelInvocation(entry.getKey(), entry.getValue()));
        }

        private void cancelInvocation(UUID invocationId, Pending value) {
            boolean dispatched;
            synchronized (value) {
                if (!pending.remove(invocationId, value)) {
                    return;
                }
                dispatched = value.dispatched;
            }
            value.cancelDeadline();
            reassembler.cancel(invocationId);
            if (dispatched) {
                try {
                    transport.cancel(key.actorId, new ClientToolCancelPayload(
                            key.requestId, invocationId));
                } catch (RuntimeException ignored) {
                    // The enclosing cancellation still owns the terminal request state.
                }
            }
            value.result.completeExceptionally(new ModelClientException(new ModelFailure(
                    "agent_cancelled", "Client Tool invocation was cancelled", null)));
        }

        private void timeoutInvocation(UUID invocationId, Pending value) {
            synchronized (value) {
                if (!pending.remove(invocationId, value)) {
                    return;
                }
                reassembler.cancel(invocationId);
            }
            try {
                transport.cancel(key.actorId, new ClientToolCancelPayload(
                        key.requestId, invocationId));
            } catch (RuntimeException ignored) {
                // Timeout remains a complete Tool result even if cancellation cannot be sent.
            }
            value.result.complete(failure(
                    value.toolId,
                    "client_tool_timeout",
                    "Player client Tool result timed out"));
        }
    }

    private CompletableFuture<AgentToolResult> completedFailure(
            String toolId, String code, String message) {
        return CompletableFuture.completedFuture(failure(toolId, code, message));
    }

    private AgentToolResult failure(String toolId, String code, String message) {
        return new AgentToolResult(
                toolId,
                normalizer.normalize(new ToolResult.Failure<>(code, message), Object.class),
                true);
    }

    private record ValidatedResult(JsonObject normalized,
            List<dev.openallay.model.image.ImageReference> images) {}

    private ValidatedResult validateNormalized(
            ToolRuntimeCatalog requestTools, String toolId, JsonObject normalized) {
        if (normalized == null
                || !normalized.has("status")
                || !normalized.get("status").isJsonPrimitive()) {
            return null;
        }
        String status = normalized.get("status").getAsString();
        if (status.equals("failure")) {
            if (!normalized.keySet().equals(Set.of("status", "code", "message"))) {
                return null;
            }
            try {
                return new ValidatedResult(normalizer.normalize(
                        new ToolResult.Failure<>(
                                normalized.get("code").getAsString(),
                                normalized.get("message").getAsString()),
                        Object.class), List.of());
            } catch (RuntimeException invalid) {
                return null;
            }
        }
        if (!status.equals("success")) {
            return null;
        }
        dev.openallay.tool.Tool<?, ?> tool = requestTools.find(toolId).orElse(null);
        if (tool == null) {
            return null;
        }
        boolean hasModelText = normalized.has("modelText");
        Set<String> expectedKeys = hasModelText
                ? Set.of("status", "outputType", "value", "modelText")
                : Set.of("status", "outputType", "value");
        if (!normalized.keySet().equals(expectedKeys)) {
            return null;
        }
        if (hasModelText
                && (!ModelFacingToolOutput.class.isAssignableFrom(tool.descriptor().outputType())
                        || !normalized.get("modelText").isJsonPrimitive()
                        || !normalized.get("modelText").getAsJsonPrimitive().isString()
                        || normalized.get("modelText").getAsString().isBlank())) {
            return null;
        }
        if (!normalized.get("outputType").isJsonPrimitive()
                || !normalized.get("outputType").getAsString()
                        .equals(tool.descriptor().outputType().getName())) {
            return null;
        }
        try {
            Object value = gson.fromJson(normalized.get("value"), tool.descriptor().outputType());
            // The structured value is authoritative; rebuild any model projection locally.
            List<dev.openallay.model.image.ImageReference> images =
                    value instanceof dev.openallay.agent.tool.ModelImageToolOutput visual
                            ? List.copyOf(visual.images()) : List.of();
            dev.openallay.model.image.ModelImages.unique(images);
            return new ValidatedResult(normalizer.normalize(
                    new ToolResult.Success<>(value), tool.descriptor().outputType()), images);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean exactSkillOutput(JsonObject output) {
        if (!output.keySet().equals(Set.of("name", "document", "source", "fingerprint", "state",
                "content", "offset", "nextOffset", "complete", "nextCursor", "availableReferences",
                "allowedTools", "provenance"))) return false;
        for (String field : List.of("name", "document", "source", "fingerprint", "state",
                "content", "nextCursor", "provenance")) {
            var value = output.get(field);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return false;
        }
        for (String field : List.of("offset", "nextOffset")) {
            var value = output.get(field);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || !value.getAsString().matches("[0-9]+")) return false;
            try {
                Integer.parseInt(value.getAsString());
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        var complete = output.get("complete");
        if (!complete.isJsonPrimitive() || !complete.getAsJsonPrimitive().isBoolean()) return false;
        for (String field : List.of("availableReferences", "allowedTools")) {
            var value = output.get(field);
            if (!value.isJsonArray() || dev.openallay.json.JsonReaders.elements(value.getAsJsonArray()).stream()
                    .anyMatch(item -> !item.isJsonPrimitive()
                            || !item.getAsJsonPrimitive().isString())) return false;
        }
        return true;
    }

    private record RequestKey(UUID actorId, UUID requestId) {}

    private static final class Pending {
        private final String toolId;
        private final CompletableFuture<AgentToolResult> result;
        private final LoadSkillTool.Input skillInput;
        private volatile ScheduledFuture<?> deadline;
        private boolean dispatched;
        private boolean accepting;

        private Pending(String toolId, CompletableFuture<AgentToolResult> result,
                LoadSkillTool.Input skillInput) {
            this.toolId = toolId;
            this.result = result;
            this.skillInput = skillInput;
        }

        private void setDeadline(ScheduledFuture<?> replacement) {
            deadline = replacement;
            if (result.isDone()) {
                replacement.cancel(false);
            }
        }

        private void cancelDeadline() {
            ScheduledFuture<?> current = deadline;
            if (current != null) {
                current.cancel(false);
            }
        }
    }
}
