package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LoadSkillToolTest {
    @Test
    void returnsOnlyAValidatedNamedSkill() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/skill.md",
                Map.of("guide/skill.md", """
                        ---
                        name: guide
                        description: Guide the player
                        required-mods: []
                        allowed-tools: []
                        references: []
                        ---
                        Follow evidence.
                        """))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository);

        ToolResult.Success<LoadSkillTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
        assertEquals("Follow evidence.", success.value().content());
        assertEquals("SKILL.md", success.value().document());
        assertEquals(tool.catalogManifest().documents().getFirst().source(), success.value().source());
        assertEquals(true, new SkillInstructionContext(tool.catalogManifest())
                .validate(new LoadSkillTool.Input("guide"), success.value()));
        assertEquals(true, success.value().complete());
        assertEquals(true, success.value().modelText().startsWith("skill_instructions\n"));
        assertEquals(false, success.value().modelText().contains("skill_context: 1"));
        assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("missing")));
    }

    @Test
    void cannotLoadSkillExcludedFromCapturedCatalog() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/skill.md",
                Map.of("guide/skill.md", """
                        ---
                        name: guide
                        description: Guide the player
                        required-mods: []
                        allowed-tools: []
                        references: []
                        ---
                        Follow evidence.
                        """))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository.snapshot(Set.of("guide")));

        ToolResult.Failure<LoadSkillTool.Output> failure = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));

        assertEquals("skill_not_found", failure.code());
        assertInstanceOf(
                ToolResult.Success.class,
                new LoadSkillTool(repository).invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
    }

    @Test
    void progressivelyLoadsOneDeclaredReferenceInsteadOfInjectingAllReferences() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/SKILL.md",
                Map.of(
                        "guide/SKILL.md", """
                                ---
                                name: guide
                                description: Guide the player
                                ---
                                Read the matching reference.
                                """,
                        "guide/references/a.md", "A contents",
                        "guide/references/b.md", "B contents"))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository);

        ToolResult.Success<LoadSkillTool.Output> entrySuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output entry = entrySuccess.value();
        assertEquals("Read the matching reference.", entry.content());
        assertEquals(java.util.List.of("references/a.md", "references/b.md"),
                entry.availableReferences());

        ToolResult.Success<LoadSkillTool.Output> referenceSuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", "references/b.md")));
        LoadSkillTool.Output reference = referenceSuccess.value();
        assertEquals("references/b.md", reference.document());
        assertEquals("B contents", reference.content());

        ToolResult.Failure<LoadSkillTool.Output> missing = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", "references/missing.md")));
        assertEquals("skill_reference_not_found", missing.code());
    }

    @Test
    void readsLargeDocumentsWithSnapshotBoundOpaqueCursors() {
        String contents = "paragraph\n\n".repeat(1_500);
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/SKILL.md",
                Map.of("guide/SKILL.md", """
                        ---
                        name: guide
                        description: Guide the player
                        ---
                        %s
                        """.formatted(contents)))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository);

        ToolResult.Success<LoadSkillTool.Output> firstSuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output first = firstSuccess.value();
        assertEquals(false, first.complete());
        String cursorPayload = new String(java.util.Base64.getUrlDecoder().decode(first.nextCursor()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("guide\u0000SKILL.md\u0000" + first.source() + "\u0000"
                + first.fingerprint() + "\u0000" + first.nextOffset(), cursorPayload);

        ToolResult.Success<LoadSkillTool.Output> secondSuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", null, first.nextCursor())));
        LoadSkillTool.Output second = secondSuccess.value();
        assertEquals(first.nextOffset(), second.offset());
        assertEquals(contents.strip(), (first.content() + second.content()
                + readRemaining(tool, second)).strip());

        ToolResult.Failure<LoadSkillTool.Output> wrongDocument = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", "references/a.md", first.nextCursor())));
        assertEquals("skill_reference_not_found", wrongDocument.code());

        ToolResult.Failure<LoadSkillTool.Output> wrongSource = assertInstanceOf(
                ToolResult.Failure.class,
                tool.withOwner("another-owner").invokeFresh(
                        ToolInvocationContext.developmentConsole("other-source"),
                        new LoadSkillTool.Input("guide", null, first.nextCursor())));
        assertEquals("skill_cursor_invalid", wrongSource.code());
        String oldPayload = "guide\u0000SKILL.md\u0000" + first.fingerprint() + "\u0000" + first.nextOffset();
        String oldCursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                oldPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ToolResult.Failure<LoadSkillTool.Output> oldShape = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("old-shape"),
                        new LoadSkillTool.Input("guide", null, oldCursor)));
        assertEquals("skill_cursor_invalid", oldShape.code());
    }

    @Test
    void completedDocumentReturnsACompactReceiptInsteadOfRepeatingContent() {
        SkillRepository repository = repository("Follow evidence.");
        LoadSkillTool tool = new LoadSkillTool(repository);
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");

        LoadSkillTool.Output first = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output duplicate = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.COMPLETE, first.state());
        assertEquals("Follow evidence.", first.content());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, duplicate.state());
        assertEquals("", duplicate.content());
        assertEquals(true, duplicate.complete());
    }

    @Test
    void requestCloseDropsPendingStagingWithoutAForcedReloadOption() {
        SkillRepository repository = repository("Follow evidence.");
        LoadSkillTool tool = new LoadSkillTool(repository);
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");

        success(tool.invoke(request, new LoadSkillTool.Input("guide")));
        tool.closeRequestScope("request-1");
        LoadSkillTool.Output reopened = success(tool.invoke(request, new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.COMPLETE, reopened.state());
        assertEquals("Follow evidence.", reopened.content());
    }

    @Test
    void successfulInvocationOnlyStagesARangeUntilTheActualProjectionIsPrepared() {
        LoadSkillTool tool = new LoadSkillTool(repository("Follow evidence.").snapshot(Set.of()));
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");
        RetainedSkillContext retained = new RetainedSkillContext();
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        tool.prepareContext("request-1", List.of(), retained);

        LoadSkillTool.Output first = success(tool.invoke(request, input));
        LoadSkillTool.Output stagedReceipt = success(tool.invoke(request, input));

        assertEquals(LoadSkillTool.LoadState.COMPLETE, first.state());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, stagedReceipt.state());
        assertEquals(List.of(), retained.ranges());
        assertEquals("", tool.manifest("request-1"));

        tool.prepareContext("request-1", List.of(), retained);
        LoadSkillTool.Output afterDroppedExchange = success(tool.invoke(request, input));
        assertEquals(LoadSkillTool.LoadState.COMPLETE, afterDroppedExchange.state());
        assertEquals(first.content(), afterDroppedExchange.content());
        assertEquals(List.of(), retained.ranges());

        tool.prepareContext("request-1", history("retained", input, afterDroppedExchange), retained);
        assertEquals(1, retained.ranges().size());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, success(tool.invoke(request, input)).state());
        assertEquals(true, tool.manifest("request-1").contains("guide / SKILL.md: full"));
    }

    @Test
    void freshEndpointAlwaysReturnsPlaintextAndNeverOwnsTheAgentProjection() {
        LoadSkillTool tool = new LoadSkillTool(repository("Follow evidence.").snapshot(Set.of()))
                .withOwner("client");
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");
        RetainedSkillContext retained = new RetainedSkillContext();
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        tool.prepareContext("request-1", List.of(), retained);

        LoadSkillTool.Output first = success(tool.invokeFresh(request, input));
        LoadSkillTool.Output second = success(tool.invokeFresh(request, input));

        assertEquals(first, second);
        assertEquals(LoadSkillTool.LoadState.COMPLETE, second.state());
        assertEquals(List.of(), retained.ranges());
        assertEquals("", tool.manifest("request-1"));
        assertEquals(LoadSkillTool.LoadState.COMPLETE, success(tool.invoke(request, input)).state());

        tool.prepareContext("request-1", history("retained", input, first), retained);
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, success(tool.invoke(request, input)).state());
        assertEquals(first, success(tool.invokeFresh(request, input)));
    }

    @Test
    void repeatingTheFirstChunkReturnsTheExpectedContinuationReceipt() {
        SkillRepository repository = repository("paragraph\n\n".repeat(1_500));
        LoadSkillTool tool = new LoadSkillTool(repository);
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");

        LoadSkillTool.Output first = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output duplicate = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.CONTENT, first.state());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, duplicate.state());
        assertEquals("", duplicate.content());
        assertEquals(false, duplicate.complete());
        assertEquals(first.nextCursor(), duplicate.nextCursor());
    }

    @Test
    void retainedPlaintextAllowsAReceiptInASecondCorrelation() {
        SkillRepository repository = repository("Follow evidence.");
        LoadSkillTool firstTool = new LoadSkillTool(repository.snapshot(Set.of()));
        LoadSkillTool.Output first = success(firstTool.invoke(
                ToolInvocationContext.developmentConsole("request-1"),
                new LoadSkillTool.Input("guide")));
        firstTool.closeRequestScope("request-1");
        LoadSkillTool secondTool = new LoadSkillTool(repository.snapshot(Set.of()));
        List<ModelMessage> history = history("load-1", new LoadSkillTool.Input("guide"), first);

        RetainedSkillContext retained = new RetainedSkillContext();
        secondTool.prepareContext("request-2", history, retained);
        assertEquals(1, retained.ranges().size());
        LoadSkillTool.Output second = success(secondTool.invoke(
                ToolInvocationContext.developmentConsole("request-2"),
                new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, second.state());
        assertEquals("", second.content());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(history, secondTool.refreshContext(history));
    }

    @Test
    void rebuildingAfterCompactionDoesNotTrustAReceiptOrRequestFlag() {
        LoadSkillTool tool = new LoadSkillTool(repository("Follow evidence.").snapshot(Set.of()));
        ToolInvocationContext context = ToolInvocationContext.developmentConsole("request-1");
        success(tool.invoke(context, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output receipt = success(tool.invoke(
                context, new LoadSkillTool.Input("guide")));

        tool.prepareContext("request-1", history(
                "receipt-1", new LoadSkillTool.Input("guide"), receipt));
        LoadSkillTool.Output afterCompaction = success(tool.invoke(
                context, new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.COMPLETE, afterCompaction.state());
        assertEquals("Follow evidence.", afterCompaction.content());
        tool.prepareContext("request-1", List.of(ModelMessage.userText("Skill guide was loaded.")));
        assertEquals(LoadSkillTool.LoadState.COMPLETE,
                success(tool.invoke(context, new LoadSkillTool.Input("guide"))).state());
    }

    @Test
    void onlyRetainedProgressiveRangesGetReceiptsAndContinuationStaysAvailable() {
        SkillRepository repository = repository("paragraph\n\n".repeat(2_000));
        LoadSkillTool firstTool = new LoadSkillTool(repository.snapshot(Set.of()));
        ToolInvocationContext firstContext = ToolInvocationContext.developmentConsole("request-1");
        LoadSkillTool.Output first = success(firstTool.invoke(
                firstContext, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Input secondInput = new LoadSkillTool.Input("guide", null, first.nextCursor());
        LoadSkillTool.Output second = success(firstTool.invoke(firstContext, secondInput));
        LoadSkillTool secondTool = new LoadSkillTool(repository.snapshot(Set.of()));
        ToolInvocationContext secondContext = ToolInvocationContext.developmentConsole("request-2");

        // Only the second plaintext chunk survives the context projection.
        RetainedSkillContext retained = new RetainedSkillContext();
        secondTool.prepareContext("request-2", history("load-2", secondInput, second), retained);
        assertEquals(true, secondTool.manifest("request-2").contains("missing_offset=0"));
        LoadSkillTool.Output firstAgain = success(secondTool.invoke(
                secondContext, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output secondReceipt = success(secondTool.invoke(secondContext, secondInput));
        LoadSkillTool.Output continuation = success(secondTool.invoke(secondContext,
                new LoadSkillTool.Input("guide", null, secondReceipt.nextCursor())));

        assertEquals(LoadSkillTool.LoadState.CONTENT, firstAgain.state());
        assertEquals(first.content(), firstAgain.content());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, secondReceipt.state());
        assertEquals(second.nextOffset(), continuation.offset());
        assertEquals(LoadSkillTool.LoadState.COMPLETE, continuation.state());
    }

    @Test
    void changedReferenceDoesNotInvalidateRetainedParentOrOtherReference() {
        SkillRepository repository = repositoryWithReferences("A original", "B unchanged");
        LoadSkillTool firstTool = new LoadSkillTool(repository.snapshot(Set.of()));
        ToolInvocationContext firstContext = ToolInvocationContext.developmentConsole("request-1");
        LoadSkillTool.Input parentInput = new LoadSkillTool.Input("guide");
        LoadSkillTool.Input aInput = new LoadSkillTool.Input("guide", "references/a.md");
        LoadSkillTool.Input bInput = new LoadSkillTool.Input("guide", "references/b.md");
        List<ModelMessage> history = new java.util.ArrayList<>();
        history.addAll(history("parent", parentInput, success(firstTool.invoke(firstContext, parentInput))));
        history.addAll(history("a", aInput, success(firstTool.invoke(firstContext, aInput))));
        history.addAll(history("b", bInput, success(firstTool.invoke(firstContext, bInput))));
        repository.reload(List.of(sourceWithReferences("A changed", "B unchanged")), Set.of());
        LoadSkillTool secondTool = new LoadSkillTool(repository.snapshot(Set.of()));
        secondTool.prepareContext("request-2", secondTool.refreshContext(history));
        ToolInvocationContext secondContext = ToolInvocationContext.developmentConsole("request-2");

        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED,
                success(secondTool.invoke(secondContext, parentInput)).state());
        LoadSkillTool.Output changed = success(secondTool.invoke(secondContext, aInput));
        assertEquals(LoadSkillTool.LoadState.COMPLETE, changed.state());
        assertEquals("A changed", changed.content());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED,
                success(secondTool.invoke(secondContext, bInput)).state());
    }

    @Test
    void instructionReceiptRequiresExactPlaintextNotMetadataOrSummary() {
        LoadSkillTool tool = new LoadSkillTool(repository("Follow evidence.").snapshot(Set.of()));
        ToolInvocationContext context = ToolInvocationContext.developmentConsole("request-1");
        LoadSkillTool.Output output = success(tool.invoke(context, new LoadSkillTool.Input("guide")));
        List<ModelMessage> history = history("load", new LoadSkillTool.Input("guide"), output);
        ModelContent.ToolResult result = (ModelContent.ToolResult) history.get(1).content().getFirst();
        ModelContent.ToolResult shortened = new ModelContent.ToolResult(result.toolUseId(),
                new JsonPrimitive(output.modelText().replace("Follow evidence.", "Summary.")), false);

        tool.prepareContext("request-2", List.of(history.getFirst(),
                new ModelMessage(ModelRole.USER, List.of(shortened))));
        assertEquals(LoadSkillTool.LoadState.COMPLETE,
                success(tool.invoke(ToolInvocationContext.developmentConsole("request-2"),
                        new LoadSkillTool.Input("guide"))).state());
    }

    private static List<ModelMessage> history(
            String id, LoadSkillTool.Input input, LoadSkillTool.Output output) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("name", input.name());
        if (input.reference() != null) {
            arguments.addProperty("reference", input.reference());
        }
        if (input.cursor() != null) {
            arguments.addProperty("cursor", input.cursor());
        }
        return List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        id, "openallay__load_skill", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        id, new JsonPrimitive(output.modelText()), false))));
    }

    private static SkillRepository repositoryWithReferences(String a, String b) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(List.of(sourceWithReferences(a, b)), Set.of());
        return repository;
    }

    private static SkillSource sourceWithReferences(String a, String b) {
        return new SkillSource("pack", "guide/SKILL.md", Map.of(
                "guide/SKILL.md", """
                        ---
                        name: guide
                        description: Guide the player
                        ---
                        Follow evidence.
                        """,
                "guide/references/a.md", a,
                "guide/references/b.md", b));
    }

    private static SkillRepository repository(String body) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/SKILL.md",
                Map.of("guide/SKILL.md", """
                        ---
                        name: guide
                        description: Guide the player
                        ---
                        %s
                        """.formatted(body)))), Set.of());
        return repository;
    }

    @SuppressWarnings("unchecked")
    private static LoadSkillTool.Output success(ToolResult<LoadSkillTool.Output> result) {
        return ((ToolResult.Success<LoadSkillTool.Output>) result).value();
    }

    private static String readRemaining(LoadSkillTool tool, LoadSkillTool.Output current) {
        StringBuilder result = new StringBuilder();
        while (!current.complete()) {
            ToolResult.Success<LoadSkillTool.Output> success = assertInstanceOf(
                    ToolResult.Success.class,
                    tool.invoke(
                            ToolInvocationContext.developmentConsole("test"),
                            new LoadSkillTool.Input("guide", null, current.nextCursor())));
            current = success.value();
            result.append(current.content());
        }
        return result.toString();
    }
}
