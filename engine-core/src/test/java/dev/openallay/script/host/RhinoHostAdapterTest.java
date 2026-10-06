package dev.openallay.script.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RhinoHostAdapterTest {
    private enum Mode { ACTIVE }

    private record Fixture(
            String id,
            List<Integer> values,
            Map<String, Object> properties,
            Optional<String> optional,
            Mode mode,
            Instant capturedAt,
            com.google.gson.JsonElement json) {}

    @Test
    void exposesOnlyRecordComponentsAndNormalArrayTransforms() {
        Fixture fixture = fixture();

        var value = execute("""
                return {
                  id: mc.fixture.id,
                  filtered: mc.fixture.values.filter(value => value >= 2),
                  mapped: mc.fixture.values.map(value => value * 3),
                  property: mc.fixture.properties["damage"],
                  numericProperty: mc.numeric[0],
                  keys: Object.keys(mc.fixture),
                  optional: mc.fixture.optional,
                  mode: mc.fixture.mode,
                  capturedAt: mc.fixture.capturedAt,
                  duration: mc.duration,
                  nested: mc.fixture.json.nested[1],
                  getClassType: typeof mc.fixture.getClass,
                  accessorType: typeof mc.fixture.id
                };
                """, Map.of(
                        "fixture", fixture,
                        "numeric", Map.of("0", "zero"),
                        "duration", Duration.ofSeconds(75))).getAsJsonObject();

        assertEquals("direct", value.get("id").getAsString());
        assertEquals(2, value.getAsJsonArray("filtered").size());
        assertEquals(9, value.getAsJsonArray("mapped").get(2).getAsInt());
        assertEquals(14, value.get("property").getAsInt());
        assertEquals("zero", value.get("numericProperty").getAsString());
        assertTrue(value.getAsJsonArray("keys").toString().contains("\"values\""));
        assertEquals("present", value.get("optional").getAsString());
        assertEquals("ACTIVE", value.get("mode").getAsString());
        assertEquals("2026-07-24T00:00:00Z", value.get("capturedAt").getAsString());
        assertEquals("PT1M15S", value.get("duration").getAsString());
        assertEquals("b", value.get("nested").getAsString());
        assertEquals("undefined", value.get("getClassType").getAsString());
        assertEquals("string", value.get("accessorType").getAsString());
    }

    @Test
    void preservesWrapperIdentityAndCanNormalizeAnUnchangedHostRecordAtReturnBoundary() {
        Fixture fixture = fixture();
        var value = execute(
                "return {same: mc.fixture === mc.again, fixture: mc.fixture};",
                Map.of("fixture", fixture, "again", fixture));

        var object = value.getAsJsonObject();
        assertTrue(object.get("same").getAsBoolean());
        assertEquals("direct", object.getAsJsonObject("fixture").get("id").getAsString());
    }

    @Test
    void nativeJsonStringifyUsesDetachedComponentsInBothAuthorityModes() {
        Fixture fixture = fixture();
        var roots = Map.<String, Object>of(
                "fixture", fixture,
                "mapping", Map.of("name", "detached", "rows", List.of(1, 2)),
                "json", dev.openallay.json.JsonTrees.parse(
                        "{\"groups\":[{\"values\":[1,2]},{\"values\":[\"a\",\"b\"]}],\"empty\":[]}"));
        var runtime = new RhinoJavascriptRuntime();
        for (boolean unrestricted : List.of(false, true)) {
            for (String name : List.of("fixture", "mapping", "json")) {
                var direct = runtime.execute("return mc." + name + ";", roots, Map.of(), Map.of(),
                        new CancellationSignal(), null, null, unrestricted).value();
                var roundTrip = runtime.execute(
                        "return JSON.parse(JSON.stringify(mc." + name + "));", roots,
                        Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value();
                assertEquals(direct, roundTrip, name + " unrestricted=" + unrestricted);
                assertFalse(roundTrip.toString().contains("dev.openallay.script.host"));
                assertFalse(roundTrip.toString().contains("getParentScope"));
            }
            var values = runtime.execute(
                    "return JSON.parse(JSON.stringify(mc.fixture.values));", roots, Map.of(), Map.of(),
                    new CancellationSignal(), null, null, unrestricted).value();
            assertEquals(dev.openallay.json.JsonTrees.parse("[1,2,3]"), values);
            var workspace = runtime.execute(
                    "return JSON.parse(JSON.stringify(workspace.open('selected')));", Map.of(),
                    Map.of("selected", dev.openallay.json.JsonTrees.parse("{\"rows\":[1,2],\"empty\":[]}")),
                    Map.of(), new CancellationSignal(), null, null, unrestricted).value();
            assertEquals(dev.openallay.json.JsonTrees.parse("{\"rows\":[1,2],\"empty\":[]}"), workspace);
        }
    }

    @Test
    void nativeJsonNestedHostsUseClosedViewsAndSafeAccessorFailures() {
        var runtime = new RhinoJavascriptRuntime();
        for (boolean unrestricted : List.of(false, true)) {
            var roots = Map.<String, Object>of(
                    "fixture", fixture(),
                    "mapping", Map.of("name", "line\nvalue", "rows", List.of(1, 2)));
            var result = runtime.execute("return JSON.parse(JSON.stringify({host:mc.fixture, rows:mc.fixture.values, map:mc.mapping}));",
                    roots, Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value();
            var direct = runtime.execute("return {host:mc.fixture, rows:mc.fixture.values, map:mc.mapping};",
                    roots, Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value();
            assertEquals(direct, result);
            var facilities = runtime.execute("return {getClass: typeof mc.fixture.getClass, entrySet: typeof mc.mapping.entrySet, "
                            + "put: typeof mc.mapping.put, iterator: typeof mc.fixture.values.iterator};",
                    roots, Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value();
            facilities.getAsJsonObject().entrySet().forEach(entry -> assertEquals("undefined", entry.getValue().getAsString()));
            var failure = assertThrows(JavascriptExecutionException.class, () -> runtime.execute(
                    "return JSON.stringify(mc.broken);", Map.of("broken", new BrokenAccessor("ignored")),
                    Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted));
            assertEquals("javascript_host_access_failed", failure.code());
            assertFalse(failure.getMessage().contains("private-native-marker"));
        }
    }

    private record BrokenAccessor(String value) {
        @Override public String value() { throw new IllegalStateException("private-native-marker"); }
    }

    @Test
    void javaSerializationViewsRemainLazyAndReadOnlyWithoutExposingMethods() {
        var factory = new dev.latvian.mods.rhino.ContextFactory();
        var context = factory.enter();
        var scope = context.initSafeStandardObjects(null, false);
        var adapter = new RhinoHostAdapter(context, scope);
        HostObjectView object = (HostObjectView) adapter.adapt(Map.of("value", 1));
        assertThrows(HostAccessException.class, () -> object.put("value", 2));
        assertThrows(HostAccessException.class, () -> object.remove("missing"));
        assertThrows(HostAccessException.class, () -> object.computeIfAbsent("value", ignored -> 2));
        assertThrows(HostAccessException.class, () -> object.replaceAll((key, value) -> 2));
        assertThrows(UnsupportedOperationException.class,
                () -> object.entrySet().iterator().next().setValue(2));
        assertThrows(UnsupportedOperationException.class, () -> object.keySet().clear());
        AtomicInteger reads = new AtomicInteger();
        HostListView list = (HostListView) adapter.adapt(new java.util.AbstractList<Integer>() {
            @Override public Integer get(int index) { reads.incrementAndGet(); return index; }
            @Override public int size() { return 2; }
        });
        var iterator = list.iterator();
        assertEquals(0, reads.get());
        assertTrue(iterator.hasNext());
        assertEquals(0, reads.get());
        assertEquals(0, iterator.next());
        assertEquals(1, reads.get());
        assertThrows(HostAccessException.class, iterator::remove);
        var result = execute("return {getClass: typeof mc.object.getClass, entrySet: typeof mc.object.entrySet, "
                + "put: typeof mc.object.put, iterator: typeof mc.list.iterator};",
                Map.of("object", Map.of("value", 1), "list", List.of(1, 2)));
        result.getAsJsonObject().entrySet().forEach(entry -> assertEquals("undefined", entry.getValue().getAsString()));
    }

    @Test
    void rejectsAllMutationPathsWithOneStableFailure() {
        Fixture fixture = fixture();
        for (String source : List.of(
                "mc.fixture.id = 'changed'; return null;",
                "delete mc.fixture.id; return null;",
                "mc.fixture.values.push(4); return null;",
                "mc.fixture.values.sort(); return null;",
                "mc.fixture.values[0] = 9; return null;")) {
            JavascriptExecutionException failure = assertThrows(
                    JavascriptExecutionException.class,
                    () -> execute(source, fixture));
            assertEquals("javascript_host_read_only", failure.code(), source);
        }
    }

    @Test
    void rejectsUnsupportedHostTypesAndNonStringMapKeys() {
        JavascriptExecutionException unsupported = assertThrows(
                JavascriptExecutionException.class,
                () -> execute("return mc.value;", Map.of("value", new Object())));
        assertEquals("javascript_host_type_unsupported", unsupported.code());

        JavascriptExecutionException map = assertThrows(
                JavascriptExecutionException.class,
                () -> execute("return mc.value;", Map.of("value", Map.of(1, "bad"))));
        assertEquals("javascript_host_map_key_unsupported", map.code());
    }

    @Test
    void arrayLengthAndRootEnumerationDoNotEagerlyReadElements() {
        AtomicInteger reads = new AtomicInteger();
        List<Integer> values = new java.util.AbstractList<>() {
            @Override public Integer get(int index) {
                reads.incrementAndGet();
                return index + 10;
            }

            @Override public int size() {
                return 100_000;
            }
        };

        var metadata = execute(
                "return {roots: Object.keys(mc), length: mc.values.length};",
                Map.of("values", values)).getAsJsonObject();
        assertEquals(100_000, metadata.get("length").getAsInt());
        assertEquals(0, reads.get());

        assertEquals(
                10,
                execute("return mc.values[0];", Map.of("values", values)).getAsInt());
        assertEquals(1, reads.get());
    }

    private static Fixture fixture() {
        return new Fixture(
                "direct",
                List.of(1, 2, 3),
                Map.of("damage", 14),
                Optional.of("present"),
                Mode.ACTIVE,
                Instant.parse("2026-07-24T00:00:00Z"),
                dev.openallay.json.JsonTrees.parse("{\"nested\":[\"a\",\"b\"]}"));
    }

    private static com.google.gson.JsonObject execute(String source, Fixture fixture) {
        return execute(source, Map.of("fixture", fixture)).getAsJsonObject();
    }

    private static com.google.gson.JsonElement execute(String source, Map<String, Object> roots) {
        return new RhinoJavascriptRuntime().execute(
                source, roots, Map.of(), new CancellationSignal()).value();
    }
}
