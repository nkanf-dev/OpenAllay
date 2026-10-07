package dev.openallay.tool;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.agent.tool.ToolSchemaGenerator;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.json.EngineJson;
import dev.openallay.json.JsonTrees;
import dev.openallay.json.ToolResultJava8Fixture;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

final class ToolResultExplicitValueTest {
    private final Gson gson = EngineJson.create();

    @Test void actualEngineJsonPreservesConstructorsGenericsAndValueContract() throws Exception {
        ToolResultJava8Fixture.run(EngineJson::create);
    }

    @Test void actualSchemaAndArgumentCodecRecognizeResultValueClasses() {
        var schema = new ToolSchemaGenerator().generate(ToolResult.Failure.class);
        assertEquals("[\"code\",\"message\"]", schema.getAsJsonArray("required").toString());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertEquals("string", schema.getAsJsonObject("properties").getAsJsonObject("code").get("type").getAsString());
        ToolArgumentCodec codec = new ToolArgumentCodec(gson);
        var result = assertInstanceOf(ToolResult.Success.class, codec.decode(
                JsonTrees.parse("{\"code\":\"c\",\"message\":\"m\"}").getAsJsonObject(), ToolResult.Failure.class));
        assertEquals(new ToolResult.Failure<>("c", "m"), result.value());
        for (String invalid : List.of("{\"extra\":1}", "{\"code\":7,\"message\":\"m\"}",
                "{\"code\":\"c\",\"message\":null}", "{\"code\":\"\u2003\",\"message\":\"m\"}")) {
            var failure = assertInstanceOf(ToolResult.Failure.class,
                    codec.decode(JsonTrees.parse(invalid).getAsJsonObject(), ToolResult.Failure.class));
            assertEquals("invalid_arguments", failure.code());
        }
    }

    @Test void actualResultSerializerRejectsForeignImplementation() {
        ToolResult<String> foreign = new ToolResult<String>() {};
        var failure = assertThrows(IllegalArgumentException.class,
                () -> new ToolResultNormalizer(gson).normalize(foreign, String.class));
        assertTrue(failure.getMessage().startsWith("Unknown ToolResult implementation: "));
        var success = new ToolResultNormalizer(gson).normalize(new ToolResult.Success<>("ok"), String.class);
        assertEquals("success", success.get("status").getAsString());
        assertEquals("ok", success.get("value").getAsString());
        var rejected = new ToolResultNormalizer(gson).normalize(new ToolResult.Failure<>("c", "m"), String.class);
        assertEquals("{\"code\":\"c\",\"message\":\"m\",\"status\":\"failure\"}", rejected.toString());
    }

    @Test void actualExecutorNormalizesValuesAndRejectsForeignToolResult() {
        for (ToolResult<String> returned : List.<ToolResult<String>>of(new ToolResult.Success<>("ok"),
                new ToolResult.Failure<>("c", "m"), new ToolResult<String>() {})) {
            ToolRegistry registry = new ToolRegistry();
            registry.register("test", List.of(new Tool<ToolResult.Failure, String>() {
                @Override public ToolDescriptor<ToolResult.Failure, String> descriptor() {
                    return new ToolDescriptor<>("test:result", "Return result", ToolResult.Failure.class,
                            String.class, ToolAccess.READ_ONLY);
                }
                @Override public ToolResult<String> invoke(ToolInvocationContext context, ToolResult.Failure input) {
                    assertEquals(new ToolResult.Failure<>("input", "message"), input);
                    return returned;
                }
            }));
            var executed = new LocalAgentToolExecutor(registry, gson).execute("test:result",
                    JsonTrees.parse("{\"code\":\"input\",\"message\":\"message\"}").getAsJsonObject(),
                    ToolInvocationContext.developmentConsole("result"), new CancellationSignal());
            if (returned instanceof ToolResult.Success<?>) {
                assertFalse(executed.join().failure());
                assertEquals("ok", executed.join().normalized().get("value").getAsString());
            } else if (returned instanceof ToolResult.Failure<?>) {
                assertTrue(executed.join().failure());
                assertEquals("c", executed.join().normalized().get("code").getAsString());
            } else {
                var rejected = assertThrows(CompletionException.class, executed::join);
                assertInstanceOf(IllegalArgumentException.class, rejected.getCause());
                assertTrue(rejected.getCause().getMessage().startsWith("Unknown ToolResult implementation: "));
            }
        }
    }

    @Test void actualRhinoHostKeepsResultsClosedDataWithoutJavaMethodsInBothModes() {
        ToolResult.Success<ToolResult.Failure<String>> value = new ToolResult.Success<>(new ToolResult.Failure<>("c", "m"));
        assertInstanceOf(TypeVariable.class, ToolResult.Success.class.getTypeParameters()[0]);
        for (boolean unrestricted : List.of(false, true)) {
            var output = execute("""
                    return {keys: Object.keys(mc.result), nestedKeys: Object.keys(mc.result.value),
                      code: mc.result.value.code, message: mc.result.value.message,
                      same: mc.result === mc.again, jsConstructor: mc.result.constructor === Object,
                      getClass: typeof mc.result.getClass, equals: typeof mc.result.equals,
                      schema: typeof mc.result.Schema, classProperty: typeof mc.result.class,
                      nestedGetClass: typeof mc.result.value.getClass};
                    """, value, unrestricted).getAsJsonObject();
            assertEquals("[\"value\"]", output.get("keys").toString());
            assertEquals("[\"code\",\"message\"]", output.get("nestedKeys").toString());
            assertEquals("c", output.get("code").getAsString());
            assertEquals("m", output.get("message").getAsString());
            assertTrue(output.get("same").getAsBoolean());
            assertTrue(output.get("jsConstructor").getAsBoolean());
            for (String field : List.of("getClass", "equals", "schema", "classProperty", "nestedGetClass")) {
                assertEquals("undefined", output.get(field).getAsString(), field);
            }
            assertEquals(execute("return mc.result;", value, unrestricted),
                    execute("return JSON.parse(JSON.stringify(mc.result));", value, unrestricted));
            for (String source : List.of("mc.result.value = 1;", "delete mc.result.value;",
                    "mc.result.extra = 1;", "mc.result.value.code = 'changed';")) {
                var failure = assertThrows(JavascriptExecutionException.class,
                        () -> execute(source + " return null;", value, unrestricted));
                assertEquals("javascript_host_read_only", failure.code());
            }
            assertThrows(JavascriptExecutionException.class,
                    () -> execute("return mc.result.value.code();", value, unrestricted));
        }
    }

    private static com.google.gson.JsonElement execute(String source, Object value, boolean unrestricted) {
        return new RhinoJavascriptRuntime().execute(source, Map.of("result", value, "again", value),
                Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value();
    }
}
