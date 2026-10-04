package dev.openallay.guide.composer;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.guide.GuideCompactResult;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SlashCommandParserTest {
    @Test void whitespaceAndExactImplementedCommand() {
        assertEquals(SlashCommandParser.Kind.COMMAND, SlashCommandParser.parse("  /compact\t\n").kind());
        assertEquals("compact", SlashCommandParser.parse("/compact").text());
        assertEquals(SlashCommandParser.Kind.TEXT, SlashCommandParser.parse("hello /compact").kind());
        assertEquals("invalid_slash_arguments", SlashCommandParser.parse("/compact now").code());
        assertEquals("unknown_slash_command", SlashCommandParser.parse("/give stone").code());
        assertEquals("unknown_slash_command", SlashCommandParser.parse("/").code());
        assertEquals("unknown_slash_command", SlashCommandParser.parse("/COMPACT").code());
    }

    @Test void doubleSlashEscapesOneSlashForOrdinaryModelText() {
        assertEquals(SlashCommandParser.Kind.TEXT, SlashCommandParser.parse(" //compact ").kind());
        assertEquals("/compact", SlashCommandParser.parse(" //compact ").text());
        assertEquals("//a", SlashCommandParser.parse("///a").text());
        assertEquals("", SlashCommandParser.parse(null).text());
    }

    @Test void helpContainsOnlyImplementedMatchingCommands() {
        assertEquals(List.of(new SlashCommandParser.Suggestion("/compact", "openallay.guide.slash.compact.help")),
                SlashCommandParser.suggestions("/com"));
        assertEquals(1, SlashCommandParser.suggestions("/").size());
        for (String draft : List.of("//", "/give", "/compact x", "hello")) {
            assertTrue(SlashCommandParser.suggestions(draft).isEmpty());
        }
    }

    @Test void unknownCommandsDoNotCallControlOrOrdinaryModelAndKeepDraftAttachments() {
        AtomicInteger calls = new AtomicInteger();
        List<String> attachments = new ArrayList<>(List.of("image-ref"));
        List<SlashCommandDispatcher.Completion> completions = new ArrayList<>();
        var dispatched = SlashCommandDispatcher.dispatch("/unknown", () -> {
            calls.incrementAndGet();
            return new CompletableFuture<>();
        }, completions::add);
        assertTrue(dispatched.handled());
        assertTrue(dispatched.retainDraft());
        assertEquals("/unknown", dispatched.normalizedText());
        assertEquals(0, calls.get());
        assertFalse(completions.getFirst().successful());
        assertEquals(List.of("image-ref"), attachments);
    }

    @Test void compactIsHandledAsynchronousAndDoesNotOwnAttachmentConsumption() {
        CompletableFuture<ToolResult<GuideCompactResult>> control = new CompletableFuture<>();
        List<SlashCommandDispatcher.Completion> completions = new ArrayList<>();
        List<String> attachments = new ArrayList<>(List.of("persisted-image"));
        var dispatched = SlashCommandDispatcher.dispatch("/compact", () -> control, completions::add);
        assertTrue(dispatched.handled());
        assertTrue(dispatched.retainDraft());
        assertTrue(completions.isEmpty());
        control.complete(new ToolResult.Success<>(new GuideCompactResult(
                GuideCompactResult.Status.NOT_NEEDED, 12, 12, 100, null)));
        assertTrue(completions.getFirst().successful());
        assertEquals("compact_not_needed", completions.getFirst().code());
        assertEquals(List.of("persisted-image"), attachments);
    }

    @Test void rejectedOrExceptionalControlKeepsDraftAndEscapedInputNeverCallsIt() {
        List<SlashCommandDispatcher.Completion> completions = new ArrayList<>();
        var rejected = SlashCommandDispatcher.dispatch("/compact", () -> CompletableFuture.completedFuture(
                new ToolResult.Failure<>("compact_busy", "Wait")), completions::add);
        assertTrue(rejected.handled());
        assertTrue(rejected.retainDraft());
        assertEquals("compact_busy", completions.getFirst().code());
        assertFalse(completions.getFirst().successful());
        var literal = SlashCommandDispatcher.dispatch("//give stone", () -> {
            fail("Escaped text must not invoke local controls or Minecraft commands");
            return null;
        }, completions::add);
        assertFalse(literal.handled());
        assertEquals("/give stone", literal.normalizedText());
        SlashCommandDispatcher.dispatch("/compact", () -> CompletableFuture.failedFuture(
                new IllegalStateException("broken")), completions::add);
        assertEquals("compact_failed", completions.getLast().code());
    }
}
