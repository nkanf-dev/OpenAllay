package dev.openallay.agent.tool;

import static dev.openallay.testing.JavascriptResultBudgetFixtures.AFTER;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.SUMMARY_FIELD_COUNT;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.BEFORE;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.encodedModelTextUtf8Bytes;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.escapingHeavyResult;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.jsonUtf8Bytes;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.largeWorldResult;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.terrainResult;
import static dev.openallay.testing.JavascriptResultBudgetFixtures.scalarSummaryResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.script.workspace.AgentResultWorkspace;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.tool.ModelResultSource;
import dev.openallay.tool.ModelResultView;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ModelToolResultBudgetTest {
    private static final int[] BUDGETS = {1_024, 4_096, 32_768};

    @Test
    void genericLargeSuccessIsBoundedWithoutChangingCanonicalNormalizedJson() {
        JsonObject normalized = success(largeWorldResult());
        String exact = normalized.toString();
        AgentToolResult result = new AgentToolResult("test:world", normalized, false);

        for (int budget : BUDGETS) {
            JsonElement modelValue = result.modelValue(budget);
            assertEncodedBudget(modelValue, budget);
            String text = modelValue.getAsString();
            assertTrue(text.contains(BEFORE), text);
            assertTrue(text.contains(AFTER), text);
            assertTrue(text.contains("schema:"), text);
            assertTrue(text.contains("scope: preview"), text);
            assertEquals(exact, result.normalized().toString());
        }
        assertFalse(result.failure());
    }

    @Test
    void terrainArrayCardinalitiesCannotOverflowTheFinalProviderFacingValue() {
        JsonObject normalized = success(terrainResult());
        String exact = normalized.toString();
        AgentToolResult result = new AgentToolResult("test:terrain", normalized, false);
        for (int budget : BUDGETS) {
            JsonElement modelValue = result.modelValue(budget);
            assertEncodedBudget(modelValue, budget);
            assertTrue(modelValue.getAsString().contains("columns"));
            assertTrue(modelValue.getAsString().contains("16641"));
            assertTrue(modelValue.getAsString().contains("tiles"));
            assertTrue(modelValue.getAsString().contains("81"));
            assertEquals(exact, result.normalized().toString());
        }
    }

    @Test
    void successBudgetCountsEscapedQuotesNewlinesAndMultibyteText() {
        JsonObject normalized = success(escapingHeavyResult());
        String exact = normalized.toString();
        AgentToolResult result = new AgentToolResult("test:escaped", normalized, false);

        for (int budget : BUDGETS) {
            JsonElement modelValue = result.modelValue(budget);
            assertEncodedBudget(modelValue, budget);
            assertTrue(modelValue.getAsString().contains(BEFORE));
            assertTrue(modelValue.getAsString().contains(AFTER));
            assertEquals(exact, result.normalized().toString());
        }
    }

    @Test
    void existingJavascriptProjectionSurvivesTheFinalModelValueBoundaryWithinBudget() {
        JsonObject canonical = largeWorldResult();
        String handle = "r_provider_world";
        for (int budget : BUDGETS) {
            var presentation = new JavascriptResultPresenter().present(
                    handle, canonical,
                    JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC), "", budget);
            JsonObject output = new JsonObject();
            output.addProperty("handle", handle);
            output.addProperty("complete", presentation.complete());
            output.add("preview", presentation.preview());
            JsonObject normalized = success(output);
            normalized.addProperty("modelText", presentation.modelText());
            AgentToolResult result = new AgentToolResult("openallay:run_javascript", normalized, false);

            assertEncodedBudget(result.modelValue(budget), budget);
            assertEquals(new JsonPrimitive(presentation.modelText()), result.modelValue(budget));
            assertTrue(result.modelValue(budget).getAsString().contains(BEFORE));
            assertTrue(result.modelValue(budget).getAsString().contains(AFTER));
            assertEquals(normalized, result.normalized());
        }
    }

    @Test
    void normalizedRunJavascriptOutputKeepsCanonicalModelViewWhenItsPreviewIsReprojected() {
        JsonArray canonical = terrainResult().getAsJsonArray("columns");
        String canonicalExact = canonical.toString();
        int canonicalBytes = jsonUtf8Bytes(canonical);
        String handle = "r_normalized_world_columns";
        var initial = new JavascriptResultPresenter().present(handle, canonical,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC), "", 32_768);
        assertFalse(initial.complete());
        assertTrue(initial.preview().getAsJsonArray().size() < canonical.size());
        assertTrue(encodedModelTextUtf8Bytes(initial.modelText()) > 1_024);
        RunJavascriptTool.Output output = new RunJavascriptTool.Output(
                handle, initial.type(), initial.cardinality(), initial.fields(), initial.preview(),
                initial.modelText(), initial.viewKind(), initial.complete(), initial.omittedRows(),
                initial.omittedFields(),
                new ModelResultView(handle, initial.type(), initial.cardinality(),
                        initial.canonicalUtf8Bytes(), initial.complete(), "current request only"),
                0, List.of(), List.of());
        JsonObject normalized = new ToolResultNormalizer(new Gson()).normalize(
                new ToolResult.Success<>(output), RunJavascriptTool.Output.class);
        String exact = normalized.toString();
        JsonObject modelView = normalized.getAsJsonObject("value").getAsJsonObject("modelView");
        assertEquals(canonical.size(), modelView.get("cardinality").getAsLong());
        assertEquals(canonicalBytes, modelView.get("canonicalUtf8Bytes").getAsLong());
        assertFalse(modelView.get("complete").getAsBoolean());
        AgentToolResult result = new AgentToolResult(RunJavascriptTool.ID, normalized, false);

        for (int budget : new int[] {1_024, 4_096}) {
            JsonElement modelValue = result.modelValue(budget);
            assertEncodedBudget(modelValue, budget);
            String text = modelValue.getAsString();
            assertTrue(text.contains("result: " + handle + " (current request only)"), text);
            assertTrue(text.contains("cardinality: " + canonical.size()), text);
            assertTrue(text.contains("size: " + canonicalBytes + " UTF-8 byte(s)"), text);
            assertTrue(text.contains("scope: preview"), text);
            assertFalse(text.contains("scope: complete"), text);
            assertFalse(text.contains("answer from this complete result"), text);
            assertTrue(text.contains("workspace.open(\"" + handle + "\")"), text);
            assertEquals(exact, result.normalized().toString());
            assertEquals(canonicalExact, canonical.toString());
        }
    }

    @Test
    void lazyLeasePreservesAllCallerDataFieldsWithoutScanningOrChangingCanonicalFacts() {
        String credentialLikeValue = "syntheticCallerOwnedValue";
        String headerValue = "syntheticCallerAuthorizationValue";
        JsonObject canonical = new JsonObject();
        canonical.addProperty("password", credentialLikeValue);
        canonical.addProperty("authorizationHeader", headerValue);
        canonical.addProperty("reasoning", "caller-owned-game-data-not-provider-reasoning");
        JsonObject summaries = scalarSummaryResult();
        summaries.entrySet().forEach(entry -> canonical.add(entry.getKey(), entry.getValue()));
        String canonicalExact = canonical.toString();
        try (AgentResultWorkspace workspace = new AgentResultWorkspace()) {
            String handle = workspace.store(canonical);
            var initial = new JavascriptResultPresenter().present(handle, canonical,
                    JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC), "", 32_768);
            assertFalse(initial.complete());
            ModelResultView view = new ModelResultView(handle, initial.type(), initial.cardinality(),
                    initial.canonicalUtf8Bytes(), false, "current request only");
            ModelResultSource live = workspace.modelSource(view, "");
            AtomicInteger projects = new AtomicInteger();
            ModelResultSource counted = new ModelResultSource() {
                @Override public JsonElement project(int maximumUtf8Bytes) {
                    projects.incrementAndGet();
                    return live.project(maximumUtf8Bytes);
                }
                @Override public int projectionSizeUpperBound() { return live.projectionSizeUpperBound(); }
            };
            RunJavascriptTool.Output output = new RunJavascriptTool.Output(
                    handle, initial.type(), initial.cardinality(), initial.fields(), initial.preview(),
                    initial.modelText(), initial.viewKind(), initial.complete(), initial.omittedRows(),
                    initial.omittedFields(), view, 0, List.of(), List.of());
            JsonObject normalized = new ToolResultNormalizer(new Gson()).normalize(
                    new ToolResult.Success<>(output), RunJavascriptTool.Output.class);
            String normalizedExact = normalized.toString();
            AgentToolResult result = new AgentToolResult(RunJavascriptTool.ID, normalized, false, counted);
            int upper = result.projectionSizeUpperBound();
            assertEquals(0, projects.get(), "a source size hint does not invoke projection or scan values");
            assertTrue(upper > 32_768, "initial transport view does not discard scalar findings");
            for (int budget : new int[] {1_024, 4_096, upper}) {
                int before = projects.get();
                JsonElement projected = result.modelValue(budget);
                assertEquals(before + 1, projects.get());
                assertEncodedBudget(projected, budget);
                String text = projected.getAsString();
                assertTrue(text.contains("password: " + new JsonPrimitive(credentialLikeValue)), text);
                assertTrue(text.contains("authorizationHeader: " + new JsonPrimitive(headerValue)), text);
                assertTrue(text.contains("reasoning: \"caller-owned-game-data-not-provider-reasoning\""), text);
                assertFalse(text.contains("[REDACTED]"), text);
                if (budget == upper) {
                    assertTrue(text.contains("scope: complete"));
                    for (int index = 0; index < SUMMARY_FIELD_COUNT; index++)
                        assertTrue(text.contains("summary_" + index + ": " + summaries.get("summary_" + index) + "\n"));
                } else {
                    assertTrue(text.contains("scope: preview"));
                    assertFalse(text.contains("scope: complete"));
                }
                assertEquals(normalizedExact, result.normalized().toString());
                assertEquals(canonicalExact, workspace.open(handle).toString());
            }
            assertTrue(result.modelValue().getAsString().contains(credentialLikeValue));
            assertTrue(result.normalized().toString().contains(headerValue));
            assertEquals(canonicalExact, workspace.select(List.of(handle)).get(handle).toString());
        }
    }

    @Test
    void opaqueHugeSingleLineIsOnlyAnArchiveReceiptNotAHalfNumberOrLiveWorkspace() {
        String originalText = "cardinality: " + "1234567890".repeat(20_000)
                + " result: r_past_archive workspace.open(\"r_past_archive\")";
        JsonPrimitive original = new JsonPrimitive(originalText);
        String exact = original.toString();
        int originalBytes = originalText.getBytes(StandardCharsets.UTF_8).length;
        String receipt = "scope: archived preview\nsize: " + originalBytes
                + " UTF-8 byte(s) of original model text\n"
                + "omitted: remainder stays in the stored transcript; no live workspace is implied";

        for (int budget : BUDGETS) {
            JsonElement projection = AgentToolResult.boundedModelValue(original, budget);
            assertEncodedBudget(projection, budget);
            assertEquals(receipt, projection.getAsString());
            assertFalse(projection.getAsString().contains("cardinality:"));
            assertFalse(projection.getAsString().contains("1234567890"));
            assertFalse(projection.getAsString().contains("r_past_archive"));
            assertFalse(projection.getAsString().contains("workspace.open("));
            assertFalse(projection.getAsString().contains("current request only"));
            assertFalse(projection.getAsString().contains("complete-line excerpt"));
            assertEquals(exact, original.toString());
            assertEquals(originalText, original.getAsString());
        }
    }

    @Test
    void oversizedCustomModelTextCannotBypassTheEncodedSuccessBudget() {
        JsonObject normalized = success(new JsonObject());
        normalized.addProperty("modelText", "\"quoted\" \\ path\n界😀\t".repeat(20_000));
        String exact = normalized.toString();
        AgentToolResult result = new AgentToolResult("test:custom", normalized, false);

        for (int budget : BUDGETS) {
            assertEncodedBudget(result.modelValue(budget), budget);
            assertEquals(exact, result.normalized().toString());
        }
    }

    @Test
    void smallCustomProjectionRemainsExactRatherThanBeingRewritten() {
        String custom = "result: r_exact (current request only)\nanswer: 42\nquoted: \"value\" 界😀";
        JsonObject normalized = success(new JsonObject());
        normalized.addProperty("modelText", custom);
        AgentToolResult result = new AgentToolResult("test:custom", normalized, false);

        for (int budget : BUDGETS) {
            assertEncodedBudget(result.modelValue(budget), budget);
            assertEquals(new JsonPrimitive(custom), result.modelValue(budget));
        }
    }

    @Test
    void smallSuccessShowsEveryRowWithoutAnArbitraryProviderRowCap() {
        JsonArray rows = new JsonArray();
        for (int index = 0; index < 80; index++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", "row_" + index);
            row.addProperty("answer", index);
            rows.add(row);
        }
        AgentToolResult result = new AgentToolResult("test:rows", success(rows), false);
        JsonElement modelValue = result.modelValue(32_768);

        assertEncodedBudget(modelValue, 32_768);
        for (int index = 0; index < 80; index++) {
            assertTrue(modelValue.getAsString().contains("id: "
                    + rows.get(index).getAsJsonObject().get("id").toString() + "\n"));
        }
        assertEquals(rows, result.normalized().get("value"));
    }

    @Test
    void reasonableFailureKeepsExactCodeAndMessageAndEveryNormalizedErrorField() {
        String message = "ReferenceError: missingBlock is not defined\n"
                + "inspectBlock (openallay-agent.js:4)\n"
                + "requested field \"id\" was unavailable 界😀";
        JsonObject normalized = failure("javascript_error", message);
        JsonObject details = new JsonObject();
        details.addProperty("source", "openallay-agent.js");
        details.addProperty("line", 4);
        details.addProperty("recoverable", true);
        normalized.add("details", details);
        String exact = normalized.toString();
        AgentToolResult result = new AgentToolResult("openallay:run_javascript", normalized, true);

        for (int budget : BUDGETS) {
            JsonElement modelValue = result.modelValue(budget);
            assertEncodedBudget(modelValue, budget);
            String text = modelValue.getAsString();
            assertTrue(text.contains("status: failure"));
            assertTrue(text.contains("code: javascript_error"));
            assertTrue(text.contains("message: " + message));
            assertEquals(exact, result.normalized().toString());
            assertEquals(details, result.normalized().get("details"));
        }
        assertTrue(result.failure());
    }

    @Test
    void hugeExactFailureIsPreservedForTheWholeRequestBudgetOwnerToHandle() {
        String message = "Error: observation failed \"quoted\" 界😀\n".repeat(5_000);
        JsonObject normalized = failure("javascript_error", message);
        String exact = normalized.toString();
        AgentToolResult result = new AgentToolResult("openallay:run_javascript", normalized, true);

        JsonElement modelValue = result.modelValue(1_024);
        assertTrue(modelValue.getAsString().contains("status: failure"));
        assertTrue(modelValue.getAsString().contains("code: javascript_error"));
        assertTrue(modelValue.getAsString().contains("message: " + message));
        assertEquals(exact, result.normalized().toString());
        // The full error cannot fit. The whole-request owner must resolve this boundary;
        // success projection must not silently turn an error into incomplete knowledge.
        assertTrue(jsonUtf8Bytes(modelValue) > 1_024);
    }

    @Test
    void projectionAndCallerMutationCannotChangeCanonicalErrorOrSuccessJson() {
        for (JsonObject normalized : new JsonObject[] {
                success(largeWorldResult()), failure("javascript_error", "exact diagnostic")}) {
            String exact = normalized.toString();
            boolean failed = "failure".equals(normalized.get("status").getAsString());
            AgentToolResult result = new AgentToolResult("test:defensive", normalized, failed);
            normalized.addProperty("status", "caller mutation");
            result.normalized().addProperty("message", "returned copy mutation");
            for (int budget : BUDGETS) {
                result.modelValue(budget);
                assertEquals(exact, result.normalized().toString());
            }
        }
    }

    private static JsonObject success(JsonElement value) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "success");
        normalized.addProperty("outputType", JsonElement.class.getName());
        normalized.add("value", value);
        return normalized;
    }

    private static JsonObject failure(String code, String message) {
        JsonObject normalized = new JsonObject();
        normalized.addProperty("status", "failure");
        normalized.addProperty("code", code);
        normalized.addProperty("message", message);
        return normalized;
    }

    private static void assertEncodedBudget(JsonElement modelValue, int budget) {
        assertTrue(modelValue.isJsonPrimitive() && modelValue.getAsJsonPrimitive().isString());
        int actual = jsonUtf8Bytes(modelValue);
        assertEquals(actual, encodedModelTextUtf8Bytes(modelValue.getAsString()));
        assertTrue(actual <= budget,
                () -> "encoded model value is " + actual + " UTF-8 bytes; budget is " + budget);
    }
}
