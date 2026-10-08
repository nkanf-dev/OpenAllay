package dev.openallay.guide.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.json.JsonReaders;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import dev.openallay.settings.ClientSettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


import dev.openallay.platform.minecraft.MinecraftNativeRegistries;


/** Explicit development-only oracle. No Builder classes or model-facing capability. */
final class GuideBuilderE2EProbe {
    @dev.openallay.value.ValueType(Anchor.ValueSchemaProvider.class)
static final class Anchor {
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    Anchor(int x, int y, int z, String dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String dimension() { return dimension; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Anchor)) return false;
        Anchor that = (Anchor) other;
        return x == that.x && y == that.y && z == that.z && java.util.Objects.equals(dimension, that.dimension);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(z);
        hash = 31 * hash + java.util.Objects.hashCode(dimension);
        return hash;
    }
    @Override public String toString() { return "Anchor[x=" + x + ", y=" + y + ", z=" + z + ", dimension=" + dimension + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Anchor> schema() {
            return new dev.openallay.value.ValueSchema<>(Anchor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Anchor>>asList(new dev.openallay.value.ValueSchema.Component<>(Anchor.class, "x", Anchor::x), new dev.openallay.value.ValueSchema.Component<>(Anchor.class, "y", Anchor::y), new dev.openallay.value.ValueSchema.Component<>(Anchor.class, "z", Anchor::z), new dev.openallay.value.ValueSchema.Component<>(Anchor.class, "dimension", Anchor::dimension)), arguments -> new Anchor((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Landmark.ValueSchemaProvider.class)
static final class Landmark {
    private final String name;
    private final int x;
    private final int y;
    private final int z;
    private final String id;
    private final Map<String, String> properties;
    Landmark(String name, int x, int y, int z, String id, Map<String, String> properties) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.id = id;
        this.properties = properties;
    }
    public String name() { return name; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String id() { return id; }
    public Map<String, String> properties() { return properties; }
Landmark(String name, int x, int y, int z, String id) {
            this(name, x, y, z, "minecraft:" + id, dev.openallay.util.Java8Collections.mapOf());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Landmark)) return false;
        Landmark that = (Landmark) other;
        return java.util.Objects.equals(name, that.name) && x == that.x && y == that.y && z == that.z && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(properties, that.properties);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(z);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(properties);
        return hash;
    }
    @Override public String toString() { return "Landmark[name=" + name + ", x=" + x + ", y=" + y + ", z=" + z + ", id=" + id + ", properties=" + properties + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Landmark> schema() {
            return new dev.openallay.value.ValueSchema<>(Landmark.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Landmark>>asList(new dev.openallay.value.ValueSchema.Component<>(Landmark.class, "name", Landmark::name), new dev.openallay.value.ValueSchema.Component<>(Landmark.class, "x", Landmark::x), new dev.openallay.value.ValueSchema.Component<>(Landmark.class, "y", Landmark::y), new dev.openallay.value.ValueSchema.Component<>(Landmark.class, "z", Landmark::z), new dev.openallay.value.ValueSchema.Component<>(Landmark.class, "id", Landmark::id), new dev.openallay.value.ValueSchema.Component<>(Landmark.class, "properties", Landmark::properties)), arguments -> new Landmark((String) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (String) arguments[4], (Map) arguments[5]));
        }
    }
}
    private GuideBuilderE2EProbe() {}

    static boolean enabled(String scenario) {
        return dev.openallay.util.Java8Collections.listOf("builder-restricted", "builder-acceptance", "builder-reload", "builder-partial", "builder-cancel", "builder-undo", "builder-server-denied", "builder-live-copy", "builder-live-undo", "builder-legacy-shapes").contains(scenario);
    }

    static void captureAnchor(String scenario, UUID actor, Consumer<Anchor> success, Consumer<String> failure,
            Consumer<String> phase) {
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        net.minecraft.client.server.IntegratedServer server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
        if (server == null) { failure.accept("No authoritative integrated server"); return; }
        phase.accept("anchor_submitted");
        dev.openallay.server.NativeServerOwner.execute(server, () -> {
            phase.accept("anchor_server_entered");
            try {
                net.minecraft.server.level.ServerPlayer player = dev.openallay.server.NativeServerOwner.player(server, actor);
                if (player == null) throw new IllegalStateException("Native player is unavailable");
                String create = System.getProperty("openallay.e2e.createWorld", "");
                String resume = System.getProperty("openallay.e2e.resumeWorld", "");
                boolean resumed = dev.openallay.util.Java8Strings.isBlank(create);
                String world = resumed ? resume : create;
                if (!Boolean.getBoolean(GuideClientE2EConfig.ENABLED) || !enabled(scenario)
                        || !dev.openallay.server.NativeServerOwner.isOwner(server) || server != dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client) || dev.openallay.server.NativeServerOwner.published(server)
                        || !world.matches("openallay-builder-[a-zA-Z0-9_.-]+")
                        || (!resumed && !dev.openallay.util.Java8Strings.isBlank(resume))
                        || (resumed && !dev.openallay.util.Java8Collections.listOf("builder-reload", "builder-live-undo").contains(scenario))
                        || !world.equals(dev.openallay.server.NativeServerOwner.worldName(server))
                        || !dev.openallay.server.NativeServerOwner.survival(server)
                        || GuideProbeWorldSettings.commandsAllowed(server) || !GuideProbeWorldSettings.isFlat(server)
                        || dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player).getSeed() != 17L
                        || !dev.openallay.platform.minecraft.MinecraftWorldSavePath.root(server).toAbsolutePath().normalize()
                                .equals(dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("saves").resolve(world).toAbsolutePath().normalize()))
                    throw new IllegalStateException("Builder setup requires the explicitly launched disposable fixture world");
                phase.accept("anchor_fixture_validated");
                GuideProbeWorldSettings.prepareBuilderFixture(server, resumed);
                phase.accept("anchor_fixture_prepared");
                Anchor anchor = new Anchor((int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.x(player)) + 8,
                        (int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.y(player)) - 1, (int)Math.floor(dev.openallay.client.context.MinecraftClientContextFacts.z(player)) + 8,
                        dev.openallay.world.MinecraftWorldObservationFacts.dimension(dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player)));
                phase.accept("anchor_client_callback_submitted");
                dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> {
                    phase.accept("anchor_client_callback_entered");
                    success.accept(anchor);
                });
            } catch (RuntimeException error) {
                phase.accept("anchor_native_failure:" + error);
                error.printStackTrace();
                dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> failure.accept(error.toString()));
            } catch (LinkageError error) {
                phase.accept("anchor_native_failure:" + error);
                error.printStackTrace();
                dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> failure.accept(error.toString()));
                throw error;
            }
        });
    }

    static List<Landmark> landmarks(String scenario) {
        if (scenario.equals("builder-legacy-shapes")) return dev.openallay.util.Java8Collections.listOf(state("legacy-box-shell",0,2,0,"stonebrick","variant","stonebrick"), new Landmark("legacy-box-air",1,1,1,"air"), state("legacy-path-start",0,0,4,"stonebrick","variant","stonebrick"), state("legacy-path-end",4,0,4,"stonebrick","variant","stonebrick"), new Landmark("legacy-path-clearance",2,1,4,"air"), state("legacy-source-stair",0,1,8,"oak_stairs","facing","north","half","bottom","shape","straight"), state("legacy-source-chest",2,1,8,"chest","facing","east"), new Landmark("legacy-source-marker",1,1,9,"gold_block"), state("legacy-rotated-stair",11,1,8,"oak_stairs","facing","east","half","bottom","shape","straight"), state("legacy-rotated-chest",11,1,10,"chest","facing","south"), new Landmark("legacy-rotated-marker",10,1,9,"gold_block"), new Landmark("legacy-rotated-air",10,1,8,"air"), state("legacy-mirrored-stair",18,1,8,"oak_stairs","facing","north","half","bottom","shape","straight"), state("legacy-mirrored-chest",16,1,8,"chest","facing","west"), new Landmark("legacy-mirrored-marker",17,1,9,"gold_block"), new Landmark("legacy-mirrored-air",18,1,9,"air"));
        if (scenario.equals("builder-live-copy") || scenario.equals("builder-live-undo")) {
            boolean copy = scenario.equals("builder-live-copy");
            List<Landmark> result = new ArrayList<>();
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
                result.add(new Landmark("live-platform-" + x + "-" + z, x, 0, z, "stone_bricks"));
                result.add(new Landmark("live-copy-ground-" + x + "-" + z, x + 8, 0, z,
                        copy ? "stone_bricks" : "grass_block"));
                if (!copy) result.add(new Landmark("live-undo-headroom-" + x + "-" + z, x + 8, 1, z, "air"));
            }
            result.add(state("live-door-lower", 2, 1, 0, "oak_door", "facing", "north", "half", "lower"));
            result.add(state("live-door-upper", 2, 2, 0, "oak_door", "facing", "north", "half", "upper"));
            result.add(state("live-chest", 2, 1, 2, "chest", "facing", "north", "type", "single"));
            result.add(state("live-stair", 0, 1, 3, "oak_stairs", "facing", "east", "half", "bottom"));
            if (copy) {
                result.add(state("live-copy-door-lower", 12, 1, 2, "oak_door", "facing", "east", "half", "lower"));
                result.add(state("live-copy-door-upper", 12, 2, 2, "oak_door", "facing", "east", "half", "upper"));
                result.add(state("live-copy-chest", 10, 1, 2, "chest", "facing", "east", "type", "single"));
                result.add(state("live-copy-stair", 9, 1, 0, "oak_stairs", "facing", "south", "half", "bottom"));
            } else result.add(new Landmark("live-undo-door-upper", 12, 2, 2, "air"));
            return dev.openallay.util.Java8Collections.listCopyOf(result);
        }
        if (scenario.equals("builder-restricted"))
            return dev.openallay.util.Java8Collections.listOf(new Landmark("restricted-write-readback", 0, 1, 0, "gold_block"));
        if (scenario.equals("builder-server-denied"))
            return dev.openallay.util.Java8Collections.listOf(new Landmark("denied-no-write", 0, 1, 0, "air"));
        if (scenario.equals("builder-partial")) return dev.openallay.util.Java8Collections.listOf(new Landmark("earlier-write-retained", 0, 1, 0, "gold_block"), new Landmark("invalid-followup-did-not-write", 1, 1, 0, "air"));
        if (scenario.equals("builder-cancel")) return dev.openallay.util.Java8Collections.listOf(new Landmark("before-cancel-retained", 0, 1, 0, "diamond_block"), new Landmark("after-cancel-denied", 1, 1, 0, "air"));
        if (scenario.equals("builder-undo")) return dev.openallay.util.Java8Collections.listOf(new Landmark("undo-restored", 0, 1, 0, "air"), new Landmark("undo-conflict-preserved", 1, 1, 0, "diamond_block"));
        List<Landmark> result = new ArrayList<>();
        result.add(new Landmark("house-floor", 0, 0, 0, "oak_planks"));
        result.add(state("house-door-lower", 3, 1, 0, "oak_door", "half", "lower", "facing", "north"));
        result.add(state("house-door-upper", 3, 2, 0, "oak_door", "half", "upper", "facing", "north"));
        result.add(state("house-bed-foot", 1, 1, 5, "red_bed", "part", "foot", "facing", "north"));
        result.add(state("house-bed-head", 1, 1, 4, "red_bed", "part", "head", "facing", "north"));
        result.add(state("house-chest", 4, 1, 5, "chest", "facing", "north"));
        result.add(state("house-lantern", 3, 4, 3, "lantern", "hanging", "true"));
        result.add(new Landmark("skyscraper-lower-light", 14, 3, 2, "sea_lantern"));
        result.add(new Landmark("skyscraper-upper-light", 14, 6, 2, "sea_lantern"));
        result.add(state("skyscraper-ladder", 13, 1, 3, "ladder", "facing", "north"));
        result.add(new Landmark("skyscraper-ladder-backing", 13, 1, 4, "iron_block"));
        result.add(state("skyscraper-rod", 14, 11, 2, "lightning_rod", "facing", "up"));
        result.add(state("cottage-beam", 25, 3, 0, "oak_log", "axis", "x"));
        result.add(state("cottage-ridge", 26, 7, -1, "dark_oak_slab", "type", "bottom"));
        result.add(state("cottage-campfire", 27, 10, 3, "campfire", "lit", "true"));
        result.add(new Landmark("cottage-chimney", 27, 9, 3, "bricks"));
        result.add(state("windmill-axle", 38, 5, 0, "oak_log", "axis", "z"));
        result.add(new Landmark("windmill-sail-spar", 39, 5, 0, "oak_fence"));
        result.add(new Landmark("windmill-sail", 39, 6, 0, "white_wool"));
        result.add(state("farm-soil", 0, 0, 18, "farmland", "moisture", "7"));
        result.add(state("farm-crop", 0, 1, 18, "beetroots", "age", "3"));
        result.add(new Landmark("farm-canal", -1, 0, 18, "water"));
        result.add(state("farm-gate", 1, 1, 17, "oak_fence_gate", "facing", "north"));
        result.add(new Landmark("dock-start", 14, 0, 18, "spruce_planks"));
        result.add(new Landmark("dock-end", 15, 0, 21, "spruce_planks"));
        result.add(state("dock-piling", 14, -2, 18, "spruce_log", "axis", "y"));
        result.add(state("dock-lantern", 13, 3, 21, "lantern", "hanging", "false"));
        result.add(new Landmark("geometry-box", 24, 2, 18, "stone_bricks"));
        result.add(new Landmark("geometry-hollow", 25, 1, 19, "air"));
        result.add(new Landmark("geometry-wall", 29, 1, 18, "polished_andesite"));
        result.add(new Landmark("geometry-wall-corner", 28, 2, 18, "gold_block"));
        result.add(new Landmark("geometry-circle", 36, 0, 19, "yellow_concrete"));
        result.add(new Landmark("geometry-cylinder", 39, 2, 19, "blue_concrete"));
        result.add(new Landmark("geometry-cylinder-interior", 40, 1, 19, "air"));
        result.add(new Landmark("geometry-cone", 43, 2, 19, "red_concrete"));
        result.add(new Landmark("geometry-arch-end", 24, 1, 22, "bricks"));
        result.add(new Landmark("geometry-arch-top", 24, 3, 24, "bricks"));
        result.add(state("geometry-roof-stair", 28, 2, 24, "spruce_stairs", "facing", "east", "half", "bottom", "shape", "straight"));
        result.add(state("geometry-roof-ridge", 29, 3, 24, "spruce_slab", "type", "bottom"));
        result.add(state("decoration-door-lower", 33, 1, 24, "birch_door", "half", "lower", "facing", "east", "hinge", "right"));
        result.add(state("decoration-door-upper", 33, 2, 24, "birch_door", "half", "upper", "facing", "east", "hinge", "right"));
        result.add(new Landmark("decoration-door-support", 33, 0, 24, "smooth_stone"));
        result.add(state("decoration-bed-foot", 34, 1, 25, "blue_bed", "facing", "east", "part", "foot"));
        result.add(state("decoration-bed-head", 35, 1, 25, "blue_bed", "facing", "east", "part", "head"));
        result.add(new Landmark("decoration-bed-support", 35, 0, 25, "smooth_stone"));
        result.add(new Landmark("decoration-flower", 32, 1, 23, "poppy"));
        result.add(new Landmark("decoration-potted-flower", 32, 1, 24, "potted_dandelion"));
        result.add(state("decoration-window", 38, 1, 23, "glass_pane", "east", "true", "west", "true"));
        result.add(state("decoration-lantern", 40, 3, 25, "lantern", "hanging", "false"));
        result.add(new Landmark("decoration-lantern-support", 40, 2, 25, "oak_fence"));
        result.add(state("decoration-tree-trunk", 43, 1, 25, "oak_log", "axis", "y"));
        result.add(state("decoration-tree-crown", 43, 4, 25, "oak_leaves", "persistent", "true"));
        result.add(new Landmark("terrain-foundation", 0, -1, 32, "dirt"));
        result.add(new Landmark("terrain-clear-flower", 0, 1, 32, "air"));
        result.add(new Landmark("terrain-clear-log", 0, 1, 33, "air"));
        result.add(new Landmark("terrain-clear-all", 2, 1, 33, "air"));
        result.add(new Landmark("terrain-straight-start", 0, 0, 32, "stone_bricks"));
        result.add(new Landmark("terrain-straight-end", 6, 0, 32, "stone_bricks"));
        result.add(new Landmark("terrain-smart-start", 0, 0, 34, "polished_andesite"));
        result.add(new Landmark("terrain-smart-end", 6, 0, 34, "polished_andesite"));
        result.add(new Landmark("terrain-smart-detour", 3, 0, 35, "polished_andesite"));
        result.add(new Landmark("terrain-obstacle", 3, 1, 34, "stone"));
        result.add(new Landmark("terrain-obstacle-ground", 3, 0, 34, "dirt"));
        result.add(state("template-source-stair", 14, 1, 32, "oak_stairs", "facing", "north"));
        result.add(state("template-source-chest", 16, 1, 33, "chest", "facing", "east"));
        result.add(state("template-rotation-stair", 21, 1, 32, "oak_stairs", "facing", "east"));
        result.add(state("template-rotation-chest", 20, 1, 34, "chest", "facing", "south"));
        result.add(new Landmark("template-rotation-marker", 19, 1, 33, "red_concrete"));
        result.add(new Landmark("template-rotation-air", 21, 1, 33, "air"));
        result.add(state("template-front-back-stair", 26, 1, 32, "oak_stairs", "facing", "north"));
        result.add(state("template-front-back-chest", 24, 1, 33, "chest", "facing", "west"));
        result.add(new Landmark("template-front-back-air", 25, 1, 32, "air"));
        result.add(state("template-left-right-stair", 29, 1, 34, "oak_stairs", "facing", "south"));
        result.add(state("template-left-right-chest", 31, 1, 33, "chest", "facing", "east"));
        result.add(new Landmark("template-left-right-air", 30, 1, 34, "air"));
        result.add(state("template-combined-stair", 36, 1, 34, "oak_stairs", "facing", "east"));
        result.add(state("template-combined-chest", 35, 1, 32, "chest", "facing", "north"));
        result.add(new Landmark("template-combined-air", 36, 1, 33, "air"));
        result.add(new Landmark("partial-earlier-write", 44, 1, 32, "gold_block"));
        result.add(new Landmark("partial-invalid-next", 45, 1, 32, "air"));
        result.add(new Landmark("cancel-earlier-write", 44, 1, 34, "diamond_block"));
        result.add(new Landmark("cancel-next-denied", 45, 1, 34, "air"));
        result.add(new Landmark("undo-restored", 44, 1, 36, "air"));
        result.add(new Landmark("undo-conflict-preserved", 45, 1, 36, "diamond_block"));
        return dev.openallay.util.Java8Collections.listCopyOf(result);
    }

    static List<Landmark> oracleLandmarks(String scenario, Anchor anchor) {
        List<Landmark> expected = new ArrayList<>(landmarks(scenario));
        if (scenario.equals("builder-acceptance") || scenario.equals("builder-reload")) {
            if (skyscraperUnavailable()) expected.removeIf(value -> value.name().startsWith("skyscraper-"));
            expected.add(new Landmark("geometry-checkerboard-floor", 32, 0, 18,
                    Math.floorMod(anchor.x() + anchor.z() + 50, 2) == 0 ? "quartz_block" : "black_concrete"));
        }
        if (scenario.equals("builder-legacy-shapes"))
            expected.add(state("legacy-checkerboard",4,0,0,"planks","variant",
                    Math.floorMod(anchor.x()+anchor.z()+4,2)==0 ? "oak" : "spruce"));
        return dev.openallay.util.Java8Collections.listCopyOf(expected);
    }

    private static Landmark state(String name, int x, int y, int z, String id, String... properties) {
        java.util.LinkedHashMap<java.lang.String, java.lang.String> map = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < properties.length; i += 2) map.put(properties[i], properties[i + 1]);
        return new Landmark(name, x, y, z, "minecraft:" + id, dev.openallay.util.Java8Collections.mapCopyOf(map));
    }

    static Anchor retainedOrigin(Anchor currentPlayer, Anchor retained, JsonObject proof, String world) {
        if (currentPlayer == null || retained == null || retained.dimension() == null
                || !retained.dimension().equals(currentPlayer.dimension()))
            throw new IllegalStateException("Reload dimension differs from retained native origin");
        if (proof == null || !"PASSED".equals(string(proof, "outcome")))
            throw new IllegalStateException("Reload requires prior independently passed native evidence");
        if (proof.has("worldName") && !world.equals(string(proof, "worldName")))
            throw new IllegalStateException("Reload receipt belongs to another world");
        com.google.gson.JsonObject origin = proof.has("nativeAnchor") ? proof.getAsJsonObject("nativeAnchor") : proof.getAsJsonObject("independentAnchor");
        if (origin == null || origin.get("x").getAsBigDecimal().intValueExact() != retained.x()
                || origin.get("y").getAsBigDecimal().intValueExact() != retained.y()
                || origin.get("z").getAsBigDecimal().intValueExact() != retained.z())
            throw new IllegalStateException("Reload receipt differs from retained native origin");
        return retained;
    }

    static String retainedOriginLine(Anchor anchor) {
        return "E2E retained native anchor: x=" + anchor.x() + ",y=" + anchor.y() + ",z=" + anchor.z();
    }

    static boolean matches(Landmark expected, String id, Map<String, String> actual) {
        return expected.id().equals(id) && actual.entrySet().containsAll(expected.properties().entrySet());
    }

    static JsonObject resultSummary(GuideRequestSnapshot request) {
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();
        for (GuideToolActivity tool : request.tools()) {
            JsonObject item = new JsonObject(); item.addProperty("toolId", tool.toolId());
            item.addProperty("status", tool.status().name());
            if (tool.normalized() != null) item.add("normalized", tool.normalized());
            tools.add(item);
        }
        result.add("tools", tools);
        return result;
    }

    static void verify(String scenario, UUID actor, Anchor anchor,
            GuideRequestSnapshot request, ClientSettingsService settings,
            boolean unrestrictedAtStart,
            Consumer<JsonObject> complete) {
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        net.minecraft.client.server.IntegratedServer server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
        JsonObject result = resultSummary(request);
        result.addProperty("oracle", "independent-integrated-server-owner-thread-readback");
        result.addProperty("unrestrictedAtStart", unrestrictedAtStart);
        boolean extensionActive = settings != null && settings.snapshot().extensions().extensions().stream()
                .anyMatch(value -> value.id().equals("openallay:builder")
                        && value.state() == dev.openallay.settings.extension.ExtensionSettingsView.State.ACTIVE);
        result.addProperty("defaultExtensionActive", extensionActive);
        if (server == null || anchor == null) {
            result.addProperty("outcome", "FAILED"); result.addProperty("failure", "No independent native binding");
            complete.accept(result); return;
        }
        dev.openallay.server.NativeServerOwner.execute(server, () -> {
            try {
                net.minecraft.server.level.ServerPlayer player = dev.openallay.server.NativeServerOwner.player(server, actor);
                if (player == null) throw new IllegalStateException("Native player disappeared");
                net.minecraft.server.level.ServerLevel level = dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player);
                if (!anchor.dimension().equals(dev.openallay.world.MinecraftWorldObservationFacts.dimension(level)))
                    throw new IllegalStateException("Native dimension changed");
                result.addProperty("worldName", dev.openallay.server.NativeServerOwner.worldName(server));
                result.addProperty("survival", dev.openallay.server.NativeServerOwner.survival(server));
                result.addProperty("cheatsOff", !GuideProbeWorldSettings.commandsAllowed(server));
                result.addProperty("flatWorld", GuideProbeWorldSettings.isFlat(server));
                JsonObject origin = new JsonObject(); origin.addProperty("x", anchor.x()); origin.addProperty("y", anchor.y()); origin.addProperty("z", anchor.z());
                result.add("independentAnchor", origin);
                JsonArray checks = new JsonArray(); boolean passed = extensionActive;
                List<Landmark> expected = oracleLandmarks(scenario, anchor);
                for (Landmark landmark : expected) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(anchor.x() + landmark.x(), anchor.y() + landmark.y(), anchor.z() + landmark.z());
                    JsonObject check = new JsonObject(); check.addProperty("name", landmark.name());
                    check.addProperty("x", pos.getX()); check.addProperty("y", pos.getY()); check.addProperty("z", pos.getZ());
                    check.addProperty("expectedId", landmark.id());
                    JsonObject expectedProperties = new JsonObject(); landmark.properties().forEach(expectedProperties::addProperty); check.add("expectedProperties", expectedProperties);
                    boolean observed = !level.isOutsideBuildHeight(pos) && dev.openallay.world.MinecraftWorldObservationFacts.loaded(level, pos);
                    check.addProperty("observed", observed); boolean match = false;
                    if (observed) {
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                        String id = MinecraftNativeRegistries.BLOCK.getKey(state.getBlock()).toString();
                        Map<String, String> properties = new java.util.LinkedHashMap<>();
                        properties.putAll(dev.openallay.context.minecraft.MinecraftBlockStateProperties.capture(state));
                        check.addProperty("actualId", id); JsonObject actualProperties = new JsonObject(); properties.forEach(actualProperties::addProperty); check.add("actualProperties", actualProperties);
                        match = matches(landmark, id, properties)
                                && (!scenario.equals("builder-legacy-shapes") || landmark.properties().equals(properties));
                    }
                    check.addProperty("passed", match); passed &= match; checks.add(check);
                }
                result.add("checks", checks);
                boolean stagedReceipt = scenario.equals("builder-acceptance")
                        || scenario.equals("builder-partial") || scenario.equals("builder-cancel");
                JsonObject receipt = stagedReceipt || scenario.equals("builder-reload") ? builderReceipt(request) : null;
                if (scenario.equals("builder-acceptance") || scenario.equals("builder-reload")) {
                    JsonArray skipped = skippedCases(skyscraperUnavailable());
                    boolean skipBinding = skipped.equals(receipt.get("skipped"));
                    result.add("skipped", skipped);
                    result.addProperty("skipNativeRegistryBindingPassed", skipBinding);
                    passed &= skipBinding;
                }
                if (scenario.equals("builder-legacy-shapes")) {
                    receipt = builderReceipt(request);
                    JsonObject palette = NativeMaterialPaletteOracle.capture();
                    JsonArray skipped = legacySkipped(palette);
                    boolean binding = palette.equals(receipt.get("materialPalette")) && skipped.equals(receipt.get("skipped"))
                            && skipped.size() == LEGACY_PRESET_ROLES.size()
                            && receipt.getAsJsonArray("availablePresets").size() == 0
                            && nativeReceiptBinding(receipt, anchor, actor);
                    result.add("skipped", skipped); result.add("nativeMaterialPalette", palette);
                    result.addProperty("skipNativePaletteBindingPassed", binding); passed &= binding;
                    boolean persisted = legacyTemplatePersisted(receipt, client);
                    result.addProperty("templatePersistencePassed", persisted); passed &= persisted;
                }
                boolean toolContract = stagedReceipt
                        ? receiptMatchesScenario(scenario, receipt) : toolContract(scenario, request);
                result.addProperty("toolContractPassed", toolContract);
                passed &= toolContract;
                if (stagedReceipt) {
                    boolean binding = toolContract && nativeReceiptBinding(receipt, anchor, actor);
                    result.addProperty("receiptNativeBindingPassed", binding);
                    passed &= binding;
                }
                passed &= startupSettingsMatch(scenario, unrestrictedAtStart);
                if (scenario.equals("builder-server-denied")) passed &= request.modelSelection().modelMode() == dev.openallay.guide.GuideModelMode.SERVER;
                passed &= dev.openallay.server.NativeServerOwner.survival(server) && !GuideProbeWorldSettings.commandsAllowed(server);
                result.addProperty("outcome", passed ? "PASSED" : "FAILED");
            } catch (RuntimeException failure) { result.addProperty("outcome", "FAILED"); result.addProperty("failure", failure.toString()); }
            dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> complete.accept(result));
        });
    }

    static boolean startupSettingsMatch(String scenario, boolean unrestrictedAtStart) {
        return !(scenario.equals("builder-restricted") || scenario.equals("builder-legacy-shapes")) || !unrestrictedAtStart;
    }

    private static final Map<String, List<String>> LEGACY_PRESET_ROLES = dev.openallay.util.Java8Collections.mapOfEntries(dev.openallay.util.Java8Collections.entry("build_cottage", dev.openallay.util.Java8Collections.listOf("air", "bricks", "campfire_lit_north", "cobblestone", "cobblestone_stairs_south", "dark_oak_slab_bottom", "dark_oak_stairs_east", "dark_oak_stairs_west", "glass_pane", "lantern_hanging", "oak_log_x", "oak_log_y", "oak_log_z", "oak_planks", "spruce_planks")), dev.openallay.util.Java8Collections.entry("build_dock", dev.openallay.util.Java8Collections.listOf("air", "lantern_standing", "spruce_fence", "spruce_log_y", "spruce_planks")), dev.openallay.util.Java8Collections.entry("build_farm", dev.openallay.util.Java8Collections.listOf("air", "beetroots_mature", "carrots_mature", "dirt", "farmland_hydrated", "oak_fence", "oak_fence_gate_north", "potatoes_mature", "water_source", "wheat_mature")), dev.openallay.util.Java8Collections.entry("build_simple_house", dev.openallay.util.Java8Collections.listOf("air", "chest_north_single", "cobblestone", "furnace_north", "glass_pane", "lantern_hanging", "oak_fence", "oak_log_y", "oak_planks", "oak_pressure_plate_unpowered", "stone_brick_slab_bottom", "stone_brick_stairs_south")), dev.openallay.util.Java8Collections.entry("build_skyscraper", dev.openallay.util.Java8Collections.listOf("air", "blue_stained_glass", "cyan_stained_glass", "iron_bars", "iron_block", "ladder_north", "light_blue_stained_glass", "lightning_rod_up", "polished_andesite", "sea_lantern", "smooth_stone", "smooth_stone_slab_bottom", "stone_brick_stairs_south", "stone_brick_wall", "stone_bricks")), dev.openallay.util.Java8Collections.entry("build_windmill", dev.openallay.util.Java8Collections.listOf("air", "cobblestone", "oak_fence", "oak_log_z", "spruce_planks", "stone_brick_stairs_south", "stone_bricks", "white_concrete", "white_wool")));

    private static JsonArray legacySkipped(JsonObject palette) {
        JsonArray skipped = new JsonArray();
        LEGACY_PRESET_ROLES.keySet().stream().sorted().forEach(name -> {
            JsonArray missing = new JsonArray();
            for (String role : LEGACY_PRESET_ROLES.get(name)) if (!palette.has(role)) missing.add(role);
            if (missing.size() != 0) {
                JsonObject item = new JsonObject(); item.addProperty("name", name); item.addProperty("status", "SKIPPED");
                item.addProperty("reason", "missing_material_palette_role"); item.add("roles", missing); skipped.add(item);
            }
        });
        return skipped;
    }

    private static boolean legacyTemplatePersisted(JsonObject receipt, net.minecraft.client.Minecraft client) {
        try {
            java.nio.file.Path file = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client)
                    .resolve("config/openallay-builder/templates/openallay_e2e_legacy_shapes.json");
            if (!java.nio.file.Files.isRegularFile(file) || java.nio.file.Files.isSymbolicLink(file)
                    || java.nio.file.Files.size(file) > 65536) return false;
            JsonObject persisted = dev.openallay.json.JsonTrees.parse(java.nio.file.Files.readString(file)).getAsJsonObject();
            JsonObject template = receipt.getAsJsonObject("template");
            return persisted.equals(template.get("value")) && persisted.getAsJsonArray("blocks").size() == 6
                    && persisted.getAsJsonArray("size").toString().equals("[3,1,2]")
                    && "1.12.2".equals(string(persisted,"gameVersion")) && booleanValue(persisted,"includesAir");
        } catch (IOException | RuntimeException malformed) { return false; }
    }

    private static final String JAVASCRIPT_TOOL = "openallay:run_javascript";
    private static final List<String> BUILD_NAMES = dev.openallay.util.Java8Collections.listOf("house", "skyscraper", "cottage", "windmill", "farm", "dock", "geometry_decoration", "terrain", "templates");

    /** Only native registry absence can remove this preset's independent world landmarks. */
    private static boolean skyscraperUnavailable() {
        return MinecraftNativeRegistries.blockKeys().stream()
                .noneMatch(id -> "minecraft:lightning_rod".equals(id.toString()));
    }

    private static JsonArray skippedCases(boolean unavailable) {
        JsonArray skipped = new JsonArray();
        if (unavailable) {
            JsonObject item = new JsonObject();
            item.addProperty("name", "skyscraper"); item.addProperty("status", "SKIPPED");
            item.addProperty("reason", "missing_material_palette_role"); item.addProperty("role", "lightning_rod_up");
            skipped.add(item);
        }
        return skipped;
    }

    private static boolean skyscraperSkipped(JsonObject receipt) {
        com.google.gson.JsonArray skipped = receipt.getAsJsonArray("skipped");
        if (skipped == null || (!skipped.equals(skippedCases(false)) && !skipped.equals(skippedCases(true))))
            throw new IllegalArgumentException("Builder skipped cases differ from the exact skyscraper palette-role receipt");
        return skipped.size() == 1;
    }

    private static List<String> buildNames(JsonObject receipt) {
        return skyscraperSkipped(receipt) ? dev.openallay.util.Java8Collections.toList(BUILD_NAMES.stream().filter(name -> !name.equals("skyscraper"))) : BUILD_NAMES;
    }

    /** Parse only after the complete fixture chronology has been checked. */
    static JsonObject builderReceipt(GuideRequestSnapshot request) {
        try {
            return validatedBuilderReceipt(request);
        } catch (IllegalArgumentException malformed) {
            throw malformed;
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Builder native receipt shape is invalid", malformed);
        }
    }

    private static JsonObject validatedBuilderReceipt(GuideRequestSnapshot request) {
        List<GuideToolActivity> javascript = dev.openallay.util.Java8Collections.toList(request.tools().stream()
                .filter(value -> value.toolId().equals(JAVASCRIPT_TOOL)));
        if (javascript.isEmpty()) throw new IllegalArgumentException("Builder JavaScript Tool result is missing");
        if (!fixtureOutcomes(request, javascript))
            throw new IllegalArgumentException("Builder ordered Tool outcomes do not match the fixture contract");
        com.google.gson.JsonObject receipt = scalarReceipt(javascript.get(javascript.size() - 1));
        String scenario = string(receipt, "scenario");
        if (!fixtureHistory(scenario, request, javascript, receipt))
            throw new IllegalArgumentException("Builder Tool history or native receipts do not match the fixture contract");
        if ("builder_acceptance".equals(scenario)) {
            // Existing controller persists operations/lifecycle, not the full refreshed list.
            // Keep exact actual public native rows with those receipts for reload comparison.
            java.util.Map<java.lang.String, com.google.gson.JsonObject> rows = journalRows(receipt.getAsJsonArray("durableOperations"));
            for (com.google.gson.JsonElement value : receipt.getAsJsonArray("operations")) attachJournal(value.getAsJsonObject(), rows);
            com.google.gson.JsonObject undo = receipt.getAsJsonObject("lifecycle").getAsJsonObject("undo");
            for (String key : dev.openallay.util.Java8Collections.listOf("originalStatus", "interventionStatus", "status"))
                attachJournal(undo.getAsJsonObject(key), rows);
        }
        return receipt;
    }

    private static void attachJournal(JsonObject status, Map<String, JsonObject> rows) {
        status.add("journal", dev.openallay.json.JsonTrees.copy(rows.get(string(status, "operationId"))));
    }

    /** One complete strict scalar JSON object from one actual successful Tool. */
    private static JsonObject scalarReceipt(GuideToolActivity tool) {
        com.google.gson.JsonObject normalized = tool.normalized();
        if (!succeeded(tool) || !normalized.has("value") || !normalized.get("value").isJsonObject())
            throw new IllegalArgumentException("Builder JavaScript Tool did not succeed");
        com.google.gson.JsonObject value = normalized.getAsJsonObject("value");
        if (!"string".equals(string(value, "resultType")) || !booleanValue(value, "complete")
                || string(value, "preview") == null)
            throw new IllegalArgumentException("Builder result must be a complete string receipt");
        String json = string(value, "preview");
        dev.openallay.bridge.protocol.BridgeJsonCodec.rejectDuplicateFields(json);
        try (com.google.gson.stream.JsonReader reader = JsonReaders.strict(new StringReader(json))) {
            com.google.gson.JsonElement receipt = JsonReaders.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || receipt == null || !receipt.isJsonObject())
                throw new IllegalArgumentException("Builder receipt must contain exactly one JSON object");
            return receipt.getAsJsonObject();
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Builder receipt is not strict JSON", malformed);
        }
    }

    private static boolean succeeded(GuideToolActivity tool) {
        return tool.status() == GuideToolStatus.SUCCEEDED && tool.normalized() != null
                && "success".equals(string(tool.normalized(), "status"));
    }

    private static boolean failed(GuideToolActivity tool, String code) {
        return tool.status() == GuideToolStatus.FAILED && tool.normalized() != null
                && "failure".equals(string(tool.normalized(), "status"))
                && code.equals(string(tool.normalized(), "code")) && nonempty(tool.normalized(), "message")
                && (!"invalid_native_input".equals(code) || "Builder native operation failed; inspect the session status".equals(string(tool.normalized(), "message")))
                && (!"session_closed".equals(code) || "Builder session is closed or cancelled".equals(string(tool.normalized(), "message")));
    }

    private static boolean fixtureOutcomes(GuideRequestSnapshot request, List<GuideToolActivity> tools) {
        if (request.status() != GuideRequestStatus.COMPLETED) return false;
        java.util.HashSet<java.lang.String> callIds = new java.util.HashSet<String>();
        for (dev.openallay.guide.GuideToolActivity tool : request.tools()) if (!callIds.add(tool.invocationId())) return false;
        if (tools.size() == 1) return request.tools().stream().allMatch(GuideBuilderE2EProbe::succeeded);
        if (request.tools().size() != tools.size() + 1) return false;
        dev.openallay.guide.GuideToolActivity skill = request.tools().get(0);
        if (!"openallay:load_skill".equals(skill.toolId()) || !succeeded(skill)
                || !"minecraft-builder".equals(string(skill.invocationArguments(), "name"))) return false;
        for (int index = 1; index < request.tools().size(); index++)
            if (!JAVASCRIPT_TOOL.equals(request.tools().get(index).toolId())) return false;
        if (tools.size() == 7) return succeeded(tools.get(0)) && failed(tools.get(1), "invalid_native_input")
                && succeeded(tools.get(2)) && failed(tools.get(3), "session_closed") && succeeded(tools.get(4))
                && succeeded(tools.get(5)) && succeeded(tools.get(6));
        return tools.size() == 3 && succeeded(tools.get(0)) && succeeded(tools.get(2))
                && (failed(tools.get(1), "invalid_native_input") || failed(tools.get(1), "session_closed"));
    }

    private static boolean fixtureHistory(String scenario, GuideRequestSnapshot request,
            List<GuideToolActivity> tools, JsonObject receipt) {
        if (request.status() != GuideRequestStatus.COMPLETED || scenario == null) return false;
        if ("builder_acceptance".equals(scenario) || "builder_partial".equals(scenario) || "builder_cancel".equals(scenario)) {
            if ("builder_acceptance".equals(scenario)) return tools.size() == 7 && acceptanceHistory(tools, receipt);
            String code = "builder_partial".equals(scenario) ? "invalid_native_input" : "session_closed";
            if (tools.size() != 3 || !failed(tools.get(1), code)) return false;
            return standaloneHistory(scenario, tools, receipt);
        }
        return tools.size() == 1 && succeeded(tools.get(0));
    }

    private static boolean acceptanceHistory(List<GuideToolActivity> tools, JsonObject finalReceipt) {
        com.google.gson.JsonObject build = scalarReceipt(tools.get(0));
        com.google.gson.JsonObject partial = scalarReceipt(tools.get(2));
        com.google.gson.JsonObject cancel = scalarReceipt(tools.get(4));
        com.google.gson.JsonObject undo = scalarReceipt(tools.get(5));
        if (!stage(build, "builder_acceptance", "build") || !stage(partial, "builder_acceptance", "partial_observation")
                || !stage(cancel, "builder_acceptance", "cancel_observation") || !stage(undo, "builder_acceptance", "undo")
                || !stage(finalReceipt, "builder_acceptance", "final")
                || !tools.get(0).invocationId().equals(string(build, "probeToken"))) return false;
        for (com.google.gson.JsonObject item : dev.openallay.util.Java8Collections.listOf(partial, cancel, undo, finalReceipt))
            if (!sameBinding(build, item) || !build.get("skipped").equals(item.get("skipped"))) return false;
        if (!completedBuild(build) || !readOnlyStatus(partial.getAsJsonObject("observationStatus"))
                || !readOnlyStatus(cancel.getAsJsonObject("observationStatus"))
                || !readOnlyStatus(finalReceipt.getAsJsonObject("observationStatus"))) return false;
        com.google.gson.JsonObject before = build.getAsJsonObject("lifecycle");
        com.google.gson.JsonObject p = partial.getAsJsonObject("lifecycle").getAsJsonObject("partial");
        com.google.gson.JsonObject c = cancel.getAsJsonObject("lifecycle").getAsJsonObject("cancel");
        com.google.gson.JsonObject u = undo.getAsJsonObject("lifecycle").getAsJsonObject("undo");
        if (!dev.openallay.json.JsonTrees.keys(before).equals(dev.openallay.util.Java8Collections.setOf("partial", "cancel", "undo"))
                || !dev.openallay.json.JsonTrees.keys(partial.getAsJsonObject("lifecycle")).equals(dev.openallay.util.Java8Collections.setOf("partial"))
                || !dev.openallay.json.JsonTrees.keys(cancel.getAsJsonObject("lifecycle")).equals(dev.openallay.util.Java8Collections.setOf("cancel"))
                || !dev.openallay.json.JsonTrees.keys(undo.getAsJsonObject("lifecycle")).equals(dev.openallay.util.Java8Collections.setOf("undo"))
                || !dev.openallay.json.JsonTrees.keys(u).equals(dev.openallay.util.Java8Collections.setOf("result", "status", "originalStatus", "interventionStatus", "positions", "beforeImages", "afterImages"))
                || !before.entrySet().stream().allMatch(entry -> dev.openallay.json.JsonTrees.keys(entry.getValue().getAsJsonObject()).equals(dev.openallay.util.Java8Collections.setOf("positions", "beforeImages")))
                || !prerequisite(before.getAsJsonObject("partial"), build, 44, 32)
                || !prerequisite(before.getAsJsonObject("cancel"), build, 44, 34)
                || !prerequisite(before.getAsJsonObject("undo"), build, 44, 36)
                || !durableFailure(p, build, "partial", 44, 32, tools.get(1).normalized())
                || !durableFailure(c, build, "cancel", 44, 34, tools.get(3).normalized())
                || !undoReceipt(u, build, 44, 36)) return false;
        for (java.util.Map.Entry<java.lang.String, com.google.gson.JsonObject> binding : dev.openallay.util.Java8Collections.listOf(dev.openallay.util.Java8Collections.entry("partial", p), dev.openallay.util.Java8Collections.entry("cancel", c), dev.openallay.util.Java8Collections.entry("undo", u))) {
            com.google.gson.JsonObject prior = before.getAsJsonObject(binding.getKey());
            if (!prior.get("positions").equals(binding.getValue().get("positions"))
                    || !prior.get("beforeImages").equals(binding.getValue().get("beforeImages"))) return false;
        }
        java.util.Map<java.lang.String, com.google.gson.JsonObject> baseline = journalRows(build.getAsJsonArray("baselineOperations"));
        java.util.Map<java.lang.String, com.google.gson.JsonObject> partialRows = journalRows(partial.getAsJsonArray("durableOperations"));
        java.util.Map<java.lang.String, com.google.gson.JsonObject> cancelRows = journalRows(cancel.getAsJsonArray("durableOperations"));
        java.util.Map<java.lang.String, com.google.gson.JsonObject> undoRows = journalRows(undo.getAsJsonArray("durableOperations"));
        java.util.Map<java.lang.String, com.google.gson.JsonObject> finalRows = journalRows(finalReceipt.getAsJsonArray("durableOperations"));
        if (!journalDelta(baseline, partialRows, dev.openallay.util.Java8Collections.listOf(p.getAsJsonObject("journal")))
                || !journalDelta(partialRows, cancelRows, dev.openallay.util.Java8Collections.listOf(c.getAsJsonObject("journal")))) return false;
        com.google.gson.JsonObject original = u.getAsJsonObject("originalStatus");
        com.google.gson.JsonObject intervention = u.getAsJsonObject("interventionStatus");
        com.google.gson.JsonObject restored = u.getAsJsonObject("status");
        String token = string(build, "probeToken");
        com.google.gson.JsonObject originals = journalForStatus(original, undoRows, "OpenAllay E2E lifecycle " + token + " undo original", 2);
        com.google.gson.JsonObject interventions = journalForStatus(intervention, undoRows, "OpenAllay E2E lifecycle " + token + " undo intervention", 1);
        com.google.gson.JsonObject restores = journalForStatus(restored, undoRows, "Undo " + string(original, "operationId"), 1);
        if (originals == null || interventions == null || restores == null
                || !journalDelta(cancelRows, undoRows, dev.openallay.util.Java8Collections.listOf(originals, interventions, restores)) || !undoRows.equals(finalRows)) return false;
        for (String key : dev.openallay.util.Java8Collections.listOf("anchor", "context", "probeToken", "operations", "status", "actions", "templates", "sites", "terrain", "baselineOperations", "seed", "provider", "skipped")) if (!build.get(key).equals(finalReceipt.get(key))) return false;
        com.google.gson.JsonObject lifecycle = finalReceipt.getAsJsonObject("lifecycle");
        return dev.openallay.json.JsonTrees.keys(lifecycle).equals(dev.openallay.util.Java8Collections.setOf("partial", "cancel", "undo"))
                && p.equals(lifecycle.get("partial")) && c.equals(lifecycle.get("cancel")) && u.equals(lifecycle.get("undo"))
                && compositeLifecycle(finalReceipt);
    }

    private static boolean standaloneHistory(String scenario, List<GuideToolActivity> tools, JsonObject receipt) {
        com.google.gson.JsonObject baseline = scalarReceipt(tools.get(0));
        String kind = "builder_partial".equals(scenario) ? "partial" : "cancel";
        if (!stage(baseline, scenario, "baseline") || !stage(receipt, scenario, "final")
                || !tools.get(0).invocationId().equals(string(baseline, "probeToken")) || !sameBinding(baseline, receipt)
                || !readOnlyStatus(baseline.getAsJsonObject("status")) || !readOnlyStatus(receipt.getAsJsonObject("status"))
                || !receipt.get("status").equals(receipt.get("observationStatus"))) return false;
        if (!dev.openallay.json.JsonTrees.keys(baseline.getAsJsonObject("lifecycle")).equals(dev.openallay.util.Java8Collections.setOf(kind))
                || !dev.openallay.json.JsonTrees.keys(receipt.getAsJsonObject("lifecycle")).equals(dev.openallay.util.Java8Collections.setOf(kind))) return false;
        com.google.gson.JsonObject prerequisite = baseline.getAsJsonObject("lifecycle").getAsJsonObject(kind);
        com.google.gson.JsonObject observed = receipt.getAsJsonObject("lifecycle").getAsJsonObject(kind);
        return (baseline.getAsJsonArray("baselineOperations").size() == 0)
                && baseline.get("baselineOperations").equals(receipt.get("baselineOperations"))
                && dev.openallay.json.JsonTrees.keys(prerequisite).equals(dev.openallay.util.Java8Collections.setOf("positions", "beforeImages"))
                && prerequisite(prerequisite, baseline, 0, 0) && durableFailure(observed, baseline, kind, 0, 0, tools.get(1).normalized())
                && prerequisite.get("positions").equals(observed.get("positions"))
                && prerequisite.get("beforeImages").equals(observed.get("beforeImages"))
                && journalDelta(journalRows(baseline.getAsJsonArray("baselineOperations")),
                        journalRows(receipt.getAsJsonArray("durableOperations")), dev.openallay.util.Java8Collections.listOf(observed.getAsJsonObject("journal")));
    }

    private static boolean stage(JsonObject receipt, String scenario, String stage) {
        return scenario.equals(string(receipt, "scenario")) && stage.equals(string(receipt, "stage"));
    }

    static boolean nativeReceiptBinding(JsonObject receipt, Anchor anchor, UUID actor) {
        try {
            long[] pos = position(receipt.getAsJsonObject("anchor"));
            com.google.gson.JsonObject context = receipt.getAsJsonObject("context");
            return dev.openallay.json.JsonTrees.keys(context).equals(dev.openallay.util.Java8Collections.setOf("dimension", "playerUuid"))
                    && pos[0] == anchor.x() && pos[1] == anchor.y() && pos[2] == anchor.z()
                    && anchor.dimension().equals(string(context, "dimension")) && actor.toString().equals(string(context, "playerUuid"));
        } catch (RuntimeException malformed) { return false; }
    }

    private static boolean sameBinding(JsonObject baseline, JsonObject receipt) {
        com.google.gson.JsonObject context = baseline.getAsJsonObject("context");
        if (!dev.openallay.json.JsonTrees.keys(context).equals(dev.openallay.util.Java8Collections.setOf("dimension", "playerUuid"))
                || !nonempty(context, "dimension") || !nonempty(context, "playerUuid") || !nonempty(baseline, "probeToken")
                || string(baseline, "probeToken").length() > 128) return false;
        UUID.fromString(string(context, "playerUuid"));
        position(baseline.getAsJsonObject("anchor"));
        return baseline.get("anchor").equals(receipt.get("anchor")) && context.equals(receipt.get("context"))
                && baseline.get("probeToken").equals(receipt.get("probeToken"));
    }

    private static boolean completedBuild(JsonObject receipt) {
        if (!"completed".equals(string(receipt.getAsJsonObject("status"), "state"))) return false;
        com.google.gson.JsonArray operations = receipt.getAsJsonArray("operations");
        java.util.List<java.lang.String> names = buildNames(receipt);
        if (operations.size() != names.size()) return false;
        java.util.Map<java.lang.String, com.google.gson.JsonObject> rows = journalRows(receipt.getAsJsonArray("baselineOperations"));
        java.util.HashSet<java.lang.String> ids = new java.util.HashSet<String>();
        for (int index = 0; index < names.size(); index++) {
            com.google.gson.JsonObject item = operations.get(index).getAsJsonObject();
            String id = string(item, "operationId");
            com.google.gson.JsonObject row = rows.get(id);
            if (!names.get(index).equals(string(item, "name")) || !completedStatus(item, ids)
                    || row == null || !"completed".equals(string(row, "status")) || number(row, "entries") <= 0
                    || !"OpenAllay E2E Builder acceptance".equals(string(row, "label"))) return false;
        }
        if (!rows.keySet().equals(ids)
                || !string(operations.get(operations.size() - 1).getAsJsonObject(), "operationId").equals(string(receipt.getAsJsonObject("status"), "operationId"))) return false;
        if (skyscraperSkipped(receipt) && receipt.getAsJsonObject("sites").has("skyscraper")) return false;
        java.util.HashSet<java.lang.String> paths = new java.util.HashSet<String>();
        for (com.google.gson.JsonElement value : receipt.getAsJsonArray("actions")) {
            com.google.gson.JsonObject action = value.getAsJsonObject(); String name = string(action, "name");
            if (skyscraperSkipped(receipt) && "skyscraper".equals(name)) return false;
            if ("terrain_path".equals(name) || "terrain_smart_path".equals(name))
                if (!"built".equals(string(action, "status")) || !paths.add(name)) return false;
        }
        com.google.gson.JsonObject templates = receipt.getAsJsonObject("templates");
        return paths.size() == 2 && booleanValue(templates, "listed") && templateNames(templates.getAsJsonArray("saved"))
                .contains("openallay_e2e_builder_native");
    }

    static boolean toolContract(String scenario, GuideRequestSnapshot request) {
        if (request.status() != GuideRequestStatus.COMPLETED) return false;
        List<GuideToolActivity> javascript = dev.openallay.util.Java8Collections.toList(request.tools().stream().filter(value -> value.toolId().equals(JAVASCRIPT_TOOL)));
        if (javascript.isEmpty()) return false;
        // Paid provider turns have their own shape; native evidence still owns their verdict.
        if (scenario.equals("builder-live-copy") || scenario.equals("builder-live-undo")) {
            boolean success = javascript.stream().allMatch(GuideBuilderE2EProbe::succeeded);
            java.util.Set<java.lang.String> sources = request.sources().stream().map(value -> value.evidence().sourceId()).collect(java.util.stream.Collectors.toSet());
            boolean evidence = scenario.equals("builder-live-copy")
                    ? sources.contains("openallay_builder:template-save") && sources.contains("openallay_builder:template-load")
                        && sources.contains("openallay_builder:write-readback")
                    : sources.contains("openallay_builder:undo-readback");
            return success && evidence && request.tools().stream().anyMatch(value -> value.toolId().equals("openallay:load_skill")
                    && "minecraft-builder".equals(string(value.invocationArguments(), "name")));
        }
        if (scenario.equals("builder-server-denied")) {
            dev.openallay.guide.GuideToolActivity tool = javascript.get(javascript.size() - 1);
            return javascript.size() == 1 && failed(tool, "javascript_error")
                    && string(tool.normalized(), "message").startsWith("ReferenceError: \"Java\" is not defined.");
        }
        try {
            return receiptMatchesScenario(scenario, builderReceipt(request));
        } catch (RuntimeException malformed) { return false; }
    }

    /** The receipt has already passed the complete ordered Tool-history contract. */
    private static boolean receiptMatchesScenario(String scenario, JsonObject receipt) {
        try {
            if (!scenario.replace('-', '_').equals(string(receipt, "scenario"))) return false;
            switch ((scenario)) {
case "builder-restricted":
{
{ return "completed".equals(string(receipt.getAsJsonObject("status"), "state"))
                        && "minecraft:gold_block".equals(string(receipt, "readback")); }
}
case "builder-legacy-shapes":
{
{
                    com.google.gson.JsonObject path = receipt.getAsJsonObject("path"); com.google.gson.JsonObject template = receipt.getAsJsonObject("template");
                    return "completed".equals(string(receipt.getAsJsonObject("status"), "state"))
                            && number(receipt.getAsJsonObject("status"), "writes") > 0
                            && "built".equals(string(path, "status")) && number(path,"length") == 5
                            && "openallay_e2e_legacy_shapes".equals(string(template,"name"))
                            && booleanValue(template,"listed") && booleanValue(template,"persistedExact");
                }
}
case "builder-acceptance":
case "builder-partial":
case "builder-cancel":
{
{ return true; }
}
case "builder-undo":
{
{
                    com.google.gson.JsonObject undo = receipt.getAsJsonObject("undo");
                    return "completed".equals(string(receipt.getAsJsonObject("status"), "state")) && number(undo, "restored") == 1
                            && undo.getAsJsonArray("conflicts").size() == 1 && (undo.getAsJsonArray("uncertain").size() == 0);
                }
}
case "builder-reload":
{
{
                    java.util.Map<java.lang.String, com.google.gson.JsonObject> rows = journalRows(receipt.getAsJsonArray("operations"));
                    return readOnlyStatus(receipt.getAsJsonObject("status")) && number(receipt, "operationCount") == rows.size()
                            && rows.size() == buildNames(receipt).size() + 5 && "openallay_e2e_builder_native".equals(string(receipt.getAsJsonObject("template"), "name"));
                }
}
default:
{
{ return false; }
}
}

        } catch (RuntimeException malformed) { return false; }
    }

    static boolean persistedOperationsMatch(JsonObject retained, JsonObject reload) {
        try {
            java.util.Map<java.lang.String, com.google.gson.JsonObject> observed = journalRows(reload.getAsJsonArray("operations"));
            java.util.List<java.lang.String> names = buildNames(retained);
            if (!retained.get("skipped").equals(reload.get("skipped")) || buildNames(reload).size() != names.size()
                    || observed.size() != names.size() + 5 || number(reload, "operationCount") != observed.size()) return false;
            java.util.HashSet<java.lang.String> expectedIds = new java.util.HashSet<String>();
            com.google.gson.JsonArray operations = retained.getAsJsonArray("operations");
            if (operations.size() != names.size()) return false;
            for (int index = 0; index < names.size(); index++) {
                com.google.gson.JsonObject item = operations.get(index).getAsJsonObject();
                if (!names.get(index).equals(string(item, "name")) || !completedStatus(item, expectedIds)
                        || !persistedRow(item.getAsJsonObject("journal"), string(item, "operationId"), "completed", observed)
                        || number(item.getAsJsonObject("journal"), "entries") <= 0
                        || !"OpenAllay E2E Builder acceptance".equals(string(item.getAsJsonObject("journal"), "label"))) return false;
            }
            com.google.gson.JsonObject lifecycle = retained.getAsJsonObject("lifecycle");
            for (String kind : dev.openallay.util.Java8Collections.listOf("partial", "cancel")) {
                com.google.gson.JsonObject row = lifecycle.getAsJsonObject(kind).getAsJsonObject("journal");
                String id = string(row, "id");
                if (!expectedIds.add(id) || !persistedRow(row, id, kind.equals("partial") ? "failed" : "cancelled", observed)
                        || number(row, "entries") != 1) return false;
            }
            com.google.gson.JsonObject undo = lifecycle.getAsJsonObject("undo");
            for (String key : dev.openallay.util.Java8Collections.listOf("originalStatus", "interventionStatus", "status")) {
                com.google.gson.JsonObject item = undo.getAsJsonObject(key);
                if (!completedStatus(item, expectedIds)
                        || !persistedRow(item.getAsJsonObject("journal"), string(item, "operationId"), "completed", observed)
                        || number(item.getAsJsonObject("journal"), "entries") != (key.equals("originalStatus") ? 2 : 1)) return false;
            }
            java.util.Set<java.lang.String> listed = templateNames(reload.getAsJsonArray("listed"));
            java.util.Set<java.lang.String> saved = templateNames(retained.getAsJsonObject("templates").getAsJsonArray("saved"));
            return expectedIds.size() == names.size() + 5 && !saved.isEmpty() && listed.containsAll(saved)
                    && listed.contains("openallay_e2e_builder_native");
        } catch (RuntimeException malformed) { return false; }
    }

    private static boolean persistedRow(JsonObject row, String id, String status, Map<String, JsonObject> observed) {
        return journalRow(row) && id != null && !dev.openallay.util.Java8Strings.isBlank(id) && id.equals(string(row, "id"))
                && status.equals(string(row, "status")) && row.equals(observed.get(id));
    }

    static boolean compositeLifecycle(JsonObject receipt) {
        try {
            com.google.gson.JsonObject lifecycle = receipt.getAsJsonObject("lifecycle");
            com.google.gson.JsonObject partial = lifecycle.getAsJsonObject("partial");
            com.google.gson.JsonObject cancel = lifecycle.getAsJsonObject("cancel");
            com.google.gson.JsonObject undo = lifecycle.getAsJsonObject("undo");
            java.util.HashSet<java.lang.String> ids = new java.util.HashSet<String>();
            for (com.google.gson.JsonElement value : receipt.getAsJsonArray("operations"))
                if (!completedStatus(value.getAsJsonObject(), ids)) return false;
            if (!durableFailure(partial, receipt, "partial", 44, 32, partial.getAsJsonObject("failure"))
                    || !durableFailure(cancel, receipt, "cancel", 44, 34, cancel.getAsJsonObject("failure"))
                    || !ids.add(string(partial.getAsJsonObject("journal"), "id"))
                    || !ids.add(string(cancel.getAsJsonObject("journal"), "id")) || !undoReceipt(undo, receipt, 44, 36)) return false;
            for (String key : dev.openallay.util.Java8Collections.listOf("originalStatus", "interventionStatus", "status"))
                if (!completedStatus(undo.getAsJsonObject(key), ids)) return false;
            return ids.size() == buildNames(receipt).size() + 5;
        } catch (RuntimeException malformed) { return false; }
    }

    private static boolean completedStatus(JsonObject status, java.util.Set<String> ids) {
        return "completed".equals(string(status, "state")) && nonempty(status, "operationId")
                && ids.add(string(status, "operationId"));
    }

    private static boolean readOnlyStatus(JsonObject status) {
        return "completed".equals(string(status, "state")) && number(status, "writes") == 0 && !status.has("operationId");
    }

    private static boolean prerequisite(JsonObject item, JsonObject receipt, int x, int z) {
        return positions(item.getAsJsonArray("positions"), receipt.getAsJsonObject("anchor"), x, z)
                && images(item.getAsJsonArray("beforeImages"), "air", "air");
    }

    private static boolean durableFailure(JsonObject item, JsonObject receipt, String kind, int x, int z, JsonObject failure) {
        com.google.gson.JsonObject row = item.getAsJsonObject("journal");
        String code = kind.equals("partial") ? "invalid_native_input" : "session_closed";
        return dev.openallay.json.JsonTrees.keys(item).equals(dev.openallay.util.Java8Collections.setOf("failure", "journal", "positions", "beforeImages", "afterImages")) && journalRow(row)
                && ("OpenAllay E2E lifecycle " + string(receipt, "probeToken") + " " + kind).equals(string(row, "label"))
                && (kind.equals("partial") ? "failed" : "cancelled").equals(string(row, "status")) && number(row, "entries") == 1
                && failure != null && dev.openallay.json.JsonTrees.keys(failure).equals(dev.openallay.util.Java8Collections.setOf("status", "code", "message"))
                && "failure".equals(string(failure, "status")) && code.equals(string(failure, "code"))
                && (kind.equals("partial") ? "Builder native operation failed; inspect the session status" : "Builder session is closed or cancelled")
                    .equals(string(failure, "message"))
                && failure.equals(item.get("failure")) && prerequisite(item, receipt, x, z)
                && images(item.getAsJsonArray("afterImages"), kind.equals("partial") ? "gold_block" : "diamond_block", "air");
    }

    private static boolean undoReceipt(JsonObject item, JsonObject receipt, int x, int z) {
        java.util.HashSet<java.lang.String> ids = new java.util.HashSet<String>();
        for (String key : dev.openallay.util.Java8Collections.listOf("originalStatus", "interventionStatus", "status"))
            if (!completedStatus(item.getAsJsonObject(key), ids)) return false;
        com.google.gson.JsonObject result = item.getAsJsonObject("result");
        return prerequisite(item, receipt, x, z) && images(item.getAsJsonArray("afterImages"), "air", "diamond_block")
                && string(item.getAsJsonObject("status"), "operationId").equals(string(result, "operationId"))
                && number(result, "restored") == 1 && (result.getAsJsonArray("uncertain").size() == 0)
                && result.getAsJsonArray("conflicts").size() == 1
                && result.getAsJsonArray("conflicts").get(0).equals(item.getAsJsonArray("positions").get(1));
    }

    private static boolean positions(JsonArray positions, JsonObject anchor, int x, int z) {
        if (positions.size() != 2) return false;
        long[] origin = position(anchor);
        for (int index = 0; index < 2; index++) {
            long[] pos = position(positions.get(index).getAsJsonObject());
            if (pos[0] != origin[0] + x + index || pos[1] != origin[1] + 1 || pos[2] != origin[2] + z) return false;
        }
        return true;
    }

    private static long[] position(JsonObject value) {
        if (!dev.openallay.json.JsonTrees.keys(value).equals(dev.openallay.util.Java8Collections.setOf("x", "y", "z"))) throw new IllegalArgumentException("Expected exact native position");
        long[] coordinates = {number(value, "x"), number(value, "y"), number(value, "z")};
        for (long coordinate : coordinates) if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Native position is outside the integer domain");
        return coordinates;
    }

    private static boolean images(JsonArray images, String first, String second) {
        return images.size() == 2 && blockImage(images.get(0).getAsJsonObject(), "minecraft:" + first)
                && blockImage(images.get(1).getAsJsonObject(), "minecraft:" + second);
    }

    private static boolean blockImage(JsonObject image, String id) {
        return dev.openallay.json.JsonTrees.keys(image).equals(dev.openallay.util.Java8Collections.setOf("id", "properties")) && id.equals(string(image, "id"))
                && image.getAsJsonObject("properties").size() == 0;
    }

    private static Map<String, JsonObject> journalRows(JsonArray values) {
        java.util.LinkedHashMap<java.lang.String, com.google.gson.JsonObject> rows = new java.util.LinkedHashMap<String, JsonObject>();
        for (com.google.gson.JsonElement value : values) {
            com.google.gson.JsonObject row = value.getAsJsonObject();
            if (!journalRow(row) || rows.put(string(row, "id"), row) != null)
                throw new IllegalArgumentException("Invalid or duplicate public native journal row");
        }
        return rows;
    }

    private static boolean journalRow(JsonObject row) {
        return row != null && dev.openallay.json.JsonTrees.keys(row).equals(dev.openallay.util.Java8Collections.setOf("id", "label", "status", "entries")) && nonempty(row, "id")
                && nonempty(row, "label") && dev.openallay.util.Java8Collections.listOf("running", "completed", "failed", "cancelled", "interrupted").contains(string(row, "status"))
                && number(row, "entries") >= 0;
    }

    private static boolean journalDelta(Map<String, JsonObject> before, Map<String, JsonObject> after, List<JsonObject> added) {
        if (after.size() != before.size() + added.size()) return false;
        for (java.util.Map.Entry<java.lang.String, com.google.gson.JsonObject> entry : before.entrySet()) if (!entry.getValue().equals(after.get(entry.getKey()))) return false;
        java.util.HashSet<java.lang.String> ids = new java.util.HashSet<String>();
        java.util.HashSet<java.lang.String> labels = new java.util.HashSet<String>();
        for (com.google.gson.JsonObject row : added) {
            String id = string(row, "id"), label = string(row, "label");
            if (!journalRow(row) || !ids.add(id) || !labels.add(label) || before.containsKey(id) || !row.equals(after.get(id))
                    || before.values().stream().anyMatch(old -> label.equals(string(old, "label")))) return false;
        }
        return true;
    }

    private static JsonObject journalForStatus(JsonObject status, Map<String, JsonObject> rows, String label, long entries) {
        com.google.gson.JsonObject row = rows.get(string(status, "operationId"));
        return journalRow(row) && "completed".equals(string(row, "status")) && label.equals(string(row, "label"))
                && number(row, "entries") == entries ? row : null;
    }

    private static java.util.Set<String> templateNames(JsonArray values) {
        java.util.HashSet<java.lang.String> names = new java.util.HashSet<String>();
        for (com.google.gson.JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || dev.openallay.util.Java8Strings.isBlank(value.getAsString())
                    || !names.add(value.getAsString())) throw new IllegalArgumentException("Invalid template list");
        }
        return names;
    }

    private static long number(JsonObject value, String key) {
        if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Expected integer " + key);
        return value.get(key).getAsBigDecimal().longValueExact();
    }

    private static boolean booleanValue(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isBoolean() && value.get(key).getAsBoolean();
    }

    private static String string(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isString() ? value.get(key).getAsString() : null;
    }
    private static boolean nonempty(JsonObject value, String key) {
        String text = string(value, key); return text != null && !dev.openallay.util.Java8Strings.isBlank(text);
    }
}
