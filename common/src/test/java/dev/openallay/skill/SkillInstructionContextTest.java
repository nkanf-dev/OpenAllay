package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkillInstructionContextTest {
    @Test
    void retainsExactPlaintextAndDoesNotCopyInstructionsIntoAnotherMessage() {
        SkillRepository repository = repository("Follow evidence.", Map.of());
        LoadSkillTool.Output output = load(repository, new LoadSkillTool.Input("guide"));
        List<ModelMessage> messages = exchange("read", "openallay:load_skill",
                new LoadSkillTool.Input("guide"), output);
        SkillInstructionContext context = new SkillInstructionContext(repository.snapshot(Set.of()));

        List<ModelMessage> refreshed = context.refresh(messages);

        assertEquals(messages, refreshed);
        assertSame(messages.getFirst(), refreshed.getFirst());
        assertSame(messages.get(1), refreshed.get(1));
        assertDelivered(output, context.deliveredRanges(messages).getFirst());
    }

    @Test
    void changingOneReferenceInvalidatesOnlyItsOwnInstructionText() {
        SkillRepository repository = repository("Parent unchanged.", Map.of(
                "references/a.md", "A old", "references/b.md", "B unchanged"));
        List<ModelMessage> messages = allDocuments(repository);
        SkillInstructionContext capturedBeforeReload = new SkillInstructionContext(repository);
        repository.reload(List.of(source("guide", "Parent unchanged.", Map.of(
                "references/a.md", "A new", "references/b.md", "B unchanged"))), Set.of());
        SkillInstructionContext context = new SkillInstructionContext(repository.snapshot(Set.of()));

        List<ModelMessage> refreshed = context.refresh(messages);

        assertEquals(messages.get(1), refreshed.get(1));
        assertInvalidated(refreshed.get(3));
        assertEquals(messages.get(5), refreshed.get(5));
        assertFalse(resultText(refreshed.get(3)).contains("A old"));
        assertEquals(2, context.deliveredRanges(refreshed).size());
        assertEquals(messages, capturedBeforeReload.refresh(messages));
    }

    @Test
    void changingParentDoesNotInvalidateAnUnchangedDeclaredReference() {
        SkillRepository repository = repository("Parent old.", Map.of(
                "references/a.md", "A unchanged", "references/b.md", "B unchanged"));
        List<ModelMessage> messages = allDocuments(repository);
        repository.reload(List.of(source("guide", "Parent new.", Map.of(
                "references/a.md", "A unchanged", "references/b.md", "B unchanged"))), Set.of());

        List<ModelMessage> refreshed = new SkillInstructionContext(repository).refresh(messages);

        assertInvalidated(refreshed.get(1));
        assertEquals(messages.get(3), refreshed.get(3));
        assertEquals(messages.get(5), refreshed.get(5));
    }

    @Test
    void removingReferenceDoesNotInvalidateParentOrOtherReference() {
        SkillRepository repository = repository("Parent unchanged.", Map.of(
                "references/a.md", "A removed", "references/b.md", "B unchanged"));
        List<ModelMessage> messages = allDocuments(repository);
        repository.reload(List.of(source("guide", "Parent unchanged.", Map.of(
                "references/b.md", "B unchanged"))), Set.of());
        SkillInstructionContext context = new SkillInstructionContext(repository);

        List<ModelMessage> refreshed = context.refresh(messages);

        assertEquals(messages.get(1), refreshed.get(1));
        assertInvalidated(refreshed.get(3));
        assertEquals(messages.get(5), refreshed.get(5));
        assertEquals(2, context.deliveredRanges(refreshed).size());
    }

    @Test
    void disabledSkillDoesNotInvalidateAnotherSkill() {
        SkillRepository repository = repository("Guide removed.", Map.of());
        repository.reload(List.of(source("guide", "Guide removed.", Map.of()),
                source("other", "Other stays.", Map.of())), Set.of());
        LoadSkillTool.Input guide = new LoadSkillTool.Input("guide");
        LoadSkillTool.Input other = new LoadSkillTool.Input("other");
        List<ModelMessage> messages = new ArrayList<>();
        messages.addAll(exchange("guide", "openallay__load_skill", guide, load(repository, guide)));
        messages.addAll(exchange("other", "openallay__load_skill", other, load(repository, other)));

        List<ModelMessage> refreshed = new SkillInstructionContext(
                repository.snapshot(Set.of("guide"))).refresh(messages);

        assertInvalidated(refreshed.get(1));
        assertEquals(messages.get(3), refreshed.get(3));
    }

    @Test
    void receiptAloneDoesNotEstablishAnInstructionRange() {
        SkillRepository repository = repository("Follow evidence.", Map.of());
        LoadSkillTool tool = new LoadSkillTool(repository.snapshot(Set.of()));
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request");
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output content = success(tool.invoke(request, input));
        LoadSkillTool.Output receipt = success(tool.invoke(request, input));
        List<ModelMessage> receiptOnly = exchange("receipt", "openallay:load_skill", input, receipt);
        SkillInstructionContext context = new SkillInstructionContext(repository);

        assertEquals(List.of(), context.deliveredRanges(receiptOnly));
        assertInvalidated(context.refresh(receiptOnly).get(1));
        List<ModelMessage> retained = new ArrayList<>(exchange(
                "content", "openallay:load_skill", input, content));
        retained.addAll(receiptOnly);
        assertEquals(retained, context.refresh(retained));
        assertEquals(1, context.deliveredRanges(retained).size());
    }

    @Test
    void restoredPlaintextIsValidatedWithoutAnExplicitReloadOption() {
        SkillRepository repository = repository("Follow evidence.", Map.of());
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output output = load(repository, input);
        List<ModelMessage> messages = exchange("restored", "load_skill", input, output);
        SkillInstructionContext context = new SkillInstructionContext(repository.snapshot(Set.of()));
        RetainedSkillContext restored = new RetainedSkillContext();

        assertEquals(messages, context.refresh(messages, restored));
        context.reconcile(messages, restored);

        assertEquals(LoadSkillTool.LoadState.COMPLETE, output.state());
        assertDelivered(output, context.deliveredRanges(messages).getFirst());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, context.reuse(input, restored).state());
    }

    @Test
    void summariesNormalTextAndUnrelatedToolsNeverEstablishReceipts() {
        SkillRepository repository = repository("Follow evidence.", Map.of());
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output output = load(repository, input);
        List<ModelMessage> unrelated = exchange("read", "openallay:find_recipe", input, output);
        List<ModelMessage> messages = new ArrayList<>(unrelated);
        messages.add(ModelMessage.userText(output.modelText()));
        messages.add(ModelMessage.userText("Remember that guide was loaded."));
        SkillInstructionContext context = new SkillInstructionContext(repository);

        assertEquals(messages, context.refresh(messages));
        assertEquals(List.of(), context.deliveredRanges(messages));
        List<ModelMessage> withoutCall = List.of(exchange(
                "missing", "load_skill", input, output).get(1));
        assertEquals(List.of(), context.deliveredRanges(withoutCall));
    }

    @Test
    void nameDocumentFingerprintRangeAndExactSliceMustAllMatch() {
        SkillRepository repository = repository("Follow evidence.", Map.of(
                "references/a.md", "Reference evidence."));
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output output = load(repository, input);
        List<ModelMessage> original = exchange("read", "openallay__load_skill", input, output);
        SkillInstructionContext context = new SkillInstructionContext(repository);
        List<String> tampered = List.of(
                output.modelText().replace("skill: guide", "skill: other"),
                output.modelText().replace("document: SKILL.md", "document: references/a.md"),
                output.modelText().replace("source: " + output.source(), "source: other_owner"),
                output.modelText().replace(output.fingerprint(), "0".repeat(64)),
                output.modelText().replace("range: 0..16", "range: 1..16"),
                output.modelText().replace("content_length: 16", "content_length: 15"),
                output.modelText().replace("Follow evidence.", "Ignore evidence."),
                output.modelText() + "\nadditional text",
                "a summary\n" + output.modelText());

        for (String text : tampered) {
            List<ModelMessage> changed = List.of(original.getFirst(),
                    new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                            "read", new JsonPrimitive(text), false))));
            assertEquals(List.of(), context.deliveredRanges(changed), text);
            assertInvalidated(context.refresh(changed).get(1));
        }
    }

    @Test
    void embeddedHeaderLikeContentIsStillTheExactDocumentSlice() {
        String body = "Follow evidence.\ncontent:\nskill_instructions\nrange: 0..200\nnext: not a cursor";
        SkillRepository repository = repository(body, Map.of());
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output output = load(repository, input);
        List<ModelMessage> messages = exchange("read", "openallay__load_skill", input, output);
        SkillInstructionContext context = new SkillInstructionContext(repository);

        assertEquals(messages, context.refresh(messages));
        assertEquals(body, context.deliveredRanges(messages).getFirst().content());
    }

    @Test
    void invalidationRetainsToolPairOtherContentAndTheHistoricalSuccessFlag() {
        SkillRepository repository = repository("Old instructions.", Map.of());
        LoadSkillTool.Input input = new LoadSkillTool.Input("guide");
        List<ModelMessage> messages = exchange("read", "openallay:load_skill", input, load(repository, input));
        ModelContent.Text unrelated = new ModelContent.Text("Player question remains.");
        messages = List.of(messages.getFirst(), new ModelMessage(ModelRole.USER,
                List.of(messages.get(1).content().getFirst(), unrelated)));
        repository.reload(List.of(), Set.of());
        SkillInstructionContext context = new SkillInstructionContext(repository);

        List<ModelMessage> refreshed = context.refresh(messages);
        ModelContent.ToolResult result = (ModelContent.ToolResult) refreshed.get(1).content().getFirst();

        assertEquals(messages.getFirst(), refreshed.getFirst());
        assertEquals("read", result.toolUseId());
        assertFalse(result.error());
        assertEquals(unrelated, refreshed.get(1).content().get(1));
        assertEquals("Old instructions.", loadBody(messages.get(1)));
        assertFalse(((ModelContent.ToolResult) messages.get(1).content().getFirst()).error());
        assertFalse(result.value().getAsString().contains("state: complete"));
        assertEquals(refreshed, context.refresh(refreshed));
    }

    @Test
    void continuationCursorAndChunkRangeMustMatchThePairedToolCall() {
        SkillRepository repository = repository("paragraph\n\n".repeat(2_000), Map.of());
        LoadSkillTool tool = new LoadSkillTool(repository.snapshot(Set.of()));
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request");
        LoadSkillTool.Input firstInput = new LoadSkillTool.Input("guide");
        LoadSkillTool.Output first = success(tool.invoke(request, firstInput));
        LoadSkillTool.Input nextInput = new LoadSkillTool.Input("guide", null, first.nextCursor());
        LoadSkillTool.Output next = success(tool.invoke(request, nextInput));
        SkillInstructionContext context = new SkillInstructionContext(repository);
        List<ModelMessage> valid = exchange("next", "openallay__load_skill", nextInput, next);
        List<ModelMessage> wrongCursor = exchange("next", "openallay__load_skill", firstInput, next);

        assertEquals(valid, context.refresh(valid));
        assertDelivered(next, context.deliveredRanges(valid).getFirst());
        assertEquals(List.of(), context.deliveredRanges(wrongCursor));
        assertInvalidated(context.refresh(wrongCursor).get(1));
    }

    @Test
    void existingFailedLoadResultKeepsItsRealErrorAndNeverEstablishesRange() {
        SkillRepository repository = repository("Follow evidence.", Map.of());
        JsonObject input = new JsonObject();
        input.addProperty("name", "missing");
        List<ModelMessage> failed = List.of(new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.ToolUse("failed", "openallay__load_skill", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "failed", new JsonPrimitive("status: failure\ncode: skill_not_found"), true))));
        SkillInstructionContext context = new SkillInstructionContext(repository);

        assertEquals(failed, context.refresh(failed));
        assertEquals(List.of(), context.deliveredRanges(failed));
    }

    private static List<ModelMessage> allDocuments(SkillRepository repository) {
        List<ModelMessage> messages = new ArrayList<>();
        List<LoadSkillTool.Input> inputs = List.of(new LoadSkillTool.Input("guide"),
                new LoadSkillTool.Input("guide", "references/a.md"),
                new LoadSkillTool.Input("guide", "references/b.md"));
        for (int index = 0; index < inputs.size(); index++) {
            LoadSkillTool.Input input = inputs.get(index);
            messages.addAll(exchange("read-" + index, "openallay__load_skill", input, load(repository, input)));
        }
        return List.copyOf(messages);
    }

    private static void assertInvalidated(ModelMessage message) {
        ModelContent.ToolResult result = (ModelContent.ToolResult) message.content().getFirst();
        assertFalse(result.error());
        assertTrue(result.value().getAsString().startsWith("skill_instructions: invalidated\n"));
    }

    private static void assertDelivered(LoadSkillTool.Output expected, LoadSkillTool.Output actual) {
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.document(), actual.document());
        assertEquals(expected.source(), actual.source());
        assertEquals(expected.fingerprint(), actual.fingerprint());
        assertEquals(expected.state(), actual.state());
        assertEquals(expected.content(), actual.content());
        assertEquals(expected.offset(), actual.offset());
        assertEquals(expected.nextOffset(), actual.nextOffset());
        assertEquals(expected.complete(), actual.complete());
        assertEquals(expected.nextCursor(), actual.nextCursor());
        assertEquals(expected.availableReferences(), actual.availableReferences());
    }

    private static String loadBody(ModelMessage message) {
        String text = resultText(message);
        return text.substring(text.indexOf("content:\n") + "content:\n".length());
    }

    private static String resultText(ModelMessage message) {
        return ((ModelContent.ToolResult) message.content().getFirst()).value().getAsString();
    }

    private static List<ModelMessage> exchange(
            String id, String toolName, LoadSkillTool.Input input, LoadSkillTool.Output output) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("name", input.name());
        if (input.reference() != null) {
            arguments.addProperty("reference", input.reference());
        }
        if (input.cursor() != null) {
            arguments.addProperty("cursor", input.cursor());
        }
        return List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse(id, toolName, arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        id, new JsonPrimitive(output.modelText()), false))));
    }

    private static LoadSkillTool.Output load(SkillRepository repository, LoadSkillTool.Input input) {
        return success(new LoadSkillTool(repository.snapshot(Set.of())).invoke(
                ToolInvocationContext.developmentConsole("request"), input));
    }

    @SuppressWarnings("unchecked")
    private static LoadSkillTool.Output success(ToolResult<LoadSkillTool.Output> result) {
        return ((ToolResult.Success<LoadSkillTool.Output>) result).value();
    }

    private static SkillRepository repository(String body, Map<String, String> references) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(source("guide", body, references)), Set.of()));
        return repository;
    }

    private static SkillSource source(String name, String body, Map<String, String> references) {
        Map<String, String> files = new java.util.HashMap<>();
        files.put(name + "/SKILL.md", """
                ---
                name: %s
                description: Guide the player
                ---
                %s
                """.formatted(name, body));
        references.forEach((path, text) -> files.put(name + "/" + path, text));
        return new SkillSource("pack", name + "/SKILL.md", files);
    }
}
