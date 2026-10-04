package dev.openallay.agent.trace;

import dev.openallay.agent.AgentState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LiveAgentTrace(
        UUID requestId,
        UUID actorId,
        String sessionId,
        Instant startedAt,
        Instant completedAt,
        AgentState finalState,
        List<LiveTraceEvent> events,
        String finalText,
        String errorCode) {
    public LiveAgentTrace {
        events = List.copyOf(events);
    }
}
