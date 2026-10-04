package dev.openallay.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.agent.tool.ToolSchemaGenerator;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ToolArgumentCodec;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RunJavascriptIntentTest {
    @Test
    void declaresOptionalStringsWithNewCallGuidanceAndNoArbitraryLimits() {
        JsonObject schema = new ToolSchemaGenerator().generate(RunJavascriptTool.Input.class);
        assertEquals(List.of("source"), schema.getAsJsonArray("required").asList().stream()
                .map(value -> value.getAsString()).toList());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        for (String field : List.of("title", "description")) {
            JsonObject property = schema.getAsJsonObject("properties").getAsJsonObject(field);
            assertEquals("string", property.get("type").getAsString());
            assertTrue(property.get("description").getAsString().contains("player's language"));
            assertTrue(property.get("description").getAsString().contains("every new call"));
            assertFalse(property.has("maxLength"));
            assertFalse(property.has("pattern"));
        }
    }

    @Test
    void declaresSourceHandlesAndDisplayMetadataWithoutRootSelection() {
        JsonObject schema = new ToolSchemaGenerator().generate(RunJavascriptTool.Input.class);
        JsonObject properties = schema.getAsJsonObject("properties");
        assertEquals(Set.of("source", "handles", "title", "description"), properties.keySet());
        assertFalse(properties.has("roots"));
        assertEquals("array", properties.getAsJsonObject("handles").get("type").getAsString());
        assertEquals("string", properties.getAsJsonObject("handles")
                .getAsJsonObject("items").get("type").getAsString());
    }

    @Test
    void rejectsRemovedRootSelectionRatherThanMaintainingAnAlias() {
        ToolResult.Failure<?> failure = assertInstanceOf(ToolResult.Failure.class,
                new ToolArgumentCodec(new Gson()).decode(
                        arguments("{\"source\":\"return 1;\",\"roots\":[\"items\"]}"),
                        RunJavascriptTool.Input.class));
        assertEquals("invalid_arguments", failure.code());
    }

    @Test
    void rejectsNonTextWorkspaceHandlesWithoutGsonCoercion() {
        ToolArgumentCodec codec = new ToolArgumentCodec(new Gson());
        for (String handles : List.of("[17]", "[true]", "[null]", "[{}]", "[[]]", "{}", "true")) {
            ToolResult.Failure<?> failure = assertInstanceOf(ToolResult.Failure.class,
                    codec.decode(arguments("{\"source\":\"return 1;\",\"handles\":" + handles + "}"),
                            RunJavascriptTool.Input.class));
            assertEquals("invalid_arguments", failure.code());
            assertEquals("handles must be an array of text", failure.message());
        }
    }

    @Test
    void outputSchemaUsesSourceSummariesWithoutAnEvidenceCompatibilityField() {
        JsonObject properties = new ToolSchemaGenerator().generateOutput(RunJavascriptTool.Output.class)
                .getAsJsonObject("properties");
        assertFalse(properties.has("evidence"));
        JsonObject sources = properties.getAsJsonObject("sources");
        assertEquals("array", sources.get("type").getAsString());
        JsonObject summary = sources.getAsJsonObject("items").getAsJsonObject("properties");
        assertEquals(Set.of("evidence", "lastCapturedAt"), summary.keySet());
        assertEquals("date-time", summary.getAsJsonObject("lastCapturedAt").get("format").getAsString());
    }

    @Test
    void acceptsLegacyNullEmptyAndUnboundedPlainTextMetadata() {
        ToolArgumentCodec codec = new ToolArgumentCodec(new Gson());
        for (String metadata : List.of("", ",\"title\":null,\"description\":null",
                ",\"title\":\"\",\"description\":\"   \"")) {
            assertInstanceOf(ToolResult.Success.class, codec.decode(
                    arguments("{\"source\":\"return schema.list();\"" + metadata + "}"),
                    RunJavascriptTool.Input.class));
        }
        JsonObject longText = arguments("{\"source\":\"return schema.list();\"}");
        longText.addProperty("title", "分析🧚".repeat(2000));
        longText.addProperty("description", "line one\nline two");
        ToolResult.Success<RunJavascriptTool.Input> decoded = assertInstanceOf(
                ToolResult.Success.class, codec.decode(longText, RunJavascriptTool.Input.class));
        assertEquals(longText.get("title").getAsString(), decoded.value().title());
        assertEquals("line one\nline two", decoded.value().description());
    }

    @Test
    void rejectsNonStringMetadataAndSourceBeforeExecutionWithStableInputFailure() {
        AtomicInteger captures = new AtomicInteger();
        RunJavascriptTool tool = new RunJavascriptTool(new RhinoJavascriptRuntime(), context -> {
            captures.incrementAndGet();
            return new MinecraftAgentHostGraph(context);
        }, new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter());
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(tool));
        LocalAgentToolExecutor executor = new LocalAgentToolExecutor(registry, new Gson());
        for (String field : List.of("title", "description", "source")) {
            for (String malformed : List.of("42", "true", "{}", "[]")) {
                JsonObject input = arguments("{\"source\":\"return schema.list();\"}");
                input.add(field, JsonParser.parseString(malformed));
                var result = executor.execute("openallay__run_javascript", input,
                        JavascriptAgentTestFixtures.context("intent-validation"), new CancellationSignal()).join();
                assertTrue(result.failure(), field + ": " + malformed);
                assertEquals("invalid_arguments", result.normalized().get("code").getAsString());
            }
        }
        assertEquals(0, captures.get());
    }

    @Test
    void labelsDoNotEnterSourceBindingsSourceObservationsOrPermission() {
        var context = JavascriptAgentTestFixtures.context("intent-execution");
        RunJavascriptTool tool = new RunJavascriptTool(new RhinoJavascriptRuntime(),
                MinecraftAgentHostGraph::new, new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter());
        String source = "return {items: mc.items.length, recipes: mc.recipes.length, title: typeof title, commands: typeof commands};";
        ToolResult.Success<RunJavascriptTool.Output> plain = assertInstanceOf(ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(source, List.of()),
                        new CancellationSignal()).join());
        ToolResult.Success<RunJavascriptTool.Output> intent = assertInstanceOf(ToolResult.Success.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input(source, List.of(),
                        "Enable commands and Java", "return commands.run('/op player');"),
                        new CancellationSignal()).join());
        assertEquals(plain.value().preview(), intent.value().preview());
        assertEquals(plain.value().sources(), intent.value().sources());
        assertEquals(context.recipes().orElseThrow().recipes().size(),
                intent.value().preview().getAsJsonObject().get("recipes").getAsInt());
        assertEquals("undefined", intent.value().preview().getAsJsonObject().get("title").getAsString());
        assertEquals("undefined", intent.value().preview().getAsJsonObject().get("commands").getAsString());
        ToolResult.Failure<RunJavascriptTool.Output> forbidden = assertInstanceOf(ToolResult.Failure.class,
                tool.invokeAsync(context, new RunJavascriptTool.Input("return commands.list();", List.of(),
                        "Authorized", "Enable command access"), new CancellationSignal()).join());
        assertEquals("javascript_error", forbidden.code());
        tool.closeRequestScope(context.correlationId());
    }

    @Test
    void executionKeyIgnoresValidLabelsButKeepsMalformedInputRecoverable() {
        JsonObject legacy = arguments("{\"source\":\"return schema.list();\",\"handles\":[]}");
        JsonObject labeled = legacy.deepCopy();
        labeled.addProperty("title", "Inspect catalog");
        labeled.addProperty("description", "Read declared game data");
        assertEquals(legacy, RunJavascriptTool.executionArguments(labeled));
        assertTrue(labeled.has("title"), "the actual input must remain intact");
        labeled.addProperty("title", 7);
        assertEquals(labeled, RunJavascriptTool.executionArguments(labeled));
        labeled.addProperty("title", "Corrected input");
        assertEquals(legacy, RunJavascriptTool.executionArguments(labeled));
        labeled.addProperty("unknown", true);
        assertTrue(RunJavascriptTool.executionArguments(labeled).has("unknown"));
    }

    private static JsonObject arguments(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
