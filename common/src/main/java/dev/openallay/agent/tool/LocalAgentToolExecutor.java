package dev.openallay.agent.tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class LocalAgentToolExecutor implements AgentToolExecutor {
    private final ToolRuntimeCatalog tools;
    private final Gson gson;
    private final ToolNameCodec names;
    private final ToolArgumentCodec arguments;
    private final ToolResultNormalizer normalizer;
    private final List<ModelToolDefinition> definitions;

    public LocalAgentToolExecutor(ToolRegistry tools, Gson gson) {
        this(ToolRuntimeCatalog.from(
                Objects.requireNonNull(tools, "tools").registrations(), Set.of()), gson);
    }

    public LocalAgentToolExecutor(ToolRuntimeCatalog tools, Gson gson) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.gson = Objects.requireNonNull(gson, "gson");
        List<ToolDescriptor<?, ?>> descriptors = tools.descriptors();
        names = new ToolNameCodec(descriptors.stream().map(ToolDescriptor::id).toList());
        arguments = new ToolArgumentCodec(gson);
        normalizer = new ToolResultNormalizer(gson);
        ToolSchemaGenerator schemas = new ToolSchemaGenerator();
        definitions = descriptors.stream()
                .map(descriptor -> new ModelToolDefinition(
                        names.encode(descriptor.id()),
                        descriptor.description(),
                        schemas.generate(descriptor.inputType())))
                .toList();
    }

    @Override
    public List<ModelToolDefinition> definitions() {
        return definitions;
    }

    @Override
    public Set<ContextCapability> requiredContext() {
        return tools.descriptors().stream()
                .flatMap(descriptor -> descriptor.requiredContext().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Optional<String> canonicalToolId(String modelToolName) {
        return tools.knownToolId(modelToolName);
    }

    @Override
    public CompletableFuture<AgentToolResult> execute(
            String modelToolName,
            JsonObject rawArguments,
            ToolInvocationContext context,
            CancellationSignal cancellation) {
        try {
            cancellation.throwIfCancelled();
            String toolId = canonicalToolId(modelToolName).orElse(UNKNOWN_TOOL_ID);
            Tool<?, ?> tool = tools.find(toolId).orElse(null);
            if (tool == null) {
                ToolResult.Failure<Object> unavailable = new ToolResult.Failure<>(
                        "tool_unavailable", "Tool is unavailable in this request");
                return CompletableFuture.completedFuture(new AgentToolResult(
                        toolId, normalizer.normalize(unavailable, Object.class), true));
            }
            ToolResult<?> decoded = arguments.decode(rawArguments, tool.descriptor().inputType());
            if (decoded instanceof ToolResult.Success<?> success) {
                return invokeAsync(tool, context, success.value(), cancellation)
                        .handle((result, failure) -> {
                            if (failure != null) {
                                if (cancellation.isCancelled()) {
                                    throw new java.util.concurrent.CompletionException(
                                            unwrap(failure));
                                }
                                Throwable cause = unwrap(failure);
                                String message = cause.getMessage();
                                result = new ToolResult.Failure<>(
                                        "tool_failure",
                                        message == null || message.isBlank()
                                                ? cause.getClass().getSimpleName()
                                                : message);
                            }
                            JsonObject normalized = normalizer.normalize(
                                    result, tool.descriptor().outputType());
                            dev.openallay.tool.ModelResultSource source = result instanceof ToolResult.Success<?> succeeded
                                    ? modelResultSource(tool, context, succeeded.value()).orElse(null) : null;
                            return new AgentToolResult(
                                    toolId,
                                    normalized,
                                    result instanceof ToolResult.Failure<?>,
                                    source);
                        });
            }
            JsonObject normalized = normalizer.normalize(decoded, tool.descriptor().outputType());
            return CompletableFuture.completedFuture(new AgentToolResult(
                    toolId,
                    normalized,
                    true));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <I, O> java.util.Optional<dev.openallay.tool.ModelResultSource> modelResultSource(
            Tool<?, ?> rawTool, ToolInvocationContext context, Object output) {
        return ((Tool<I, O>) rawTool).modelResultSource(context, (O) output);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletableFuture<ToolResult<O>> invokeAsync(
            Tool<?, ?> rawTool,
            ToolInvocationContext context,
            Object rawInput,
            CancellationSignal cancellation) {
        Tool<I, O> tool = (Tool<I, O>) rawTool;
        try {
            return tool.invokeAsync(context, (I) rawInput, cancellation);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages) {
        List<dev.openallay.model.ModelMessage> current = messages;
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                current = skill.refreshContext(current);
            }
        }
        return current;
    }

    @Override
    public void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages) {
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                skill.prepareContext(correlationId, messages);
            }
        }
    }

    @Override
    public List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages, dev.openallay.skill.RetainedSkillContext retained) {
        List<dev.openallay.model.ModelMessage> current = messages;
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                current = skill.refreshContext(current, retained);
            }
        }
        return current;
    }

    @Override
    public void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages,
            dev.openallay.skill.RetainedSkillContext retained) {
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                skill.prepareContext(correlationId, messages, retained);
            }
        }
    }

    @Override
    public void prepareSystem(String systemPrompt, dev.openallay.skill.RetainedSkillContext retained) {
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                skill.prepareSystem(systemPrompt, retained);
            }
        }
    }

    @Override
    public String skillManifest(String correlationId) {
        return tools.registrations().stream().map(registration -> registration.tool())
                .filter(dev.openallay.skill.LoadSkillTool.class::isInstance)
                .map(dev.openallay.skill.LoadSkillTool.class::cast)
                .map(skill -> skill.manifest(correlationId)).filter(text -> !text.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Override
    public String skillSystemPrompt(String prompt) {
        String safe = prompt;
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                safe = skill.systemPrompt(safe);
            }
        }
        return safe;
    }

    @Override
    public void closeSkillContext(String correlationId) {
        for (var registration : tools.registrations()) {
            if (registration.tool() instanceof dev.openallay.skill.LoadSkillTool skill) {
                skill.closeRequestScope(correlationId);
            }
        }
    }

    @Override
    public void closeRequestScope(String correlationId) {
        tools.registrations().stream()
                .map(registration -> registration.tool())
                .filter(RequestScopeParticipant.class::isInstance)
                .map(RequestScopeParticipant.class::cast)
                .forEach(participant -> participant.closeRequestScope(correlationId));
    }
}
