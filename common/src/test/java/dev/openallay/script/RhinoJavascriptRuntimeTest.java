package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RhinoJavascriptRuntimeTest {
    @Test
    void unrestrictedModeProvidesJavaTypeAndArbitrarySystemAccessWhileDefaultModeDeniesIt() {
        var runtime = new RhinoJavascriptRuntime();
        assertThrows(RuntimeException.class, () -> runtime.execute(
                "return Java.type('java.lang.System').getProperty('java.version');",
                Map.of(), Map.of(), new CancellationSignal()));
        JavascriptExecution execution = runtime.execute(
                "return Java.type('java.lang.System').getProperty('java.version');",
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, true);
        assertTrue(execution.value().getAsString().length() > 0);
        String largeSource = "return '" + "x".repeat(40) + "';";
        assertEquals("javascript_source_too_large", assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime(Duration.ofSeconds(1),
                        new JavascriptRuntimeLimits(32, 4, 12, 8, 4, 16))
                        .execute(largeSource, Map.of(), Map.of(), new CancellationSignal())).code());
        var unrestricted = new RhinoJavascriptRuntime(Duration.ofSeconds(1),
                new JavascriptRuntimeLimits(32, 4, 12, 8, 4, 16));
        assertEquals("x".repeat(40), unrestricted.execute(
                largeSource, Map.of(), Map.of(), Map.of(), new CancellationSignal(),
                null, null, true).value().getAsString());
    }

    @Test
    void progressivelyDescribesDeclaredMinecraftPathsWithoutSelectingTheirValues() {
        MinecraftAgentHostGraph graph = new MinecraftAgentHostGraph(
                JavascriptAgentTestFixtures.context("schema-discovery"));

        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                """
                return {
                  roots: schema.list().map(root => root.name),
                  installed: schema.describe("game.mods.installed"),
                  providers: schema.describe("recipeCatalog.providers"),
                  selectedGame: typeof mc.game
                };
                """,
                graph.select(List.of("items")),
                Map.of(),
                new CancellationSignal());

        var value = execution.value().getAsJsonObject();
        assertTrue(value.getAsJsonArray("roots").asList().stream()
                .anyMatch(root -> root.getAsString().equals("registryEntries")));
        assertEquals(
                "list",
                value.getAsJsonObject("installed")
                        .getAsJsonObject("schema")
                        .get("kind")
                        .getAsString());
        assertEquals(
                "list",
                value.getAsJsonObject("providers")
                        .getAsJsonObject("schema")
                        .get("kind")
                        .getAsString());
        assertEquals("undefined", value.get("selectedGame").getAsString());
    }

    @Test
    void transformsDetachedMinecraftDataWithNormalJavascript() {
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();

        JavascriptExecution execution = runtime.execute(
                """
                const swords = mc.items
                  .filter(item => item.tags.includes("minecraft:swords"))
                  .map(item => ({id: item.id, damage: item.damage}))
                  .sort((a, b) => b.damage - a.damage);
                return {
                  strongest: swords[0],
                  totalDamage: swords.reduce((sum, item) => sum + item.damage, 0),
                  grouped: helpers.groupBy(swords, item => item.damage >= 10 ? "high" : "normal")
                };
                """,
                Map.of("items", JsonParser.parseString("""
                        [
                          {"id":"minecraft:iron_sword","damage":6,"tags":["minecraft:swords"]},
                          {"id":"example:obsidian_sword","damage":12,"tags":["minecraft:swords"]},
                          {"id":"minecraft:apple","damage":0,"tags":[]}
                        ]
                        """)),
                Map.of(),
                new CancellationSignal());

        assertEquals(
                "example:obsidian_sword",
                execution.value()
                        .getAsJsonObject()
                        .getAsJsonObject("strongest")
                        .get("id")
                        .getAsString());
        assertEquals(
                18,
                execution.value().getAsJsonObject().get("totalDamage").getAsInt());
        assertEquals(
                1,
                execution.value()
                        .getAsJsonObject()
                        .getAsJsonObject("grouped")
                        .getAsJsonArray("high")
                        .size());
    }

    @Test
    void supportsLexicalDeclarationsInsideRepeatedArrayCallbacks() {
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                """
                return mc.items.map(item => {
                  const value = item.value;
                  const selected = value + 1;
                  return selected;
                });
                """,
                Map.of("items", JsonParser.parseString(
                        "[{\"value\":1},{\"value\":2},{\"value\":3}]")),
                Map.of(),
                new CancellationSignal());

        assertEquals(
                JsonParser.parseString("[2,3,4]"),
                execution.value());
    }

    @Test
    void explainsTheKubeJsRhinoNestedCallbackLimitation() {
        JavascriptExecutionException failure = assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        """
                        return mc.items.map(item => {
                          const modifiers = item.modifiers;
                          let damage = null;
                          if (Array.isArray(modifiers)) {
                            const damageModifier = modifiers.find(modifier =>
                              modifier.type === "minecraft:attack_damage");
                            if (damageModifier) damage = damageModifier.amount;
                          }
                          return damage;
                        });
                        """,
                        Map.of("items", JsonParser.parseString("""
                                [
                                  {"modifiers":[{"type":"minecraft:attack_damage","amount":5}]},
                                  {"modifiers":[{"type":"minecraft:attack_damage","amount":9}]}
                                ]
                                """)),
                        Map.of(),
                        new CancellationSignal()));

        assertEquals("javascript_error", failure.code());
        assertTrue(failure.getMessage().contains("indexed loop"));
    }

    @Test
    void runsTheExactBundledHighestDamageSwordExampleAgainstMinecraft26Shape() {
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                bundledExample("Highest-damage sword"),
                Map.of("items", JsonParser.parseString("""
                        [
                          {
                            "id":"minecraft:iron_sword",
                            "displayName":"Iron Sword",
                            "properties":{"minecraft:attribute_modifiers":[
                              {
                                "type":"minecraft:attack_damage",
                                "id":"minecraft:base_attack_damage",
                                "slot":"mainhand",
                                "amount":5
                              }
                            ]}
                          },
                          {
                            "id":"example:steel_sword",
                            "displayName":"Steel Sword",
                            "properties":{"minecraft:attribute_modifiers":[
                              {
                                "type":"minecraft:attack_damage",
                                "id":"minecraft:base_attack_damage",
                                "slot":"mainhand",
                                "amount":9
                              }
                            ]}
                          },
                          {
                            "id":"example:steel_knife",
                            "displayName":"Steel Knife",
                            "properties":{"minecraft:attribute_modifiers":[
                              {
                                "type":"minecraft:attack_damage",
                                "id":"minecraft:base_attack_damage",
                                "slot":"mainhand",
                                "amount":12
                              }
                            ]}
                          }
                        ]
                        """)),
                Map.of(),
                new CancellationSignal());

        assertEquals(
                "example:steel_sword",
                execution.value()
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsString());
        assertEquals(
                9,
                execution.value()
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("damage")
                        .getAsInt());
        assertEquals(2, execution.value().getAsJsonArray().size());
    }

    @Test
    void runsTheExactBundledLeastMaterialContainerExample() {
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                bundledExample("Least-material container recipe"),
                Map.of(
                        "items", JsonParser.parseString("""
                                [
                                  {"id":"minecraft:chest","tags":["minecraft:container"]},
                                  {"id":"minecraft:barrel","tags":["minecraft:container"]},
                                  {"id":"minecraft:stick","tags":[]}
                                ]
                                """),
                        "recipes", JsonParser.parseString("""
                                [
                                  {
                                    "id":"minecraft:chest",
                                    "outputs":[{"stack":{"itemId":"minecraft:chest"}}],
                                    "ingredients":[{"count":8,"consumed":true}],
                                    "catalysts":[],
                                    "fluids":[]
                                  },
                                  {
                                    "id":"minecraft:barrel",
                                    "outputs":[{"stack":{"itemId":"minecraft:barrel"}}],
                                    "ingredients":[
                                      {"count":3,"consumed":true},
                                      {"count":3,"consumed":true}
                                    ],
                                    "catalysts":[],
                                    "fluids":[]
                                  }
                                ]
                                """)),
                Map.of(),
                new CancellationSignal());

        assertEquals(2, execution.value().getAsJsonArray().size());
        assertEquals(
                "minecraft:barrel",
                execution.value()
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("output")
                        .getAsString());
        assertEquals(
                6,
                execution.value()
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .get("consumedItems")
                        .getAsInt());
    }

    @Test
    void runsTheExactBundledStrongestPoisonExample() {
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                bundledExample("Strongest poison effect and its production path"),
                Map.of(
                        "items", JsonParser.parseString("""
                                [
                                  {
                                    "id":"minecraft:poisonous_potato",
                                    "properties":{"minecraft:effects":[
                                      {"id":"minecraft:poison","amplifier":0,"duration":100}
                                    ]}
                                  },
                                  {
                                    "id":"example:venom_flask",
                                    "properties":{"minecraft:effects":[
                                      {"id":"minecraft:poison","amplifier":2,"duration":300}
                                    ]}
                                  }
                                ]
                                """),
                        "recipes", JsonParser.parseString("""
                                [
                                  {
                                    "id":"example:venom_flask_recipe",
                                    "outputs":[{"stack":{"itemId":"example:venom_flask"}}]
                                  },
                                  {
                                    "id":"minecraft:baked_potato",
                                    "outputs":[{"stack":{"itemId":"minecraft:baked_potato"}}]
                                  }
                                ]
                                """)),
                Map.of(),
                new CancellationSignal());

        assertEquals(
                "example:venom_flask",
                execution.value()
                        .getAsJsonObject()
                        .getAsJsonObject("best")
                        .get("itemId")
                        .getAsString());
        assertEquals(
                "example:venom_flask_recipe",
                execution.value()
                        .getAsJsonObject()
                        .getAsJsonArray("recipes")
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsString());
    }

    @Test
    void reopensOnlyExplicitWorkspaceValues() {
        Map<String, com.google.gson.JsonElement> values =
                Map.of("r_1", JsonParser.parseString("[3,8,5]"));

        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                "return helpers.maxBy(workspace.open('r_1'), value => value);",
                Map.of(),
                values,
                new CancellationSignal());

        assertEquals(8, execution.value().getAsInt());
        JavascriptExecutionException unavailable = assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return workspace.open('r_other');",
                        Map.of(),
                        values,
                        new CancellationSignal()));
        assertEquals("workspace_handle_unavailable", unavailable.code());
        assertTrue(unavailable.getMessage().contains("unavailable"));
    }

    @Test
    void loadsBundledModulesByExactIdAndReportsTheirUse() {
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                """
                const first = require("openallay:crafting");
                const second = require("openallay:crafting");
                return {
                  same: first === second,
                  cost: first.recipeCost({
                    ingredients: [
                      {count: 2, consumed: true},
                      {count: 1, consumed: true}
                    ],
                    catalysts: [{count: 1, consumed: false}],
                    fluids: []
                  })
                };
                """,
                Map.of(),
                Map.of(),
                new CancellationSignal());

        assertTrue(execution.value().getAsJsonObject().get("same").getAsBoolean());
        assertEquals(
                3,
                execution.value()
                        .getAsJsonObject()
                        .getAsJsonObject("cost")
                        .get("consumedItems")
                        .getAsInt());
        assertEquals(List.of("openallay:crafting"), execution.modules());
    }

    @Test
    void rejectsUnknownBundledModules() {
        JavascriptExecutionException failure = assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "return require('example:missing');",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal()));

        assertEquals("javascript_module_unavailable", failure.code());
    }

    @Test
    void safeScopeDoesNotExposeJavaOrHostFacilities() {
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
                """
                return {
                  packages: typeof Packages,
                  java: typeof Java,
                  adapter: typeof JavaAdapter,
                  load: typeof load,
                  quit: typeof quit
                };
                """,
                Map.of(),
                Map.of(),
                new CancellationSignal());

        execution.value()
                .getAsJsonObject()
                .entrySet()
                .forEach(entry -> assertEquals("undefined", entry.getValue().getAsString()));
    }

    @Test
    void rejectsCyclesAndNonFiniteNumbers() {
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        JavascriptExecutionException cycle = assertThrows(
                JavascriptExecutionException.class,
                () -> runtime.execute(
                        "const value = {}; value.self = value; return value;",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal()));
        assertEquals("javascript_result_invalid", cycle.code());

        JavascriptExecutionException infinity = assertThrows(
                JavascriptExecutionException.class,
                () -> runtime.execute(
                        "return Infinity;",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal()));
        assertEquals("javascript_result_invalid", infinity.code());
    }

    @Test
    void stopsInfiniteScriptsAtDeadline() {
        RhinoJavascriptRuntime runtime =
                new RhinoJavascriptRuntime(Duration.ofMillis(25));

        JavascriptExecutionException timeout = assertThrows(
                JavascriptExecutionException.class,
                () -> runtime.execute(
                        "while (true) {}",
                        Map.of(),
                        Map.of(),
                        new CancellationSignal()));

        assertEquals("javascript_timeout", timeout.code());
    }

    @Test
    void rejectsOversizedSourceAndResultsBeforeTheyEnterAWorkspace() {
        JavascriptRuntimeLimits limits =
                new JavascriptRuntimeLimits(32, 4, 12, 8, 4, 16);
        RhinoJavascriptRuntime runtime =
                new RhinoJavascriptRuntime(Duration.ofSeconds(1), limits);

        assertEquals(
                "javascript_source_too_large",
                assertThrows(
                                JavascriptExecutionException.class,
                                () -> runtime.execute(
                                        "return '" + "x".repeat(40) + "';",
                                        Map.of(),
                                        Map.of(),
                                        new CancellationSignal()))
                        .code());
        assertEquals(
                "javascript_result_budget_exceeded",
                assertThrows(
                                JavascriptExecutionException.class,
                                () -> runtime.execute(
                                        "return [1,2,3,4,5,6,7,8,9];",
                                        Map.of(),
                                        Map.of(),
                                        new CancellationSignal()))
                        .code());
        assertEquals(
                "javascript_result_budget_exceeded",
                assertThrows(
                                JavascriptExecutionException.class,
                                () -> runtime.execute(
                                        "return 'abcdefghijklmnopq';",
                                        Map.of(),
                                        Map.of(),
                        new CancellationSignal()))
                        .code());
    }

    @Test
    void readsRecordComponentsWithoutStringifyingOrSerializingTheInput() {
        record DirectValue(String id, List<Integer> values) {
            @Override
            public String toString() {
                throw new AssertionError("The direct host path must not stringify snapshots");
            }
        }
        DirectValue direct = new DirectValue("direct", List.of(2, 4, 6));

        JavascriptExecution result = new RhinoJavascriptRuntime().execute(
                "return {id: mc.fixture.id, total: helpers.sum(mc.fixture.values)};",
                Map.of("fixture", direct),
                Map.of(),
                new CancellationSignal());

        assertEquals("direct", result.value().getAsJsonObject().get("id").getAsString());
        assertEquals(12, result.value().getAsJsonObject().get("total").getAsInt());
    }

    private static String bundledExample(String heading) {
        String path = "dev/openallay/script/rhino-examples.md";
        try (var input = RhinoJavascriptRuntimeTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing bundled Skill reference " + path);
            }
            String document = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int section = document.indexOf("## " + heading);
            int fence = document.indexOf("```js\n", section);
            int end = document.indexOf("\n```", fence + 6);
            if (section < 0 || fence < 0 || end < 0) {
                throw new AssertionError("Missing JavaScript example " + heading);
            }
            return document.substring(fence + 6, end);
        } catch (IOException failure) {
            throw new AssertionError("Unable to read bundled Skill example", failure);
        }
    }
}
