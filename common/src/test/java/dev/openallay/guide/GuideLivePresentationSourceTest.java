package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentEvent;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.model.ModelEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideLivePresentationSourceTest {
    private final UUID actor = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final GuideStateReducer reducer = new GuideStateReducer(new Gson());
    private final GuideLivePresentationSource source = new GuideLivePresentationSource(actor);
    private final List<GuidePresentationEvent> observed = new ArrayList<>();

    @Test void onlyAdmittedFinalBeforeAfterEmitsReplyAndCompletionExactlyOnce() {
        source.subscribe(observed::add);
        GuideRequestSnapshot before = start();
        AgentEvent.FinalText finalText = new AgentEvent.FinalText("Full **player** result");
        GuideRequestSnapshot after = reducer.apply(before, finalText, now);
        source.applied(owner, before, after, finalText);
        assertTrue(observed.isEmpty(), "unknown loaded/late requests are not live admissions");
        source.admit(owner, before);
        source.applied(UUID.randomUUID(), before, after, finalText);
        assertTrue(observed.isEmpty(), "same session name cannot replace the captured owner");
        source.applied(owner, before, after, finalText);
        assertEquals(List.of(GuidePresentationEvent.Kind.REPLY_FINAL,
                GuidePresentationEvent.Kind.TASK_COMPLETED), observed.stream().map(GuidePresentationEvent::kind).toList());
        assertEquals("Full player result", observed.getFirst().preview());
        assertEquals(source.generation(), observed.getFirst().key().connectionGeneration());
        assertEquals(actor, observed.getFirst().key().actorId());
        assertEquals(owner, observed.getFirst().key().sessionOwner());
        assertEquals(1, observed.getFirst().key().sequence());
        assertEquals(2, observed.getLast().key().sequence());
        source.applied(owner, before, after, finalText);
        source.applied(owner, after, after, finalText);
        assertEquals(2, observed.size());
        assertEquals("Full **player** result", after.assistantText(), "receipt must not mutate source reply");
    }

    @Test void streamingUsageAndToolProgressAreNotReplyOrCardEvents() {
        source.subscribe(observed::add);
        GuideRequestSnapshot before = start(); source.admit(owner, before);
        AgentEvent delta = new AgentEvent.ModelProgress(new ModelEvent.TextDelta("partial"));
        GuideRequestSnapshot streaming = reducer.apply(before, delta, now);
        source.applied(owner, before, streaming, delta);
        AgentEvent started = new AgentEvent.ToolStarted("lookup", "load_skill");
        GuideRequestSnapshot closed = reducer.apply(streaming, started, now);
        source.applied(owner, streaming, closed, started);
        assertTrue(observed.isEmpty(), "closed intermediate assistant text is not a final answer");
        GuideRequestSnapshot cancelled = reducer.apply(closed,
                new AgentEvent.Failed("agent_cancelled", "User stopped task"), now);
        source.applied(owner, closed, cancelled, new AgentEvent.Failed("agent_cancelled", "User stopped task"));
        source.applied(owner, cancelled, reducer.apply(cancelled, new AgentEvent.FinalText("late"), now),
                new AgentEvent.FinalText("late"));
        assertTrue(observed.isEmpty(), "cancel and late final text are non-notifying");
    }

    @Test void successfulActualNativeProjectionGetsStableToolCardIdentity() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        AgentEvent started = new AgentEvent.ToolStarted("actual-item", "run_javascript");
        GuideRequestSnapshot running = reducer.apply(initial, started, now);
        source.applied(owner, initial, running, started);
        AgentEvent complete = new AgentEvent.ToolCompleted("actual-item", "run_javascript", false, JsonParser.parseString("""
                {"status":"success","value":{"viewKind":"ITEM","preview":[{"itemId":"minecraft:stone","count":2}]}}
                """).getAsJsonObject());
        GuideRequestSnapshot done = reducer.apply(running, complete, now);
        source.applied(owner, running, done, complete);
        assertEquals(1, observed.size());
        assertEquals(GuidePresentationEvent.Kind.CARD_BATCH, observed.getFirst().kind());
        assertEquals(List.of(new GuidePresentationEvent.ContentRef(0, "tool:actual-item:card:0")),
                observed.getFirst().content());
        source.applied(owner, running, done, complete);
        assertEquals(1, observed.size(), "same identity must not produce a second card batch");
    }

    @Test void invalidDebugJsonAndFailedToolsNeverBecomeCards() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        AgentEvent started = new AgentEvent.ToolStarted("empty", "load_skill");
        GuideRequestSnapshot running = reducer.apply(initial, started, now);
        AgentEvent complete = new AgentEvent.ToolCompleted("empty", "load_skill", false, JsonParser.parseString(
                "{\"status\":\"success\",\"value\":{\"debug\":\"opaque\"}}") .getAsJsonObject());
        source.applied(owner, running, reducer.apply(running, complete, now), complete);
        assertTrue(observed.isEmpty());
    }

    @Test void onlyStableValidatedSemanticCardsNotPartialProgressOrStatusNotify() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        String itemId = "a".repeat(64), statusId = "b".repeat(64);
        SemanticDocument document = SemanticDocument.of(List.of(
                new SemanticBlock.Component(itemId, new RichComponent.ItemRow(itemId, List.of(
                        new RichComponent.Item("minecraft:stone", 2, "Stone", "actual-item")), "Two stone", "Two stone")),
                new SemanticBlock.Component(statusId, new RichComponent.StatusBadge(statusId,
                        RichComponent.BadgeState.SUCCESS, "Done", "Done", "Done"))), List.of());
        GuideRequestSnapshot partial = timeline(initial, new GuideTimelineEntry.Assistant(0, "Two stone", document, true, List.of()));
        AgentEvent started = new AgentEvent.ToolStarted("next", "load_skill");
        source.applied(owner, initial, partial, started);
        assertTrue(observed.isEmpty());
        GuideRequestSnapshot stable = timeline(initial, new GuideTimelineEntry.Assistant(0, "Two stone", document, false, List.of()));
        source.applied(owner, partial, stable, started);
        assertEquals(List.of(new GuidePresentationEvent.ContentRef(0, "node:" + itemId)), observed.getFirst().content());
        source.applied(owner, partial, stable, started);
        assertEquals(1, observed.size());
    }

    @Test void realFailureAndProtocolFailureNotifyButInvalidatedGenerationDoesNot() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        AgentEvent invalid = new AgentEvent.ToolCompleted("unknown", "run_javascript", false, new com.google.gson.JsonObject());
        source.applied(owner, initial, reducer.apply(initial, invalid, now), invalid);
        assertEquals(GuidePresentationEvent.Kind.TASK_FAILED, observed.getFirst().kind());
        GuideRequestSnapshot next = start(); source.admit(owner, next);
        source.invalidate();
        source.applied(owner, next, reducer.apply(next, new AgentEvent.Failed("failure", "late"), now),
                new AgentEvent.Failed("failure", "late"));
        assertEquals(1, observed.size());
    }

    @Test void intermediateStableCardsDoNotBecomeFinalReplyAndClosedSessionDropsAdmission() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        source.discardSession(owner);
        AgentEvent.FinalText finalText = new AgentEvent.FinalText("closed result");
        source.applied(owner, initial, reducer.apply(initial, finalText, now), finalText);
        assertTrue(observed.isEmpty());
    }

    @Test void restoredCompletedRowsNeverReplayOnSubscribe() {
        GuideRequestSnapshot loaded = reducer.apply(start(), new AgentEvent.FinalText("Restored answer"), now);
        source.subscribe(observed::add);
        source.applied(owner, loaded, loaded, new AgentEvent.FinalText("Restored answer"));
        assertTrue(observed.isEmpty());
    }

    private GuideRequestSnapshot start() {
        return GuideRequestSnapshot.start(UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "question", now);
    }
    private GuideRequestSnapshot timeline(GuideRequestSnapshot request, GuideTimelineEntry.Assistant assistant) {
        return new GuideRequestSnapshot(request.requestId(), request.sessionId(), request.topology(),
                request.userMessage(), List.of(assistant), request.status(), request.sources(), request.usage(),
                null, null, request.createdAt(), now, null, request.modelSelection(), request.progress());
    }
}
