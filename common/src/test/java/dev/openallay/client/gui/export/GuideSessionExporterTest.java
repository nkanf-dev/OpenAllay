package dev.openallay.client.gui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.export.GuideSessionExportSnapshot;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuideSessionExporterTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:34:56.789Z");

    @Test
    void writesChronologicalCredentialRedactedTextUnderTheFixedManagedRoot(@TempDir Path game)
            throws Exception {
        GuideSessionExporter exporter = new GuideSessionExporter(game);
        GuideSessionExportSnapshot snapshot = snapshot(
                "API key: sk-" + "abcdefghijklmnopqrstuvwxyz\n\"apiKey\":\"opaque-provider-value\"",
                "authorization: Bearer " + "highly-sensitive-token");

        GuideSessionExporter.ExportedFile exported = exporter.export(snapshot);
        Path file = game.resolve("openallay/exports").resolve(exported.filename());
        String text = Files.readString(file);

        assertTrue(exported.filename().matches(
                "main-20260719-123456-789-[a-f0-9]{12}\\.txt"));
        assertEquals(1, exported.requestCount());
        assertTrue(text.indexOf("User") < text.indexOf("Assistant"));
        assertTrue(text.indexOf("Assistant") < text.indexOf("Tool · get_recipe"));
        assertFalse(text.contains("abcdefghijklmnopqrstuvwxyz"));
        assertFalse(text.contains("opaque-provider-value"));
        assertFalse(text.contains("highly-sensitive-token"));
        assertFalse(text.contains("normalizedSecret"));
        assertEquals(1, Files.list(game.resolve("openallay/exports")).count());
    }

    @Test
    void rejectsAServiceDirectorySymlinkWithoutWritingOutside(@TempDir Path game)
            throws Exception {
        Path outside = Files.createDirectory(game.resolve("outside"));
        try {
            Files.createSymbolicLink(game.resolve("openallay"), outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot("hello", "answer")));
        assertEquals(0, Files.list(outside).count());
    }

    @Test
    void failedManagedRootCreationPublishesNoFile(@TempDir Path game) throws Exception {
        Files.writeString(game.resolve("openallay"), "not a directory");

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot("hello", "answer")));
        assertFalse(Files.exists(game.resolve("openallay/exports")));
    }

    @Test
    void marksCancelledPartialResponsesWithoutChangingCompletedExports() {
        GuideSessionExportSnapshot.Request completed = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED,
                "complete",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant("finished", false)),
                List.of(), null);
        GuideSessionExportSnapshot.Request cancelled = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW.plusSeconds(1),
                GuideRequestStatus.CANCELLED,
                "cancelled",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant("partial ans", false)),
                List.of(), null);

        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(completed, cancelled), NOW.plusSeconds(2)));

        assertEquals(1, text.split(
                "\\[This request ended before the response completed\\.]", -1).length - 1);
        assertTrue(text.indexOf("finished") < text.indexOf("=== Request 2"));
        assertTrue(text.indexOf("partial ans")
                < text.indexOf("[This request ended before the response completed.]"));
    }

    @Test
    void exportsOriginalCallsAndErrorsWithRequestFailureAfterCompaction() {
        UUID requestId = UUID.randomUUID();
        JsonObject input = new JsonObject();
        input.addProperty("source", "return mc.items.missing();");
        input.addProperty("apiKey", "opaque-secret-in-args");
        List<ModelMessage> original = List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse("failed-call", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "failed-call", new JsonPrimitive("status: failure\ncode: javascript_error\n"
                                + "message: TypeError at script line 1; token=opaque-secret-in-error"), true))));
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                requestId, NOW, GuideRequestStatus.FAILED, "check the error",
                List.of(new GuideSessionExportSnapshot.Entry.Tool(
                        "failed-call", "openallay:run_javascript", GuideToolStatus.FAILED)),
                original, new GuideFailure("provider_unavailable", "provider did not respond"));

        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(request), NOW));

        assertTrue(text.contains(requestId.toString()));
        assertTrue(text.contains("Invocation ID: failed-call"));
        assertTrue(text.contains("return mc.items.missing();"));
        assertTrue(text.contains("javascript_error"));
        assertTrue(text.contains("TypeError at script line 1"));
        assertTrue(text.contains("provider_unavailable"));
        assertTrue(text.contains("provider did not respond"));
        assertFalse(text.contains("opaque-secret-in-args"));
        assertFalse(text.contains("opaque-secret-in-error"));
        assertTrue(text.contains("[REDACTED]"));
        assertTrue(text.contains("Workspace handles do not survive"));
    }

    @Test
    void originalExchangeOrderSurvivesAbsentOrDifferentDisplayCards() {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return 42;");
        List<ModelMessage> original = List.of(
                ModelMessage.userText("question"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("before tool"),
                        new ModelContent.ToolUse("call-42", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "call-42", new JsonPrimitive("actual answer 42"), false))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("after tool"))));
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "question", List.of(), original, null);
        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot("main", List.of(request), NOW));
        assertTrue(text.indexOf("before tool") < text.indexOf("SUBMITTED"));
        assertTrue(text.indexOf("SUBMITTED") < text.indexOf("actual answer 42"));
        assertTrue(text.indexOf("actual answer 42") < text.indexOf("after tool"));
        assertEquals(1, text.split("question", -1).length - 1);
    }

    @Test
    void mergedDisplayTextIsNotAppendedAfterTheOriginalToolExchange() {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return 42;");
        List<ModelMessage> original = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Text("first-segment"), new ModelContent.Text("second-segment"),
                new ModelContent.ToolUse("call-42", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "call-42", new JsonPrimitive("actual answer 42"), false))));
        var request = new GuideSessionExportSnapshot.Request(UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED, "question",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant(
                        "first-segmentsecond-segment", false)), original, null);
        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot("main", List.of(request), NOW));
        assertEquals(1, text.split("first-segment", -1).length - 1);
        assertEquals(1, text.split("second-segment", -1).length - 1);
        assertFalse(text.contains("first-segmentsecond-segment"));
        assertTrue(text.indexOf("second-segment") < text.indexOf("actual answer 42"));
    }

    @Test
    void unfinishedCallsAreNotExportedAsSuccessfulResults() {
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.TOOL_WAIT, "still working",
                List.of(new GuideSessionExportSnapshot.Entry.Tool(
                        "pending-call", "openallay:run_javascript", GuideToolStatus.RUNNING)),
                List.of(), null);
        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(request), NOW));
        assertTrue(text.contains("Invocation ID: pending-call"));
        assertTrue(text.contains("No completed model-visible result was recorded"));
        assertFalse(text.contains("Result (model-visible)"));
    }

    @Test
    void reasoningCannotEnterExportProjection() {
        assertThrows(IllegalArgumentException.class, () -> new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "question", List.of(),
                List.of(new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.Reasoning("private reasoning", null)))), null));
    }

    private static GuideSessionExportSnapshot snapshot(String user, String assistant) {
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED,
                user,
                List.of(
                        new GuideSessionExportSnapshot.Entry.Assistant(assistant, false),
                        new GuideSessionExportSnapshot.Entry.Tool(
                                "call-recipe", "openallay:get_recipe", GuideToolStatus.SUCCEEDED)),
                List.of(), null);
        return new GuideSessionExportSnapshot("main", List.of(request), NOW);
    }
}
