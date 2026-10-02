package dev.openallay.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.context.ContextCompactor;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.trace.LiveAgentTrace;
import dev.openallay.agent.trace.LiveAgentTraceRecorder;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelTurn;
import dev.openallay.guide.GuideToolInvocationPresentation;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class GameGuideAgent {
    private static final String REPEATED_CALL_SENTINEL = "\u0000no_new_information";
    private final ModelClient model;
    private final AgentToolExecutor tools;
    private final AgentSessionStore sessions;
    private final Gson gson;
    private final ToolResultNormalizer canonicalizer;
    private final ContextCompactor compactor;
    private final java.util.function.BiConsumer<AgentRequest, Integer> contextEstimates;

    public GameGuideAgent(
            ModelClient model,
            AgentToolExecutor tools,
            AgentSessionStore sessions,
            Gson gson) {
        this(model, tools, sessions, gson, null);
    }

    public GameGuideAgent(
            ModelClient model,
            AgentToolExecutor tools,
            AgentSessionStore sessions,
            Gson gson,
            ContextCompactor compactor) {
        this(model, tools, sessions, gson, compactor, (request, tokens) -> {});
    }

    /** Local counts-only observer. It does not add Agent events or change the wire/history schema. */
    public GameGuideAgent(
            ModelClient model, AgentToolExecutor tools, AgentSessionStore sessions, Gson gson,
            ContextCompactor compactor,
            java.util.function.BiConsumer<AgentRequest, Integer> contextEstimates) {
        this.contextEstimates = Objects.requireNonNull(contextEstimates, "contextEstimates");
        this.model = dev.openallay.model.ObservingModelClient.observe(model);
        this.tools = Objects.requireNonNull(tools, "tools");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.compactor = compactor;
        canonicalizer = new ToolResultNormalizer(gson);
    }

    private static void emitModelUsage(Consumer<AgentEvent> events, ModelEvent event) {
        if (event instanceof ModelEvent.UsageObserved usage) {
            events.accept(new AgentEvent.ModelUsageObserved(
                    usage.callId(), usage.modelIdentifier(), usage.usage()));
        } else if (event instanceof ModelEvent.UsageStarted started) {
            events.accept(new AgentEvent.ModelUsageStarted(started.callId(), started.modelIdentifier()));
        }
    }

    public CompletableFuture<AgentResult> ask(
            AgentRequest request, Consumer<AgentEvent> events) {
        return askReserved(
                request,
                events,
                sessions.reserve(request.sessionKey(), request.requestId()));
    }

    public CompletableFuture<AgentResult> askWithHistory(
            AgentRequest request,
            List<ModelMessage> history,
            Consumer<AgentEvent> events) {
        return askReserved(
                request,
                events,
                sessions.reserveWithHistory(
                        request.sessionKey(), request.requestId(), history));
    }

    private CompletableFuture<AgentResult> askReserved(
            AgentRequest rawRequest,
            Consumer<AgentEvent> rawEvents,
            ToolResult<AgentSessionStore.Lease> reservation) {
        AgentRequest request = new AgentRequest(rawRequest.requestId(), rawRequest.actorId(), rawRequest.sessionId(),
                rawRequest.userInput(), tools.skillSystemPrompt(rawRequest.systemPrompt()),
                rawRequest.context(), rawRequest.stream(), rawRequest.images());
        Consumer<AgentEvent> events = rawEvents;
        if (reservation instanceof ToolResult.Failure<AgentSessionStore.Lease> failure) {
            events.accept(new AgentEvent.Failed(failure.code(), failure.message()));
            return CompletableFuture.completedFuture(new AgentResult(
                    AgentState.FAILED, null, failure.code(), failure.message(), null));
        }
        AgentSessionStore.Lease lease =
                ((ToolResult.Success<AgentSessionStore.Lease>) reservation).value();
        LiveAgentTraceRecorder trace = new LiveAgentTraceRecorder(gson, request);
        try {
            transition(AgentState.PREPARING, trace, events);
            lease.cancellation().throwIfCancelled();
            List<ModelMessage> originalHistory = List.copyOf(lease.history());
            tools.prepareSystem(request.systemPrompt(), lease.retainedSkills());
            List<ModelMessage> restoredView = compactor == null ? originalHistory
                    : compactor.prepareModelView(originalHistory);
            List<ModelMessage> messages = new ArrayList<>(dev.openallay.agent.context.ModelContextCodec.safe(tools.refreshContext(
                    restoredView, lease.retainedSkills())));
            int protectedFromIndex = messages.size();
            ModelMessage question = request.userInput();
            messages.add(question);
            List<ModelMessage> complete = new ArrayList<>(originalHistory);
            complete.add(question);
            List<ModelMessage> completeMessages = List.copyOf(complete);
            tools.prepareContext(request.context().correlationId(), messages, lease.retainedSkills());
            String preparedPrompt = systemPrompt(request);
            sessions.recordContext(lease, messages, completeMessages);
            events.accept(new AgentEvent.ContextUpdated(messages, lease.progress().requestMessages()));
            if (compactor != null && !lease.checkpoints().isEmpty()) {
                for (int index = lease.checkpoints().size() - 1; index >= 0; index--) {
                    var reused = compactor.reuse(
                            lease.checkpoints().get(index),
                            preparedPrompt,
                            messages,
                            protectedFromIndex,
                            tools.definitions());
                    if (reused.isPresent()) {
                        transition(AgentState.MODEL_WAIT, trace, events);
                        return loop(
                                        request,
                                        lease,
                                        reused.orElseThrow().messages(),
                                        completeMessages,
                                        Math.max(0, reused.orElseThrow().messages().size() - 1),
                                        Map.of(),
                                        trace,
                                        events)
                                .exceptionallyAsync(throwable ->
                                        fail(request, lease, trace, events, throwable));
                    }
                }
            }
            transition(AgentState.MODEL_WAIT, trace, events);
            return loop(
                            request,
                            lease,
                            messages,
                            completeMessages,
                            protectedFromIndex,
                            Map.of(),
                            trace,
                            events)
                    .exceptionallyAsync(throwable -> fail(request, lease, trace, events, throwable));
        } catch (RuntimeException failure) {
            return CompletableFuture.supplyAsync(() -> fail(request, lease, trace, events, failure));
        }
    }

    private CompletableFuture<AgentResult> loop(
            AgentRequest request,
            AgentSessionStore.Lease lease,
            List<ModelMessage> messages,
            List<ModelMessage> completeMessages,
            int protectedFromIndex,
            Map<String, String> previousCallOutcomes,
            LiveAgentTraceRecorder trace,
            Consumer<AgentEvent> events) {
        lease.cancellation().throwIfCancelled();
        List<AgentSessionStore.Steer> instructions = sessions.drainSteers(lease);
        if (!instructions.isEmpty()) {
            List<ModelMessage> updated = new ArrayList<>(messages);
            List<ModelMessage> original = new ArrayList<>(completeMessages);
            for (AgentSessionStore.Steer instruction : instructions) {
                updated.add(instruction.message());
                original.add(instruction.message());
            }
            List<ModelMessage> admitted = dev.openallay.agent.context.ModelContextCodec.safe(updated);
            List<ModelMessage> complete = dev.openallay.agent.context.ModelContextCodec.safe(original);
            if (sessions.recordContext(lease, admitted, complete)) {
                events.accept(new AgentEvent.ContextUpdated(admitted, lease.progress().requestMessages()));
                instructions.forEach(instruction -> events.accept(
                        new AgentEvent.SteerApplied(instruction.messageId(), instruction.message())));
            }
            return loop(request, lease, admitted, complete, protectedFromIndex,
                    previousCallOutcomes, trace, events);
        }
        tools.prepareSystem(request.systemPrompt(), lease.retainedSkills());
        List<ModelMessage> modelView = compactor == null ? messages : compactor.prepareModelView(messages);
        List<ModelMessage> projectedMessages = dev.openallay.agent.context.ModelContextCodec.safe(tools.refreshContext(
                modelView, lease.retainedSkills()));
        tools.prepareContext(request.context().correlationId(), projectedMessages, lease.retainedSkills());
        String projectedPrompt = systemPrompt(request);
        if (compactor != null
                && compactor.requiresCompaction(
                        projectedPrompt, projectedMessages, tools.definitions())) {
            int protectedMessageCount = projectedMessages.size() - protectedFromIndex;
            transition(AgentState.COMPACTING, trace, events);
            return compactor.compact(
                            candidate -> promptForProjection(request, lease, candidate),
                            projectedMessages,
                            protectedFromIndex,
                            tools.definitions(),
                            request.stream(),
                            request.sessionKey().schedulingKey(),
                            lease.cancellation(),
                            request.images(),
                            usage -> emitModelUsage(events, usage))
                    .thenCompose(result -> {
                        if (result.checkpoint() != null) {
                            sessions.recordCheckpoint(lease, result.checkpoint());
                            events.accept(new AgentEvent.ContextCompacted(result.checkpoint()));
                        }
                        if (!result.successful()) {
                            throw new ModelClientException(new dev.openallay.model.ModelFailure(
                                    result.failureCode(), result.failureMessage(), null));
                        }
                        if (result.projection().messages().equals(projectedMessages)
                                || result.projection().estimatedTokens() >= compactor.estimateTokens(
                                        projectedPrompt, projectedMessages, tools.definitions())) {
                            throw new ModelClientException(new dev.openallay.model.ModelFailure(
                                    "context_compaction_no_progress",
                                    "Context projection made no budget progress", null));
                        }
                        int nextProtectedFrom = Math.max(
                                0,
                                result.projection().messages().size()
                                        - protectedMessageCount);
                        transition(AgentState.MODEL_WAIT, trace, events);
                        return loop(
                                request,
                                lease,
                                result.projection().messages(),
                                completeMessages,
                                nextProtectedFrom,
                                previousCallOutcomes,
                                trace,
                                events);
                    });
        }
        List<ModelMessage> dispatchCompleteMessages = captureInitialResultProjection(
                completeMessages, projectedMessages);
        boolean changedProjection = !lease.progress().projected().equals(projectedMessages);
        if (sessions.recordContext(lease, projectedMessages, dispatchCompleteMessages) && changedProjection) {
            events.accept(new AgentEvent.ContextUpdated(projectedMessages, lease.progress().requestMessages()));
        }
        ModelRequest modelRequest = new ModelRequest(
                projectedPrompt,
                projectedMessages,
                tools.definitions(),
                request.stream(),
                request.sessionKey().schedulingKey(),
                null,
                request.images());
        lease.cancellation().throwIfCancelled();
        int estimatedTokens = compactor == null
                ? dev.openallay.model.tokenizer.ModelContextTokenEstimator.conservative().estimate(
                        modelRequest.systemPrompt(), modelRequest.messages(), modelRequest.tools())
                : compactor.estimateTokens(
                        modelRequest.systemPrompt(), modelRequest.messages(), modelRequest.tools());
        if (compactor != null && estimatedTokens > compactor.inputTokenBudget()) {
            throw new ModelClientException(new dev.openallay.model.ModelFailure(
                    "context_budget_exceeded", "Final model request exceeds the configured input budget", null));
        }
        try {
            contextEstimates.accept(request, estimatedTokens);
        } catch (RuntimeException ignored) {
            // An optional diagnostic observer cannot break model execution.
        }
        trace.modelRequest(modelRequest);
        return lease.cancellation().observe(model.complete(
                        modelRequest,
                        event -> {
                            if (event instanceof ModelEvent.UsageObserved
                                    || event instanceof ModelEvent.UsageStarted) {
                                emitModelUsage(events, event);
                            } else if (!lease.cancellation().isCancelled()) {
                                events.accept(new AgentEvent.ModelProgress(event));
                            }
                        },
                        lease.cancellation()))
                .thenCompose(rawTurn -> {
                    lease.cancellation().throwIfCancelled();
                    var turn = new dev.openallay.model.ModelTurn(
                            rawTurn.providerId(), rawTurn.model(),
                            rawTurn.content().stream().filter(content -> !(content instanceof ModelContent.Reasoning)).toList(),
                            rawTurn.stopReason(), rawTurn.usage());
                    trace.modelTurn(turn);
                    List<ModelMessage> nextMessages = new ArrayList<>(projectedMessages);
                    nextMessages.add(new ModelMessage(ModelRole.ASSISTANT, turn.content()));
                    List<ModelMessage> nextCompleteMessages = new ArrayList<>(dispatchCompleteMessages);
                    nextCompleteMessages.add(new ModelMessage(ModelRole.ASSISTANT, turn.content()));
                    if (turn.toolUses().isEmpty()) {
                        sessions.sealSteers(lease);
                        nextMessages = new ArrayList<>(dev.openallay.agent.context.ModelContextCodec.safe(nextMessages));
                        nextCompleteMessages = new ArrayList<>(dev.openallay.agent.context.ModelContextCodec.safe(nextCompleteMessages));
                        if (turn.text().isBlank()) {
                            throw new ModelClientException(new dev.openallay.model.ModelFailure(
                                    "model_protocol_error",
                                    "Model returned neither tool use nor final text",
                                    null));
                        }
                        transition(AgentState.COMPLETED, trace, events);
                        // Runtime memory keeps the successfully projected context. Durable guide
                        // history independently retains the original request/timeline projection.
                        lease.cancellation().cancel();
                        try {
                            tools.closeRequestScope(request.context().correlationId());
                        } catch (RuntimeException ignored) {
                            // Cleanup cannot replace an actual completed answer.
                        }
                        sessions.recordContext(lease, nextMessages, nextCompleteMessages);
                        sessions.finish(lease, nextMessages);
                        events.accept(new AgentEvent.ContextUpdated(
                                nextMessages, lease.progress().requestMessages()));
                        events.accept(new AgentEvent.ContextFinalized(
                                nextMessages, lease.progress().requestMessages()));
                        LiveAgentTrace completed = trace.finish(AgentState.COMPLETED, turn.text(), null);
                        events.accept(new AgentEvent.FinalText(turn.text()));
                        return CompletableFuture.completedFuture(new AgentResult(
                                AgentState.COMPLETED, turn.text(), null, null, completed));
                    }
                    transition(AgentState.TOOL_WAIT, trace, events);
                    return executeTools(
                                    request,
                                    lease,
                                    turn.toolUses(),
                                    nextMessages,
                                    nextCompleteMessages,
                                    previousCallOutcomes,
                                    trace,
                                    events)
                            .thenCompose(outcome -> {
                                transition(AgentState.MODEL_WAIT, trace, events);
                                return loop(
                                        request,
                                        lease,
                                        outcome.messages(),
                                        outcome.completeMessages(),
                                        protectedFromIndex,
                                        outcome.callOutcomes(),
                                        trace,
                                        events);
                            });
                });
    }

    private CompletableFuture<ToolOutcome> executeTools(
            AgentRequest request,
            AgentSessionStore.Lease lease,
            List<ModelContent.ToolUse> calls,
            List<ModelMessage> messages,
            List<ModelMessage> completeMessages,
            Map<String, String> previousCallOutcomes,
            LiveAgentTraceRecorder trace,
            Consumer<AgentEvent> events) {
        List<PendingToolCall> pending = new ArrayList<>();
        java.util.Set<String> firstCalls = new java.util.HashSet<>();
        java.util.Set<String> duplicatedThisTurn = new java.util.HashSet<>();
        java.util.concurrent.atomic.AtomicReference<RuntimeException> terminalFailure =
                new java.util.concurrent.atomic.AtomicReference<>();
        for (ModelContent.ToolUse call : calls) {
            String exposedId = tools.canonicalToolId(call.name())
                    .orElse(AgentToolExecutor.UNKNOWN_TOOL_ID);
            JsonObject executionArguments = dev.openallay.tool.builtin.RunJavascriptTool.ID.equals(exposedId)
                    ? dev.openallay.tool.builtin.RunJavascriptTool.executionArguments(call.input())
                    : call.input();
            String callKey = exposedId + ":" + canonical(executionArguments);
            String previousOutcome = previousCallOutcomes.get(callKey);
            AgentToolResult feedback = null;
            boolean skillRead = "openallay:load_skill".equals(exposedId);
            if (!skillRead && REPEATED_CALL_SENTINEL.equals(previousOutcome)) {
                ModelClientException repeated = new ModelClientException(
                        new dev.openallay.model.ModelFailure(
                                "repeated_tool_call",
                                "Model ignored a no-new-information result and repeated the same tool call again",
                                null));
                terminalFailure.compareAndSet(null, repeated);
                feedback = recoverToolFailure(exposedId, repeated);
            } else if (!skillRead && (previousOutcome != null || !firstCalls.add(callKey))) {
                duplicatedThisTurn.add(callKey);
                feedback = noNewInformation(exposedId);
            }
            trace.toolCall(exposedId, call.input());
            pending.add(new PendingToolCall(call, exposedId, callKey, feedback));
        }

        // Declare the entire exchange and its capture before any executor can cancel,
        // block, or complete. Cancellation always has a slot for each advertised ID.
        CompletableFuture<?>[] futures = pending.stream()
                .map(item -> item.result)
                .toArray(CompletableFuture[]::new);
        java.util.concurrent.atomic.AtomicBoolean capturedContext =
                new java.util.concurrent.atomic.AtomicBoolean();
        CompletableFuture<ToolOutcome> captured = CompletableFuture.allOf(futures).thenApply(ignored -> {
            List<ModelContent> results = new ArrayList<>();
            List<AgentToolResult> freshResults = new ArrayList<>();
            Map<String, String> updatedCallOutcomes =
                    new java.util.HashMap<>(previousCallOutcomes);
            for (PendingToolCall item : pending) {
                AgentToolResult raw = item.result.join();
                AgentToolResult result = raw;
                results.add(new ModelContent.ToolResult(
                        item.call.id(), result.modelValue(), result.failure()));
                freshResults.add(result);
                updatedCallOutcomes.put(
                        item.callKey,
                        duplicatedThisTurn.contains(item.callKey)
                                ? REPEATED_CALL_SENTINEL
                                : canonical(result.modelValue()));
            }
            List<ModelMessage> updated = new ArrayList<>(messages);
            updated.add(new ModelMessage(ModelRole.USER, results));
            updated = new ArrayList<>(dev.openallay.agent.context.ModelContextCodec.safe(updated));
            if (compactor != null) {
                // Allocate all successful results against the same full request, not one per-tool
                // token/byte guess. Keep completed errors exact and the original history untouched.
                List<ModelMessage> actual = updated;
                var fitted = compactor.fitResults(
                        candidate -> promptForProjection(request, lease, candidate),
                        actual, tools.definitions(), freshResults);
                if (fitted.isPresent()) updated = new ArrayList<>(fitted.orElseThrow().messages());
            }
            List<ModelContent> initialResults = updated.getLast().content();
            List<ModelMessage> updatedComplete = new ArrayList<>(completeMessages);
            updatedComplete.add(new ModelMessage(ModelRole.USER, initialResults));
            updatedComplete = new ArrayList<>(dev.openallay.agent.context.ModelContextCodec.safe(updatedComplete));
            capturedContext.set(sessions.recordContext(lease, updated, updatedComplete));
            // Capture only safe progress here. Observers and terminal cleanup run later.
            return new ToolOutcome(
                    updated, updatedComplete, Map.copyOf(updatedCallOutcomes), List.copyOf(results));
        });
        for (PendingToolCall item : pending) {
            lease.cancellation().onCancel(() -> cancelToolResult(item));
        }
        for (PendingToolCall item : pending) {
            if (item.feedback != null) item.result.complete(item.feedback);
        }

        if (!lease.cancellation().isCancelled()) {
            try {
                tools.prepareContext(request.context().correlationId(),
                        messages.subList(0, messages.size() - 1), lease.retainedSkills());
            } catch (RuntimeException failure) {
                terminalFailure.compareAndSet(null, failure);
                for (PendingToolCall item : pending) {
                    item.result.complete(recoverToolFailure(item.exposedId, failure));
                }
            }
        }
        for (PendingToolCall item : pending) {
            if (item.result.isDone()) continue;
            if (lease.cancellation().isCancelled()) {
                cancelToolResult(item);
                continue;
            }
            item.started.set(true);
            events.accept(new AgentEvent.ToolStarted(
                    item.call.id(),
                    item.exposedId,
                    item.call.input(),
                    GuideToolInvocationPresentation.messages(item.exposedId, item.call.input())));
            if (item.result.isDone()) continue;
            if (lease.cancellation().isCancelled()) {
                cancelToolResult(item);
                continue;
            }
            CompletableFuture<AgentToolResult> execution;
            try {
                execution = tools.execute(
                        item.call.name(), item.call.input(), request.context(), lease.cancellation());
                if (execution == null) {
                    execution = CompletableFuture.failedFuture(new IllegalStateException(
                            "Tool executor completed without a result"));
                }
            } catch (RuntimeException failure) {
                execution = CompletableFuture.failedFuture(failure);
            }
            item.execution.set(execution);
            if (lease.cancellation().isCancelled()) {
                // A value returned only after Stop is late feedback, not a pre-Stop result.
                item.result.complete(cancelledToolResult(item.exposedId));
            } else {
                execution.whenComplete((result, failure) -> settleToolResult(item, result, failure));
            }
        }
        return captured.thenApplyAsync(outcome -> {
            for (PendingToolCall item : pending) {
                AgentToolResult result = item.result.join();
                trace.toolResult(result);
                if (item.started.get()) {
                    events.accept(new AgentEvent.ToolCompleted(
                            item.call.id(), result.toolId(), result.failure(), result.normalized()));
                }
            }
            if (capturedContext.get()) {
                events.accept(new AgentEvent.ContextUpdated(
                        outcome.messages(), lease.progress().requestMessages()));
            }
            // Pair capture has no observer callbacks. Terminal cleanup stays on this worker.
            lease.cancellation().throwIfCancelled();
            RuntimeException failure = terminalFailure.get();
            if (failure != null) throw failure;
            return outcome;
        });
    }

    private void settleToolResult(
            PendingToolCall item, AgentToolResult rawResult, Throwable executionFailure) {
        if (item.result.isDone()) return;
        try {
            if (executionFailure == null && rawResult != null) {
                item.result.complete(rawResult);
            } else {
                item.result.complete(recoverToolFailure(item.exposedId, executionFailure));
            }
        } catch (RuntimeException malformed) {
            item.result.complete(recoverToolFailure(item.exposedId, malformed));
        }
    }

    private void cancelToolResult(PendingToolCall item) {
        if (item.result.isDone()) return;
        CompletableFuture<AgentToolResult> execution = item.execution.get();
        // Read only a completed raw future. This does not wait for tool execution.
        if (execution != null && execution.isDone()) {
            try {
                settleToolResult(item, execution.join(), null);
            } catch (RuntimeException failure) {
                settleToolResult(item, null, failure);
            }
        } else {
            item.result.complete(cancelledToolResult(item.exposedId));
        }
    }

    private AgentToolResult cancelledToolResult(String exposedId) {
        return new AgentToolResult(exposedId, normalizedFailure(
                "agent_cancelled", "Tool call did not complete before the Agent request was cancelled"),
                true);
    }

    private AgentResult fail(
            AgentRequest request,
            AgentSessionStore.Lease lease,
            LiveAgentTraceRecorder trace,
            Consumer<AgentEvent> events,
            Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String code;
        String message;
        AgentState state;
        if (lease.cancellation().isCancelled()) {
            code = "agent_cancelled";
            message = "Agent request was cancelled";
            state = AgentState.CANCELLED;
        } else if (cause instanceof ModelClientException exception) {
            code = exception.failure().code();
            message = exception.failure().message();
            state = code.equals("agent_cancelled") ? AgentState.CANCELLED : AgentState.FAILED;
        } else {
            code = "agent_failure";
            message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            state = AgentState.FAILED;
        }
        sessions.sealSteers(lease);
        trace.failure(code, message);
        List<ModelMessage> retained = new ArrayList<>(lease.progress().projected());
        List<ModelMessage> original = new ArrayList<>(lease.progress().original());
        ModelMessage failureNote = new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(
                "[OpenAllay request ended: " + code + "] " + message)));
        retained.add(failureNote);
        original.add(failureNote);
        boolean recorded = sessions.recordContext(lease, retained, original);
        // Keep the lease owned until request resources are revoked and closed.
        lease.cancellation().cancel();
        try {
            tools.closeRequestScope(request.context().correlationId());
        } catch (RuntimeException ignored) {
            // Cleanup cannot replace the original failure or strand the active lease.
        }
        sessions.finish(lease, retained);
        if (recorded) {
            events.accept(new AgentEvent.ContextUpdated(retained, lease.progress().requestMessages()));
        }
        if (state == AgentState.CANCELLED) {
            sessions.finalizeCancelled(lease, retained);
        }
        events.accept(new AgentEvent.ContextFinalized(retained, lease.progress().requestMessages()));
        transition(state, trace, events);
        LiveAgentTrace completed = trace.finish(state, null, code);
        events.accept(new AgentEvent.Failed(code, message));
        return new AgentResult(state, null, code, message, completed);
    }

    /** The new exchange is captured with its first admitted provider view, not a raw dump. */
    private static List<ModelMessage> captureInitialResultProjection(
            List<ModelMessage> original, List<ModelMessage> projected) {
        if (original.isEmpty() || projected.isEmpty()) return original;
        ModelMessage originalLast = original.getLast();
        ModelMessage projectedLast = projected.getLast();
        if (originalLast.content().stream().allMatch(ModelContent.ToolResult.class::isInstance)
                && projectedLast.content().stream().allMatch(ModelContent.ToolResult.class::isInstance)) {
            List<String> originalIds = originalLast.content().stream()
                    .map(ModelContent.ToolResult.class::cast).map(ModelContent.ToolResult::toolUseId).toList();
            List<String> projectedIds = projectedLast.content().stream()
                    .map(ModelContent.ToolResult.class::cast).map(ModelContent.ToolResult::toolUseId).toList();
            if (originalIds.equals(projectedIds)) {
                ArrayList<ModelMessage> captured = new ArrayList<>(original);
                captured.set(captured.size() - 1, projectedLast);
                return List.copyOf(captured);
            }
        }
        return original;
    }

    private String promptForProjection(AgentRequest request, AgentSessionStore.Lease lease,
            List<ModelMessage> messages) {
        lease.cancellation().throwIfCancelled();
        tools.prepareContext(request.context().correlationId(), messages, lease.retainedSkills());
        return systemPrompt(request);
    }

    private String systemPrompt(AgentRequest request) {
        String facts = tools.skillManifest(request.context().correlationId());
        return facts.isBlank() ? request.systemPrompt() : request.systemPrompt() + "\n" + facts;
    }

    private String canonical(JsonElement value) {
        return gson.toJson(canonicalizer.canonicalize(value));
    }

    private JsonObject normalizedFailure(String code, String message) {
        return canonicalizer.normalize(new ToolResult.Failure<>(code, message), Object.class);
    }

    private AgentToolResult noNewInformation(String toolId) {
        return new AgentToolResult(
                toolId,
                normalizedFailure(
                        "no_new_information",
                        "This exact read-only call already completed against the same request snapshot; stop calling tools and answer from the existing evidence."),
                true);
    }

    private AgentToolResult recoverToolFailure(String toolId, Throwable throwable) {
        Throwable cause = throwable == null
                ? new IllegalStateException("Tool executor completed without a result")
                : unwrap(throwable);
        String code = cause instanceof ModelClientException exception
                ? exception.failure().code() : "tool_failure";
        String detail = cause instanceof ModelClientException exception
                ? exception.failure().message() : cause.getMessage();
        if (detail == null || detail.isBlank()) detail = cause.getClass().getSimpleName();
        return new AgentToolResult(toolId, normalizedFailure(code, detail), true);
    }

    private static void transition(
            AgentState state,
            LiveAgentTraceRecorder trace,
            Consumer<AgentEvent> events) {
        trace.state(state);
        events.accept(new AgentEvent.StateChanged(state));
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record ToolOutcome(
            List<ModelMessage> messages,
            List<ModelMessage> completeMessages,
            Map<String, String> callOutcomes,
            List<ModelContent> results) {}

    private static final class PendingToolCall {
        private final ModelContent.ToolUse call;
        private final String exposedId;
        private final String callKey;
        private final AgentToolResult feedback;
        private final CompletableFuture<AgentToolResult> result = new CompletableFuture<>();
        private final java.util.concurrent.atomic.AtomicBoolean started =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicReference<CompletableFuture<AgentToolResult>> execution =
                new java.util.concurrent.atomic.AtomicReference<>();

        private PendingToolCall(
                ModelContent.ToolUse call, String exposedId, String callKey, AgentToolResult feedback) {
            this.call = call;
            this.exposedId = exposedId;
            this.callKey = callKey;
            this.feedback = feedback;
        }
    }
}
