package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkillInstructionCoverageIndexTest {
    @Test
    void unionMergesManyOutOfOrderOverlappingAndDuplicateRangesWithoutFillingAGap() {
        var key = new RetainedSkillContext.Key("guide", "SKILL.md", "local", "a".repeat(64));
        List<RetainedSkillContext.Range> ranges = new ArrayList<>();
        int count = 2_048;
        int length = count * 4;
        int missing = count / 2;
        for (int index = count - 1; index >= 0; index--) {
            if (index == missing) continue;
            int offset = index * 4;
            ranges.add(new RetainedSkillContext.Range(key, offset + 1, offset + 4, length));
            ranges.add(new RetainedSkillContext.Range(key, offset, offset + 2, length));
            ranges.add(new RetainedSkillContext.Range(key, offset, offset + 2, length));
        }
        var coverage = new RetainedSkillContext.Coverage(ranges);
        var retained = new RetainedSkillContext();
        retained.reconcile(ranges);
        int gapStart = missing * 4;
        int gapEnd = gapStart + 4;

        for (int index = 0; index < count; index++) {
            int offset = index * 4;
            var range = new RetainedSkillContext.Range(key, offset, offset + 4, length);
            assertEquals(index != missing, coverage.contains(range));
            assertEquals(index != missing, retained.contains(key, offset, offset + 4));
        }
        assertTrue(coverage.contains(key, 0, gapStart));
        assertTrue(coverage.contains(key, gapEnd, length));
        assertFalse(coverage.contains(key, 0, length));
        assertFalse(coverage.contains(key, gapStart - 1, gapEnd + 1));
        assertFalse(coverage.contains(new RetainedSkillContext.Key(
                "guide", "SKILL.md", "other", key.fingerprint()), 0, gapStart));
        assertFalse(coverage.contains(new RetainedSkillContext.Key(
                "guide", "SKILL.md", "local", "b".repeat(64)), 0, gapStart));
        assertFalse(coverage.contains(new RetainedSkillContext.Key(
                "guide", "references/a.md", "local", key.fingerprint()), 0, gapStart));
        assertFalse(coverage.contains(new RetainedSkillContext.Key(
                "other", "SKILL.md", "local", key.fingerprint()), 0, gapStart));

        var bridge = new RetainedSkillContext.Range(key, gapStart, gapEnd, length);
        coverage.add(bridge);
        coverage.add(bridge);
        assertTrue(coverage.contains(key, 0, length));
        assertFalse(retained.contains(key, 0, length));
        ranges.add(bridge);
        retained.reconcile(ranges);
        assertTrue(retained.contains(key, 0, length));
        retained.reconcile(List.of(bridge));
        assertEquals(List.of(bridge), retained.ranges());
        assertFalse(retained.contains(key, 0, length));
    }

    @Test
    void manyOutOfOrderChunksKeepFirstPlaintextAndReplaceOnlyDuplicatesWithReceipts() {
        Fixture fixture = fixture("guide", 256);
        List<ModelMessage> messages = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int index = fixture.document().chunks().size() - 1; index >= 0; index--) {
            order.add(index);
            messages.addAll(exchange("first-" + index, fixture, index, false));
        }
        for (int index = 0; index < fixture.document().chunks().size(); index++) {
            messages.addAll(exchange("duplicate-" + index, fixture, index, false));
        }
        var retained = new RetainedSkillContext();

        List<ModelMessage> refreshed = fixture.context().refresh(messages, retained);
        int count = fixture.document().chunks().size();
        for (int position = 0; position < count; position++) {
            assertSame(messages.get(position * 2), refreshed.get(position * 2));
            assertSame(messages.get(position * 2 + 1), refreshed.get(position * 2 + 1));
            assertEquals(output(fixture, position, true).modelText(),
                    resultText(refreshed.get((count + position) * 2 + 1)));
            assertEquals(output(fixture, position, false).modelText(),
                    resultText(messages.get((count + position) * 2 + 1)));
            assertFalse(((ModelContent.ToolResult) refreshed.get(
                    (count + position) * 2 + 1).content().getFirst()).error());
        }
        fixture.context().reconcile(refreshed, retained);
        assertEquals(count, retained.ranges().size());
        assertTrue(retained.contains(fixture.document().key(), 0, fixture.document().length()));
        assertEquals(refreshed, fixture.context().refresh(refreshed, retained));
        assertTrue(fixture.context().manifest(retained).contains("guide / SKILL.md: full"));
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED,
                fixture.context().reuse(input(fixture, order.getFirst()), retained).state());
    }

    @Test
    void receiptsUseCurrentPlaintextEvenWhenItAppearsLaterButNotStaleRetainedCoverage() {
        Fixture fixture = fixture("guide", 64);
        int missing = 31;
        List<ModelMessage> allPlaintext = new ArrayList<>();
        List<ModelMessage> current = new ArrayList<>();
        // Receipts precede the plaintext; their validity is based on the whole current projection.
        for (int index = 0; index < fixture.document().chunks().size(); index++) {
            current.addAll(exchange("receipt-" + index, fixture, index, true));
            allPlaintext.addAll(exchange("content-" + index, fixture, index, false));
        }
        for (int index = fixture.document().chunks().size() - 1; index >= 0; index--) {
            if (index != missing) current.addAll(exchange("actual-" + index, fixture, index, false));
        }
        var retained = new RetainedSkillContext();
        fixture.context().reconcile(allPlaintext, retained);
        assertTrue(retained.contains(fixture.document().key(), 0, fixture.document().length()));

        List<ModelMessage> refreshed = fixture.context().refresh(current, retained);
        int count = fixture.document().chunks().size();
        for (int index = 0; index < count; index++) {
            if (index == missing) {
                assertInvalidated(refreshed.get(index * 2 + 1));
            } else {
                assertSame(current.get(index * 2 + 1), refreshed.get(index * 2 + 1));
            }
        }
        for (int position = count * 2; position < refreshed.size(); position++) {
            assertSame(current.get(position), refreshed.get(position));
        }
        fixture.context().reconcile(refreshed, retained);
        var gap = fixture.document().chunks().get(missing);
        assertFalse(retained.contains(fixture.document().key(), gap.offset(), gap.end()));
        assertFalse(retained.contains(fixture.document().key(), 0, fixture.document().length()));
        assertTrue(fixture.context().manifest(retained).contains("guide / SKILL.md: partial ["));
        assertFalse(fixture.context().manifest(retained).contains("guide / SKILL.md: full"));

        List<ModelMessage> receiptOnly = exchange("alone", fixture, missing - 1, true);
        assertInvalidated(fixture.context().refresh(receiptOnly, retained).get(1));
        fixture.context().reconcile(receiptOnly, retained);
        assertEquals(List.of(), retained.ranges());
    }

    @Test
    void overlappingSystemRangesDeduplicateOnlyFullyOwnedChunksAndDoNotFillGaps() {
        Fixture fixture = fixture("guide", 4);
        var retained = new RetainedSkillContext();
        var key = fixture.document().key();
        int length = fixture.document().length();
        retained.systemRanges(List.of(
                new RetainedSkillContext.Range(key, 12, 24, length),
                new RetainedSkillContext.Range(key, 0, 16, length),
                new RetainedSkillContext.Range(key, 28, 32, length)));
        List<ModelMessage> messages = new ArrayList<>();
        for (int index = 0; index < fixture.document().chunks().size(); index++) {
            messages.addAll(exchange("content-" + index, fixture, index, false));
        }

        List<ModelMessage> refreshed = fixture.context().refresh(messages, retained);
        for (int index = 0; index < 3; index++) {
            assertEquals(output(fixture, index, true).modelText(), resultText(refreshed.get(index * 2 + 1)));
        }
        assertSame(messages.get(7), refreshed.get(7));
        fixture.context().reconcile(refreshed, retained);
        assertTrue(retained.contains(key, 0, length));

        retained.systemRanges(List.of());
        List<ModelMessage> withoutSystem = fixture.context().refresh(refreshed, retained);
        for (int index = 0; index < 3; index++) assertInvalidated(withoutSystem.get(index * 2 + 1));
        assertSame(refreshed.get(7), withoutSystem.get(7));
        fixture.context().reconcile(withoutSystem, retained);
        assertFalse(retained.contains(key, 0, length));
    }

    @Test
    void onlyExactPreparedSystemPlaintextCanOwnAllDocumentChunks() {
        Fixture fixture = fixture(SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT, 8);
        var retained = new RetainedSkillContext();
        List<ModelMessage> messages = new ArrayList<>();
        for (int index = 0; index < fixture.document().chunks().size(); index++) {
            messages.addAll(exchange("content-" + index, fixture, index, false));
        }
        String system = "preamble\n\n## UNRESTRICTED JAVASCRIPT GUIDANCE\n" + fixture.text()
                + "\n\n## OTHER\nOther guidance";
        fixture.context().prepareSystem(system, retained);

        List<ModelMessage> refreshed = fixture.context().refresh(messages, retained);
        for (int index = 0; index < fixture.document().chunks().size(); index++) {
            assertEquals(output(fixture, index, true).modelText(), resultText(refreshed.get(index * 2 + 1)));
        }
        fixture.context().reconcile(refreshed, retained);
        assertTrue(retained.contains(fixture.document().key(), 0, fixture.document().length()));

        fixture.context().prepareSystem(system.replace(fixture.text(), "X" + fixture.text().substring(1)), retained);
        List<ModelMessage> invalidated = fixture.context().refresh(refreshed, retained);
        for (int index = 0; index < fixture.document().chunks().size(); index++) {
            assertInvalidated(invalidated.get(index * 2 + 1));
        }
        fixture.context().reconcile(invalidated, retained);
        assertEquals(List.of(), retained.ranges());
    }

    @Test
    void malformedPlaintextNeverEstablishesCoverageForAReceipt() {
        Fixture fixture = fixture("guide", 1);
        List<ModelMessage> valid = exchange("content", fixture, 0, false);
        var retained = new RetainedSkillContext();
        fixture.context().reconcile(valid, retained);
        List<ModelMessage> malformed = new ArrayList<>();
        malformed.add(valid.getFirst());
        malformed.add(new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                "content", new JsonPrimitive(resultText(valid.get(1)) + "extra"), false))));
        malformed.addAll(exchange("receipt", fixture, 0, true));

        List<ModelMessage> refreshed = fixture.context().refresh(malformed, retained);
        assertInvalidated(refreshed.get(1));
        assertInvalidated(refreshed.get(3));
        fixture.context().reconcile(refreshed, retained);
        assertEquals(List.of(), retained.ranges());
    }

    @Test
    void emptyRangeNeedsAnActualIndexedRange() {
        var key = new RetainedSkillContext.Key("guide", "references/empty.md", "local", "a".repeat(64));
        var range = new RetainedSkillContext.Range(key, 0, 0, 0);
        var coverage = new RetainedSkillContext.Coverage(List.of());
        assertFalse(coverage.contains(range));
        coverage.add(range);
        coverage.add(range);
        assertTrue(coverage.contains(range));
    }

    private record Fixture(SkillCatalogManifest.Document document, String text, SkillInstructionContext context) {}

    private static Fixture fixture(String name, int count) {
        String text = "abcdefgh".repeat(count);
        List<SkillCatalogManifest.Chunk> chunks = new ArrayList<>();
        for (int offset = 0; offset < text.length(); offset += 8) {
            chunks.add(new SkillCatalogManifest.Chunk(offset, offset + 8,
                    LoadSkillTool.fingerprint(text.substring(offset, offset + 8))));
        }
        var document = new SkillCatalogManifest.Document(name, "SKILL.md", "local",
                LoadSkillTool.fingerprint(text), text.length(), chunks, List.of(), "Guide the player");
        return new Fixture(document, text, new SkillInstructionContext(new SkillCatalogManifest(List.of(document))));
    }

    private static LoadSkillTool.Input input(Fixture fixture, int index) {
        var document = fixture.document();
        int offset = document.chunks().get(index).offset();
        return new LoadSkillTool.Input(document.name(), null, offset == 0 ? null : cursor(document, offset));
    }

    private static LoadSkillTool.Output output(Fixture fixture, int index, boolean receipt) {
        var document = fixture.document();
        var chunk = document.chunks().get(index);
        boolean complete = chunk.end() == document.length();
        return new LoadSkillTool.Output(document.name(), document.document(), document.source(), document.fingerprint(),
                receipt ? LoadSkillTool.LoadState.ALREADY_LOADED
                        : complete ? LoadSkillTool.LoadState.COMPLETE : LoadSkillTool.LoadState.CONTENT,
                receipt ? "" : fixture.text().substring(chunk.offset(), chunk.end()), chunk.offset(), chunk.end(), complete,
                complete ? "" : cursor(document, chunk.end()), document.availableReferences(), List.of(), "");
    }

    private static String cursor(SkillCatalogManifest.Document document, int offset) {
        return LoadSkillTool.encodeCursor(document.name(), document.document(), document.source(),
                document.fingerprint(), offset);
    }

    private static List<ModelMessage> exchange(String id, Fixture fixture, int index, boolean receipt) {
        LoadSkillTool.Input input = input(fixture, index);
        JsonObject arguments = new JsonObject();
        arguments.addProperty("name", input.name());
        if (input.cursor() != null) arguments.addProperty("cursor", input.cursor());
        return List.of(new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.ToolUse(id, "openallay:load_skill", arguments))),
                new ModelMessage(ModelRole.USER,
                        List.of(new ModelContent.ToolResult(id, new JsonPrimitive(output(fixture, index, receipt).modelText()),
                                false))));
    }

    private static String resultText(ModelMessage message) {
        return ((ModelContent.ToolResult) message.content().getFirst()).value().getAsString();
    }

    private static void assertInvalidated(ModelMessage message) {
        ModelContent.ToolResult result = (ModelContent.ToolResult) message.content().getFirst();
        assertFalse(result.error());
        assertTrue(result.value().getAsString().startsWith("skill_instructions: invalidated\n"));
    }
}
