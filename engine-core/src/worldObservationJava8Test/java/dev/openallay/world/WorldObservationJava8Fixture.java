package dev.openallay.world;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.openallay.context.*;
import dev.openallay.context.game.ObservableGameStateSnapshot;
import dev.openallay.context.game.ObservableGameStateSnapshot.*;
import dev.openallay.json.EngineJson;
import dev.openallay.platform.InstalledModMetadata;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

/** Full actual detached observation frontier; shared original-modern / genuine8 reporter. */
public final class WorldObservationJava8Fixture {
    private interface Checked { void run() throws Exception; }
    private static boolean java8;
    private static final List<Object> VALUES = new ArrayList<>();
    private static final Gson JSON = EngineJson.create(builder -> builder.registerTypeAdapter(Optional.class,
            new JsonSerializer<Optional<?>>() {
                @Override public JsonElement serialize(Optional<?> value, Type type, JsonSerializationContext context) {
                    return value.isPresent() ? context.serialize(value.get()) : JsonNull.INSTANCE;
                }
            }));
    private WorldObservationJava8Fixture() {}
    public static void main(String[] args) throws Exception {
        java8 = args.length == 1 && args[0].equals("java8");
        if (java8) {
            equal("1.8", System.getProperty("java.specification.version"));
            for (String sourceOwner : Arrays.asList(
                "dev.openallay.context.BlockPositionSnapshot",
                "dev.openallay.context.CallerKind",
                "dev.openallay.context.CallerSnapshot",
                "dev.openallay.context.ContextMetrics",
                "dev.openallay.context.ContextValidation",
                "dev.openallay.context.DataAuthority",
                "dev.openallay.context.DataCompleteness",
                "dev.openallay.context.EvidenceMetadata",
                "dev.openallay.context.FluidRequirementSnapshot",
                "dev.openallay.context.IngredientAlternativeSnapshot",
                "dev.openallay.context.IngredientRequirementSnapshot",
                "dev.openallay.context.InventorySlotSnapshot",
                "dev.openallay.context.InventorySnapshot",
                "dev.openallay.context.ItemStackSnapshot",
                "dev.openallay.context.PlayerSnapshot",
                "dev.openallay.context.RecipeEntrySnapshot",
                "dev.openallay.context.RecipeLayoutSnapshot",
                "dev.openallay.context.RecipeOutputSnapshot",
                "dev.openallay.context.RecipeProcessingSnapshot",
                "dev.openallay.context.RecipeReference",
                "dev.openallay.context.RecipeSnapshot",
                "dev.openallay.context.RegistryEntrySnapshot",
                "dev.openallay.context.RegistrySnapshot",
                "dev.openallay.context.ToolInvocationContext",
                "dev.openallay.context.game.ObservableGameStateSnapshot",
                "dev.openallay.json.ConstructorValueJsonAdapter",
                "dev.openallay.json.EngineJson",
                "dev.openallay.json.RecordJsonAdapter",
                "dev.openallay.platform.InstalledModMetadata",
                "dev.openallay.recipe.RecipeCanonicalizer",
                "dev.openallay.recipe.RecipeCatalogDiagnostic",
                "dev.openallay.recipe.RecipeProviderDiagnostic",
                "dev.openallay.recipe.RecipeProviderSnapshot",
                "dev.openallay.recipe.RecipeProviderState",
                "dev.openallay.recipe.RecipeSemanticGroup",
                "dev.openallay.recipe.RecipeUnlockState",
                "dev.openallay.util.Java8Collections",
                "dev.openallay.util.Java8Hex",
                "dev.openallay.util.Java8Strings",
                "dev.openallay.value.ValueSchema",
                "dev.openallay.value.ValueSchemas",
                "dev.openallay.value.ValueType",
                "dev.openallay.world.WorldFocusObservation",
                "dev.openallay.world.WorldPosition",
                "dev.openallay.json.JsonReaders",
                "dev.openallay.json.JsonTrees")) classMajor(Class.forName(sourceOwner));
        }
        Instant time = Instant.ofEpochSecond(42, 17);
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000007");
        EvidenceMetadata evidence = new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.PARTIAL,
                time, "test:visible", "test:loaded_only", "1.12.2", "forge",
                Collections.singletonMap("test:boundary", "loaded-only; client-visible; not authoritative"));
        WorldPosition blockPosition = add(new WorldPosition(-7, 64, 9));
        equal(new WorldPosition(-9, 62, 7), blockPosition.subtract(new WorldPosition(2, 2, 2)));
        WorldFocusObservation.Camera camera = add(new WorldFocusObservation.Camera(-0.0d, 64, 9, -0.0f,
                2, 70, "first_person", true, false, actor));
        check(!camera.equals(new WorldFocusObservation.Camera(0.0d, 64, 9, -0.0f, 2, 70,
                "first_person", true, false, actor)), "signed zero camera equality");
        WorldFocusObservation.Position hit = add(new WorldFocusObservation.Position(-7, 64.5, 9));
        Map<String, String> properties = new LinkedHashMap<>(); properties.put("axis", "y");
        WorldFocusObservation.Block block = add(new WorldFocusObservation.Block("minecraft:oak_log",
                blockPosition, "up", true, null, properties, ""));
        properties.clear(); equal("y", block.properties().get("axis"));
        immutable(() -> block.properties().clear());
        WorldFocusObservation.Entity entity = add(new WorldFocusObservation.Entity(actor, 14, "minecraft:pig",
                "Pig", hit, blockPosition, true));
        WorldFocusObservation.Target target = add(new WorldFocusObservation.Target("block", hit, block, null));
        add(new WorldFocusObservation.Target("entity", hit, null, entity));
        add(new WorldFocusObservation.Target("miss", hit, null, null));
        add(new WorldFocusObservation.Target("none", null, null, null));
        JsonObject components = new JsonObject(); components.addProperty("test:custom", 3);
        WorldFocusObservation.Item item = add(new WorldFocusObservation.Item("minecraft:stone", 3, "Stone",
                0, 0, components, true, ""));
        int itemHash = item.hashCode(); String itemString = item.toString();
        components.addProperty("later", true); item.components().addProperty("getter", true);
        check(!item.components().has("later") && !item.components().has("getter"), "detached components");
        equal(itemHash, item.hashCode()); equal(itemString, item.toString());
        WorldFocusObservation.Item unavailable = add(new WorldFocusObservation.Item("minecraft:air", 0, "",
                0, 0, new JsonObject(), false, "Components unavailable"));
        WorldFocusObservation.Screen screen = add(new WorldFocusObservation.Screen("test.Screen", "Inventory",
                320, 240, false, true, "game_ui"));
        WorldFocusObservation.Menu menu = add(new WorldFocusObservation.Menu("test.Menu", 7, 9,
                "minecraft:chest", true, true, true, 27, item, ""));
        WorldFocusObservation.Hover hover = add(new WorldFocusObservation.Hover(-0.0d, 12, false,
                "slot", 3, 8, item, ""));
        add(new WorldFocusObservation.Hover(0, 0, true, "none", -1, -1, null, ""));
        add(new WorldFocusObservation.Hover(0, 0, false, "unavailable", -1, -1, null, "No native slot"));
        WorldFocusObservation focus = add(new WorldFocusObservation(time, actor, "minecraft:overworld", camera,
                target, item, unavailable, screen, menu, hover, evidence));
        InstalledModMetadata mod = add(new InstalledModMetadata("test", "Test", "1", null,
                Arrays.asList("Author"), Arrays.asList("MIT"), Collections.singletonMap("home", "test"),
                "client", Arrays.asList("minecraft")));
        SectionDiagnostic diagnostic = add(new SectionDiagnostic("loaded_only", "Visible loaded state"));
        List<SectionDiagnostic> diagnostics = new ArrayList<>(Arrays.asList(diagnostic));
        RuntimeState runtime = add(new RuntimeState("1.12.2", "forge", false, "remote", evidence, diagnostics));
        diagnostics.clear(); equal(1, runtime.diagnostics().size()); immutable(() -> runtime.diagnostics().clear());
        ModsState mods = add(new ModsState(Arrays.asList(mod), evidence, Arrays.asList(diagnostic)));
        OptionValue option = add(new OptionValue("video", "render_distance", "Render Distance", "8"));
        OptionsState options = add(new OptionsState(Arrays.asList(option), evidence, Collections.emptyList()));
        PackInfo pack = add(new PackInfo("vanilla", "Default", null, true, true, "compatible", "builtin"));
        PacksState packs = add(new PacksState(Arrays.asList(pack), Arrays.asList("vanilla"), evidence, Collections.emptyList()));
        ShaderState shaders = add(new ShaderState(false, "none", null, Collections.emptyMap(), evidence, Collections.emptyList()));
        DiagnosticValue diagnosticValue = add(new DiagnosticValue("performance", "fps", "60"));
        DiagnosticsState stateDiagnostics = add(new DiagnosticsState(Arrays.asList(diagnosticValue), evidence, Collections.emptyList()));
        InventorySnapshot inventory = new InventorySnapshot(Collections.emptyList(), 0, -1, -1,
                ItemStackSnapshot.empty(), true, evidence);
        PlayerSnapshot player = new PlayerSnapshot(actor, "Player", "minecraft:overworld",
                new BlockPositionSnapshot(-7, 64, 9), "survival", inventory, evidence);
        PlayerUiState playerUi = add(new PlayerUiState(player, "test.Screen", null, evidence,
                Collections.emptyList(), Optional.of(focus)));
        add(new PlayerUiState(player, "none", null, evidence, Collections.emptyList()));
        QueryValue query = add(new QueryValue("time", "42", false, "Client-visible synchronized time"));
        WorldQueriesState queries = add(new WorldQueriesState(Collections.singletonMap("time", query), evidence, Collections.emptyList()));
        ObservableGameStateSnapshot snapshot = add(new ObservableGameStateSnapshot(time, runtime, mods, options,
                packs, shaders, stateDiagnostics, playerUi, queries));
        CallerSnapshot caller = new CallerSnapshot(CallerKind.PLAYER, actor, "Player", true);
        ContextMetrics metrics = new ContextMetrics(0, 0, 0, 20, 10);
        RecipeSnapshot recipes = new RecipeSnapshot(evidence, Collections.emptyList());
        RegistrySnapshot registries = new RegistrySnapshot(evidence, Collections.emptyList());
        ToolInvocationContext context = add(new ToolInvocationContext("trace:world", time, caller, Optional.of(player),
                Optional.of(registries), Optional.of(recipes), Optional.of(snapshot), metrics, true));
        check(context.unrestrictedJavascript(), "existing unrestricted flag preserved");
        check(!new ToolInvocationContext("trace:short", time, caller, Optional.of(player), Optional.empty(),
                Optional.empty(), metrics).unrestrictedJavascript(), "overload default preserved");
        check(!new ToolInvocationContext("trace:middle", time, caller, Optional.of(player), Optional.empty(),
                Optional.empty(), Optional.of(snapshot), metrics).unrestrictedJavascript(), "middle overload default");
        equal(CallerKind.CONSOLE, ToolInvocationContext.developmentConsole("trace:console").caller().kind());
        fail("identity", () -> new ToolInvocationContext("trace:bad", time,
                new CallerSnapshot(CallerKind.PLAYER, UUID.fromString("00000000-0000-0000-0000-000000000008"), "Other", true),
                Optional.of(player), Optional.empty(), Optional.empty(), metrics));
        fail("blankCorrelation", () -> new ToolInvocationContext(" ", time, caller, Optional.empty(), Optional.empty(), Optional.empty(), metrics));
        fail("nullOptional", () -> new ToolInvocationContext("trace:null", time, caller, null, Optional.empty(), Optional.empty(), metrics));
        fail("focusTime", () -> new WorldFocusObservation(Instant.EPOCH, actor, "minecraft:overworld", camera,
                target, item, unavailable, screen, menu, hover, evidence));
        fail("dimension", () -> new WorldFocusObservation(time, actor, " ", camera, target, item, unavailable, screen, menu, hover, evidence));
        fail("cameraFinite", () -> new WorldFocusObservation.Camera(Double.NaN, 0, 0, 0, 0, 70, "first", true, false, actor));
        fail("positionFinite", () -> new WorldFocusObservation.Position(0, Double.POSITIVE_INFINITY, 0));
        fail("targetPayload", () -> new WorldFocusObservation.Target("block", hit, null, null));
        fail("noneHit", () -> new WorldFocusObservation.Target("none", hit, null, null));
        fail("targetKind", () -> new WorldFocusObservation.Target("unknown", null, null, null));
        fail("itemCount", () -> new WorldFocusObservation.Item("minecraft:stone", -1, "Stone", 0, 0, new JsonObject(), true, ""));
        fail("availableDiagnostic", () -> new WorldFocusObservation.Item("minecraft:stone", 1, "Stone", 0, 0, new JsonObject(), true, "failure"));
        fail("unavailableDiagnostic", () -> new WorldFocusObservation.Item("minecraft:stone", 1, "Stone", 0, 0, new JsonObject(), false, " "));
        fail("screenSize", () -> new WorldFocusObservation.Screen("", "", -1, 0, false, false, "gameplay"));
        fail("screenRole", () -> new WorldFocusObservation.Screen("", "", 0, 0, false, false, "unknown"));
        fail("menuType", () -> new WorldFocusObservation.Menu("test.Menu", 0, 0, "", true, true, true, 0, item, ""));
        fail("slotIdentity", () -> new WorldFocusObservation.Hover(0, 0, false, "slot", -1, 0, item, ""));
        fail("nonSlotPayload", () -> new WorldFocusObservation.Hover(0, 0, false, "none", 0, 0, item, ""));
        fail("hoverFinite", () -> new WorldFocusObservation.Hover(Double.NaN, 0, false, "none", -1, -1, null, ""));
        fail("modBlank", () -> new InstalledModMetadata(" ", "Test", "1", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), "client", Collections.emptyList()));
        fail("queryAuthorityNote", () -> new QueryValue("time", "42", false, " "));
        fail("snapshotSection", () -> new ObservableGameStateSnapshot(time, null, mods, options, packs, shaders, stateDiagnostics, playerUi, queries));
        expectNull("nullListElement", () -> new ModsState(Arrays.asList((InstalledModMetadata) null), evidence, Collections.emptyList()));
        immutable(() -> mods.installed().clear()); immutable(() -> queries.values().clear());
        Set<Class<?>> seen = new HashSet<>();
        for (Object value : VALUES) {
            inspect(value); seen.add(value.getClass());
            System.out.println(value.getClass().getName() + "=" + canonical(JSON.toJsonTree(value)));
        }
        equal(27, seen.size());
        equal(time, focus.capturedAt()); equal(actor, focus.actorId());
        equal(evidence, focus.evidence()); equal(time, snapshot.capturedAt());
        equal(DataAuthority.CLIENT_VISIBLE, context.observableGameState().get().worldQueries().evidence().authority());
        equal(DataCompleteness.PARTIAL, context.observableGameState().get().worldQueries().evidence().completeness());
        check(!context.observableGameState().get().worldQueries().values().get("time").authoritative(), "client value not upgraded");
        System.out.println("PASS 27 actual observation values; identity/evidence/loaded-only/copy/schema/hash contracts");
    }
    private static <T> T add(T value) { VALUES.add(value); return value; }
    private static void inspect(Object value) throws Exception {
        Class<?> owner = value.getClass(); List<String> names = new ArrayList<>(); List<Object> arguments = new ArrayList<>();
        if (java8) {
            classMajor(owner); ValueSchema<?> schema = ValueSchemas.of(owner);
            for (ValueSchema.Component<?> component : schema.components()) {
                names.add(component.name()); arguments.add(owner.getMethod(component.name()).invoke(value));
                equal(owner.getDeclaredField(component.name()).getType(), component.rawType());
                equal(owner.getMethod(component.name()).getGenericReturnType(), component.genericType());
            }
            equal(value, schema.construct(arguments.toArray()));
        } else {
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(owner);
            for (Object component : components) {
                Class<?> metadata = Class.forName("java.lang.reflect.RecordComponent");
                String name = (String) metadata.getMethod("getName").invoke(component);
                names.add(name); arguments.add(owner.getMethod(name).invoke(value));
            }
        }
        int hash = 0; StringBuilder text = new StringBuilder(owner.getSimpleName()).append('[');
        for (int i = 0; i < names.size(); i++) {
            hash = 31 * hash + Objects.hashCode(arguments.get(i));
            if (i > 0) text.append(", "); text.append(names.get(i)).append('=').append(arguments.get(i));
        }
        text.append(']'); equal(hash, value.hashCode()); equal(text.toString(), value.toString());
        check(!value.equals(new Object()), "exact owner equality");
    }
    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) sorted.put(entry.getKey(), entry.getValue());
            JsonObject result = new JsonObject(); for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) result.add(entry.getKey(), canonical(entry.getValue()));
            return result;
        }
        if (value.isJsonArray()) { JsonArray result = new JsonArray(); for (JsonElement child : value.getAsJsonArray()) result.add(canonical(child)); return result; }
        return value;
    }
    private static void fail(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void expectNull(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected null rejection"); }
        catch (NullPointerException expected) { System.out.println(name + "=NullPointerException"); }
    }
    private static void immutable(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected immutable container"); } catch (UnsupportedOperationException expected) {}
    }
    private static void classMajor(Class<?> owner) throws Exception {
        try (InputStream input = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int position = 0;
            while (position < 8) { int count = input.read(header, position, 8 - position); check(count > 0, "class header"); position += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static void equal(Object expected, Object actual) { check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
