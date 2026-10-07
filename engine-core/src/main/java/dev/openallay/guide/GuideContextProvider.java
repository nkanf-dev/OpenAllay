package dev.openallay.guide;

import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.tool.ToolResult;
import java.util.Set;

@FunctionalInterface
public interface GuideContextProvider {
    ToolResult<ToolInvocationContext> capture(
            Set<ContextCapability> capabilities, String correlationId);

    default void associateInputObservation(String correlationId,
            java.util.Optional<dev.openallay.world.ClientObservationAnchor> observation) {}

    default void freezeRequest(String correlationId, boolean clientLocalModel) {}

    /** Revoke native observation work without dropping request-produced image pins. */
    default void closeRequest(String correlationId) {}

    /** Typed producer references; no images are inferred from model-returned JSON. */
    default java.util.List<dev.openallay.model.image.ImageReference> observationImageReferences(String correlationId) {
        return dev.openallay.util.Java8Collections.listOf();
    }

    /** Internal custody barrier. Call only after the published transcript owns its images. */
    default java.util.concurrent.CompletableFuture<Void> releaseObservationImages(String correlationId) {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    /** Detach old native work now; release only that snapshot after custody settles. */
    default java.util.concurrent.CompletableFuture<Void> detachConnectionState(
            java.util.concurrent.CompletableFuture<Void> custody) {
        clearConnectionState();
        return custody;
    }

    /** Owner-thread connection boundary. It must invalidate captured data without sampling Game state. */
    default void clearConnectionState() {}

    default ToolResult<Integer> refreshKnowledge() {
        return new ToolResult.Success<>(0);
    }
}
