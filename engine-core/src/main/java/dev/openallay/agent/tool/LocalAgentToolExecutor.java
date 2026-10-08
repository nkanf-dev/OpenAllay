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
                Objects.requireNonNull(tools, "tools").registrations(), dev.openallay.util.Java8Collections.setOf()), gson);
    }

    public LocalAgentToolExecutor(ToolRuntimeCatalog tools, Gson gson) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.gson = dev.openallay.json.EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
        List<ToolDescriptor<?, ?>> descriptors = tools.descriptors();
        names = new ToolNameCodec(dev.openallay.util.Java8Collections.toList(descriptors.stream().map(ToolDescriptor::id)));
        arguments = new ToolArgumentCodec(gson);
        normalizer = new ToolResultNormalizer(gson);
        ToolSchemaGenerator schemas = new ToolSchemaGenerator();
        definitions = dev.openallay.util.Java8Collections.toList(descriptors.stream()
                .map(descriptor -> new ModelToolDefinition(
                        names.encode(descriptor.id()),
                        descriptor.description(),
                        schemas.generate(descriptor.inputType()))));
    }

    @Override
    public List<ModelToolDefinition> definitions() {
        return definitions;
    }

    @Override
    public Set<ContextCapability> requiredContext() {
        return tools.descriptors().stream()
                .flatMap(descriptor -> descriptor.requiredContext().stream())
                .collect(dev.openallay.util.Java8ApiSupport.toUnmodifiableSet());
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
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Success<?> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = decoded) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<?>) $oaPattern0_holder.value) != null))) {
                return invokeAsync(tool, context, $oaPattern0_holder.bound.value(), cancellation)
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
                                        message == null || dev.openallay.util.Java8Strings.isBlank(message)
                                                ? cause.getClass().getSimpleName()
                                                : message);
                            }
                            JsonObject normalized = normalizer.normalize(
                                    result, tool.descriptor().outputType());
                            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<java.lang.Object> value; ToolResult.Success<?> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
dev.openallay.tool.ModelResultSource source = (($oaPattern1_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern1_holder.bound = (ToolResult.Success<?>) $oaPattern1_holder.value) != null))
                                    ? modelResultSource(tool, context, $oaPattern1_holder.bound.value()).orElse(null) : null;
                            final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<java.lang.Object> value; ToolResult.Success<?> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
final class $oaPattern3_Holder { java.lang.Object value; ModelImageToolOutput bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
return new AgentToolResult(
                                    toolId,
                                    normalized,
                                    result instanceof ToolResult.Failure<?>,
                                    source,
                                    (($oaPattern2_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern2_holder.bound = (ToolResult.Success<?>) $oaPattern2_holder.value) != null))
                                            && (($oaPattern3_holder.value = $oaPattern2_holder.bound.value()) instanceof dev.openallay.agent.tool.ModelImageToolOutput && (($oaPattern3_holder.bound = (ModelImageToolOutput) $oaPattern3_holder.value) != null))
                                            ? $oaPattern3_holder.bound.images() : dev.openallay.util.Java8Collections.listOf());
                        });
            }
            JsonObject normalized = normalizer.normalize(decoded, tool.descriptor().outputType());
            return CompletableFuture.completedFuture(new AgentToolResult(
                    toolId,
                    normalized,
                    true));
        } catch (RuntimeException exception) {
            return dev.openallay.util.Java8Futures.failedFuture(exception);
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
            return dev.openallay.util.Java8Futures.failedFuture(exception);
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
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern4_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern4_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern4_holder.value) != null))) {
                current = $oaPattern4_holder.bound.refreshContext(current);
            }
        }
        return current;
    }

    @Override
    public void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages) {
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern5_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern5_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern5_holder.value) != null))) {
                $oaPattern5_holder.bound.prepareContext(correlationId, messages);
            }
        }
    }

    @Override
    public List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages, dev.openallay.skill.RetainedSkillContext retained) {
        List<dev.openallay.model.ModelMessage> current = messages;
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern6_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern6_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern6_holder.value) != null))) {
                current = $oaPattern6_holder.bound.refreshContext(current, retained);
            }
        }
        return current;
    }

    @Override
    public void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages,
            dev.openallay.skill.RetainedSkillContext retained) {
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern7_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern7_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern7_holder.value) != null))) {
                $oaPattern7_holder.bound.prepareContext(correlationId, messages, retained);
            }
        }
    }

    @Override
    public void prepareSystem(String systemPrompt, dev.openallay.skill.RetainedSkillContext retained) {
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern8_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern8_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern8_holder.value) != null))) {
                $oaPattern8_holder.bound.prepareSystem(systemPrompt, retained);
            }
        }
    }

    @Override
    public String skillManifest(String correlationId) {
        return tools.registrations().stream().map(registration -> registration.tool())
                .filter(dev.openallay.skill.LoadSkillTool.class::isInstance)
                .map(dev.openallay.skill.LoadSkillTool.class::cast)
                .map(skill -> skill.manifest(correlationId)).filter(text -> !dev.openallay.util.Java8Strings.isBlank(text))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Override
    public String skillSystemPrompt(String prompt) {
        String safe = prompt;
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern9_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern9_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern9_holder.value) != null))) {
                safe = $oaPattern9_holder.bound.systemPrompt(safe);
            }
        }
        return safe;
    }

    @Override
    public void closeSkillContext(String correlationId) {
        for (dev.openallay.tool.RegisteredTool registration : tools.registrations()) {
            final class $oaPattern10_Holder { dev.openallay.tool.Tool<?, ?> value; dev.openallay.skill.LoadSkillTool bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = registration.tool()) instanceof dev.openallay.skill.LoadSkillTool && (($oaPattern10_holder.bound = (dev.openallay.skill.LoadSkillTool) $oaPattern10_holder.value) != null))) {
                $oaPattern10_holder.bound.closeRequestScope(correlationId);
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
