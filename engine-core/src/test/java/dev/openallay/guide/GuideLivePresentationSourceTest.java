package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

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
    private final GuideStateReducer reducer = new GuideStateReducer(dev.openallay.json.EngineJson.create());
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

    @Test void admittedBeforeCannotEmitAnotherRequestAfterSnapshot() {
        source.subscribe(observed::add);
        GuideRequestSnapshot before = start(); source.admit(owner, before);
        AgentEvent.FinalText event = new AgentEvent.FinalText("Other request");
        GuideRequestSnapshot after = reducer.apply(start(), event, now);
        source.applied(owner, before, after, event);
        assertTrue(observed.isEmpty());
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
        AgentEvent complete = new AgentEvent.ToolCompleted("actual-item", "run_javascript", false, dev.openallay.json.JsonTrees.parse("""
                {"status":"success","value":{"viewKind":"ITEM","preview":[{"itemId":"minecraft:stone","count":2}]}}
                """).getAsJsonObject());
        GuideRequestSnapshot done = reducer.apply(running, complete, now);
        source.applied(owner, running, done, complete);
        assertEquals(1, observed.size());
        assertEquals(GuidePresentationEvent.Kind.CARD_BATCH, observed.getFirst().kind());
        assertEquals(List.of(new GuidePresentationEvent.ContentRef(0, "tool:actual-item:card:0")),
                observed.getFirst().content());
        assertEquals(List.of(new GuidePresentationEvent.CardPreview(observed.getFirst().content().getFirst(),
                "minecraft:stone", "minecraft:stone × 2")), observed.getFirst().cardPreviews());
        source.applied(owner, running, done, complete);
        assertEquals(1, observed.size(), "same identity must not produce a second card batch");
    }

    @Test void invalidDebugJsonAndFailedToolsNeverBecomeCards() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        AgentEvent started = new AgentEvent.ToolStarted("empty", "load_skill");
        GuideRequestSnapshot running = reducer.apply(initial, started, now);
        AgentEvent complete = new AgentEvent.ToolCompleted("empty", "load_skill", false, dev.openallay.json.JsonTrees.parse(
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
        assertEquals(List.of(new GuidePresentationEvent.CardPreview(observed.getFirst().content().getFirst(),
                "Stone", "Two stone")), observed.getFirst().cardPreviews());
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

    @Test void canonicalIntentNotResultFieldsOrProgramProvidesCardTitleAndDescription() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        var arguments = dev.openallay.json.JsonTrees.parse("""
                {"title":"Stone supply","description":"Compare available stone for the build.",
                 "source":"PRIVATE_PROGRAM_DO_NOT_DISPLAY"}
                """).getAsJsonObject();
        AgentEvent.ToolStarted started = new AgentEvent.ToolStarted("supply", "openallay:run_javascript", arguments, List.of());
        GuideRequestSnapshot running = reducer.apply(initial, started, now);
        AgentEvent.ToolCompleted complete = new AgentEvent.ToolCompleted("supply", "openallay:run_javascript", false,
                dev.openallay.json.JsonTrees.parse("""
                    {"status":"success","value":{"viewKind":"TABLE","title":"Wrong result title",
                     "description":"Wrong result description","handle":"PRIVATE_HANDLE",
                     "preview":[{"privateColumn":"PRIVATE_ROW_DO_NOT_DISPLAY"}],"complete":true}}
                    """).getAsJsonObject());
        GuideRequestSnapshot after = reducer.apply(running, complete, now);
        source.applied(owner, running, after, complete);
        var event = observed.getFirst();
        assertEquals(List.of(new GuidePresentationEvent.CardPreview(event.content().getFirst(),
                "Stone supply", "Compare available stone for the build.")), event.cardPreviews());
        assertEquals(arguments, ((GuideTimelineEntry.Tool) after.timeline().getFirst()).activity().invocationArguments());
        assertFalse(event.cardPreviews().toString().contains("PRIVATE"));
    }

    @Test void transportedInvocationMessagesProvideTheSameCanonicalMetadataWithoutArguments() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        AgentEvent.ToolStarted started = new AgentEvent.ToolStarted("remote", "openallay:run_javascript", List.of(
                GuideToolMessage.of(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT,
                        "Remote stone list", "Read available building blocks.")));
        GuideRequestSnapshot running = reducer.apply(initial, started, now);
        AgentEvent.ToolCompleted complete = new AgentEvent.ToolCompleted("remote", "openallay:run_javascript", false,
                dev.openallay.json.JsonTrees.parse("""
                    {"status":"success","value":{"viewKind":"ITEM",
                     "preview":[{"itemId":"minecraft:stone","displayName":"Stone","count":2}]}}
                    """).getAsJsonObject());
        source.applied(owner, running, reducer.apply(running, complete, now), complete);
        assertEquals("Remote stone list", observed.getFirst().cardPreviews().getFirst().title());
        assertEquals("Read available building blocks.", observed.getFirst().cardPreviews().getFirst().description());
    }

    @Test void absentIntentNeverPromotesDataRowsOrTextBodyToNotificationMetadata() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        AgentEvent.ToolStarted started = new AgentEvent.ToolStarted("table", "run_javascript");
        GuideRequestSnapshot running = reducer.apply(initial, started, now);
        AgentEvent.ToolCompleted complete = new AgentEvent.ToolCompleted("table", "run_javascript", false,
                dev.openallay.json.JsonTrees.parse("""
                    {"status":"success","value":{"viewKind":"TABLE","preview":[{"body":"Not a description"}]}}
                    """).getAsJsonObject());
        source.applied(owner, running, reducer.apply(running, complete, now), complete);
        assertEquals(1, observed.getFirst().content().size(), "admitted content identity is retained");
        assertTrue(observed.getFirst().cardPreviews().isEmpty(), "no manufactured title or raw-cell description");
    }

    @Test void typedSemanticLabelsAndFallbackRemainExactThroughNestedQuoteAndList() {
        source.subscribe(observed::add);
        GuideRequestSnapshot initial = start(); source.admit(owner, initial);
        String first = "c".repeat(64), second = "d".repeat(64), quote = "e".repeat(64), list = "f".repeat(64);
        var choice = new SemanticBlock.Component(first, new RichComponent.ChoiceGroup(first, "Choose the roof",
                List.of(new RichComponent.Choice("oak", "Oak")), "Oak roof available", "Oak roof available"));
        var items = new SemanticBlock.Component(second, new RichComponent.ItemRow(second, List.of(
                new RichComponent.Item("minecraft:stone", 2, "Stone", "supply")), "Two stone", "Two stone"));
        var nestedList = new SemanticBlock.ListBlock(list, false, 1, List.of(List.of(choice, items)));
        SemanticDocument document = SemanticDocument.of(List.of(new SemanticBlock.Quote(quote, List.of(nestedList))), List.of());
        GuideRequestSnapshot stable = timeline(initial, new GuideTimelineEntry.Assistant(0, document.fallbackText(), document, false, List.of()));
        source.applied(owner, initial, stable, new AgentEvent.ToolStarted("next", "load_skill"));
        assertEquals(List.of("Choose the roof", "Stone"), observed.getFirst().cardPreviews().stream()
                .map(GuidePresentationEvent.CardPreview::title).toList());
        assertEquals(observed.getFirst().content(), observed.getFirst().cardPreviews().stream()
                .map(GuidePresentationEvent.CardPreview::source).toList());
    }

    @Test void metadataIsImmutableExactRefValidatedAndUnicodeBoundedWithoutChangingIntent() {
        var ref = new GuidePresentationEvent.ContentRef(2, "tool:preview:card:0");
        var key = new GuidePresentationEvent.Key(source.generation(), actor, owner, "main", UUID.randomUUID(), 1);
        String title = "🌲".repeat(200), description = "🧱".repeat(600);
        var preview = new GuidePresentationEvent.CardPreview(ref, title, description);
        assertEquals(161, preview.title().codePointCount(0, preview.title().length()));
        assertEquals(513, preview.description().codePointCount(0, preview.description().length()));
        List<GuidePresentationEvent.CardPreview> mutable = new ArrayList<>(List.of(preview));
        var event = new GuidePresentationEvent(key, GuidePresentationEvent.Kind.CARD_BATCH, List.of(ref), "", mutable, now);
        mutable.clear(); assertEquals(1, event.cardPreviews().size());
        assertThrows(UnsupportedOperationException.class, () -> event.cardPreviews().clear());
        assertThrows(IllegalArgumentException.class, () -> new GuidePresentationEvent.CardPreview(ref, "", "body"));
        assertThrows(IllegalArgumentException.class, () -> new GuidePresentationEvent(key,
                GuidePresentationEvent.Kind.CARD_BATCH, List.of(new GuidePresentationEvent.ContentRef(3, ref.contentId())), "", List.of(preview), now));
        assertThrows(IllegalArgumentException.class, () -> new GuidePresentationEvent(key,
                GuidePresentationEvent.Kind.CARD_BATCH, List.of(ref), "", List.of(preview, preview), now));
        assertThrows(IllegalArgumentException.class, () -> new GuidePresentationEvent(key,
                GuidePresentationEvent.Kind.REPLY_FINAL, List.of(ref), "reply", List.of(preview), now));
        assertEquals(200, title.codePointCount(0, title.length()));
        assertEquals(600, description.codePointCount(0, description.length()));
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
