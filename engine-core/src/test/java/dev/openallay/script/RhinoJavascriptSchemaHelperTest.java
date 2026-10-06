package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonElement;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.CancellationSignal;
import dev.openallay.world.BlockObservation;
import dev.openallay.world.EntityObservation;
import dev.openallay.world.WorldBlockSnapshot;
import dev.openallay.world.WorldEntitySnapshot;
import dev.openallay.world.WorldObservationCoordinator;
import dev.openallay.world.WorldObservationCoverage;
import dev.openallay.world.WorldObservationRequest;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.world.WorldPosition;
import java.time.Instant;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Dynamic value inference is distinct from the declared Java host schema catalog. */
final class RhinoJavascriptSchemaHelperTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void infersNestedArraysAndRepeatedObjectBranches(boolean unrestricted) {
        assertSchema(unrestricted, """
                return helpers.schema({
                  nested: [[1, 2], [3], ["text"], [1]],
                  left: {values: [{id: "a", tags: ["x"]}, {id: "b", tags: ["y"]}]},
                  right: {values: [{id: "c", tags: ["z"]}]}
                }, 5);
                """, """
                {
                  "left":{"values":[{"id":"string","tags":["string"]}]},
                  "nested":[["number"],["string"]],
                  "right":{"values":[{"id":"string","tags":["string"]}]}
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void samplesOnlyTheFirstEightArrayPositionsAndKeepsDistinctSchemasInOrder(boolean unrestricted) {
        assertSchema(unrestricted, """
                return helpers.schema([
                  1, 2, "a", "b", null, false, {id: 1}, {id: 2}, {notSampled: true}
                ]);
                """, """
                ["number","string","null","boolean",{"id":"number"}]
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ignoresSparseHolesButIncludesExplicitUndefinedAndInheritedPositions(boolean unrestricted) {
        assertSchema(unrestricted, """
                const values = [, undefined, , 1, , "a", , false, {notSampled: true}];
                Array.prototype[2] = true;
                return helpers.schema(values);
                """, """
                ["undefined","boolean","number","string"]
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesScalarTypesAtEveryDepth(boolean unrestricted) {
        assertSchema(unrestricted, """
                return [helpers.schema(null, 0), helpers.schema(undefined, 0),
                  helpers.schema(true, 0), helpers.schema(1, 0), helpers.schema("x", 0),
                  helpers.schema(function () {}, 0), helpers.schema([], 0),
                  helpers.schema({}, 0)];
                """, """
                ["null","undefined","boolean","number","string","function",[],"object"]
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void keepsDepthCoercionAndDefaultDepth(boolean unrestricted) {
        assertSchema(unrestricted, """
                const value = {a: {b: {c: {d: 1}}}, items: [{id: 1}]};
                return {
                  defaultDepth: helpers.schema(value),
                  zero: helpers.schema(value, 0),
                  negative: helpers.schema(value, -1),
                  invalid: helpers.schema(value, "not a number"),
                  numericString: helpers.schema(value, "2"),
                  fractional: helpers.schema(value, 1.5),
                  explicitUndefined: helpers.schema(value, undefined),
                  nullDepth: helpers.schema(value, null),
                  falseDepth: helpers.schema(value, false),
                  infiniteDepth: helpers.schema(value, Infinity)
                };
                """, """
                {
                  "defaultDepth":{"a":{"b":{"c":"object"}},"items":[{"id":"number"}]},
                  "zero":"object","negative":"object","invalid":"object",
                  "numericString":{"a":{"b":"object"},"items":["object"]},
                  "fractional":{"a":{"b":"object"},"items":["object"]},
                  "explicitUndefined":{"a":{"b":{"c":"object"}},"items":[{"id":"number"}]},
                  "nullDepth":"object","falseDepth":"object",
                  "infiniteDepth":{"a":{"b":{"c":{"d":"number"}}},"items":[{"id":"number"}]}
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expandsCyclesOnlyToTheRequestedFiniteDepth(boolean unrestricted) {
        assertSchema(unrestricted, """
                const object = {};
                object.self = object;
                const array = [];
                array.push(array);
                return {object: helpers.schema(object), array: helpers.schema(array)};
                """, """
                {"object":{"self":{"self":{"self":"object"}}},"array":[[[[]]]]}
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retainsSortedOwnEnumerableFieldNamesWithoutInvokingThePrototypeSetter(boolean unrestricted) {
        assertSchema(unrestricted, """
                const value = JSON.parse('{"z":true,"samples":[],"__proto__":{"nested":1},'
                  + '"constructor":null,"a.b":2,"toString":"text","é":false}');
                Object.setPrototypeOf(value, {inherited: 1});
                Object.defineProperty(value, "hidden", {value: 1, enumerable: false});
                const shape = helpers.schema(value);
                return {shape, keys: Object.keys(shape), prototype: Object.getPrototypeOf(shape) === Object.prototype};
                """, """
                {
                  "shape":{"__proto__":{"nested":"number"},"a.b":"number","constructor":"null",
                    "samples":[],"toString":"string","z":"boolean","é":"boolean"},
                  "keys":["__proto__","a.b","constructor","samples","toString","z","é"],
                  "prototype":true
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void infersHostAndWorkspaceArrayViewsWithTheSameSemanticsAsNativeArrays(boolean unrestricted) {
        JsonElement fixture = dev.openallay.json.JsonTrees.parse("""
                {"groups":[{"values":[1,2]},{"values":["a","b"]}],"empty":[]}
                """);
        JsonElement actual = new RhinoJavascriptRuntime().execute("""
                return {host: helpers.schema(mc.fixture, 5),
                  workspace: helpers.schema(workspace.open("fixture"), 5),
                  native: helpers.schema({groups: [{values: [1,2]}, {values: ["a","b"]}], empty: []}, 5)};
                """, Map.of("fixture", fixture), Map.of("fixture", fixture), Map.of(),
                new CancellationSignal(), null, null, unrestricted).value();
        JsonElement expected = dev.openallay.json.JsonTrees.parse("""
                {"empty":[],"groups":[{"values":["number"]},{"values":["string"]}]}
                """);
        for (String field : List.of("host", "workspace", "native")) {
            assertEquals(expected, actual.getAsJsonObject().get(field), field);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsOnlyEightElementsFromLargeLazyHostArrays(boolean unrestricted) {
        int[] reads = {0};
        List<Object> values = new AbstractList<>() {
            @Override
            public int size() { return 8_704; }

            @Override
            public Object get(int index) {
                reads[0]++;
                return Map.of("id", index, "nested", List.of(List.of(index)));
            }
        };
        JsonElement actual = new RhinoJavascriptRuntime().execute(
                "return helpers.schema(mc.values, 5);", Map.of("values", values),
                Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value();
        assertEquals(dev.openallay.json.JsonTrees.parse("[{\"id\":\"number\",\"nested\":[[\"number\"]]}]"), actual);
        assertEquals(8, reads[0], "Schema inference must retain bounded lazy sampling");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void executesBothExactWorldInspectionSourcesWithoutChangingUserCode(boolean unrestricted) {
        // Both exported sources contain no variable named samples. The old failure was in HELPERS.
        List<String> sources = List.of(
                "const p=mc.player.position; const r=world.inspect({from:{x:-62,y:120,z:159},"
                        + "to:{x:-58,y:127,z:163}},{includeAir:false}); return {schema:helpers.schema(r),sample:r};",
                "var r=world.inspect({from:{x:-84,y:101,z:137},to:{x:-36,y:149,z:185}},"
                        + "{includeAir:false}); return {shape:helpers.schema(r),coverage:r.coverage,"
                        + "evidence:r.evidence,sample:r.blocks.slice(0,12),count:r.blocks.length};");
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            assertFalse(source.contains("samples"));
            var observations = new WorldObservationRuntime();
            var coordinator = new SchemaWorldCoordinator();
            String requestId = "schema-world-" + index;
            observations.capture(requestId, coordinator);
            CancellationSignal cancellation = new CancellationSignal();
            var bridge = observations.bridge(requestId, cancellation).orElseThrow();
            try {
                JsonElement actual = new RhinoJavascriptRuntime().execute(source,
                        Map.of("player", Map.of("position", new WorldPosition(-60, 120, 161))),
                        Map.of(), Map.of(), cancellation, null, bridge, unrestricted).value();
                var result = actual.getAsJsonObject();
                assertEquals(worldSchema(), result.get(index == 0 ? "schema" : "shape"));
                assertEquals(2, index == 0
                        ? result.getAsJsonObject("sample").getAsJsonArray("blocks").size()
                        : result.get("count").getAsInt());
                if (index == 1) {
                    assertEquals(2, result.getAsJsonArray("sample").size());
                    assertEquals("chunk:unavailable", result.getAsJsonObject("coverage")
                            .getAsJsonArray("unavailableSections").get(0).getAsString());
                }
                assertFalse(coordinator.request.includeAir());
            } finally {
                observations.closeRequest(requestId);
            }
        }
    }

    private static void assertSchema(boolean unrestricted, String source, String expected) {
        assertEquals(dev.openallay.json.JsonTrees.parse(expected), new RhinoJavascriptRuntime().execute(source,
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value());
    }

    private static JsonElement worldSchema() {
        return dev.openallay.json.JsonTrees.parse("""
                {
                  "blocks":[{"blockEntity":"boolean","fluid":"string","id":"string",
                    "position":"object","relative":"object","state":"object"}],
                  "bounds":{"from":{"x":"number","y":"number","z":"number"},
                    "to":{"x":"number","y":"number","z":"number"}},
                  "coverage":{"complete":"boolean","loadedPositions":"number",
                    "requestedPositions":"number","unavailableSections":["string"]},
                  "evidence":{"authority":"string","capturedAt":"string","completeness":"string",
                    "details":{"minecraft:dimension":"string"},"gameVersion":"string","loader":"string",
                    "provenance":"string","sourceId":"string"}
                }
                """);
    }

    private static final class SchemaWorldCoordinator implements WorldObservationCoordinator {
        private WorldObservationRequest request;

        @Override
        public CompletionStage<BlockObservation> inspect(
                WorldObservationRequest request, CancellationSignal cancellation) {
            this.request = request;
            WorldPosition from = request.bounds().from();
            var block = new WorldBlockSnapshot("minecraft:oak_log", from,
                    new WorldPosition(0, 0, 0), Map.of("axis", "y"), "", false);
            return CompletableFuture.completedFuture(new BlockObservation(request.bounds(),
                    List.of(block, block), new WorldObservationCoverage(request.bounds().volume(),
                            request.bounds().volume() - 1, false, List.of("chunk:unavailable")),
                    new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST, DataCompleteness.PARTIAL,
                            Instant.EPOCH, "openallay:test_world", "openallay:test", "26.2", "test",
                            Map.of("minecraft:dimension", "minecraft:overworld"))));
        }

        @Override
        public CompletionStage<EntityObservation> entities(
                WorldObservationRequest request, CancellationSignal cancellation) {
            throw new AssertionError("The exact sources do not select entities");
        }

        @Override
        public CompletionStage<WorldEntitySnapshot> entity(
                String observationId, CancellationSignal cancellation) {
            throw new AssertionError("The exact sources do not select an entity");
        }
    }
}
