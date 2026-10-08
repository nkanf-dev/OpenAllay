package dev.openallay.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.json.EngineJson;
import dev.openallay.json.JsonTrees;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.ToolSchemaJava8Fixture;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.value.RecordMetadata;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ToolSchemaJava8CompatibilityTest {
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.RECORD_COMPONENT)
    @interface ComponentOnly { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
    @interface AccessorOnly { String value(); }
    @ToolAtLeastOne({"id", "kind"})
    record ModernInput(@ComponentOnly("component-first") @ToolPattern("^[a-z]+:[a-z_]+$")
            @ToolDescription("Exact identifier") @ToolOptional String id,
            @ToolOptional String kind, List<String> fields) {
        @Override @ToolDescription("Accessor must not override field") @AccessorOnly("public-accessor") public String id() { return id; }
    }

    @Test void actualJava8VectorsAlsoRunOnModernBoundEngineJson() throws Exception {
        ToolSchemaJava8Fixture.run();
    }

    @Test void optionalRecordMetadataRetainsGenericOrderAndAnnotationFallback() {
        assertTrue(RecordMetadata.isRecord(ModernInput.class));
        assertFalse(RecordMetadata.isRecord(ToolSchemaJava8Fixture.Lookup.class));
        var components = RecordMetadata.components(ModernInput.class);
        assertEquals(List.of("id", "kind", "fields"), components.stream().map(RecordMetadata.Component::name).toList());
        assertEquals("component-first", components.getFirst().annotation(ComponentOnly.class).value());
        assertEquals("Exact identifier", components.getFirst().annotation(ToolDescription.class).value());
        assertEquals("^[a-z]+:[a-z_]+$", components.getFirst().annotation(ToolPattern.class).value());
        assertNotNull(components.getFirst().annotation(ToolOptional.class));
        assertEquals("public-accessor", components.getFirst().annotation(AccessorOnly.class).value());
        assertThrows(NullPointerException.class, () -> RecordMetadata.isRecord(null));
        assertEquals(String.class, components.getFirst().rawType());
        assertEquals(ModernInput.class, components.getFirst().fieldMetadata().getDeclaringClass());
        assertEquals("id", components.getFirst().accessorMetadata().getName());
        var generic = assertInstanceOf(ParameterizedType.class, components.get(2).genericType());
        assertArrayEquals(new java.lang.reflect.Type[] {String.class}, generic.getActualTypeArguments());
        assertThrows(UnsupportedOperationException.class, components::clear);
        assertThrows(IllegalArgumentException.class, () -> RecordMetadata.components(String.class));
        var schema = new ToolSchemaGenerator().generate(ModernInput.class);
        assertEquals("[\"fields\"]", schema.getAsJsonArray("required").toString());
        assertEquals("^[a-z]+:[a-z_]+$", schema.getAsJsonObject("properties").getAsJsonObject("id").get("pattern").getAsString());
        assertEquals("Exact identifier", schema.getAsJsonObject("properties").getAsJsonObject("id").get("description").getAsString());
        assertEquals(2, schema.getAsJsonArray("anyOf").size());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        ToolArgumentCodec codec = new ToolArgumentCodec(EngineJson.create());
        var decoded = assertInstanceOf(ToolResult.Success.class, codec.decode(
                JsonTrees.parse("{\"id\":\"mod:item\",\"fields\":[\"a\"]}").getAsJsonObject(), ModernInput.class));
        assertEquals(new ModernInput("mod:item", null, List.of("a")), decoded.value());
        for (String malformed : List.of("{\"id\":7}", "{\"fields\":[7]}", "{\"removed\":true}")) {
            assertInstanceOf(ToolResult.Failure.class, codec.decode(JsonTrees.parse(malformed).getAsJsonObject(), ModernInput.class));
        }
    }
}
