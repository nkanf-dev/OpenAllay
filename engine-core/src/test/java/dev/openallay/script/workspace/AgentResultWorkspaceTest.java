package dev.openallay.script.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.openallay.context.SourceObservation;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.testing.GroundedTestFixtures;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AgentResultWorkspaceTest {
    @Test
    void storesReopensAndInvalidatesCanonicalResults() {
        AgentResultWorkspace workspace = new AgentResultWorkspace();
        String handle = workspace.store(JsonParser.parseString("[1,2,3]"));

        assertEquals(3, workspace.open(handle).getAsJsonArray().size());
        assertEquals(1, workspace.select(List.of(handle)).size());
        assertSame(
                workspace.select(List.of(handle)).get(handle),
                workspace.select(List.of(handle)).get(handle));

        workspace.close();
        WorkspaceException closed =
                assertThrows(WorkspaceException.class, () -> workspace.open(handle));
        assertEquals("workspace_closed", closed.code());
    }

    @Test
    void sourceReplayRetainsExactSummaryWithoutCountingReadsAsCaptures() {
        AgentResultWorkspace workspace = new AgentResultWorkspace();
        SourceObservation source = new SourceObservation(
                GroundedTestFixtures.serverEvidence(), Instant.EPOCH.plusSeconds(20));
        ArrayList<SourceObservation> supplied = new ArrayList<>(List.of(source));
        String handle = workspace.store(
                JsonParser.parseString("[1,2,3]"),
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC), false, supplied);
        supplied.clear();

        for (int index = 0; index < 20; index++) {
            workspace.open(handle);
            workspace.select(List.of(handle));
            assertEquals(List.of(source), workspace.sources(handle));
            assertEquals(List.of(source), workspace.selectSources(List.of(handle)).get(handle));
        }
        assertSame(source, workspace.sources(handle).getFirst());
        assertEquals(1, workspace.selectSources(List.of(handle, handle)).size());
        assertThrows(UnsupportedOperationException.class, () -> workspace.sources(handle).clear());
        assertThrows(UnsupportedOperationException.class,
                () -> workspace.selectSources(List.of(handle)).clear());
    }

    @Test
    void sourceSelectionRejectsMissingAndClosedHandles() {
        AgentResultWorkspace workspace = new AgentResultWorkspace();
        String handle = workspace.store(JsonParser.parseString("1"));
        assertEquals(List.of(), workspace.sources(handle));
        assertTrue(workspace.selectSources(null).isEmpty());
        assertEquals("workspace_handle_unavailable", assertThrows(WorkspaceException.class,
                () -> workspace.sources("missing")).code());
        assertEquals("workspace_handle_unavailable", assertThrows(WorkspaceException.class,
                () -> workspace.selectSources(List.of(handle, "missing"))).code());

        workspace.close();
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> workspace.sources(handle)).code());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> workspace.selectSources(List.of(handle))).code());
    }

    @Test
    void presentsEverySmallArrayRowWithoutAnArbitraryRowCap() {
        var value = JsonParser.parseString("""
                [
                  {"id":"a","damage":1},{"id":"b","damage":2},{"id":"c","damage":3},
                  {"id":"d","damage":4},{"id":"e","damage":5},{"id":"f","damage":6},
                  {"id":"g","damage":7},{"id":"h","damage":8},{"id":"i","damage":9},
                  {"id":"j","damage":10},{"id":"k","damage":11},{"id":"l","damage":12},
                  {"id":"m","damage":13},{"id":"n","damage":14},{"id":"o","damage":15}
                ]
                """);

        var result = new JavascriptResultPresenter().present("r_test", value);

        assertEquals(15, result.cardinality());
        assertEquals(0, result.omittedRows());
        assertEquals(15, result.preview().getAsJsonArray().size());
        assertTrue(result.complete());
        assertTrue(result.modelText().contains("scope: complete"));
        assertTrue(result.modelText().contains("id: \"a\""));
        assertTrue(result.modelText().contains("id: \"o\""));
        assertTrue(!result.modelText().contains("{\"id\""));
    }

    @Test
    void limitsTheWholeModelProjectionByTokenEstimateAndKeepsTheCanonicalHandle() {
        com.google.gson.JsonArray value = new com.google.gson.JsonArray();
        for (int index = 0; index < 500; index++) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("id", "example:item_" + index);
            row.addProperty("description", "配方分析结果".repeat(8));
            value.add(row);
        }

        var result = new JavascriptResultPresenter().present("r_large_rows", value);

        assertTrue(!result.complete());
        assertTrue(result.preview().getAsJsonArray().size() > 12);
        assertTrue(result.omittedRows() > 0);
        assertTrue(result.modelText().contains("workspace.open(\"r_large_rows\")"));
        assertTrue(result.modelText().getBytes(StandardCharsets.UTF_8).length
                <= JavascriptResultPresenter.MODEL_TEXT_BYTE_BUDGET);
    }

    @Test
    void boundsStructuredPreviewBeforeRenderingLargeObjects() {
        com.google.gson.JsonObject value = new com.google.gson.JsonObject();
        for (int index = 0; index < 100; index++) {
            value.addProperty("field" + index, "界".repeat(1_000));
        }

        var result = new JavascriptResultPresenter().present("r_large", value);

        assertTrue(!result.complete());
        assertTrue(result.preview().getAsJsonObject().size() < value.size());
        assertTrue(result.omittedFields() > 0);
        result.preview().getAsJsonObject().entrySet().forEach(entry ->
                assertTrue(!entry.getValue().getAsString().isBlank()));
        assertTrue(result.modelText().getBytes(StandardCharsets.UTF_8).length
                <= JavascriptResultPresenter.MODEL_TEXT_BYTE_BUDGET);
    }

    @Test
    void rejectsTooManySelectedHandlesWithoutPartiallyOpeningThem() {
        AgentResultWorkspace workspace = new AgentResultWorkspace();
        java.util.ArrayList<String> handles = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            handles.add(workspace.store(JsonParser.parseString("[" + index + "]")));
        }

        WorkspaceException failure = assertThrows(
                WorkspaceException.class, () -> workspace.select(handles));

        assertEquals("workspace_selection_too_large", failure.code());
        assertEquals(5, workspace.size());
    }

    @Test
    void marksAnswerSizedResultsCompleteAndTellsModelToStopVerifying() {
        var result = new JavascriptResultPresenter().present(
                "r_complete", JsonParser.parseString("[{\"id\":\"example:sword\",\"damage\":14}]"));

        assertTrue(result.complete());
        assertTrue(result.modelText().contains("scope: complete"));
        assertTrue(result.modelText().contains(
                "do not call run_javascript again only to verify it"));
    }
}
