package dev.openallay.agent.tool;

import com.google.gson.JsonObject;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelToolDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface AgentToolExecutor {
    String UNKNOWN_TOOL_ID = "openallay:unknown";

    List<ModelToolDefinition> definitions();

    Set<ContextCapability> requiredContext();

    /**
     * Resolves one provider-returned name to the canonical registered Tool ID.
     * Implementations that accept aliases override this method; the default keeps
     * existing exact-name executors source-compatible.
     */
    default Optional<String> canonicalToolId(String modelToolName) {
        if (modelToolName == null || modelToolName.isBlank()) {
            return Optional.empty();
        }
        return definitions().stream()
                .anyMatch(definition -> definition.name().equals(modelToolName))
                ? Optional.of(modelToolName)
                : Optional.empty();
    }

    CompletableFuture<AgentToolResult> execute(
            String modelToolName,
            JsonObject arguments,
            ToolInvocationContext context,
            CancellationSignal cancellation);

    /** Validates instruction documents against this request's captured catalog. */
    default List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages) { return messages; }

    /** Receipts must describe text in the actual current projection, not a prior loaded flag. */
    default void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages) {}

    /** Session facts are separate from request resources and never own another plaintext copy. */
    default List<dev.openallay.model.ModelMessage> refreshContext(
            List<dev.openallay.model.ModelMessage> messages, dev.openallay.skill.RetainedSkillContext retained) {
        return refreshContext(messages);
    }

    default void prepareContext(String correlationId, List<dev.openallay.model.ModelMessage> messages,
            dev.openallay.skill.RetainedSkillContext retained) {
        prepareContext(correlationId, messages);
    }

    /** Called with the actual redacted system text before each active projection is prepared. */
    default void prepareSystem(String systemPrompt, dev.openallay.skill.RetainedSkillContext retained) {}

    /** Factual index only. It does not grant capabilities or require another Tool call. */
    default String skillManifest(String correlationId) { return ""; }

    /** Assemble inline guidance from the same captured safe document view as load_skill. */
    default String skillSystemPrompt(String prompt) { return prompt; }

    /** Discards an ephemeral Skill context binding without closing unrelated request resources. */
    default void closeSkillContext(String correlationId) {}

    /** Releases resources owned by one terminal Agent request. */
    default void closeRequestScope(String correlationId) {}
}
