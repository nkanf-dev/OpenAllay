package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.ui.hud.GuideHudToolCards;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideToolStepFlowTest {
    private static final UUID REQUEST = UUID.fromString("590f1b55-4d2f-4b35-9b48-a714428d20ae");

    @Test void configuredExpandedDefaultAndSingleStepFoldsNeverHideOtherCalls() {
        var one = scalar("one", 1);
        var two = scalar("two", 2);
        var state = new GuideToolStepFlowState();
        state.synchronize("actor:session:world", List.of(one, two), false);
        assertTrue(state.expanded(one));
        assertTrue(state.expanded(two));
        state.toggle(one);
        assertFalse(state.expanded(one));
        assertTrue(state.expanded(two));
        state.toggle(one);
        assertTrue(state.expanded(one));
        assertEquals("tool:" + REQUEST + ":one", GuideToolStepFlowState.id(one));
    }

    @Test void taskBatchOnlyChangesCurrentStepsAndNewCallsUseDefaults() {
        var one = scalar("one", 1);
        var two = scalar("two", 2);
        var next = scalar("next", 3);
        var state = new GuideToolStepFlowState();
        state.synchronize("owner", List.of(one, two), false);
        state.toggleTask(List.of(one, two));
        assertFalse(state.expanded(one));
        assertFalse(state.expanded(two));
        state.synchronize("owner", List.of(one, two, next), false);
        assertTrue(state.expanded(next), "a new execution is visible after a previous batch fold");
        state.toggleTask(List.of(one, two, next));
        assertFalse(state.expanded(next));
        state.toggleTask(List.of(one, two, next));
        assertTrue(state.expanded(one));
        assertTrue(state.expanded(next));
        var newTask = new GuideUiRow.Tool(UUID.randomUUID(), 0, one.activity(), one.detail());
        assertTrue(state.expanded(newTask));
    }

    @Test void ownerDefaultAndRemovedStepChangesDiscardEphemeralChoices() {
        var one = scalar("one", 1);
        var state = new GuideToolStepFlowState();
        state.synchronize("actor-A:session:world-A", List.of(one), false);
        state.toggle(one);
        assertFalse(state.expanded(one));
        assertTrue(state.synchronize("actor-A:session:world-B", List.of(one), false));
        assertTrue(state.expanded(one));
        state.synchronize("actor-A:session:world-B", List.of(one), true);
        assertFalse(state.expanded(one));
        state.toggle(one);
        assertTrue(state.expanded(one));
        state.synchronize("actor-A:session:world-B", List.of(one), false);
        assertTrue(state.expanded(one), "Reset/default change clears view-local overrides");
        state.toggle(one);
        state.synchronize("actor-A:session:world-B", List.of(), false);
        state.synchronize("actor-A:session:world-B", List.of(one), false);
        assertTrue(state.expanded(one), "removed calls do not leave stale fold IDs");
        state.toggle(one);
        state.resetChoices();
        assertTrue(state.expanded(one));
    }

    @Test void partialPlayerResultHasScopeWithoutOpaqueWorkspaceOrFieldCountHeaders() {
        var tool = tool("partial", GuideToolStatus.SUCCEEDED, """
                {"status":"success","value":{"resultType":"object","cardinality":12,
                "viewKind":"KEY_VALUE","preview":{"placed":318,"remaining":17,"height":3.125},
                "complete":false,"omittedFields":9,"handle":"r_private_workspace","modelText":"raw envelope"}}
                """);
        assertTrue(GuideToolStepFlowPresenter.partial(tool));
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_VALUE_PREVIEW)),
                GuideToolStepFlowPresenter.messages(tool));
        var body = GuideHudToolCards.project(tool, key -> "Result");
        assertTrue(body.document().fallbackText().contains("height: 3.125"));
        assertTrue(body.document().fallbackText().contains("placed: 318"));
        assertFalse(body.document().fallbackText().contains("r_private_workspace"));
        assertFalse(body.document().fallbackText().contains("raw envelope"));
        assertFalse(GuideToolStepFlowPresenter.messages(tool).stream().anyMatch(message ->
                message.key() == GuideToolMessage.Key.ANALYSIS_FIELDS_COMPLETE
                        || message.key() == GuideToolMessage.Key.ANALYSIS_FIELDS_PREVIEW));
        var typed = assertInstanceOf(GuideDetailCard.KeyValue.class, tool.detail().cards().getFirst());
        assertEquals("3.125", typed.entries().stream().filter(cell -> cell.key().equals("height"))
                .findFirst().orElseThrow().value());
    }

    @Test void failedAndMissingResultsKeepRealFactsWithoutInventingNativeCards() {
        var failure = tool("failed", GuideToolStatus.FAILED,
                "{\"status\":\"failure\",\"code\":\"javascript_error\",\"message\":\"ReferenceError at line 17\"}");
        assertEquals("javascript_error", failure.detail().failure().orElseThrow().code());
        assertEquals("ReferenceError at line 17", failure.detail().failure().orElseThrow().message());
        assertTrue(GuideToolStepFlowPresenter.messages(failure).isEmpty());
        assertTrue(GuideHudToolCards.project(failure, key -> key).document().blocks().isEmpty());
        var activity = new GuideToolActivity("missing", 0, "openallay:run_javascript",
                GuideToolStatus.RUNNING, null, List.of(), List.of());
        var missing = new GuideUiRow.Tool(REQUEST, 0, activity,
                GuideToolDetailPresenter.project(activity, false).forRequest(true));
        assertEquals(GuideToolDisplayStatus.NO_RESULT_RECORDED, missing.detail().displayStatus());
        assertEquals(List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_DETAIL_NOT_STORED)),
                GuideToolStepFlowPresenter.messages(missing));
        assertTrue(GuideHudToolCards.project(missing, key -> key).document().blocks().isEmpty());
    }

    @Test void itemAndTableBodiesKeepAllTypedResultsNotThreeSummaryLines() {
        var items = tool("items", GuideToolStatus.SUCCEEDED, """
                {"status":"success","value":{"resultType":"array","cardinality":2,
                "viewKind":"ITEM","preview":[{"id":"minecraft:apple","count":128},
                {"id":"minecraft:bread","count":64}],"complete":true}}
                """);
        var body = GuideHudToolCards.project(items, key -> "Items");
        var nodes = body.document().blocks().stream().filter(SemanticBlock.Component.class::isInstance)
                .map(SemanticBlock.Component.class::cast).toList();
        assertEquals(2, nodes.size());
        assertEquals(128, ((RichComponent.ItemRow) nodes.getFirst().component()).items().getFirst().count());
        assertTrue(GuideToolStepFlowPresenter.messages(items).isEmpty());
        var rows = new com.google.gson.JsonArray();
        for (int i = 0; i < 40; i++) {
            var row = new com.google.gson.JsonObject();
            row.addProperty("step", i); row.addProperty("blocks", i + 100); rows.add(row);
        }
        var value = new com.google.gson.JsonObject();
        value.addProperty("viewKind", "TABLE"); value.addProperty("complete", true); value.add("preview", rows);
        var normalized = new com.google.gson.JsonObject();
        normalized.addProperty("status", "success"); normalized.add("value", value);
        var tableTool = tool("table", GuideToolStatus.SUCCEEDED, normalized.toString());
        var table = (SemanticBlock.Table) GuideHudToolCards.project(tableTool, key -> "Result").document().blocks().getLast();
        assertEquals(40, table.rows().size());
    }

    @Test void widthAwareBodyLayoutAndFoldedHeaderUseOneExactGeometry() {
        var tool = tool("receipt", GuideToolStatus.SUCCEEDED, """
                {"status":"success","value":{"resultType":"object","cardinality":2,
                "viewKind":"KEY_VALUE","preview":{"placed":12345,"remaining":12},"complete":true}}
                """);
        var body = GuideHudToolCards.project(tool, key -> "Receipt").document();
        var engine = new SemanticLayoutEngine();
        var wide = engine.layout(body, 260, MEASURER);
        var narrow = engine.layout(body, 60, MEASURER);
        assertTrue(narrow.height() >= wide.height());
        for (int width : List.of(40, 90, 180, 320)) {
            var expanded = GuideToolStepFlowGeometry.measure(9, 30, width, 20, 10, narrow.height(), true, 8);
            var collapsed = GuideToolStepFlowGeometry.measure(9, 30, width, 20, 10, 0, false, 8);
            assertTrue(expanded.rowHeight() > collapsed.rowHeight());
            assertEquals(expanded.card().height() + 8, expanded.rowHeight());
            assertTrue(expanded.title().right() <= expanded.toggle().x());
            assertTrue(expanded.body().right() <= expanded.card().right());
            assertFalse(expanded.toggle().contains(expanded.body().x(), expanded.body().y()));
            assertTrue(expanded.detail().y() >= expanded.body().bottom());
            assertEquals(0, collapsed.body().height());
            assertTrue(collapsed.card().height() > 0, "collapsed cards retain their own virtual row");
        }
    }

    private static final SemanticLayoutEngine.Measurer MEASURER = new SemanticLayoutEngine.Measurer() {
        public int width(String text, SemanticLayout.Style style) { return text.length() * 6; }
        public int lineHeight(SemanticLayout.Kind kind) { return 10; }
    };
    private static GuideUiRow.Tool scalar(String id, int number) {
        return tool(id, GuideToolStatus.SUCCEEDED, """
                {"status":"success","value":{"resultType":"number","cardinality":1,
                "viewKind":"SCALAR","preview":%s,"complete":true}}
                """.formatted(number));
    }
    private static GuideUiRow.Tool tool(String id, GuideToolStatus status, String normalized) {
        var activity = new GuideToolActivity(id, 0, "openallay:run_javascript", status,
                JsonParser.parseString(normalized).getAsJsonObject(), List.of(), List.of());
        return new GuideUiRow.Tool(REQUEST, 0, activity, GuideToolDetailPresenter.project(activity, false).forRequest(true));
    }
}
