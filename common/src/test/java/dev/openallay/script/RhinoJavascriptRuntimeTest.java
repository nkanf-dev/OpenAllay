package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.EvaluatorException;
import dev.latvian.mods.rhino.WrappedException;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
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
    void bundledJavaExamplesRunWithStaticInstanceAndCollectionCalls() throws IOException {
        String root = "assets/openallay/openallay_skills/unrestricted-javascript/";
        int examples = 0;
        for (String file : List.of("SKILL.md", "references/java-jvm.md")) {
            String document;
            try (var input = getClass().getClassLoader().getResourceAsStream(root + file)) {
                document = new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
            }
            int offset = 0;
            while ((offset = document.indexOf("```javascript\n", offset)) >= 0) {
                examples++;
                int start = offset + "```javascript\n".length();
                int end = document.indexOf("\n```", start);
                assertTrue(end >= start, "JavaScript example must have a closing fence");
                var result = new RhinoJavascriptRuntime().execute(document.substring(start, end),
                        Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, true).value();
                if (result.isJsonPrimitive()) assertTrue(result.getAsString().startsWith("Java "));
                else if (result.getAsJsonObject().has("values")) {
                    assertEquals(2, result.getAsJsonObject().get("size").getAsInt());
                    assertEquals(JsonParser.parseString("[\"stone\",\"dirt\"]"), result.getAsJsonObject().get("values"));
                } else assertEquals("config", result.getAsJsonObject().get("name").getAsString());
                offset = end + 4;
            }
        }
        assertEquals(4, examples);
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
                graph.open(),
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
        assertEquals("object", value.get("selectedGame").getAsString());
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
    void reportsTheActualKubeJsRhinoNestedCallbackErrorWithoutRepairAdvice() {
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
        assertTrue(failure.getMessage().contains("redeclaration of var"), failure.getMessage());
        assertFalse(failure.getMessage().contains("indexed loop"));
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
    void executableAndUndefinedResultsOfferRecoveryWithoutClaimingMissingGameData() {
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        for (String source : List.of("return function () {};", "return require('openallay:crafting');")) {
            JavascriptExecutionException executable = assertThrows(
                    JavascriptExecutionException.class,
                    () -> runtime.execute(source, Map.of(), Map.of(), new CancellationSignal()));
            assertEquals("javascript_result_invalid", executable.code());
            assertTrue(executable.getMessage().contains("contains a function"));
            assertTrue(executable.getMessage().contains("Return JSON data from the operation"));
            assertTrue(executable.getMessage().contains("not the function or module itself; omit function properties"));
            assertTrue(executable.getMessage().contains("does not mean the operation is unavailable"));
        }
        JavascriptExecutionException undefined = assertThrows(
                JavascriptExecutionException.class,
                () -> runtime.execute("return mc.missing;", Map.of(), Map.of(), new CancellationSignal()));
        assertEquals("javascript_result_invalid", undefined.code());
        assertTrue(undefined.getMessage().contains("result is undefined"));
        assertTrue(undefined.getMessage().contains("selected roots and documented fields"));
        assertTrue(undefined.getMessage().contains("does not mean game data is unavailable"));
        assertEquals(3, runtime.execute("""
                return require("openallay:crafting").recipeCost({
                  ingredients: [{count: 3, consumed: true}], catalysts: [], fluids: []
                }).consumedItems;
                """, Map.of(), Map.of(), new CancellationSignal()).value().getAsInt());
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

    @Test
    void referenceErrorsUseUserLinesAndNamedScriptFramesInBothModes() {
        String source = """
                function inspectItem() {
                  return missingItem.id;
                }
                return inspectItem();
                """;
        for (boolean unrestricted : List.of(false, true)) {
            JavascriptExecutionException failure = assertThrows(
                    JavascriptExecutionException.class,
                    () -> new RhinoJavascriptRuntime().execute(
                            source, Map.of(), Map.of(), Map.of(), new CancellationSignal(),
                            null, null, unrestricted));

            assertEquals("javascript_error", failure.code());
            assertTrue(failure.getMessage().contains("ReferenceError:"), failure.getMessage());
            assertTrue(failure.getMessage().contains("missingItem"), failure.getMessage());
            assertTrue(failure.getMessage().contains("inspectItem (openallay-agent.js:2)"), failure.getMessage());
            assertFalse(failure.getMessage().contains("openallay-runtime.js"));
            assertFalse(failure.getMessage().contains("dev.latvian"));
            assertFalse(failure.getMessage().contains("RhinoJavascriptRuntime.java"));
        }
    }

    @Test
    void typeErrorsAndSyntaxErrorsHaveSourceRelativeLocations() {
        for (String source : List.of(
                "const item = null;\nreturn item.id;",
                "const item = {};\nconst broken = ;\nreturn item;",
                "const item = 1;\nconst broken = (")) {
            JavascriptExecutionException failure = assertThrows(
                    JavascriptExecutionException.class,
                    () -> new RhinoJavascriptRuntime().execute(
                            source, Map.of(), Map.of(), new CancellationSignal()));

            assertEquals("javascript_error", failure.code());
            assertTrue(failure.getMessage().contains(
                    source.contains("broken") ? "SyntaxError:" : "TypeError:"), failure.getMessage());
            assertTrue(failure.getMessage().contains("openallay-agent.js:2"), failure.getMessage());
        }
    }

    @Test
    void moduleErrorsOnlyExposeRegisteredModuleAndUserFramesWithRelativeLines() {
        var runtime = new RhinoJavascriptRuntime(Duration.ofSeconds(1), JavascriptRuntimeLimits.DEFAULT,
                new JavascriptModuleCatalog(Map.of("example:broken", "const value = 1;\nmissingModuleCall();")));
        JavascriptExecutionException failure = assertThrows(
                JavascriptExecutionException.class,
                () -> runtime.execute("return require('example:broken');",
                        Map.of(), Map.of(), new CancellationSignal()));

        assertEquals("javascript_module_error", failure.code());
        assertTrue(failure.getMessage().contains("ReferenceError:"), failure.getMessage());
        assertTrue(failure.getMessage().contains("openallay-module-example:broken.js:2"), failure.getMessage());
        assertTrue(failure.getMessage().contains("openallay-agent.js:1"), failure.getMessage());
        assertFalse(failure.getMessage().contains("openallay-runtime.js"));
        assertFalse(failure.getMessage().contains(".java:"));
    }

    @Test
    void nativeErrorsKeepTheirSafeClassAndMessageWithoutTheNativeStack() {
        JavascriptExecutionException failure = assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "const Integer = Java.type('java.lang.Integer');\nreturn Integer.parseInt('not-a-number');",
                        Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, true));

        assertEquals("javascript_error", failure.code());
        assertTrue(failure.getMessage().contains("NumberFormatException:"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not-a-number"), failure.getMessage());
        assertTrue(failure.getMessage().contains("openallay-agent.js:2"), failure.getMessage());
        assertFalse(failure.getMessage().contains("java.lang."));
        assertFalse(failure.getMessage().contains(".java:"));

        JavascriptExecutionException missingFile = assertThrows(
                JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(
                        "const MissingFile = Java.type('java.nio.file.NoSuchFileException');\nthrow new MissingFile('report.txt');",
                        Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, true));
        assertTrue(missingFile.getMessage().contains("NoSuchFileException: report.txt"), missingFile.getMessage());
        assertTrue(missingFile.getMessage().contains("openallay-agent.js:2"), missingFile.getMessage());
    }

    @Test
    void javascriptAndNativeDiagnosticsPreservePlayerSuppliedValuesWithoutScanning() {
        String data = "api_key=sample-game-value password='sample password' "
                + "Authorization: Bearer sample-bearer-value sk-examplevalue123456";
        for (String source : List.of(
                "throw new Error(" + new com.google.gson.Gson().toJson(data) + ");",
                "const Integer = Java.type('java.lang.Integer');\nreturn Integer.parseInt("
                        + new com.google.gson.Gson().toJson(data) + ");")) {
            JavascriptExecutionException failure = assertThrows(
                    JavascriptExecutionException.class,
                    () -> new RhinoJavascriptRuntime().execute(
                            source, Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, true));
            String diagnostic = failure.getMessage();
            assertTrue(diagnostic.contains(data), diagnostic);
            assertFalse(diagnostic.contains("[REDACTED]"), diagnostic);
            assertTrue(diagnostic.contains("openallay-agent.js:"), diagnostic);
            assertFalse(diagnostic.contains("RhinoJavascriptRuntimeTest.java"));
        }
        String longValue = "sk-" + "z".repeat(600);
        JavascriptExecutionException longError = assertThrows(JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute("throw new Error('" + longValue + "');",
                        Map.of(), Map.of(), new CancellationSignal()));
        assertTrue(longError.getMessage().contains(longValue));
        for (String dataValue : List.of("Authorization: Basic dXNlcjpwYXNzd29yZA==",
                "Cookie: first=private; second=hidden")) {
            assertEquals("IllegalArgumentException: " + dataValue,
                    JavascriptFailureFormatter.format(new IllegalArgumentException(dataValue)));
        }
    }

    @Test
    void formattingDoesNotRunErrorGettersOrStringifyThrownObjects() {
        for (String source : List.of(
                "throw {toString() { throw new Error('formatter executed user code'); }};",
                "const error = new Error('safe'); Object.defineProperty(error, 'message', "
                        + "{get() { throw new Error('formatter executed user code'); }}); throw error;")) {
            JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                    () -> new RhinoJavascriptRuntime().execute(source, Map.of(), Map.of(), new CancellationSignal()));
            assertEquals("javascript_error", failure.code());
            assertTrue(failure.getMessage().contains("Thrown value has no plain-text message"), failure.getMessage());
            assertFalse(failure.getMessage().contains("formatter executed user code"));
        }
    }

    @Test
    void modExceptionMessagesKeepUsefulUrlsJsonAndMultilineDetailsWithoutNativeFrames() {
        Context context = new OpenAllayRhinoContextFactory(new CancellationSignal(), Duration.ofSeconds(1), false).enter();
        var formatter = new JavascriptFailureFormatter("return 1;");
        EvaluatorException foreign = new EvaluatorException(context, "unexpected token",
                "/private/provider/secret.js", 29);
        String diagnostic = formatter.format(foreign, context);
        assertTrue(diagnostic.contains("SyntaxError: unexpected token"));
        assertFalse(diagnostic.contains("/private/provider"));

        String message = "Cannot resolve https://example.invalid/schema\n"
                + "{\"field\":\"blockstate\",\"password\":\"private-password\"}\n"
                + "The actual reason remains available. " + "detail ".repeat(1000);
        RuntimeException modFailure = new ModOperationException(message);
        diagnostic = formatter.format(modFailure, context);
        assertTrue(diagnostic.startsWith("ModOperationException:"), diagnostic);
        assertTrue(diagnostic.contains("https://example.invalid/schema"), diagnostic);
        assertTrue(diagnostic.contains("\"field\":\"blockstate\""), diagnostic);
        assertTrue(diagnostic.contains("The actual reason remains available."));
        assertTrue(diagnostic.endsWith("detail ".repeat(1000)), "message must not be clipped");
        assertEquals("ModOperationException: " + message, diagnostic,
                "player-supplied exception values are retained without field-name scanning");
        assertTrue(diagnostic.contains("\"password\":\"private-password\""));
        assertFalse(diagnostic.contains("RhinoJavascriptRuntimeTest.java"));
    }

    @Test
    void allRegisteredScriptFramesSurviveWithoutAnArbitraryFrameCap() {
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            // Preserve real caller frames instead of allowing Rhino's tail-call optimization.
            source.append("function step").append(index).append("() { const value = ")
                    .append(index == 39 ? "missingValue" : "step" + (index + 1) + "()")
                    .append("; return value + 1; }\n");
        }
        source.append("return step0();");
        JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                () -> new RhinoJavascriptRuntime().execute(source.toString(), Map.of(), Map.of(), new CancellationSignal()));
        assertTrue(failure.getMessage().contains("step39 (openallay-agent.js:40)"), failure.getMessage());
        assertTrue(failure.getMessage().contains("step0 (openallay-agent.js:1)"), failure.getMessage());
    }

    private static final class ModOperationException extends RuntimeException {
        private ModOperationException(String message) { super(message); }
    }

    @Test
    void helpersStayFrozenAndPrivateAcrossExecutions() {
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        JavascriptExecution execution = runtime.execute("""
                return {
                  frozen: Object.isFrozen(helpers),
                  nativeRepeat: typeof __nativeRepeat,
                  guard: typeof __openallayGuardedStringSize,
                  schemaView: typeof __openallayArrayView
                };
                """, Map.of(), Map.of(), new CancellationSignal());
        assertTrue(execution.value().getAsJsonObject().get("frozen").getAsBoolean());
        for (String key : List.of("nativeRepeat", "guard", "schemaView")) {
            assertEquals("undefined", execution.value().getAsJsonObject().get(key).getAsString());
        }
        assertThrows(JavascriptExecutionException.class,
                () -> runtime.execute("helpers.sum = () => 99;", Map.of(), Map.of(), new CancellationSignal()));
        assertEquals(3, runtime.execute("return helpers.sum([1,2]);",
                Map.of(), Map.of(), new CancellationSignal()).value().getAsInt());
    }

    @Test
    void wrappedControlFailuresKeepHostCodesAndCancellationIdentity() {
        Context context = new OpenAllayRhinoContextFactory(new CancellationSignal(), Duration.ofSeconds(1), false).enter();
        JavascriptExecutionException host = new JavascriptExecutionException("workspace_handle_unavailable", "unavailable");
        assertSame(host, assertThrows(JavascriptExecutionException.class,
                () -> JavascriptFailureFormatter.rethrowControlFailure(new WrappedException(context, host))));

        CancellationSignal cancellation = new CancellationSignal();
        cancellation.cancel();
        ModelClientException cancelled = assertThrows(ModelClientException.class, cancellation::throwIfCancelled);
        assertSame(cancelled, assertThrows(ModelClientException.class,
                () -> JavascriptFailureFormatter.rethrowControlFailure(new WrappedException(context, cancelled))));
        assertEquals("agent_cancelled", assertThrows(ModelClientException.class,
                () -> new RhinoJavascriptRuntime().execute("return 1;", Map.of(), Map.of(), cancellation)).failure().code());
    }

    @Test
    void toolBoundaryRetainsActualDiagnosticTextAndDoesNotDumpNativeStackFrames() {
        String diagnostic = "ReferenceError: api_key=player-game-value\nat openallay-agent.js:2";
        assertEquals("IllegalArgumentException: " + diagnostic,
                JavascriptFailureFormatter.format(new IllegalArgumentException(diagnostic)));
        String detail = "Unknown class: value\nRequested by the current script.";
        assertEquals("IllegalArgumentException: " + detail,
                JavascriptFailureFormatter.format(new IllegalArgumentException(detail)));
        assertEquals("IllegalArgumentException: password=player-game-value",
                JavascriptFailureFormatter.format(new IllegalArgumentException("password=player-game-value")));
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
