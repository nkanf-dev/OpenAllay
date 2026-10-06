package dev.openallay.script.workspace;

import static dev.openallay.testing.JavascriptResultBudgetFixtures.AFTER;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.BEFORE;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.BLOCK_COUNT;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.COLUMN_COUNT;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.TILE_COUNT;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.SUMMARY_FIELD_COUNT;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.encodedModelTextUtf8Bytes;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.escapingHeavyResult;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.jsonUtf8Bytes;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.largeWorldResult;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.terrainResult;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.scalarSummaryResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ModelResultView;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

final class JavascriptResultBudgetTest {
    private static final JavascriptResultShape SHAPE =
            JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC);
    private static final int[] BUDGETS = {1_024, 4_096, 32_768};

    @Test
    void boundsNestedWorldProjectionByEncodedJsonBytesAndKeepsBothSmallConclusions() {
        JsonObject canonical = largeWorldResult();
        int canonicalBytes = jsonUtf8Bytes(canonical);
        assertTrue(canonicalBytes >= 1_600_000 && canonicalBytes <= 1_700_000);
        String exact = canonical.toString();
        String handle = "r_world_8704";

        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter()
                    .present(handle, canonical, SHAPE, "", budget);
            String text = presentation.modelText();

            assertEncodedBudget(text, budget);
            assertFalse(presentation.complete());
            assertEquals("object", presentation.type());
            assertEquals(3, presentation.cardinality());
            assertEquals(handle, presentation.handle());
            assertTrue(text.contains("result: " + handle + " (current request only)"), text);
            assertTrue(text.contains("cardinality: 3"), text);
            assertTrue(text.contains("size: " + canonicalBytes + " UTF-8 byte(s)"), text);
            assertTrue(text.contains("scope: preview"), text);
            assertTrue(text.contains("schema:"), text);
            assertTrue(text.contains("blocks") && text.contains(Integer.toString(BLOCK_COUNT)), text);
            assertTrue(text.contains("position") && text.contains("blockEntity"), text);
            assertTrue(text.contains(BEFORE), text);
            assertTrue(text.contains(AFTER), text);
            assertTrue(text.contains("workspace.open(\"" + handle + "\")"), text);
            assertTrue(jsonUtf8Bytes(presentation.preview()) < canonicalBytes);
            assertEquals(exact, canonical.toString());
        }
    }

    @Test
    void pureTerrainShapeKeepsBothArrayCardinalitiesAndExactCanonicalNumericTypes() {
        JsonObject canonical = terrainResult();
        assertEquals(COLUMN_COUNT, canonical.getAsJsonArray("columns").size());
        assertEquals(TILE_COUNT, canonical.getAsJsonArray("tiles").size());
        assertTrue(canonical.getAsJsonObject("bounds").get("found").getAsJsonPrimitive().isNumber());
        assertTrue(canonical.getAsJsonArray("columns").get(0).getAsJsonObject()
                .get("x").getAsJsonPrimitive().isNumber());
        int canonicalBytes = jsonUtf8Bytes(canonical);
        assertTrue(canonicalBytes >= 1_500_000 && canonicalBytes <= 1_700_000);
        String exact = canonical.toString();

        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter()
                    .present("r_terrain", canonical, SHAPE, "", budget);
            String text = presentation.modelText();
            assertEncodedBudget(text, budget);
            assertFalse(presentation.complete());
            assertEquals(6, presentation.cardinality());
            assertTrue(text.contains("schema:"), text);
            assertTrue(text.contains("columns") && text.contains(Integer.toString(COLUMN_COUNT)), text);
            assertTrue(text.contains("tiles") && text.contains(Integer.toString(TILE_COUNT)), text);
            assertTrue(text.contains("properties"), text);
            assertTrue(text.contains("size: " + canonicalBytes + " UTF-8 byte(s)"), text);
            assertEquals(exact, canonical.toString());
        }
    }

    @Test
    void countsJsonEscapingAndUtf8RatherThanOnlyRawModelTextBytes() {
        JsonObject value = escapingHeavyResult();
        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter()
                    .present("r_escaped", value, SHAPE, "", budget);
            assertEncodedBudget(presentation.modelText(), budget);
            assertTrue(presentation.modelText().contains(BEFORE));
            assertTrue(presentation.modelText().contains(AFTER));
            assertFalse(presentation.complete());
        }
    }

    @Test
    void oversizedSuffixCannotEscapeTheEncodedBudgetOrHideTheCanonicalHandle() {
        JsonObject value = new JsonObject();
        value.addProperty("answer", 42);
        String suffix = "input coverage: CLIENT_VISIBLE PARTIAL \"quoted\" 界😀\n".repeat(5_000);
        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter()
                    .present("r_coverage", value, SHAPE, suffix, budget);
            assertEncodedBudget(presentation.modelText(), budget);
            assertTrue(presentation.modelText().contains("r_coverage"));
            assertTrue(presentation.modelText().contains("answer: 42"));
        }
    }

    @Test
    void projectionLeavesWorkspaceOpenAndSelectionFullyExactUntilRequestCloses() {
        JsonObject supplied = largeWorldResult();
        String exact = supplied.toString();
        AgentResultWorkspaceRegistry registry = new AgentResultWorkspaceRegistry();
        String request = "world-budget-canonical";
        AgentResultWorkspace workspace = registry.open(request);
        String handle = workspace.store(supplied, SHAPE);
        supplied.addProperty("after_bulk", "caller mutation");

        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter()
                    .present(handle, workspace.open(handle), SHAPE, "", budget);
            assertEncodedBudget(presentation.modelText(), budget);
            assertEquals(exact, workspace.open(handle).toString());
            assertEquals(exact, workspace.select(List.of(handle)).get(handle).toString());
            assertEquals(SHAPE, workspace.selectShapes(List.of(handle)).get(handle));
            assertSame(workspace.select(List.of(handle)).get(handle),
                    workspace.select(List.of(handle)).get(handle));
            presentation.preview().getAsJsonObject().addProperty("after_bulk", "preview mutation");
            workspace.open(handle).getAsJsonObject().addProperty("after_bulk", "open mutation");
            assertEquals(exact, workspace.open(handle).toString());
        }

        registry.close(request);
        assertEquals(0, registry.activeCount());
        assertEquals(0, workspace.size());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> workspace.open(handle)).code());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> workspace.select(List.of(handle))).code());
        AgentResultWorkspace nextRequest = registry.open(request);
        assertEquals("workspace_handle_unavailable", assertThrows(WorkspaceException.class,
                () -> nextRequest.open(handle)).code());
        assertEquals("workspace_handle_unavailable", assertThrows(WorkspaceException.class,
                () -> nextRequest.select(List.of(handle))).code());
        registry.closeAll();
    }

    @Test
    void lazyWorkspaceLeaseCanExpandBeyondItsInitialPartialViewAndCannotResurrectAfterClose() {
        JsonObject canonical = scalarSummaryResult();
        String exact = canonical.toString();
        AgentResultWorkspaceRegistry registry = new AgentResultWorkspaceRegistry();
        String request = "lazy-large-canonical";
        AgentResultWorkspace workspace = registry.open(request);
        String handle = workspace.store(canonical, SHAPE);
        var initial = new JavascriptResultPresenter().present(handle, canonical, SHAPE, "", 32_768);
        assertFalse(initial.complete());
        assertTrue(jsonUtf8Bytes(initial.preview()) < jsonUtf8Bytes(canonical));
        ModelResultView initialView = new ModelResultView(handle, initial.type(), initial.cardinality(),
                initial.canonicalUtf8Bytes(), false, "current request only");
        var source = workspace.modelSource(initialView, "");

        for (int budget : new int[] {1_024, 4_096}) {
            JsonElement small = source.project(budget);
            assertTrue(jsonUtf8Bytes(small) <= budget);
            assertTrue(small.getAsString().contains("scope: preview"));
            assertFalse(small.getAsString().contains("scope: complete"));
        }
        int upper = source.projectionSizeUpperBound();
        assertTrue(upper > 32_768, "capacity search must not stop at the partial initial view");
        assertTrue(upper > jsonUtf8Bytes(canonical));
        JsonElement complete = source.project(upper);
        assertTrue(jsonUtf8Bytes(complete) <= upper);
        assertTrue(complete.getAsString().contains("scope: complete"));
        assertFalse(complete.getAsString().contains("scope: preview"));
        assertTrue(complete.getAsString().contains("cardinality: " + SUMMARY_FIELD_COUNT));
        for (int index = 0; index < SUMMARY_FIELD_COUNT; index++) {
            assertTrue(complete.getAsString().contains("summary_" + index + ": "
                            + canonical.get("summary_" + index).toString() + "\n"),
                    "full lazy projection must include exact scalar summary " + index);
        }
        assertEquals(exact, workspace.open(handle).toString());
        assertEquals(exact, workspace.select(List.of(handle)).get(handle).toString());

        registry.close(request);
        assertEquals(0, registry.activeCount());
        // A capacity hint uses only the descriptor. It must not project or reopen the workspace.
        assertEquals(upper, source.projectionSizeUpperBound());
        assertEquals(0, registry.activeCount());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> source.project(1_024)).code());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> source.project(upper)).code());
        assertEquals(0, registry.activeCount());
        AgentResultWorkspace newScope = registry.open(request);
        assertEquals("workspace_handle_unavailable", assertThrows(WorkspaceException.class,
                () -> newScope.open(handle)).code());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> source.project(upper)).code());
        registry.closeAll();
    }

    @Test
    void typedScalarProjectionDistinguishesNullNumbersAndEscapedStringContent() {
        JsonObject canonical = new JsonObject();
        canonical.add("actual_null", JsonNull.INSTANCE);
        canonical.addProperty("string_null", "null");
        canonical.addProperty("actual_number", 3);
        canonical.addProperty("string_number", "3");
        canonical.addProperty("actual_newline", "line\nnext");
        canonical.addProperty("literal_backslash", "line\\nnext");
        canonical.addProperty("ordinary_id", "minecraft:stone");
        String exact = canonical.toString();
        var presentation = new JavascriptResultPresenter()
                .present("r_typed", canonical, SHAPE, "", 4_096);
        String text = presentation.modelText();
        assertEncodedBudget(text, 4_096);
        assertTrue(presentation.complete());
        assertEquals(canonical, presentation.preview());
        assertTrue(text.contains("actual_null: null\n"), text);
        assertTrue(text.contains("string_null: " + canonical.get("string_null") + "\n"), text);
        assertTrue(text.contains("actual_number: 3\n"), text);
        assertTrue(text.contains("string_number: " + canonical.get("string_number") + "\n"), text);
        assertTrue(text.contains("actual_newline: " + canonical.get("actual_newline") + "\n"), text);
        assertTrue(text.contains("literal_backslash: " + canonical.get("literal_backslash") + "\n"), text);
        assertTrue(text.contains("ordinary_id: " + canonical.get("ordinary_id").toString() + "\n"), text);
        assertEquals(exact, canonical.toString());
    }

    @Test
    void minimumBudgetFallbackKeepsFiniteMixedInputCoverageHandleAndPartialScope() {
        String coverage = "input coverage: CLIENT_VISIBLE PARTIAL; INTEGRATION_API COMPLETE";
        JsonObject canonical = largeWorldResult();
        for (int budget : new int[] {256, 512}) {
            var presentation = new JavascriptResultPresenter()
                    .present("r_cov", canonical, SHAPE, coverage, budget);
            String text = presentation.modelText();
            assertEncodedBudget(text, budget);
            assertFalse(presentation.complete());
            assertTrue(text.contains("result: r_cov (current request only)"), text);
            assertTrue(text.contains("scope: preview"), text);
            assertTrue(text.contains(coverage), text);
            assertTrue(text.contains("size: " + jsonUtf8Bytes(canonical) + " UTF-8 byte(s)"), text);
            assertFalse(text.contains("scope: complete"), text);
            assertFalse(text.contains("input coverage: complete"), text);
            assertFalse(text.contains("answer from this complete result"), text);
        }
    }

    @Test
    void callerAllocationCanSelectABoundedPreviewOrTheCompleteCanonicalArray() {
        JsonObject canonical = terrainResult();
        String exact = canonical.toString();
        AgentResultWorkspaceRegistry registry = new AgentResultWorkspaceRegistry();
        String request = "bulk-control-plane";
        AgentResultWorkspace workspace = registry.open(request);
        String handle = workspace.store(canonical, SHAPE);
        var initial = new JavascriptResultPresenter().present(handle, canonical, SHAPE, "", 32_768);
        var view = new ModelResultView(handle, initial.type(), initial.cardinality(),
                initial.canonicalUtf8Bytes(), false, "current request only");
        var source = workspace.modelSource(view, "");
        int upper = source.projectionSizeUpperBound();
        assertTrue(upper > 32_768);
        JsonElement modelValue = source.project(4_096);
        String text = modelValue.getAsString();
        assertTrue(jsonUtf8Bytes(modelValue) <= 4_096,
                "a small explicit allocation produces a useful bounded control-plane view");
        assertTrue(text.contains("scope: preview"), text);
        assertFalse(text.contains("scope: complete"), text);
        assertTrue(text.contains("columns: array[" + COLUMN_COUNT + "]"), text);
        assertTrue(text.contains("tiles: array[" + TILE_COUNT + "]"), text);
        assertTrue(text.contains("properties"), text);
        assertTrue(text.contains("minecraft:smooth_stone"), text);
        assertTrue(text.contains("workspace.open(\"" + handle + "\")"), text);
        // Artifact semantics are producer-owned and do not change when capacity grows.
        JsonElement preferred = source.project(upper);
        assertTrue(jsonUtf8Bytes(preferred) <= upper);
        assertTrue(preferred.getAsString().contains("scope: preview"));
        assertFalse(preferred.getAsString().contains("scope: complete"));
        assertTrue(preferred.getAsString().contains("representative sample:"));
        // An explicitly selected answer source is a separate producer contract, not an
        // automatic array-size or available-capacity heuristic.
        var answer = workspace.answerModelSource(view, "");
        int answerUpper = answer.projectionSizeUpperBound();
        JsonElement complete = answer.project(answerUpper);
        assertTrue(jsonUtf8Bytes(complete) <= answerUpper);
        assertTrue(complete.getAsString().contains("scope: complete"));
        assertFalse(complete.getAsString().contains("scope: preview"));
        assertTrue(complete.getAsString().contains("cardinality: 6"));
        assertEquals(exact, workspace.open(handle).toString());
        JsonObject selected = workspace.select(List.of(handle)).get(handle).getAsJsonObject();
        assertEquals(exact, selected.toString());
        assertEquals(COLUMN_COUNT, selected.getAsJsonArray("columns").size());
        assertEquals(TILE_COUNT, selected.getAsJsonArray("tiles").size());
        registry.close(request);
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> source.project(upper)).code());
        assertEquals(0, registry.activeCount());
    }

    @Test
    void optInPureShapeFixtureKeepsCanonicalValuesAndBoundedModelReceipts() throws Exception {
        String fixturePath = System.getenv("OPENALLAY_TEST_RESULT_FIXTURE");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank(),
                "Set OPENALLAY_TEST_RESULT_FIXTURE to an explicit pure JSON fixture path");
        String fixtureJson = Files.readString(Path.of(fixturePath), StandardCharsets.UTF_8);
        JsonElement canonical = dev.openallay.json.JsonTrees.parse(fixtureJson);
        String canonicalExact = canonical.toString();
        JsonObject object = canonical.getAsJsonObject();
        assertEquals(COLUMN_COUNT, object.getAsJsonArray("columns").size());
        assertEquals(TILE_COUNT, object.getAsJsonArray("tiles").size());
        AgentResultWorkspaceRegistry registry = new AgentResultWorkspaceRegistry();
        String request = "opt-in-pure-shape-fixture";
        AgentResultWorkspace workspace = registry.open(request);
        String handle = workspace.store(canonical, SHAPE);
        var initial = new JavascriptResultPresenter().present(handle, canonical, SHAPE, "", 32_768);
        var source = workspace.modelSource(new ModelResultView(handle, initial.type(),
                initial.cardinality(), initial.canonicalUtf8Bytes(), false, "current request only"), "");

        for (int budget : BUDGETS) {
            JsonElement modelValue = source.project(budget);
            assertTrue(jsonUtf8Bytes(modelValue) <= budget);
            String text = modelValue.getAsString();
            assertTrue(text.contains("scope: preview"));
            assertFalse(text.contains("scope: complete"));
            assertTrue(text.contains("schema:"));
            assertTrue(text.contains("columns: array[" + COLUMN_COUNT + "]"));
            assertTrue(text.contains("tiles: array[" + TILE_COUNT + "]"));
            assertTrue(text.contains("size: " + jsonUtf8Bytes(canonical) + " UTF-8 byte(s)"));
            JsonElement selected = workspace.select(List.of(handle)).get(handle);
            assertEquals(canonical, selected);
            assertEquals(canonicalExact, selected.toString());
            assertEquals(canonicalExact, workspace.open(handle).toString());
            assertEquals(canonicalExact, canonical.toString());
        }
        registry.close(request);
        assertEquals(0, registry.activeCount());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> source.project(1_024)).code());
        assertEquals("workspace_closed", assertThrows(WorkspaceException.class,
                () -> workspace.select(List.of(handle))).code());
        assertEquals(0, registry.activeCount());
    }

    @Test
    void allAnswerSizedRowsRemainCompleteWithoutAnArbitraryRowCap() {
        JsonArray rows = new JsonArray();
        for (int index = 0; index < 80; index++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", "row_" + index);
            row.addProperty("count", index);
            rows.add(row);
        }
        var presentation = new JavascriptResultPresenter()
                .present("r_small_80", rows, SHAPE, "", 32_768);

        assertEncodedBudget(presentation.modelText(), 32_768);
        assertTrue(presentation.complete());
        assertEquals(80, presentation.cardinality());
        assertEquals(0, presentation.omittedRows());
        assertEquals(0, presentation.omittedFields());
        assertEquals(rows, presentation.preview());
        assertTrue(presentation.modelText().contains("scope: complete"));
        for (int index = 0; index < 80; index++) {
            assertTrue(presentation.modelText().contains("id: "
                    + rows.get(index).getAsJsonObject().get("id").toString() + "\n"));
        }
    }

    @Test
    void unrestrictedRunJavascriptKeepsJavaPermissionsButUsesTheSameBoundedProjection() {
        String javaSource = "return Java.type('java.lang.Integer').parseInt('42');";
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        assertThrows(RuntimeException.class, () -> runtime.execute(
                javaSource, Map.of(), Map.of(), new CancellationSignal()));
        assertEquals(42, runtime.execute(javaSource, Map.of(), Map.of(), Map.of(),
                new CancellationSignal(), null, null, true).value().getAsInt());

        AgentResultWorkspaceRegistry workspaces = new AgentResultWorkspaceRegistry();
        RunJavascriptTool tool = new RunJavascriptTool(runtime, MinecraftAgentHostGraph::new,
                workspaces, new JavascriptResultPresenter());
        ToolInvocationContext base = JavascriptAgentTestFixtures.context("unrestricted-budget");
        ToolInvocationContext authorized = new ToolInvocationContext(
                base.correlationId(), base.capturedAt(), base.caller(), base.player(),
                base.registries(), base.recipes(), base.observableGameState(), base.metrics(), true);
        String source = """
                const Integer = Java.type('java.lang.Integer');
                return {
                  answer: Integer.parseInt('42'),
                  bulk: Array.from({length: 8704}, (_, index) => ({
                    id: 'minecraft:stone',
                    position: {x: index % 128, y: 64, z: Math.floor(index / 128)},
                    relative: {x: index % 128, y: 0, z: Math.floor(index / 128)},
                    state: {facing: 'north', waterlogged: 'false'},
                    fluid: 'minecraft:empty',
                    blockEntity: false
                  })),
                  conclusion: 'java-access-retained'
                };
                """;
        ToolResult.Success<?> rawSuccess = assertInstanceOf(ToolResult.Success.class,
                tool.invokeAsync(authorized, new RunJavascriptTool.Input(source, List.of()),
                        new CancellationSignal()).join());
        RunJavascriptTool.Output output = assertInstanceOf(RunJavascriptTool.Output.class,
                rawSuccess.value());
        JsonElement canonical = workspaces.open(base.correlationId()).open(output.handle());
        assertEquals(42, canonical.getAsJsonObject().get("answer").getAsInt());
        assertEquals(BLOCK_COUNT, canonical.getAsJsonObject().getAsJsonArray("bulk").size());
        assertEquals("java-access-retained", canonical.getAsJsonObject().get("conclusion").getAsString());
        assertFalse(output.complete());
        assertEncodedBudget(output.modelText(), JavascriptResultPresenter.MODEL_TEXT_BYTE_BUDGET);
        assertTrue(output.modelText().contains("answer: 42"));
        assertTrue(output.modelText().contains("java-access-retained"));
        var choice = dev.openallay.tool.result.NaturalModelView.artifact(canonical);
        var ordinary = new JavascriptResultPresenter().presentChosen(output.handle(), choice.value(), canonical,
                output.modelView(), SHAPE, "");
        assertEquals(ordinary.modelText(), output.modelText());

        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter()
                    .present(output.handle(), canonical, SHAPE, "", budget);
            JsonObject normalized = new JsonObject();
            normalized.addProperty("status", "success");
            normalized.addProperty("modelText", presentation.modelText());
            normalized.add("value", canonical);
            assertTrue(jsonUtf8Bytes(new AgentToolResult(RunJavascriptTool.ID, normalized, false)
                    .modelValue(budget)) <= budget);
            assertEquals(canonical, workspaces.open(base.correlationId()).open(output.handle()));
        }
        tool.closeRequestScope(base.correlationId());
        assertEquals(0, workspaces.activeCount());
    }

    private static void assertEncodedBudget(String text, int budget) {
        int actual = encodedModelTextUtf8Bytes(text);
        assertTrue(actual <= budget,
                () -> "encoded model text is " + actual + " UTF-8 bytes; budget is " + budget);
    }
}
