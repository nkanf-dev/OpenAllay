package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentState;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
import dev.openallay.guide.semantic.SemanticInline;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelUsage;
import dev.openallay.testing.GroundedTestFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideStateReducerTest {
    private final GuideStateReducer reducer = new GuideStateReducer(dev.openallay.json.EngineJson.create());

    @Test
    void rejectsMalformedProgressClocksAndAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new GuideRequestProgress(
                GuideRequestPhase.MODEL_WAIT,
                at(2),
                at(1),
                at(3),
                1,
                null,
                null));
        assertThrows(IllegalArgumentException.class, () -> new GuideRequestProgress(
                GuideRequestPhase.ENDPOINT_WAIT,
                Instant.EPOCH,
                at(1),
                at(2),
                -1,
                at(3),
                null));
        assertThrows(IllegalArgumentException.class, () -> new GuideRequestProgress(
                GuideRequestPhase.ENDPOINT_WAIT,
                Instant.EPOCH,
                at(1),
                at(2),
                1,
                at(1),
                null));
    }

    @Test
    void exposesCompactingStateWithoutAddingPlayerContent() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);

        request = reducer.apply(
                request, new AgentEvent.StateChanged(AgentState.COMPACTING), at(1));

        assertEquals(GuideRequestStatus.COMPACTING, request.status());
        assertEquals(List.of(), request.timeline());
        assertEquals(GuideRequestPhase.COMPACTING, request.progress().phase());
        assertEquals(at(1), request.progress().phaseStartedAt());
        assertEquals(at(1), request.progress().lastProgressAt());
    }

    @Test
    void tracksRedactedModelLifecycleAndMonotonicProgress() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);
        assertEquals(GuideRequestPhase.PREPARING, request.progress().phase());
        assertEquals(Instant.EPOCH, request.progress().requestStartedAt());
        assertEquals(Instant.EPOCH, request.progress().phaseStartedAt());
        assertEquals(Instant.EPOCH, request.progress().lastProgressAt());

        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.AttemptStarted(1, 10_000L)), at(1));
        assertEquals(GuideRequestPhase.MODEL_WAIT, request.progress().phase());
        assertEquals(1, request.progress().attempt());
        assertEquals(at(1).plusMillis(10_000), request.progress().deadlineAt());

        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.ResponseStarted()), at(2));
        assertEquals(GuideRequestPhase.RESPONSE_STREAMING, request.progress().phase());
        assertEquals(at(2), request.progress().phaseStartedAt());

        request = reducer.apply(request, text("one"), at(4));
        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.ReasoningDelta("redacted")), at(3));
        assertEquals(at(4), request.progress().lastProgressAt());
        assertEquals("one", request.assistantText());

        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-1", "openallay:get_recipe"), at(5));
        assertEquals(GuideRequestPhase.TOOL_WAIT, request.progress().phase());
        request = reducer.apply(request, new AgentEvent.FinalText("done"), at(6));
        assertEquals(GuideRequestPhase.COMPLETING, request.progress().phase());
        assertEquals(at(6), request.progress().lastProgressAt());
        assertTrue(request.terminal());
    }

    @Test
    void rateLimitPublishesRetryTimeAndClearsTheAttemptDeadline() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);
        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.AttemptStarted(2, 10_000L)), at(1));

        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.RateLimited(1500, 2)), at(2));

        assertEquals(GuideRequestPhase.ENDPOINT_WAIT, request.progress().phase());
        assertEquals(2, request.progress().attempt());
        assertEquals(at(2).plusMillis(1500), request.progress().retryAt());
        assertEquals(null, request.progress().deadlineAt());
    }

    @Test
    void snapshotDerivesFinalTextAndToolsFromChronologicalTimeline() {
        UUID requestId = UUID.randomUUID();
        GuideToolActivity tool = new GuideToolActivity(
                "call-1",
                0,
                "openallay:get_recipe",
                GuideToolStatus.SUCCEEDED,
                groundedResult(),
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                List.of());
        GuideRequestSnapshot request = new GuideRequestSnapshot(
                requestId,
                "main",
                GuideTopology.CLIENT_LOCAL,
                "How?",
                List.of(
                        new GuideTimelineEntry.Assistant(0, "I will check.", false, List.of()),
                        new GuideTimelineEntry.Tool(1, tool),
                        new GuideTimelineEntry.Assistant(
                                2, "You need nine ingots.", false, List.of())),
                GuideRequestStatus.COMPLETED,
                List.of(),
                ModelUsage.empty(),
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(3),
                Instant.EPOCH.plusSeconds(3));

        assertEquals(List.of(0, 1, 2), request.timeline().stream()
                .map(GuideTimelineEntry::ordinal)
                .toList());
        assertEquals("You need nine ingots.", request.assistantText());
        assertEquals(List.of(tool), request.tools());
    }

    @Test
    void reducesAssistantToolsAndContinuationsInChronologicalOrder() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(),
                "main",
                GuideTopology.CLIENT_LOCAL,
                "How?",
                Instant.EPOCH);

        request = reducer.apply(request, new AgentEvent.StateChanged(AgentState.MODEL_WAIT), at(1));
        request = reducer.apply(request, text("I will inspect the recipe."), at(2));
        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.ReasoningDelta("secret")), at(3));
        JsonObject invocationArguments = new JsonObject();
        invocationArguments.addProperty("source", "return mc.recipes;");
        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-1", "openallay:get_recipe", invocationArguments, List.of()), at(4));
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-1", "openallay:get_recipe", false, groundedResult()), at(5));
        request = reducer.apply(request, text("Now I will inspect inventory."), at(6));
        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-2", "openallay:inspect_inventory"), at(7));
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-2", "openallay:inspect_inventory", false, groundedResult()), at(8));
        request = reducer.apply(request, text("You are missing five ingots."), at(9));
        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.UsageUpdate(new ModelUsage(10, 3, 2))), at(10));
        request = reducer.apply(request, new AgentEvent.FinalText(
                "You are missing five ingots."), at(11));

        assertEquals(
                List.of(
                        GuideTimelineEntry.Assistant.class,
                        GuideTimelineEntry.Tool.class,
                        GuideTimelineEntry.Assistant.class,
                        GuideTimelineEntry.Tool.class,
                        GuideTimelineEntry.Assistant.class),
                request.timeline().stream().map(Object::getClass).toList());
        assertEquals(List.of(0, 1, 2, 3, 4), request.timeline().stream()
                .map(GuideTimelineEntry::ordinal)
                .toList());
        assertEquals("I will inspect the recipe.",
                ((GuideTimelineEntry.Assistant) request.timeline().get(0)).text());
        assertEquals("Now I will inspect inventory.",
                ((GuideTimelineEntry.Assistant) request.timeline().get(2)).text());
        assertEquals("You are missing five ingots.", request.assistantText());
        assertEquals(GuideRequestStatus.COMPLETED, request.status());
        assertEquals(GuideToolStatus.SUCCEEDED, request.tools().getFirst().status());
        assertEquals(
                "return mc.recipes;",
                request.tools().getFirst().invocationArguments().get("source").getAsString());
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                request.tools().getFirst().presentationMessages());
        assertEquals(List.of("call-1", "call-2"), request.tools().stream()
                .map(GuideToolActivity::invocationId)
                .toList());
        assertEquals("minecraft:recipe_manager", request.sources().getFirst().evidence().sourceId());
        assertEquals(new ModelUsage(10, 3, 2), request.usage());
    }

    @Test
    void repeatedToolNamesRemainDistinctByInvocationId() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);

        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-1", "openallay:get_recipe"), at(1));
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-1", "openallay:get_recipe", false, groundedResult()), at(2));
        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-2", "openallay:get_recipe"), at(3));
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-2", "openallay:get_recipe", false, groundedResult()), at(4));

        assertEquals(List.of("call-1", "call-2"), request.tools().stream()
                .map(GuideToolActivity::invocationId)
                .toList());
    }

    @Test
    void semanticSegmentsAreIndependentAndOnlyLaterTextCanUseToolHandles() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);

        request = reducer.apply(request, text("**Before lookup**"), at(1));
        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-1", "openallay:get_recipe"), at(2));
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-1", "openallay:get_recipe", false, referenceResult()), at(3));
        request = reducer.apply(request, text("Use [[tw:item|minecraft:iron_"), at(4));
        request = reducer.apply(request, text("block|Iron block]]."), at(5));
        request = reducer.apply(request, new AgentEvent.FinalText(
                "Use [[tw:item|minecraft:iron_block|Iron block]]."), at(6));

        GuideTimelineEntry.Assistant before =
                (GuideTimelineEntry.Assistant) request.timeline().get(0);
        GuideTimelineEntry.Assistant after =
                (GuideTimelineEntry.Assistant) request.timeline().get(2);
        assertEquals("Before lookup", before.semantic().fallbackText());
        assertFalse(hasReference(before));
        assertEquals("Use Iron block.", after.semantic().fallbackText());
        assertTrue(hasReference(after));
        assertEquals("call-1", reference(after).originInvocationId());
    }

    @Test
    void finalTextReconcilesOnlyTheLastSemanticSegment() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);
        request = reducer.apply(request, text("# First"), at(1));
        request = reducer.apply(request, new AgentEvent.ToolStarted(
                "call-1", "openallay:get_recipe"), at(2));
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-1", "openallay:get_recipe", false, groundedResult()), at(3));
        request = reducer.apply(request, text("Partial"), at(4));
        GuideTimelineEntry.Assistant firstBefore =
                (GuideTimelineEntry.Assistant) request.timeline().getFirst();

        request = reducer.apply(request, new AgentEvent.FinalText("## Final"), at(5));

        GuideTimelineEntry.Assistant firstAfter =
                (GuideTimelineEntry.Assistant) request.timeline().getFirst();
        GuideTimelineEntry.Assistant last =
                (GuideTimelineEntry.Assistant) request.timeline().getLast();
        assertSame(firstBefore.semantic(), firstAfter.semantic());
        assertEquals("First", firstAfter.semantic().fallbackText());
        assertEquals("Final", last.semantic().fallbackText());
        assertEquals("## Final", last.text());
    }

    @Test
    void missingToolInvocationFailsRequestClosed() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "How?", Instant.EPOCH);

        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "missing", "openallay:get_recipe", false, groundedResult()), at(1));

        assertEquals(GuideRequestStatus.FAILED, request.status());
        assertEquals("timeline_protocol_error", request.failure().code());
        assertEquals(at(1), request.terminalAt());
        assertEquals(List.of(), request.timeline());
    }

    @Test
    void rateLimitIsVisibleAndLateEventsAreSuppressed() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.SERVER, "Question", Instant.EPOCH);
        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.RateLimited(1500, 2)), at(1));
        assertEquals(GuideRequestStatus.RATE_LIMITED, request.status());
        assertEquals(1500, request.retryAfterMillis());

        request = reducer.apply(
                request, new AgentEvent.StateChanged(AgentState.CANCELLED), at(3));
        request = reducer.apply(request, new AgentEvent.Failed("agent_cancelled", "cancelled"), at(3));
        GuideRequestSnapshot terminal = request;
        GuideRequestSnapshot late = reducer.apply(
                terminal, new AgentEvent.FinalText("late"), at(4));

        assertEquals(GuideRequestStatus.CANCELLED, terminal.status());
        assertEquals(null, terminal.progress().retryAt());
        assertSame(terminal, late);
    }

    @Test
    void javascriptIntentAppearsPendingAndSurvivesOutOfOrderResultsAndFinalText() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "Compare", Instant.EPOCH);
        request = reducer.apply(request, text("I will compare."), at(1));
        JsonObject first = javascriptArguments("Compare swords", "Rank attack damage");
        JsonObject second = javascriptArguments("Check recipes", "Find ingredients");
        request = reducer.apply(request, new AgentEvent.ToolStarted("call-1", "openallay:run_javascript",
                first, GuideToolInvocationPresentation.messages("openallay:run_javascript", first)), at(2));
        assertEquals(new GuideToolIntent("Compare swords", "Rank attack damage"), request.tools().getFirst().intent());
        assertEquals(GuideToolStatus.RUNNING, request.tools().getFirst().status());
        assertTrue(request.tools().getFirst().sources().isEmpty());
        first.addProperty("title", "mutated after start");
        request = reducer.apply(request, new AgentEvent.ToolStarted("call-2", "openallay:run_javascript",
                second, GuideToolInvocationPresentation.messages("openallay:run_javascript", second)), at(3));
        JsonObject failure = new JsonObject();
        failure.addProperty("status", "failure");
        failure.addProperty("code", "javascript_error");
        failure.addProperty("title", "Overwrite by failure");
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-2", "openallay:run_javascript", true, failure), at(4));
        JsonObject success = javascriptResult(new SourceObservation(GroundedTestFixtures.serverEvidence()));
        success.getAsJsonObject("value").addProperty("title", "Overwrite by result");
        request = reducer.apply(request, new AgentEvent.ToolCompleted(
                "call-1", "openallay:run_javascript", false, success), at(5));
        request = reducer.apply(request, new AgentEvent.ModelProgress(
                new ModelEvent.ReasoningDelta("hidden reasoning must not become intent")), at(6));
        request = reducer.apply(request, text("The results differ."), at(7));
        request = reducer.apply(request, new AgentEvent.FinalText("Final answer"), at(8));

        assertEquals(List.of(GuideTimelineEntry.Assistant.class, GuideTimelineEntry.Tool.class,
                GuideTimelineEntry.Tool.class, GuideTimelineEntry.Assistant.class),
                request.timeline().stream().map(Object::getClass).toList());
        assertEquals(List.of(0, 1, 2, 3), request.timeline().stream().map(GuideTimelineEntry::ordinal).toList());
        assertEquals(List.of("call-1", "call-2"), request.tools().stream().map(GuideToolActivity::invocationId).toList());
        assertEquals(List.of("Compare swords", "Check recipes"), request.tools().stream()
                .map(activity -> activity.intent().title()).toList());
        assertEquals(List.of(GuideToolStatus.SUCCEEDED, GuideToolStatus.FAILED), request.tools().stream()
                .map(GuideToolActivity::status).toList());
        assertEquals(List.of(new GuideSource("openallay:run_javascript", GroundedTestFixtures.serverEvidence())),
                request.tools().getFirst().sources());
        assertTrue(request.tools().get(1).sources().isEmpty());
        assertFalse(request.timeline().toString().contains("hidden reasoning"));
        assertEquals("Final answer", request.assistantText());
    }

    @Test
    void javascriptSourcesMergeByMetadataAndToolWithoutRetainingEveryTimestamp() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "Inspect", Instant.EPOCH);
        SourceObservation later = new SourceObservation(
                evidenceAt(20, DataCompleteness.COMPLETE, Map.of()), at(40));
        SourceObservation middle = new SourceObservation(
                evidenceAt(10, DataCompleteness.COMPLETE, Map.of()), at(30));
        SourceObservation first = new SourceObservation(
                evidenceAt(2, DataCompleteness.COMPLETE, Map.of()), at(50));
        request = complete(request, "call-1", "openallay:run_javascript",
                javascriptResult(later, middle), 1);
        request = complete(request, "call-2", "openallay:run_javascript",
                javascriptResult(first), 3);

        assertEquals(List.of(new GuideSource(
                "openallay:run_javascript", first.evidence(), at(50))), request.sources());
        assertEquals(List.of(new GuideSource(
                "openallay:run_javascript", middle.evidence(), at(40))),
                request.tools().getFirst().sources());
        assertEquals(List.of(new GuideSource(
                "openallay:run_javascript", first.evidence(), at(50))),
                request.tools().getLast().sources());
    }

    @Test
    void javascriptSourceCoverageDetailsAndOtherToolIdsStaySeparate() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "Inspect", Instant.EPOCH);
        SourceObservation complete = new SourceObservation(
                evidenceAt(0, DataCompleteness.COMPLETE, Map.of()));
        SourceObservation partial = new SourceObservation(
                evidenceAt(1, DataCompleteness.PARTIAL, Map.of()));
        SourceObservation unknown = new SourceObservation(
                evidenceAt(2, DataCompleteness.UNKNOWN, Map.of()));
        SourceObservation covered = new SourceObservation(
                evidenceAt(3, DataCompleteness.COMPLETE, Map.of("test:coverage", "loaded_chunks")));
        request = complete(request, "call-1", "openallay:run_javascript",
                javascriptResult(complete, partial, unknown, covered), 1);
        request = complete(request, "call-2", "openallay:get_recipe", groundedResult(), 3);

        assertEquals(5, request.sources().size());
        assertEquals(4, request.tools().getFirst().sources().size());
    }

    @Test
    void javascriptNeverFallsBackToTheOldEvidenceArray() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "Inspect", Instant.EPOCH);
        request = complete(request, "call-1", "openallay:run_javascript", groundedResult(), 1);
        assertTrue(request.sources().isEmpty());
        assertTrue(request.tools().getFirst().sources().isEmpty());

        request = complete(request, "call-2", "server__openallay__run_javascript", groundedResult(), 3);
        assertTrue(request.sources().isEmpty());
        assertTrue(request.tools().getLast().sources().isEmpty());

        SourceObservation source = new SourceObservation(
                evidenceAt(4, DataCompleteness.PARTIAL, Map.of("test:coverage", "loaded_chunks")), at(6));
        JsonObject normalized = javascriptResult(source);
        normalized.getAsJsonObject("value").add(
                "evidence", groundedResult().getAsJsonObject("value").get("evidence"));
        request = complete(request, "call-3", "server__openallay__run_javascript", normalized, 5);

        assertEquals(List.of(new GuideSource(
                "server__openallay__run_javascript", source.evidence(), at(6))), request.sources());
    }

    @Test
    void genuineEvidenceBearingToolCapturesStillAccumulate() {
        GuideRequestSnapshot request = GuideRequestSnapshot.start(
                UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "Inspect", Instant.EPOCH);
        request = complete(request, "call-1", "openallay:get_recipe", groundedResult(), 1);
        request = complete(request, "call-2", "openallay:get_recipe", groundedResult(), 3);

        assertEquals(List.of(new GuideSource("openallay:get_recipe",
                GroundedTestFixtures.serverEvidence(), Instant.EPOCH)), request.sources());
    }

    private GuideRequestSnapshot complete(
            GuideRequestSnapshot request, String invocationId, String toolId,
            JsonObject normalized, long seconds) {
        request = reducer.apply(request, new AgentEvent.ToolStarted(invocationId, toolId), at(seconds));
        return reducer.apply(request, new AgentEvent.ToolCompleted(
                invocationId, toolId, false, normalized), at(seconds + 1));
    }

    private static EvidenceMetadata evidenceAt(
            long seconds, DataCompleteness completeness, Map<String, String> details) {
        EvidenceMetadata original = GroundedTestFixtures.serverEvidence();
        return new EvidenceMetadata(
                original.authority(), completeness, at(seconds), original.sourceId(),
                original.provenance(), original.gameVersion(), original.loader(), details);
    }

    private static JsonObject javascriptResult(SourceObservation... observations) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        JsonObject value = new JsonObject();
        JsonArray sources = new JsonArray();
        for (SourceObservation observation : observations) {
            sources.add(dev.openallay.json.EngineJson.create().toJsonTree(observation));
        }
        value.add("sources", sources);
        result.add("value", value);
        return result;
    }

    private static JsonObject javascriptArguments(String title, String description) {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return mc.items.length;");
        input.addProperty("title", title);
        input.addProperty("description", description);
        return input;
    }

    private static JsonObject groundedResult() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        JsonObject value = new JsonObject();
        JsonArray evidence = new JsonArray();
        evidence.add(dev.openallay.json.EngineJson.create().toJsonTree(GroundedTestFixtures.serverEvidence()));
        value.add("evidence", evidence);
        result.add("value", value);
        return result;
    }

    private static JsonObject referenceResult() {
        JsonObject result = groundedResult();
        JsonObject value = result.getAsJsonObject("value");
        JsonObject recipe = new JsonObject();
        recipe.addProperty("sourceId", "minecraft:recipe_manager");
        recipe.addProperty("generation", GroundedTestFixtures.RECIPE_GENERATION);
        recipe.addProperty("recipeId", "minecraft:iron_block");
        recipe.addProperty("itemId", "minecraft:iron_block");
        value.add("recipe", recipe);
        return result;
    }

    private static boolean hasReference(GuideTimelineEntry.Assistant assistant) {
        return assistant.semantic().blocks().stream()
                .filter(dev.openallay.guide.semantic.SemanticBlock.Paragraph.class::isInstance)
                .map(dev.openallay.guide.semantic.SemanticBlock.Paragraph.class::cast)
                .flatMap(block -> block.content().stream())
                .anyMatch(SemanticInline.Reference.class::isInstance);
    }

    private static dev.openallay.guide.semantic.SemanticReference reference(
            GuideTimelineEntry.Assistant assistant) {
        return assistant.semantic().blocks().stream()
                .filter(dev.openallay.guide.semantic.SemanticBlock.Paragraph.class::isInstance)
                .map(dev.openallay.guide.semantic.SemanticBlock.Paragraph.class::cast)
                .flatMap(block -> block.content().stream())
                .filter(SemanticInline.Reference.class::isInstance)
                .map(SemanticInline.Reference.class::cast)
                .map(SemanticInline.Reference::reference)
                .findFirst().orElseThrow();
    }

    private static AgentEvent text(String value) {
        return new AgentEvent.ModelProgress(new ModelEvent.TextDelta(value));
    }

    private static Instant at(long seconds) {
        return Instant.EPOCH.plusSeconds(seconds);
    }
}
