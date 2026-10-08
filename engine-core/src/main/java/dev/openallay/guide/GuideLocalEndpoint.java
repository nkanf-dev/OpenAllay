package dev.openallay.guide;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface GuideLocalEndpoint {
    Set<ContextCapability> requiredContext();

    default String defaultProfileId() {
        return "default";
    }

    default List<GuideClientModelProfile> profiles() {
        return dev.openallay.util.Java8Collections.listOf(new GuideClientModelProfile(
                defaultProfileId(),
                "Client model",
                true,
                true,
                "default",
                null));
    }

    default Optional<GuideClientModelProfile> profile(String profileId) {
        return profiles().stream().filter(profile -> profile.id().equals(profileId)).findFirst();
    }

    default Set<ContextCapability> requiredContext(String profileId) {
        GuideClientModelProfile profile = profile(profileId).orElseThrow(() ->
                new GuideModelProfileException(
                        "model_not_configured", "The selected client model profile does not exist"));
        if (!profile.available()) {
            throw new GuideModelProfileException(profile.failure().code(), profile.failure().message());
        }
        return requiredContext();
    }

    default Optional<GuideContextSpec> contextSpec(String profileId) {
        return Optional.empty();
    }

    /** Local runtime-only status. Remote or unobserved requests remain explicitly unknown. */
    default Optional<GuideContextEstimate> contextEstimate(
            String profileId, UUID actor, String sessionId) {
        return Optional.empty();
    }

    /** Available only for a local endpoint with a captured, known context budget. */
    default boolean compactAvailable(String profileId) { return false; }

    /** Exact local profile/capability state, not merely the model name. */
    default Object compactIdentity(String profileId) { return null; }

    /** Prepare only. The caller must save the durable projection before publication. */
    default CompletableFuture<dev.openallay.tool.ToolResult<GuidePreparedCompaction>> prepareCompaction(
            String profileId,
            UUID actor,
            String sessionId,
            UUID controlId,
            List<dev.openallay.model.ModelMessage> durableSeed,
            dev.openallay.model.CancellationSignal cancellation,
            Consumer<AgentEvent> usage) {
        return prepareCompaction(profileId, actor, sessionId, controlId, durableSeed, cancellation,
                dev.openallay.model.image.ImagePayloadResolver.unavailable(), usage);
    }

    /** Image access is request-only and bound to the captured player's actual source references. */
    default CompletableFuture<dev.openallay.tool.ToolResult<GuidePreparedCompaction>> prepareCompaction(
            String profileId,
            UUID actor,
            String sessionId,
            UUID controlId,
            List<dev.openallay.model.ModelMessage> durableSeed,
            dev.openallay.model.CancellationSignal cancellation,
            dev.openallay.model.image.ImagePayloadResolver images,
            Consumer<AgentEvent> usage) {
        return CompletableFuture.completedFuture(new dev.openallay.tool.ToolResult.Failure<>(
                "compact_unavailable", "Manual compaction is unavailable for this model endpoint"));
    }

    /** True only when this scoped session already has actual runtime model context. */
    default boolean hasContext(UUID actor, String sessionId) { return false; }

    default void hydrateContext(
            UUID actor,
            String sessionId,
            List<dev.openallay.model.ModelMessage> messages,
            List<ContextCheckpoint> checkpoints) {}

    CompletableFuture<AgentResult> ask(
            UUID actor,
            String sessionId,
            UUID requestId,
            String question,
            ToolInvocationContext context,
            Consumer<AgentEvent> events);

    default CompletableFuture<AgentResult> ask(
            String profileId,
            UUID actor,
            String sessionId,
            UUID requestId,
            String question,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        if (!defaultProfileId().equals(profileId)) {
            return dev.openallay.util.Java8Futures.failedFuture(new GuideModelProfileException(
                    "model_not_configured", "The selected client model profile does not exist"));
        }
        return ask(actor, sessionId, requestId, question, context, events);
    }

    /** Typed input must never be silently reduced to a text label. */
    default CompletableFuture<AgentResult> ask(
            UUID actor,
            String sessionId,
            UUID requestId,
            dev.openallay.model.ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        dev.openallay.agent.AgentRequest.validateUserInput(userInput);
        if (dev.openallay.model.image.ModelImages.hasImages(dev.openallay.util.Java8Collections.listOf(userInput))) {
            return dev.openallay.util.Java8Futures.failedFuture(new GuideModelProfileException(
                    "image_input_unsupported", "This client model endpoint does not support image input"));
        }
        return ask(actor, sessionId, requestId,
                dev.openallay.agent.AgentRequest.displayText(userInput), context, events);
    }

    default CompletableFuture<AgentResult> ask(
            String profileId,
            UUID actor,
            String sessionId,
            UUID requestId,
            dev.openallay.model.ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        dev.openallay.agent.AgentRequest.validateUserInput(userInput);
        if (dev.openallay.model.image.ModelImages.hasImages(dev.openallay.util.Java8Collections.listOf(userInput))) {
            return dev.openallay.util.Java8Futures.failedFuture(new GuideModelProfileException(
                    "image_input_unsupported", "This client model endpoint does not support image input"));
        }
        return ask(profileId, actor, sessionId, requestId,
                dev.openallay.agent.AgentRequest.displayText(userInput), context, events);
    }

    default dev.openallay.tool.ToolResult<Boolean> steer(
            UUID actor, String sessionId, UUID requestId, UUID messageId,
            dev.openallay.model.ModelMessage message) {
        return new dev.openallay.tool.ToolResult.Success<>(false);
    }

    default boolean cancelSteer(UUID actor, String sessionId, UUID requestId, UUID messageId) {
        return false;
    }

    boolean cancel(UUID actor, String sessionId);

    default boolean cancel(UUID actor, String sessionId, UUID expectedRequestId) {
        return cancel(actor, sessionId);
    }

    void clearSession(UUID actor, String sessionId);

    void clearActor(UUID actor);

}
