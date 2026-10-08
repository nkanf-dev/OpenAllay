package dev.openallay.script.host;

import static org.junit.jupiter.api.Assertions.*;

import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.fixture.ExplicitHostValue;
import dev.openallay.tool.query.QueryOperation;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class ExplicitHostValueTest {
    @Test
    void exposesCanonicalQueryOperationAsClosedOrderedDataInBothAuthorityModes() {
        List<String> selected = new ArrayList<>(List.of("/id", "/name"));
        QueryOperation operation = new QueryOperation(QueryOperation.Op.SELECT, null, null,
                null, selected, null, null, null, null);
        selected.clear();
        for (boolean unrestricted : List.of(false, true)) {
            var result = execute("""
                    return {op: mc.operation.op, fields: mc.operation.fields.map(field => field),
                      keys: Object.keys(mc.operation), jsConstructor: mc.operation.constructor === Object, same: mc.operation === mc.again,
                      absent: typeof mc.operation.missing, getClass: typeof mc.operation.getClass,
                      getter: typeof mc.operation.secretGetter, type: typeof mc.operation.TYPE,
                      schema: typeof mc.operation.Schema, equals: typeof mc.operation.equals,
                      classProperty: typeof mc.operation.class};
                    """, Map.of("operation", operation, "again", operation), unrestricted).getAsJsonObject();
            assertEquals("SELECT", result.get("op").getAsString());
            assertEquals("[\"/id\",\"/name\"]", result.get("fields").toString());
            assertEquals("[\"op\",\"field\",\"operator\",\"value\",\"fields\",\"direction\",\"aggregate\",\"groupBy\",\"count\"]",
                    result.get("keys").toString());
            assertTrue(result.get("same").getAsBoolean());
            assertTrue(result.get("jsConstructor").getAsBoolean());
            for (String property : List.of("absent", "getClass", "getter", "type", "schema", "equals", "classProperty")) {
                assertEquals("undefined", result.get(property).getAsString(), property);
            }
            assertEquals(execute("return mc.operation;", Map.of("operation", operation), unrestricted),
                    execute("return JSON.parse(JSON.stringify(mc.operation));", Map.of("operation", operation), unrestricted));
        }
    }

    @Test
    void nestedExplicitValuesNormalizeAndExposeNoJavaAuthority() {
        ExplicitHostValue value = fixture();
        for (boolean unrestricted : List.of(false, true)) {
            var result = execute("""
                    return {name: mc.value.name, nested: mc.value.properties.nested[0].id,
                      rows: mc.value.rows.map(row => row.id), keys: Object.keys(mc.value),
                      same: mc.value === mc.again, jsConstructor: mc.value.constructor === Object, note: mc.value.note,
                      java: typeof Java, getClass: typeof mc.value.getClass,
                      getter: typeof mc.value.secretGetter, privateField: typeof mc.value.privateAuthority,
                      nestedGetClass: typeof mc.value.rows[0].getClass,
                      listIterator: typeof mc.value.rows.iterator,
                      mapEntrySet: typeof mc.value.properties.entrySet,
                      type: typeof mc.value.TYPE, schema: typeof mc.value.Schema,
                      getterCallable: typeof mc.value.name};
                    """, Map.of("value", value, "again", value), unrestricted).getAsJsonObject();
            assertEquals("normalized", result.get("name").getAsString());
            assertEquals("nested", result.get("nested").getAsString());
            assertEquals("[\"nested\"]", result.get("rows").toString());
            assertEquals("[\"name\",\"rows\",\"properties\",\"note\"]", result.get("keys").toString());
            assertTrue(result.get("same").getAsBoolean());
            assertTrue(result.get("jsConstructor").getAsBoolean());
            assertEquals("string", result.get("getterCallable").getAsString());
            assertEquals("present", result.get("note").getAsString());
            if (!unrestricted) assertEquals("undefined", result.get("java").getAsString());
            for (String property : List.of("getClass", "getter", "privateField", "nestedGetClass",
                    "listIterator", "mapEntrySet", "type", "schema")) {
                assertEquals("undefined", result.get(property).getAsString(), property);
            }
            assertEquals(execute("return mc.value;", Map.of("value", value), unrestricted),
                    execute("return JSON.parse(JSON.stringify(mc.value));", Map.of("value", value), unrestricted));
            for (String source : List.of("mc.value.name = 'changed';", "delete mc.value.name;",
                    "mc.value.extra = 1;", "mc.value.rows.push(1);", "mc.value.properties.nested = [];")) {
                var failure = assertThrows(JavascriptExecutionException.class,
                        () -> execute(source + " return null;", Map.of("value", value), unrestricted));
                assertEquals("javascript_host_read_only", failure.code(), source);
            }
            for (String source : List.of("return mc.value.name();", "return new mc.value.Schema();")) {
                assertThrows(JavascriptExecutionException.class,
                        () -> execute(source, Map.of("value", value), unrestricted));
            }
        }
    }

    @Test
    void componentCacheUsesTypedMetadataAndMissingSentinelWithoutEagerReads() {
        HostRecordSchema schema = HostRecordSchema.of(ExplicitHostValue.class);
        assertEquals(List.of("name", "rows", "properties", "note"), schema.names());
        assertSame(schema, HostRecordSchema.of(ExplicitHostValue.class));
        assertSame(HostRecordSchema.Missing.INSTANCE, schema.read(fixture(), "getClass"));
        assertSame(TypeInfo.NONE, schema.type("getClass"));
        assertEquals(List.class, schema.type("rows").asClass());
        assertEquals(ExplicitHostValue.Leaf.class, schema.type("rows").param(0).asClass());
        assertEquals(String.class, schema.type("properties").param(0).asClass());
        assertEquals(ExplicitHostValue.Leaf.class, schema.type("properties").param(1).param(0).asClass());
        assertEquals(List.of("value"), HostRecordSchema.of(BrokenValue.class).names());
        var context = new ContextFactory().enter();
        var scope = context.initSafeStandardObjects(null, false);
        var adapter = new RhinoHostAdapter(context, scope);
        HostObjectView view = (HostObjectView) adapter.adapt(fixture());
        assertArrayEquals(schema.names().toArray(), view.getIds(context));
        assertSame(Scriptable.NOT_FOUND, view.get(context, "secretGetter", view));
        assertThrows(HostAccessException.class, () -> view.put("name", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> view.entrySet().iterator().next().setValue("changed"));
    }

    @Test
    void rejectsInvalidProvidersAndUnannotatedPojoWithoutReflectiveFallback() {
        for (Object value : List.of(new MissingProvider(), new PlainPojo())) {
            var failure = assertThrows(JavascriptExecutionException.class,
                    () -> execute("return mc.value;", Map.of("value", value), false));
            assertEquals("javascript_host_type_unsupported", failure.code());
        }
        for (String source : List.of("return mc.value.value;", "return JSON.stringify(mc.value);")) {
            var failure = assertThrows(JavascriptExecutionException.class,
                    () -> execute(source, Map.of("value", new BrokenValue("hidden")), false));
            assertEquals("javascript_host_access_failed", failure.code());
            assertFalse(failure.getMessage().contains("private-native-marker"));
        }
    }

    @ValueType(MissingProvider.Schema.class)
    public static final class MissingProvider {
        public static final class Schema implements ValueSchema.Provider {
            private Schema() {}
            @Override public ValueSchema<?> schema() { throw new AssertionError("must not call private provider"); }
        }
    }

    private static final class PlainPojo {
        public String name() { return "not authorized"; }
    }

    @ValueType(BrokenValue.Schema.class)
    public static final class BrokenValue {
        private final String value;
        public BrokenValue(String value) { this.value = value; }
        public String value() { throw new IllegalStateException("private-native-marker"); }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<BrokenValue> schema() {
                return new ValueSchema<>(BrokenValue.class,
                        Collections.singletonList(new ValueSchema.Component<>(BrokenValue.class, "value", BrokenValue::value)),
                        arguments -> new BrokenValue((String) arguments[0]));
            }
        }
    }

    private static ExplicitHostValue fixture() {
        var rows = new ArrayList<>(List.of(new ExplicitHostValue.Leaf(" nested ")));
        var value = ValueSchemas.of(ExplicitHostValue.class).construct(new Object[] {
                " normalized ", rows, Map.of("nested", rows), Optional.of("present")});
        rows.clear();
        return value;
    }

    private static com.google.gson.JsonElement execute(String source, Map<String, Object> roots, boolean unrestricted) {
        return new RhinoJavascriptRuntime().execute(source, roots, Map.of(), Map.of(),
                new CancellationSignal(), null, null, unrestricted).value();
    }
}
