package dev.openallay.guide;

import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.tool.ToolResult;
import java.util.Set;

@FunctionalInterface
public interface GuideContextProvider {
    ToolResult<ToolInvocationContext> capture(
            Set<ContextCapability> capabilities, String correlationId);

    default void freezeRequest(String correlationId, boolean clientLocalModel) {}

    default void closeRequest(String correlationId) {}

    /** Owner-thread connection boundary. It must invalidate captured data without sampling Game state. */
    default void clearConnectionState() {}

    default ToolResult<Integer> refreshKnowledge() {
        return new ToolResult.Success<>(0);
    }
}
