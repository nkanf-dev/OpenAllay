package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ToolSchemaGenerator;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.query.QueryOperation;
import dev.openallay.trace.replay.ToolArgumentCodec;
import org.junit.jupiter.api.Test;

final class ExplicitValueSchemaTest {
    @Test void actualEngineJsonOwnsExplicitConstructorValues() throws Exception {
        ValueSchemaJava8Fixture.run(EngineJson::create);
    }
    @Test void actualToolSchemaAndStrictArgumentCodecKeepInputBoundary() {
        JsonObject schema = new ToolSchemaGenerator().generate(QueryOperation.class);
        assertEquals("[\"op\"]", schema.getAsJsonArray("required").toString());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertEquals("array", schema.getAsJsonObject("properties").getAsJsonObject("fields").get("type").getAsString());
        assertEquals("string", schema.getAsJsonObject("properties").getAsJsonObject("fields").getAsJsonObject("items").get("type").getAsString());
        assertEquals("Fields retained by SELECT", schema.getAsJsonObject("properties").getAsJsonObject("fields").get("description").getAsString());
        ToolArgumentCodec codec = new ToolArgumentCodec(EngineJson.create());
        for (String invalid : new String[] {"{\"extra\":1}", "{\"field\":7}", "{\"fields\":[7]}", "{\"fields\":[null]}", "{\"fields\":\"x\"}"}) {
            assertInstanceOf(ToolResult.Failure.class, codec.decode(JsonTrees.parse(invalid).getAsJsonObject(), QueryOperation.class));
        }
        assertInstanceOf(ToolResult.Success.class, codec.decode(JsonTrees.parse("{\"op\":\"SELECT\",\"fields\":[\"a\"]}").getAsJsonObject(), QueryOperation.class));
    }
}
