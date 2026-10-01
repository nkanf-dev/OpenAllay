package dev.openallay.agent;

import com.google.gson.JsonElement;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.agent.trace.LiveTraceJson;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Known credentials only. This is an output boundary, not a JVM sandbox. */
public final class KnownSecretRedactor {
    private record State(Set<String> values, LiveTraceJson.SecretPlan plan) {}
    private final AtomicReference<State> secrets = new AtomicReference<>(
            new State(Set.of(), new LiveTraceJson.SecretPlan(Set.of())));

    public KnownSecretRedactor() {}

    public KnownSecretRedactor(Set<String> initial) {
        remember(initial);
    }

    /** Keep retired credentials while captured requests and shared runtime history can still use them. */
    public void remember(Set<String> additional) {
        secrets.updateAndGet(current -> {
            var updated = new java.util.HashSet<>(current.values());
            additional.stream().filter(value -> value != null && !value.isBlank()).forEach(updated::add);
            if (updated.size() == current.values().size()) return current;
            Set<String> values = Set.copyOf(updated);
            return new State(values, new LiveTraceJson.SecretPlan(values));
        });
    }

    public String text(String value) {
        return LiveTraceJson.redact(value, secrets.get().plan());
    }

    public JsonElement json(JsonElement value) {
        return LiveTraceJson.redact(value, secrets.get().plan());
    }

    public List<ModelMessage> messages(List<ModelMessage> messages) {
        return ModelContextCodec.redacted(messages, secrets.get().plan());
    }

    /** A model turn may end in unpaired tool uses, so do not validate a complete exchange here. */
    public List<ModelContent> content(List<ModelContent> content) {
        ArrayList<ModelContent> safe = new ArrayList<>();
        for (ModelContent item : content) {
            switch (item) {
                case ModelContent.Text value -> safe.add(new ModelContent.Text(text(value.text())));
                case ModelContent.ToolUse use -> safe.add(new ModelContent.ToolUse(
                        LiveTraceJson.redactIdentity(use.id(), secrets.get().plan()), text(use.name()), json(use.input()).getAsJsonObject()));
                case ModelContent.ToolResult result -> safe.add(new ModelContent.ToolResult(
                        LiveTraceJson.redactIdentity(result.toolUseId(), secrets.get().plan()), json(result.value()), result.error()));
                case ModelContent.Reasoning ignored -> { /* Provider-private reasoning is not retained. */ }
            }
        }
        return List.copyOf(safe);
    }

    public AgentEvent event(AgentEvent event) {
        return AgentEventRedactor.redact(event, secrets.get().plan());
    }

    public ModelRequest request(ModelRequest request) {
        List<ModelToolDefinition> tools = request.tools().stream().map(tool -> new ModelToolDefinition(
                text(tool.name()), text(tool.description()), json(tool.inputSchema()).getAsJsonObject())).toList();
        return new ModelRequest(text(request.systemPrompt()), messages(request.messages()), tools,
                request.stream(), text(request.sessionKey()));
    }

    /** Encodes snapshots without publishing the credential set. */
    public String encodeTrace(dev.openallay.agent.trace.LiveAgentTrace trace) {
        return new dev.openallay.agent.trace.LiveTraceJson().encode(trace, secrets.get().plan());
    }
}
