package dev.openallay.model.live;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.context.CallerKind;
import dev.openallay.context.CallerSnapshot;
import dev.openallay.context.ContextMetrics;
import dev.openallay.context.IngredientAlternativeSnapshot;
import dev.openallay.context.IngredientRequirementSnapshot;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeLayoutSnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeProcessingSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.context.RegistrySnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.InstalledModMetadata;
import dev.openallay.platform.PlatformService;
import dev.openallay.resource.mod.ModResourceSnapshot;
import dev.openallay.resource.runtime.ResourceRequestRegistry;
import dev.openallay.testing.GroundedTestFixtures;
import dev.openallay.tool.ToolRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Deterministic VFS tour dump for sword damage and container recipes. */
final class VfsTourPrintDemoTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void printSwordAndContainerVfsTours() {
        try (Runtime runtime = Runtime.open()) {
            System.out.println("===== VFS TOUR START =====");

            // Tour 1: discover swords / damage schema-ish browse
            runTour(runtime, 1, "resource_list",
                    argsPaths("/item/minecraft"));

            // Tour 2: grep swords
            runTour(runtime, 2, "resource_grep", argsGrep(
                    "/item", "sword", "TOKEN"));

            // Tour 3: query highest attack damage among sword-tagged items
            runTour(runtime, 3, "resource_query", argsQuery("""
                    {
                      "roots": ["/item"],
                      "pipeline": [
                        {"operation": "expand", "field": "/tags"},
                        {"operation": "filter", "field": "/tags", "operator": "EQ", "value": "minecraft:swords"},
                        {"operation": "sort", "field": "/properties/minecraft:attack_damage", "direction": "DESC"},
                        {"operation": "select", "fields": ["/@path", "/id", "/name", "/properties/minecraft:attack_damage"]},
                        {"operation": "take", "count": 3}
                      ]
                    }
                    """));

            // Tour 4: list recipe roots
            runTour(runtime, 4, "resource_list",
                    argsPaths("/recipe/minecraft"));

            // Tour 5: read the three container recipes
            runTour(runtime, 5, "resource_read",
                    argsPaths(
                            "/recipe/minecraft/chest",
                            "/recipe/minecraft/barrel",
                            "/recipe/minecraft/hopper"));

            // Tour 6: query container recipes sorted by ingredient requirement count
            runTour(runtime, 6, "resource_query", argsQuery("""
                    {
                      "roots": ["/recipe"],
                      "pipeline": [
                        {"operation": "filter", "field": "/id", "operator": "CONTAINS", "value": "chest"},
                        {"operation": "select", "fields": ["/id", "/ingredients", "/outputs"]},
                        {"operation": "take", "count": 5}
                      ]
                    }
                    """));

            runTour(runtime, 7, "resource_query", argsQuery("""
                    {
                      "roots": ["/recipe"],
                      "pipeline": [
                        {"operation": "filter", "field": "/id", "operator": "CONTAINS", "value": "barrel"},
                        {"operation": "select", "fields": ["/id", "/ingredients", "/outputs"]},
                        {"operation": "take", "count": 5}
                      ]
                    }
                    """));

            runTour(runtime, 8, "resource_query", argsQuery("""
                    {
                      "roots": ["/recipe"],
                      "pipeline": [
                        {"operation": "filter", "field": "/id", "operator": "CONTAINS", "value": "hopper"},
                        {"operation": "select", "fields": ["/id", "/ingredients", "/outputs"]},
                        {"operation": "take", "count": 5}
                      ]
                    }
                    """));

            System.out.println("===== VFS TOUR END =====");
        }
    }

    private static void runTour(Runtime runtime, int index, String tool, JsonObject arguments) {
        System.out.println();
        System.out.println("----- TOUR " + index + " tool=" + tool + " -----");
        System.out.println("[args]");
        System.out.println(GSON.toJson(arguments));
        AgentToolResult result = runtime.executor().execute(
                tool,
                arguments,
                runtime.context(),
                new CancellationSignal()).join();
        System.out.println("[failure]=" + result.failure());
        System.out.println("[normalized]");
        System.out.println(GSON.toJson(result.normalized()));
        System.out.println("[model_projection]");
        System.out.println(result.modelView().text());
    }

    private static JsonObject argsPaths(String... paths) {
        JsonObject args = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String path : paths) {
            arr.add(path);
        }
        args.add("paths", arr);
        return args;
    }

    private static JsonObject argsGrep(String root, String pattern, String mode) {
        JsonObject search = new JsonObject();
        JsonArray roots = new JsonArray();
        roots.add(root);
        search.add("roots", roots);
        search.addProperty("pattern", pattern);
        search.addProperty("mode", mode);
        JsonArray searches = new JsonArray();
        searches.add(search);
        JsonObject args = new JsonObject();
        args.add("searches", searches);
        return args;
    }

    private static JsonObject argsQuery(String planJson) {
        JsonObject plan = JsonParser.parseString(planJson).getAsJsonObject();
        JsonArray plans = new JsonArray();
        plans.add(plan);
        JsonObject args = new JsonObject();
        args.add("plans", plans);
        return args;
    }

    private static final class Runtime implements AutoCloseable {
        private final ResourceRequestRegistry resources;
        private final ResourceRequestRegistry.RequestHandle handle;
        private final LocalAgentToolExecutor executor;
        private final ToolInvocationContext context;

        private Runtime(
                ResourceRequestRegistry resources,
                ResourceRequestRegistry.RequestHandle handle,
                LocalAgentToolExecutor executor,
                ToolInvocationContext context) {
            this.resources = resources;
            this.handle = handle;
            this.executor = executor;
            this.context = context;
        }

        static Runtime open() {
            ToolInvocationContext context = buildContext("vfs-tour-demo");
            ResourceRequestRegistry resources = new ResourceRequestRegistry(
                    new TestPlatform(), new KnowledgeRegistry());
            long connection = resources.connectionGeneration(GroundedTestFixtures.PLAYER_ID);
            ResourceRequestRegistry.RequestHandle handle = resources.open(
                    GroundedTestFixtures.PLAYER_ID,
                    "vfs-tour",
                    UUID.nameUUIDFromBytes(context.correlationId()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    connection,
                    "client",
                    Set.of(
                            "resource_list",
                            "resource_read",
                            "resource_glob",
                            "resource_grep",
                            "resource_query"),
                    new dev.openallay.agent.context.ContextBudget(100_000, 4_096),
                    context);
            ToolRegistry tools = new ToolRegistry();
            tools.registerResourceTools("demo:vfs", resources);
            return new Runtime(
                    resources,
                    handle,
                    new LocalAgentToolExecutor(tools, new Gson()),
                    context);
        }

        LocalAgentToolExecutor executor() { return executor; }
        ToolInvocationContext context() { return context; }

        @Override
        public void close() {
            handle.close();
            resources.close();
        }
    }

    private static ToolInvocationContext buildContext(String correlation) {
        ArrayList<RegistryEntrySnapshot> entries = new ArrayList<>();
        entries.add(item("minecraft:wooden_sword", "Wooden Sword", 4.0D, Set.of("minecraft:swords")));
        entries.add(item("minecraft:iron_sword", "Iron Sword", 6.0D, Set.of("minecraft:swords")));
        entries.add(item("minecraft:diamond_sword", "Diamond Sword", 7.0D, Set.of("minecraft:swords")));
        entries.add(item("minecraft:netherite_sword", "Netherite Sword", 8.0D, Set.of("minecraft:swords")));
        entries.add(item("minecraft:oak_planks", "Oak Planks", null, Set.of("minecraft:planks")));
        entries.add(item("minecraft:iron_ingot", "Iron Ingot", null, Set.of()));
        entries.add(item("minecraft:chest", "Chest", null, Set.of("minecraft:containers")));
        entries.add(item("minecraft:barrel", "Barrel", null, Set.of("minecraft:containers")));
        entries.add(item("minecraft:hopper", "Hopper", null, Set.of("minecraft:containers")));

        RegistrySnapshot registries = new RegistrySnapshot(
                GroundedTestFixtures.serverEvidence(), entries);
        String generation = GroundedTestFixtures.RECIPE_GENERATION;
        List<RecipeEntrySnapshot> recipes = List.of(
                recipe("minecraft:chest", "Chest", generation,
                        List.of(requirement("planks", 8, "minecraft:oak_planks")),
                        "minecraft:chest"),
                recipe("minecraft:barrel", "Barrel", generation,
                        List.of(
                                requirement("planks", 6, "minecraft:oak_planks"),
                                requirement("slabs", 2, "minecraft:oak_planks")),
                        "minecraft:barrel"),
                recipe("minecraft:hopper", "Hopper", generation,
                        List.of(
                                requirement("iron", 5, "minecraft:iron_ingot"),
                                requirement("chest", 1, "minecraft:chest")),
                        "minecraft:hopper"));

        return new ToolInvocationContext(
                correlation,
                Instant.parse("2026-07-20T00:00:00Z"),
                new CallerSnapshot(CallerKind.PLAYER, GroundedTestFixtures.PLAYER_ID, "Builder", true),
                Optional.of(GroundedTestFixtures.player()),
                Optional.of(registries),
                Optional.of(new RecipeSnapshot(GroundedTestFixtures.serverEvidence(), recipes)),
                Optional.of(GroundedTestFixtures.observableGameState()),
                new ContextMetrics(10, 3, 3, 0, 0));
    }

    private static RegistryEntrySnapshot item(
            String id, String name, Double attackDamage, Set<String> tags) {
        Map<String, com.google.gson.JsonElement> properties = attackDamage == null
                ? Map.of()
                : Map.of("minecraft:attack_damage", new JsonPrimitive(attackDamage));
        return new RegistryEntrySnapshot(
                id, "item", name, id.substring(0, id.indexOf(":")), "minecraft:registry",
                List.of(name.toLowerCase(Locale.ROOT)), tags, Set.of(), properties);
    }

    private static IngredientRequirementSnapshot requirement(String key, long count, String itemId) {
        return new IngredientRequirementSnapshot(
                key, count, true,
                List.of(new IngredientAlternativeSnapshot("item", itemId, List.of(itemId))));
    }

    private static RecipeEntrySnapshot recipe(
            String recipeId, String outputName, String generation,
            List<IngredientRequirementSnapshot> inputs, String outputId) {
        return new RecipeEntrySnapshot(
                new RecipeReference("minecraft:recipe_manager", generation, recipeId),
                recipeId, "minecraft:crafting",
                new RecipeLayoutSnapshot(3, 3, true),
                "minecraft:crafting_table",
                inputs, List.of(), List.of(),
                List.of(new RecipeOutputSnapshot(new ItemStackSnapshot(outputId, 1, outputName), 1.0D)),
                List.of(), RecipeProcessingSnapshot.unknown(), List.of(), Map.of(),
                GroundedTestFixtures.serverEvidence());
    }

    private static final class TestPlatform implements PlatformService {
        @Override public String platformName() { return "common-test"; }
        @Override public String gameVersion() { return "26.2"; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public boolean isDevelopmentEnvironment() { return true; }
        @Override public List<InstalledModMetadata> installedMods() {
            return List.of(new InstalledModMetadata(
                    "openallay", "OpenAllay", "test", "fixture",
                    List.of(), List.of(), Map.of(), "client", List.of()));
        }
        @Override public ModResourceSnapshot captureModResources() {
            return ModResourceSnapshot.unavailable(Instant.EPOCH, "fixture");
        }
    }
}
