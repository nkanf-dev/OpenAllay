package dev.openallay.agent.tool;

import com.google.gson.JsonObject;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CompositeAgentToolExecutor implements AgentToolExecutor {
    private final List<AgentToolExecutor> delegates;

    public CompositeAgentToolExecutor(List<? extends AgentToolExecutor> delegates) {
        this.delegates = dev.openallay.util.Java8Collections.listCopyOf(delegates);
    }

    @Override
    public List<ModelToolDefinition> definitions() {
        List<ModelToolDefinition> definitions = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (AgentToolExecutor delegate : delegates) {
            for (ModelToolDefinition definition : delegate.definitions()) {
                if (!names.add(definition.name())) {
                    throw new IllegalStateException("Duplicate model tool name " + definition.name());
                }
                definitions.add(definition);
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(definitions);
    }

    @Override
    public Set<ContextCapability> requiredContext() {
        return delegates.stream().flatMap(value -> value.requiredContext().stream())
                .collect(dev.openallay.util.Java8ApiSupport.toUnmodifiableSet());
    }

    @Override
    public Optional<String> canonicalToolId(String modelToolName) {
        String resolved = null;
        for (AgentToolExecutor delegate : delegates) {
            Optional<String> candidate = delegate.canonicalToolId(modelToolName);
            if (!candidate.isPresent()) {
                continue;
            }
            if (resolved != null && !resolved.equals(candidate.orElseThrow(() -> new java.util.NoSuchElementException("No value present")))) {
                throw new IllegalStateException(
                        "Ambiguous model Tool alias " + modelToolName);
            }
            resolved = candidate.orElseThrow(() -> new java.util.NoSuchElementException("No value present"));
        }
        return Optional.ofNullable(resolved);
    }

    @Override
    public CompletableFuture<AgentToolResult> execute(
            String modelToolName,
            JsonObject arguments,
            ToolInvocationContext context,
            CancellationSignal cancellation) {
        for (AgentToolExecutor delegate : delegates) {
            if (delegate.canonicalToolId(modelToolName).isPresent()) {
                return delegate.execute(modelToolName, arguments, context, cancellation);
            }
        }
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "failure");
        normalized.addProperty("code", "tool_unavailable");
        normalized.addProperty("message", "Tool is unavailable in this request");
        return CompletableFuture.completedFuture(
                new AgentToolResult(UNKNOWN_TOOL_ID, normalized, true));
    }

    @Override
    public List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages) {
        List<dev.openallay.model.ModelMessage> current = messages;
        for (AgentToolExecutor delegate : delegates) current = delegate.refreshContext(current);
        return current;
    }

    @Override
    public void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages) {
        delegates.forEach(delegate -> delegate.prepareContext(correlationId, messages));
    }

    @Override
    public List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages, dev.openallay.skill.RetainedSkillContext retained) {
        List<dev.openallay.model.ModelMessage> current = messages;
        for (AgentToolExecutor delegate : delegates) current = delegate.refreshContext(current, retained);
        return current;
    }

    @Override
    public void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages,
            dev.openallay.skill.RetainedSkillContext retained) {
        delegates.forEach(delegate -> delegate.prepareContext(correlationId, messages, retained));
    }

    @Override
    public void prepareSystem(String systemPrompt, dev.openallay.skill.RetainedSkillContext retained) {
        delegates.forEach(delegate -> delegate.prepareSystem(systemPrompt, retained));
    }

    @Override
    public String skillManifest(String correlationId) {
        return delegates.stream().map(delegate -> delegate.skillManifest(correlationId))
                .filter(text -> !dev.openallay.util.Java8Strings.isBlank(text)).collect(java.util.stream.Collectors.joining("\n"));
    }

    @Override
    public String skillSystemPrompt(String prompt) {
        String safe = prompt;
        for (AgentToolExecutor delegate : delegates) safe = delegate.skillSystemPrompt(safe);
        return safe;
    }

    @Override
    public void closeSkillContext(String correlationId) {
        delegates.forEach(delegate -> delegate.closeSkillContext(correlationId));
    }

    @Override
    public void closeRequestScope(String correlationId) {
        delegates.forEach(delegate -> delegate.closeRequestScope(correlationId));
    }
}
