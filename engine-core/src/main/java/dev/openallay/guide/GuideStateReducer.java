package dev.openallay.guide;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentState;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
import dev.openallay.context.SourceObservationCollector;
import dev.openallay.guide.semantic.SemanticMessageParser;
import dev.openallay.guide.semantic.SemanticReferenceIndex;
import dev.openallay.guide.semantic.SemanticStreamingState;
import dev.openallay.json.EngineJson;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuideStateReducer {
    private final Gson gson;
    private final SemanticMessageParser semanticParser = new SemanticMessageParser();
    private final ConcurrentHashMap<SegmentKey, SemanticStreamingState> semanticStates =
            new ConcurrentHashMap<>();

    public GuideStateReducer(Gson gson) {
        this.gson = EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
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
        dev.openallay.model.ModelUsage usage = current.usage();
        Long retryAfter = current.retryAfterMillis();
        GuideFailure failure = current.failure();
        Instant terminalAt = null;
        GuideRequestProgress progress = current.progress();

        final class $oaPattern0_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.StateChanged bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = event) instanceof dev.openallay.agent.AgentEvent.StateChanged && (($oaPattern0_holder.bound = (AgentEvent.StateChanged) $oaPattern0_holder.value) != null))) {
            status = state($oaPattern0_holder.bound.state());
            progress = progress.advance(
                    phase($oaPattern0_holder.bound.state()), now, progress.attempt(), null, null);
        } else {
final class $oaPattern1_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.RequestReleased bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = event) instanceof dev.openallay.agent.AgentEvent.RequestReleased && (($oaPattern1_holder.bound = (AgentEvent.RequestReleased) $oaPattern1_holder.value) != null))) {
            return current;
        } else {
final class $oaPattern2_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ModelUsageStarted bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ModelUsageStarted && (($oaPattern2_holder.bound = (AgentEvent.ModelUsageStarted) $oaPattern2_holder.value) != null))) {
            return current;
        } else {
final class $oaPattern3_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ModelUsageObserved bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ModelUsageObserved && (($oaPattern3_holder.bound = (AgentEvent.ModelUsageObserved) $oaPattern3_holder.value) != null))) {
            return current;
        } else {
final class $oaPattern4_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextUpdated bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextUpdated && (($oaPattern4_holder.bound = (AgentEvent.ContextUpdated) $oaPattern4_holder.value) != null))) {
            return current;
        } else {
final class $oaPattern5_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextFinalized bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextFinalized && (($oaPattern5_holder.bound = (AgentEvent.ContextFinalized) $oaPattern5_holder.value) != null))) {
            return current;
        } else {
final class $oaPattern6_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.SteerRejected bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = event) instanceof dev.openallay.agent.AgentEvent.SteerRejected && (($oaPattern6_holder.bound = (AgentEvent.SteerRejected) $oaPattern6_holder.value) != null))) {
            return current;
        } else {
final class $oaPattern7_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.SteerApplied bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = event) instanceof dev.openallay.agent.AgentEvent.SteerApplied && (($oaPattern7_holder.bound = (AgentEvent.SteerApplied) $oaPattern7_holder.value) != null))) {
            if (timeline.stream().filter(GuideTimelineEntry.User.class::isInstance)
                    .map(GuideTimelineEntry.User.class::cast)
                    .anyMatch(user -> user.messageId().equals($oaPattern7_holder.bound.messageId()))) return current;
            timeline = closeAssistant(current.requestId(), timeline);
            ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
            next.add(new GuideTimelineEntry.User(next.size(), $oaPattern7_holder.bound.messageId(),
                    GuidePendingMessage.displayText($oaPattern7_holder.bound.message())));
            timeline = dev.openallay.util.Java8Collections.listCopyOf(next);
        } else {
final class $oaPattern8_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ContextCompacted bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ContextCompacted && (($oaPattern8_holder.bound = (AgentEvent.ContextCompacted) $oaPattern8_holder.value) != null))) {
            progress = progress.advance(
                    GuideRequestPhase.COMPACTING,
                    now,
                    progress.attempt(),
                    null,
                    null);
        } else {
final class $oaPattern9_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ToolStarted bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ToolStarted && (($oaPattern9_holder.bound = (AgentEvent.ToolStarted) $oaPattern9_holder.value) != null))) {
            if (toolMatches(timeline, $oaPattern9_holder.bound.invocationId()) != 0) {
                return protocolFailure(
                        current,
                        timeline,
                        "Tool invocation identity is duplicated: " + $oaPattern9_holder.bound.invocationId(),
                        now);
            }
            timeline = closeAssistant(current.requestId(), timeline);
            timeline = startTool(timeline, $oaPattern9_holder.bound);
            status = GuideRequestStatus.TOOL_WAIT;
            progress = progress.advance(
                    GuideRequestPhase.TOOL_WAIT,
                    now,
                    progress.attempt(),
                    null,
                    null);
        } else {
final class $oaPattern10_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ToolCompleted bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ToolCompleted && (($oaPattern10_holder.bound = (AgentEvent.ToolCompleted) $oaPattern10_holder.value) != null))) {
            int match = runningTool(timeline, $oaPattern10_holder.bound.invocationId());
            if (match < 0) {
                return protocolFailure(
                        current,
                        timeline,
                        "Tool completion identity is missing or ambiguous: "
                                + $oaPattern10_holder.bound.invocationId(),
                        now);
            }
            GuideTimelineEntry.Tool running =
                    (GuideTimelineEntry.Tool) timeline.get(match);
            if (!sameTool(running.activity().toolId(), $oaPattern10_holder.bound.toolId())) {
                return protocolFailure(
                        current,
                        timeline,
                        "Tool completion identity changed tool: "
                                + $oaPattern10_holder.bound.invocationId(),
                        now);
            }
            List<GuideSource> toolSources = sources($oaPattern10_holder.bound.toolId(), $oaPattern10_holder.bound.normalized());
            GuideToolActivity replacement = new GuideToolActivity(
                    $oaPattern10_holder.bound.invocationId(),
                    running.activity().index(),
                    $oaPattern10_holder.bound.toolId(),
                    $oaPattern10_holder.bound.failure() ? GuideToolStatus.FAILED : GuideToolStatus.SUCCEEDED,
                    running.activity().invocationArguments(),
                    running.activity().invocation().withNormalized($oaPattern10_holder.bound.normalized()),
                    $oaPattern10_holder.bound.normalized(),
                    mergePresentationMessages(
                            running.activity().presentationMessages(),
                            GuideToolPresentation.messages(
                                    $oaPattern10_holder.bound.toolId(), $oaPattern10_holder.bound.normalized())),
                    toolSources);
            ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
            next.set(match, new GuideTimelineEntry.Tool(running.ordinal(), replacement));
            timeline = dev.openallay.util.Java8Collections.listCopyOf(next);
            sources = mergeSources(sources, toolSources);
            progress = progress.advance(GuideRequestPhase.TOOL_WAIT, now);
        } else {
final class $oaPattern11_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ModelProgress bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ModelProgress && (($oaPattern11_holder.bound = (AgentEvent.ModelProgress) $oaPattern11_holder.value) != null))) {
            ModelEvent modelEvent = $oaPattern11_holder.bound.event();
            Objects.requireNonNull(modelEvent);
            final class $oaPattern12_Holder { dev.openallay.model.ModelEvent value; ModelEvent.AttemptStarted bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.AttemptStarted && (($oaPattern12_holder.bound = (ModelEvent.AttemptStarted) $oaPattern12_holder.value) != null))) {
                Instant observed = now.isBefore(progress.lastProgressAt())
                        ? progress.lastProgressAt()
                        : now;
                Instant deadline = $oaPattern12_holder.bound.attemptTimeoutMillis() != null
                        ? observed.plusMillis($oaPattern12_holder.bound.attemptTimeoutMillis())
                        : $oaPattern12_holder.bound.attempt() == progress.attempt()
                                ? progress.deadlineAt()
                                : null;
                status = GuideRequestStatus.MODEL_WAIT;
                retryAfter = null;
                progress = progress.advance(
                        GuideRequestPhase.MODEL_WAIT,
                        observed,
                        $oaPattern12_holder.bound.attempt(),
                        null,
                        deadline);
            } else {
final class $oaPattern13_Holder { dev.openallay.model.ModelEvent value; ModelEvent.ResponseStarted bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.ResponseStarted && (($oaPattern13_holder.bound = (ModelEvent.ResponseStarted) $oaPattern13_holder.value) != null))) {
                status = GuideRequestStatus.MODEL_WAIT;
                retryAfter = null;
                progress = progress.advance(
                        GuideRequestPhase.RESPONSE_STREAMING,
                        now,
                        progress.attempt(),
                        null,
                        progress.deadlineAt());
            } else {
final class $oaPattern14_Holder { dev.openallay.model.ModelEvent value; ModelEvent.TextDelta bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.TextDelta && (($oaPattern14_holder.bound = (ModelEvent.TextDelta) $oaPattern14_holder.value) != null))) {
                if (!$oaPattern14_holder.bound.text().isEmpty()) {
                    timeline = appendText(current.requestId(), timeline, $oaPattern14_holder.bound.text());
                }
                status = GuideRequestStatus.MODEL_WAIT;
                retryAfter = null;
                progress = progress.advance(
                        GuideRequestPhase.RESPONSE_STREAMING,
                        now,
                        progress.attempt(),
                        null,
                        progress.deadlineAt());
            } else {
final class $oaPattern15_Holder { dev.openallay.model.ModelEvent value; ModelEvent.UsageStarted bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.UsageStarted && (($oaPattern15_holder.bound = (ModelEvent.UsageStarted) $oaPattern15_holder.value) != null))) {
                return current;
            } else {
final class $oaPattern16_Holder { dev.openallay.model.ModelEvent value; ModelEvent.UsageObserved bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if ((($oaPattern16_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.UsageObserved && (($oaPattern16_holder.bound = (ModelEvent.UsageObserved) $oaPattern16_holder.value) != null))) {
                return current;
            } else {
final class $oaPattern17_Holder { dev.openallay.model.ModelEvent value; ModelEvent.UsageUpdate bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if ((($oaPattern17_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.UsageUpdate && (($oaPattern17_holder.bound = (ModelEvent.UsageUpdate) $oaPattern17_holder.value) != null))) {
                usage = $oaPattern17_holder.bound.usage();
                progress = progress.advance(
                        GuideRequestPhase.RESPONSE_STREAMING, now);
            } else {
final class $oaPattern18_Holder { dev.openallay.model.ModelEvent value; ModelEvent.RateLimited bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.RateLimited && (($oaPattern18_holder.bound = (ModelEvent.RateLimited) $oaPattern18_holder.value) != null))) {
                status = GuideRequestStatus.RATE_LIMITED;
                retryAfter = $oaPattern18_holder.bound.retryAfterMillis();
                Instant observed = now.isBefore(progress.lastProgressAt())
                        ? progress.lastProgressAt()
                        : now;
                progress = progress.advance(
                        GuideRequestPhase.ENDPOINT_WAIT,
                        observed,
                        $oaPattern18_holder.bound.attempt(),
                        observed.plusMillis($oaPattern18_holder.bound.retryAfterMillis()),
                        null);
            } else {
final class $oaPattern19_Holder { dev.openallay.model.ModelEvent value; ModelEvent.ReasoningDelta bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.ReasoningDelta && (($oaPattern19_holder.bound = (ModelEvent.ReasoningDelta) $oaPattern19_holder.value) != null))) {
                progress = progress.advance(GuideRequestPhase.RESPONSE_STREAMING, now);
            } else {
final class $oaPattern20_Holder { dev.openallay.model.ModelEvent value; ModelEvent.ToolUseComplete bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.ToolUseComplete && (($oaPattern20_holder.bound = (ModelEvent.ToolUseComplete) $oaPattern20_holder.value) != null))) {
                progress = progress.advance(GuideRequestPhase.RESPONSE_STREAMING, now);
            } else {
final class $oaPattern21_Holder { dev.openallay.model.ModelEvent value; ModelEvent.MessageComplete bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = modelEvent) instanceof dev.openallay.model.ModelEvent.MessageComplete && (($oaPattern21_holder.bound = (ModelEvent.MessageComplete) $oaPattern21_holder.value) != null))) {
                progress = progress.advance(
                        GuideRequestPhase.COMPLETING,
                        now,
                        progress.attempt(),
                        null,
                        null);
            } else {
final class $oaPattern22_Holder { dev.openallay.model.ModelEvent value; ModelFailure bound; }
final $oaPattern22_Holder $oaPattern22_holder = new $oaPattern22_Holder();
if ((($oaPattern22_holder.value = modelEvent) instanceof dev.openallay.model.ModelFailure && (($oaPattern22_holder.bound = (ModelFailure) $oaPattern22_holder.value) != null))) {
                progress = progress.advance(progress.phase(), now);
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
}
}
}
}
}
}
}
}
        } else {
final class $oaPattern23_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.FinalText bound; }
final $oaPattern23_Holder $oaPattern23_holder = new $oaPattern23_Holder();
if ((($oaPattern23_holder.value = event) instanceof dev.openallay.agent.AgentEvent.FinalText && (($oaPattern23_holder.bound = (AgentEvent.FinalText) $oaPattern23_holder.value) != null))) {
            timeline = reconcileFinal(current.requestId(), timeline, $oaPattern23_holder.bound.text());
            clearSemanticStates(current.requestId());
            status = GuideRequestStatus.COMPLETED;
            retryAfter = null;
            progress = progress.advance(
                    GuideRequestPhase.COMPLETING,
                    now,
                    progress.attempt(),
                    null,
                    null);
            terminalAt = progress.lastProgressAt();
        } else {
final class $oaPattern24_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.Failed bound; }
final $oaPattern24_Holder $oaPattern24_holder = new $oaPattern24_Holder();
if ((($oaPattern24_holder.value = event) instanceof dev.openallay.agent.AgentEvent.Failed && (($oaPattern24_holder.bound = (AgentEvent.Failed) $oaPattern24_holder.value) != null))) {
            timeline = closeAssistant(current.requestId(), timeline);
            clearSemanticStates(current.requestId());
            failure = new GuideFailure($oaPattern24_holder.bound.code(), $oaPattern24_holder.bound.message());
            status = $oaPattern24_holder.bound.code().equals("agent_cancelled")
                    ? GuideRequestStatus.CANCELLED
                    : GuideRequestStatus.FAILED;
            retryAfter = null;
            progress = progress.advance(
                    GuideRequestPhase.COMPLETING,
                    now,
                    progress.attempt(),
                    null,
                    null);
            terminalAt = progress.lastProgressAt();
        } else {
            throw new IncompatibleClassChangeError();
        }
}
}
}
}
}
}
}
}
}
}
}
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
                progress.lastProgressAt(),
                terminalAt,
                current.modelSelection(),
                progress);
    }

    private List<GuideTimelineEntry> appendText(
            UUID requestId, List<GuideTimelineEntry> timeline, String delta) {
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        int ordinal;
        String text;
        final class $oaPattern25_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern25_Holder $oaPattern25_holder = new $oaPattern25_Holder();
if (!next.isEmpty()
                && (($oaPattern25_holder.value = next.get(next.size() - 1)) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern25_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern25_holder.value) != null))
                && $oaPattern25_holder.bound.streaming()) {
            ordinal = $oaPattern25_holder.bound.ordinal();
            text = $oaPattern25_holder.bound.text() + delta;
            SemanticStreamingState state = semanticStates.computeIfAbsent(
                    new SegmentKey(requestId, ordinal),
                    ignored -> SemanticStreamingState.empty().update(
                            $oaPattern25_holder.bound.text(), false, semanticParser,
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
                    $oaPattern25_holder.bound.sources()));
        } else {
            ordinal = next.size();
            text = delta;
            SemanticStreamingState state = SemanticStreamingState.empty().update(
                    text, false, semanticParser,
                    SemanticReferenceIndex.from(requestId, timeline));
            semanticStates.put(new SegmentKey(requestId, ordinal), state);
            next.add(new GuideTimelineEntry.Assistant(
                    ordinal, text, state.document(), true, dev.openallay.util.Java8Collections.listOf()));
        }
        return dev.openallay.util.Java8Collections.listCopyOf(next);
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
                started.arguments(),
                null,
                started.presentationMessages(),
                dev.openallay.util.Java8Collections.listOf())));
        return dev.openallay.util.Java8Collections.listCopyOf(next);
    }

    private static List<GuideToolMessage> mergePresentationMessages(
            List<GuideToolMessage> invocation, List<GuideToolMessage> result) {
        ArrayList<GuideToolMessage> messages = new ArrayList<>(invocation.size() + result.size());
        for (GuideToolMessage message : invocation) {
            if (!messages.contains(message)) {
                messages.add(message);
            }
        }
        for (GuideToolMessage message : result) {
            if (!messages.contains(message)) {
                messages.add(message);
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(messages);
    }

    private List<GuideTimelineEntry> closeAssistant(
            UUID requestId,
            List<GuideTimelineEntry> timeline) {
        final class $oaPattern26_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern26_Holder $oaPattern26_holder = new $oaPattern26_Holder();
if (timeline.isEmpty()
                || !((($oaPattern26_holder.value = timeline.get(timeline.size() - 1)) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern26_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern26_holder.value) != null)))
                || !$oaPattern26_holder.bound.streaming()) {
            return timeline;
        }
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        SegmentKey key = new SegmentKey(requestId, $oaPattern26_holder.bound.ordinal());
        SemanticStreamingState state = semanticStates.getOrDefault(
                key, SemanticStreamingState.empty());
        state = state.update(
                $oaPattern26_holder.bound.text(), true, semanticParser,
                SemanticReferenceIndex.from(requestId, timeline));
        semanticStates.remove(key);
        next.set(next.size() - 1, new GuideTimelineEntry.Assistant(
                $oaPattern26_holder.bound.ordinal(), $oaPattern26_holder.bound.text(), state.document(), false,
                $oaPattern26_holder.bound.sources()));
        return dev.openallay.util.Java8Collections.listCopyOf(next);
    }

    private List<GuideTimelineEntry> reconcileFinal(
            UUID requestId, List<GuideTimelineEntry> timeline, String text) {
        ArrayList<GuideTimelineEntry> next = new ArrayList<>(timeline);
        int ordinal = next.size();
        List<GuideSource> sources = dev.openallay.util.Java8Collections.listOf();
        final class $oaPattern27_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern27_Holder $oaPattern27_holder = new $oaPattern27_Holder();
if (!next.isEmpty()
                && (($oaPattern27_holder.value = next.get(next.size() - 1)) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern27_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern27_holder.value) != null))) {
            ordinal = $oaPattern27_holder.bound.ordinal();
            sources = $oaPattern27_holder.bound.sources();
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
        return dev.openallay.util.Java8Collections.listCopyOf(next);
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
            final class $oaPattern28_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Tool bound; }
