package dev.tomewisp.guide;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tomewisp.agent.AgentEvent;
import dev.tomewisp.agent.AgentState;
import dev.tomewisp.context.EvidenceMetadata;
import dev.tomewisp.model.ModelEvent;
import dev.tomewisp.model.ModelFailure;
import dev.tomewisp.guide.semantic.SemanticMessageParser;
import dev.tomewisp.guide.semantic.SemanticReferenceIndex;
import dev.tomewisp.guide.semantic.SemanticStreamingState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuideStateReducer {
    private final Gson gson;
    private final SemanticMessageParser semanticParser = new SemanticMessageParser();
    private final ConcurrentHashMap<SegmentKey, SemanticStreamingState> semanticStates =
            new ConcurrentHashMap<>();

    public GuideStateReducer(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public GuideRequestSnapshot apply(
            GuideRequestSnapshot current, AgentEvent event, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(now, "now");
        if (current.terminal()) {
            return current;
        }

        List<GuideTimelineEntry> timeline = current.timeline();
        GuideRequestStatus status = current.status();
        List<GuideSource> sources = current.sources();
        var usage = current.usage();
        Long retryAfter = current.retryAfterMillis();
        GuideFailure failure = current.failure();
        Instant terminalAt = null;

        switch (event) {
            case AgentEvent.StateChanged changed -> status = state(changed.state());
            case AgentEvent.ContextCompacted ignored -> {}
            case AgentEvent.ToolStarted started -> {
                if (toolMatches(timeline, started.invocationId()) != 0) {
                    return protocolFailure(
                            current,
                            timeline,
                            "Tool invocation identity is duplicated: " + started.invocationId(),
                            now);
                }
                timeline = closeAssistant(current.requestId(), timeline);
                timeline = startTool(timeline, started);
                status = GuideRequestStatus.TOOL_WAIT;
            }
            case AgentEvent.ToolCompleted completed -> {
                int match = runningTool(timeline, completed.invocationId());
                if (match < 0) {
                    return protocolFailure(
                            current,
                            timeline,
                            "Tool completion identity is missing or ambiguous: "
                                    + completed.invocationId(),
                            now);
                }
                GuideTimelineEntry.Tool running =
                        (GuideTimelineEntry.Tool) timeline.get(match);
                if (!sameTool(running.activity().toolId(), completed.toolId())) {
                    return protocolFailure(
                            current,
                            timeline,
                            "Tool completion identity changed tool: "
                                    + completed.invocationId(),
                            now);
                }
                List<GuideSource> toolSources = sources(completed.toolId(), completed.normalized());
                GuideToolActivity replacement = new GuideToolActivity(
                        completed.invocationId(),
                        running.activity().index(),
                        completed.toolId(),
                        completed.failure() ? GuideToolStatus.FAILED : GuideToolStatus.SUCCEEDED,
                        completed.normalized(),
                        GuideToolPresentation.lines(completed.toolId(), completed.normalized()),
                        toolSources);
                ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
                next.set(match, new GuideTimelineEntry.Tool(running.ordinal(), replacement));
                timeline = List.copyOf(next);
                ArrayList<GuideSource> merged = new ArrayList<>(sources);
                for (GuideSource source : toolSources) {
                    if (!merged.contains(source)) {
                        merged.add(source);
                    }
                }
                merged.sort(Comparator
                        .comparing((GuideSource value) -> value.evidence().sourceId())
                        .thenComparing(value -> value.evidence().provenance())
                        .thenComparing(GuideSource::toolId));
                sources = List.copyOf(merged);
            }
            case AgentEvent.ModelProgress progress -> {
                switch (progress.event()) {
                    case ModelEvent.TextDelta delta -> {
                        if (!delta.text().isEmpty()) {
                            timeline = appendText(current.requestId(), timeline, delta.text());
                        }
                        status = GuideRequestStatus.MODEL_WAIT;
                        retryAfter = null;
                    }
                    case ModelEvent.UsageUpdate update -> usage = update.usage();
                    case ModelEvent.RateLimited limited -> {
                        status = GuideRequestStatus.RATE_LIMITED;
                        retryAfter = limited.retryAfterMillis();
                    }
                    case ModelEvent.ReasoningDelta ignored -> {
                        return current;
                    }
                    case ModelEvent.ToolUseComplete ignored -> {}
                    case ModelEvent.MessageComplete ignored -> {}
                    case ModelFailure ignored -> {}
                }
            }
            case AgentEvent.FinalText completed -> {
                timeline = reconcileFinal(current.requestId(), timeline, completed.text());
                clearSemanticStates(current.requestId());
                status = GuideRequestStatus.COMPLETED;
                retryAfter = null;
                terminalAt = now;
            }
            case AgentEvent.Failed failed -> {
                timeline = closeAssistant(current.requestId(), timeline);
                clearSemanticStates(current.requestId());
                failure = new GuideFailure(failed.code(), failed.message());
                status = failed.code().equals("agent_cancelled")
                        ? GuideRequestStatus.CANCELLED
                        : GuideRequestStatus.FAILED;
                retryAfter = null;
                terminalAt = now;
            }
        }
        return new GuideRequestSnapshot(
                current.requestId(),
                current.sessionId(),
                current.topology(),
                current.userMessage(),
                timeline,
                status,
                sources,
                usage,
                retryAfter,
                failure,
                current.createdAt(),
                now,
                terminalAt,
                current.modelSelection());
    }

    private List<GuideTimelineEntry> appendText(
            UUID requestId, List<GuideTimelineEntry> timeline, String delta) {
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        int ordinal;
        String text;
        if (!next.isEmpty()
                && next.getLast() instanceof GuideTimelineEntry.Assistant assistant
                && assistant.streaming()) {
            ordinal = assistant.ordinal();
            text = assistant.text() + delta;
            SemanticStreamingState state = semanticStates.computeIfAbsent(
                    new SegmentKey(requestId, ordinal),
                    ignored -> SemanticStreamingState.empty().update(
                            assistant.text(), false, semanticParser,
                            SemanticReferenceIndex.from(requestId, timeline)));
            state = state.update(
                    text, false, semanticParser,
                    SemanticReferenceIndex.from(requestId, timeline));
            semanticStates.put(new SegmentKey(requestId, ordinal), state);
            next.set(next.size() - 1, new GuideTimelineEntry.Assistant(
                    ordinal,
                    text,
                    state.document(),
                    true,
                    assistant.sources()));
        } else {
            ordinal = next.size();
            text = delta;
            SemanticStreamingState state = SemanticStreamingState.empty().update(
                    text, false, semanticParser,
                    SemanticReferenceIndex.from(requestId, timeline));
            semanticStates.put(new SegmentKey(requestId, ordinal), state);
            next.add(new GuideTimelineEntry.Assistant(
                    ordinal, text, state.document(), true, List.of()));
        }
        return List.copyOf(next);
    }

    private static List<GuideTimelineEntry> startTool(
            List<GuideTimelineEntry> timeline, AgentEvent.ToolStarted started) {
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        int toolIndex = (int) next.stream()
                .filter(GuideTimelineEntry.Tool.class::isInstance)
                .count();
        next.add(new GuideTimelineEntry.Tool(next.size(), new GuideToolActivity(
                started.invocationId(),
                toolIndex,
                started.toolId(),
                GuideToolStatus.RUNNING,
                null,
                List.of(),
                List.of())));
        return List.copyOf(next);
    }

    private List<GuideTimelineEntry> closeAssistant(
            UUID requestId,
            List<GuideTimelineEntry> timeline) {
        if (timeline.isEmpty()
                || !(timeline.getLast() instanceof GuideTimelineEntry.Assistant assistant)
                || !assistant.streaming()) {
            return timeline;
        }
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        SegmentKey key = new SegmentKey(requestId, assistant.ordinal());
        SemanticStreamingState state = semanticStates.getOrDefault(
                key, SemanticStreamingState.empty());
        state = state.update(
                assistant.text(), true, semanticParser,
                SemanticReferenceIndex.from(requestId, timeline));
        semanticStates.remove(key);
        next.set(next.size() - 1, new GuideTimelineEntry.Assistant(
                assistant.ordinal(), assistant.text(), state.document(), false,
                assistant.sources()));
        return List.copyOf(next);
    }

    private List<GuideTimelineEntry> reconcileFinal(
            UUID requestId, List<GuideTimelineEntry> timeline, String text) {
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        int ordinal = next.size();
        List<GuideSource> sources = List.of();
        if (!next.isEmpty()
                && next.getLast() instanceof GuideTimelineEntry.Assistant assistant) {
            ordinal = assistant.ordinal();
            sources = assistant.sources();
        }
        SegmentKey key = new SegmentKey(requestId, ordinal);
        SemanticStreamingState state = semanticStates.getOrDefault(
                key, SemanticStreamingState.empty());
        state = state.update(
                text, true, semanticParser,
                SemanticReferenceIndex.from(requestId, timeline));
        GuideTimelineEntry.Assistant reconciled = new GuideTimelineEntry.Assistant(
                ordinal, text, state.document(), false, sources);
        if (ordinal < next.size()) {
            next.set(ordinal, reconciled);
        } else {
            next.add(reconciled);
        }
        return List.copyOf(next);
    }

    private void clearSemanticStates(UUID requestId) {
        semanticStates.keySet().removeIf(key -> key.requestId().equals(requestId));
    }

    private static int toolMatches(
            List<GuideTimelineEntry> timeline, String invocationId) {
        return (int) timeline.stream()
                .filter(GuideTimelineEntry.Tool.class::isInstance)
                .map(GuideTimelineEntry.Tool.class::cast)
                .filter(entry -> entry.activity().invocationId().equals(invocationId))
                .count();
    }

    private static int runningTool(
            List<GuideTimelineEntry> timeline, String invocationId) {
        int match = -1;
        for (int index = 0; index < timeline.size(); index++) {
            if (timeline.get(index) instanceof GuideTimelineEntry.Tool tool
                    && tool.activity().invocationId().equals(invocationId)
                    && tool.activity().status() == GuideToolStatus.RUNNING) {
                if (match >= 0) {
                    return -1;
                }
                match = index;
            }
        }
        return match;
    }

    private static boolean sameTool(String started, String completed) {
        return started.equals(completed) || decodedModelToolId(started).equals(completed);
    }

    private GuideRequestSnapshot protocolFailure(
            GuideRequestSnapshot current,
            List<GuideTimelineEntry> timeline,
            String message,
            Instant now) {
        clearSemanticStates(current.requestId());
        return new GuideRequestSnapshot(
                current.requestId(),
                current.sessionId(),
                current.topology(),
                current.userMessage(),
                timeline,
                GuideRequestStatus.FAILED,
                current.sources(),
                current.usage(),
                null,
                new GuideFailure("timeline_protocol_error", message),
                current.createdAt(),
                now,
                now,
                current.modelSelection());
    }

    private static GuideRequestStatus state(AgentState state) {
        return switch (state) {
            case IDLE, PREPARING -> GuideRequestStatus.PREPARING;
            case COMPACTING -> GuideRequestStatus.COMPACTING;
            case MODEL_WAIT -> GuideRequestStatus.MODEL_WAIT;
            case TOOL_WAIT -> GuideRequestStatus.TOOL_WAIT;
            case COMPLETED -> GuideRequestStatus.COMPLETING;
            case FAILED -> GuideRequestStatus.FAILED;
            case CANCELLED -> GuideRequestStatus.CANCELLED;
        };
    }

    private List<GuideSource> sources(String toolId, JsonObject normalized) {
        if (!normalized.has("value") || !normalized.get("value").isJsonObject()) {
            return List.of();
        }
        JsonElement evidence = normalized.getAsJsonObject("value").get("evidence");
        if (evidence == null || !evidence.isJsonArray()) {
            return List.of();
        }
        ArrayList<GuideSource> result = new ArrayList<>();
        for (JsonElement item : evidence.getAsJsonArray()) {
            result.add(new GuideSource(toolId, gson.fromJson(item, EvidenceMetadata.class)));
        }
        return List.copyOf(result);
    }

    private static String decodedModelToolId(String value) {
        String local = value.startsWith("server__")
                ? value.substring("server__".length())
                : value;
        return local.replace("_slash_", "/")
                .replace("_dot_", ".")
                .replace("__", ":");
    }

    private record SegmentKey(UUID requestId, int ordinal) {}
}