final $oaPattern28_Holder $oaPattern28_holder = new $oaPattern28_Holder();
if ((($oaPattern28_holder.value = timeline.get(index)) instanceof dev.openallay.guide.GuideTimelineEntry.Tool && (($oaPattern28_holder.bound = (GuideTimelineEntry.Tool) $oaPattern28_holder.value) != null))
                    && $oaPattern28_holder.bound.activity().invocationId().equals(invocationId)
                    && $oaPattern28_holder.bound.activity().status() == GuideToolStatus.RUNNING) {
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
        GuideRequestProgress failedProgress = current.progress().advance(
                GuideRequestPhase.COMPLETING,
                now,
                current.progress().attempt(),
                null,
                null);
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
                failedProgress.lastProgressAt(),
                failedProgress.lastProgressAt(),
                current.modelSelection(),
                failedProgress);
    }

    private static GuideRequestStatus state(AgentState state) {
        {
dev.openallay.guide.GuideRequestStatus $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((state)) {
case IDLE:
case PREPARING:
{
$oaSwitch1_exit_result = GuideRequestStatus.PREPARING; break $oaSwitch1_exit;
}
case COMPACTING:
{
$oaSwitch1_exit_result = GuideRequestStatus.COMPACTING; break $oaSwitch1_exit;
}
case MODEL_WAIT:
{
$oaSwitch1_exit_result = GuideRequestStatus.MODEL_WAIT; break $oaSwitch1_exit;
}
case TOOL_WAIT:
{
$oaSwitch1_exit_result = GuideRequestStatus.TOOL_WAIT; break $oaSwitch1_exit;
}
case COMPLETED:
{
$oaSwitch1_exit_result = GuideRequestStatus.COMPLETING; break $oaSwitch1_exit;
}
case FAILED:
{
$oaSwitch1_exit_result = GuideRequestStatus.FAILED; break $oaSwitch1_exit;
}
case CANCELLED:
{
$oaSwitch1_exit_result = GuideRequestStatus.CANCELLED; break $oaSwitch1_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch1_exit_result;
}
    }

    private static GuideRequestPhase phase(AgentState state) {
        {
dev.openallay.guide.GuideRequestPhase $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((state)) {
case IDLE:
case PREPARING:
{
$oaSwitch0_exit_result = GuideRequestPhase.PREPARING; break $oaSwitch0_exit;
}
case COMPACTING:
{
$oaSwitch0_exit_result = GuideRequestPhase.COMPACTING; break $oaSwitch0_exit;
}
case MODEL_WAIT:
{
$oaSwitch0_exit_result = GuideRequestPhase.MODEL_WAIT; break $oaSwitch0_exit;
}
case TOOL_WAIT:
{
$oaSwitch0_exit_result = GuideRequestPhase.TOOL_WAIT; break $oaSwitch0_exit;
}
case COMPLETED:
case FAILED:
case CANCELLED:
{
$oaSwitch0_exit_result = GuideRequestPhase.COMPLETING; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }

    private List<GuideSource> sources(String toolId, JsonObject normalized) {
        if (!normalized.has("value") || !normalized.get("value").isJsonObject()) {
            return dev.openallay.util.Java8Collections.listOf();
        }
        boolean javascript = "openallay:run_javascript".equals(decodedModelToolId(toolId));
        JsonElement values = normalized.getAsJsonObject("value")
                .get(javascript ? "sources" : "evidence");
        if (values == null || !values.isJsonArray()) {
            return dev.openallay.util.Java8Collections.listOf();
        }
        SourceObservationCollector observations = new SourceObservationCollector();
        for (JsonElement item : values.getAsJsonArray()) {
            if (javascript) {
                observations.add(gson.fromJson(item, SourceObservation.class));
            } else {
                observations.add(gson.fromJson(item, EvidenceMetadata.class));
            }
        }
        return dev.openallay.util.Java8Collections.toList(observations.snapshot().stream()
                .map(source -> new GuideSource(
                        toolId, source.evidence(), source.lastCapturedAt())));
    }

    private static List<GuideSource> mergeSources(
            List<GuideSource> existing, List<GuideSource> additions) {
        Map<String, SourceObservationCollector> byTool = new LinkedHashMap<>();
        for (List<GuideSource> sources : dev.openallay.util.Java8Collections.listOf(existing, additions)) {
            for (GuideSource source : sources) {
                byTool.computeIfAbsent(source.toolId(), ignored -> new SourceObservationCollector())
                        .add(new SourceObservation(
                                source.evidence(), source.lastCapturedAt()));
            }
        }
        ArrayList<GuideSource> merged = new ArrayList<>();
        byTool.forEach((toolId, observations) -> observations.snapshot().forEach(source ->
                merged.add(new GuideSource(
                        toolId, source.evidence(), source.lastCapturedAt()))));
        merged.sort(Comparator
                .comparing((GuideSource value) -> value.evidence().sourceId())
                .thenComparing(value -> value.evidence().provenance())
                .thenComparing(GuideSource::toolId));
        return dev.openallay.util.Java8Collections.listCopyOf(merged);
    }

    private static String decodedModelToolId(String value) {
        String local = value.startsWith("server__")
                ? value.substring("server__".length())
                : value;
        return local.replace("_slash_", "/")
                .replace("_dot_", ".")
                .replace("__", ":");
    }

    @dev.openallay.value.ValueType(SegmentKey.ValueSchemaProvider.class)
private static final class SegmentKey {
    private final UUID requestId;
    private final int ordinal;
    private SegmentKey(UUID requestId, int ordinal) {
        this.requestId = requestId;
        this.ordinal = ordinal;
    }
    public UUID requestId() { return requestId; }
    public int ordinal() { return ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SegmentKey)) return false;
        SegmentKey that = (SegmentKey) other;
        return java.util.Objects.equals(requestId, that.requestId) && ordinal == that.ordinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        return hash;
    }
    @Override public String toString() { return "SegmentKey[requestId=" + requestId + ", ordinal=" + ordinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SegmentKey> schema() {
            return new dev.openallay.value.ValueSchema<>(SegmentKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SegmentKey>>asList(new dev.openallay.value.ValueSchema.Component<>(SegmentKey.class, "requestId", SegmentKey::requestId), new dev.openallay.value.ValueSchema.Component<>(SegmentKey.class, "ordinal", SegmentKey::ordinal)), arguments -> new SegmentKey((UUID) arguments[0], (Integer) arguments[1]));
        }
    }
}
}
