package dev.openallay.guide.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.settings.ClientSettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/** Explicit development-only oracle. No Builder classes or model-facing capability. */
final class GuideBuilderE2EProbe {
    record Anchor(int x, int y, int z, String dimension) {}
    record Landmark(String name, int x, int y, int z, String id, Map<String, String> properties) {
        Landmark(String name, int x, int y, int z, String id) {
            this(name, x, y, z, "minecraft:" + id, Map.of());
        }
    }
    private GuideBuilderE2EProbe() {}

    static boolean enabled(String scenario) {
        return List.of("builder-disabled", "builder-acceptance", "builder-reload", "builder-partial",
                "builder-cancel", "builder-undo", "builder-server-denied", "builder-live-copy", "builder-live-undo").contains(scenario);
    }

    static void captureAnchor(UUID actor, Consumer<Anchor> success, Consumer<String> failure) {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null) { failure.accept("No authoritative integrated server"); return; }
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(actor);
                if (player == null) throw new IllegalStateException("Native player is unavailable");
                Anchor anchor = new Anchor((int)Math.floor(player.getX()) + 8,
                        (int)Math.floor(player.getY()) - 1, (int)Math.floor(player.getZ()) + 8,
                        dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(player.level().dimension()).toString());
                client.execute(() -> success.accept(anchor));
            } catch (RuntimeException error) { client.execute(() -> failure.accept(error.toString())); }
        });
    }

    static List<Landmark> landmarks(String scenario) {
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
            return List.copyOf(result);
        }
        if (scenario.equals("builder-disabled") || scenario.equals("builder-server-denied"))
            return List.of(new Landmark("denied-no-write", 0, 1, 0, "air"));
        if (scenario.equals("builder-partial")) return List.of(new Landmark("earlier-write-retained", 0, 1, 0, "gold_block"),
                new Landmark("invalid-followup-did-not-write", 1, 1, 0, "air"));
        if (scenario.equals("builder-cancel")) return List.of(new Landmark("before-cancel-retained", 0, 1, 0, "diamond_block"),
                new Landmark("after-cancel-denied", 1, 1, 0, "air"));
        if (scenario.equals("builder-undo")) return List.of(new Landmark("undo-restored", 0, 1, 0, "air"),
                new Landmark("undo-conflict-preserved", 1, 1, 0, "diamond_block"));
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
        result.add(new Landmark("terrain-obstacle-ground", 3, 0, 34, "grass_block"));
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
        return List.copyOf(result);
    }

    private static Landmark state(String name, int x, int y, int z, String id, String... properties) {
        var map = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < properties.length; i += 2) map.put(properties[i], properties[i + 1]);
        return new Landmark(name, x, y, z, "minecraft:" + id, Map.copyOf(map));
    }

    static Anchor retainedOrigin(Anchor currentPlayer, Anchor retained, JsonObject proof, String world) {
        if (currentPlayer == null || retained == null || retained.dimension() == null
                || !retained.dimension().equals(currentPlayer.dimension()))
            throw new IllegalStateException("Reload dimension differs from retained native origin");
        if (proof == null || !"PASSED".equals(string(proof, "outcome")))
            throw new IllegalStateException("Reload requires prior independently passed native evidence");
        if (proof.has("worldName") && !world.equals(string(proof, "worldName")))
            throw new IllegalStateException("Reload receipt belongs to another world");
        var origin = proof.has("nativeAnchor") ? proof.getAsJsonObject("nativeAnchor") : proof.getAsJsonObject("independentAnchor");
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
            boolean unrestrictedAtStart, boolean revocationCompleted,
            Consumer<JsonObject> complete) {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        JsonObject result = resultSummary(request);
        result.addProperty("oracle", "independent-integrated-server-owner-thread-readback");
        result.addProperty("unrestrictedAtStart", unrestrictedAtStart);
        result.addProperty("revocationCompleted", revocationCompleted);
        boolean extensionActive = settings != null && settings.snapshot().extensions().extensions().stream()
                .anyMatch(value -> value.id().equals("openallay:builder")
                        && value.state() == dev.openallay.settings.extension.ExtensionSettingsView.State.ACTIVE);
        result.addProperty("defaultExtensionActive", extensionActive);
        if (server == null || anchor == null) {
            result.addProperty("outcome", "FAILED"); result.addProperty("failure", "No independent native binding");
            complete.accept(result); return;
        }
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(actor);
                if (player == null) throw new IllegalStateException("Native player disappeared");
                ServerLevel level = dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player);
                if (!anchor.dimension().equals(dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(level.dimension()).toString()))
                    throw new IllegalStateException("Native dimension changed");
                result.addProperty("worldName", server.getWorldData().getLevelName());
                result.addProperty("survival", server.getWorldData().getGameType() == net.minecraft.world.level.GameType.SURVIVAL);
                result.addProperty("cheatsOff", !server.getWorldData().isAllowCommands());
                result.addProperty("flatWorld", server.getWorldData().isFlatWorld());
                JsonObject origin = new JsonObject(); origin.addProperty("x", anchor.x()); origin.addProperty("y", anchor.y()); origin.addProperty("z", anchor.z());
                result.add("independentAnchor", origin);
                JsonArray checks = new JsonArray(); boolean passed = extensionActive;
                List<Landmark> expected = new ArrayList<>(landmarks(scenario));
                if (scenario.equals("builder-acceptance") || scenario.equals("builder-reload")) {
                    expected.add(new Landmark("geometry-checkerboard-floor", 32, 0, 18,
                            Math.floorMod(anchor.x() + anchor.z() + 50, 2) == 0 ? "quartz_block" : "black_concrete"));
                }
                for (Landmark landmark : expected) {
                    BlockPos pos = new BlockPos(anchor.x() + landmark.x(), anchor.y() + landmark.y(), anchor.z() + landmark.z());
                    JsonObject check = new JsonObject(); check.addProperty("name", landmark.name());
                    check.addProperty("x", pos.getX()); check.addProperty("y", pos.getY()); check.addProperty("z", pos.getZ());
                    check.addProperty("expectedId", landmark.id());
                    JsonObject expectedProperties = new JsonObject(); landmark.properties().forEach(expectedProperties::addProperty); check.add("expectedProperties", expectedProperties);
                    boolean observed = !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
                    check.addProperty("observed", observed); boolean match = false;
                    if (observed) {
                        var state = level.getBlockState(pos);
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        Map<String, String> properties = new java.util.LinkedHashMap<>();
                        properties.putAll(dev.openallay.context.minecraft.MinecraftBlockStateProperties.capture(state));
                        check.addProperty("actualId", id); JsonObject actualProperties = new JsonObject(); properties.forEach(actualProperties::addProperty); check.add("actualProperties", actualProperties);
                        match = matches(landmark, id, properties);
                    }
                    check.addProperty("passed", match); passed &= match; checks.add(check);
                }
                result.add("checks", checks);
                boolean toolContract = toolContract(scenario, request);
                result.addProperty("toolContractPassed", toolContract);
                passed &= toolContract;
                if (scenario.equals("builder-disabled")) passed &= !unrestrictedAtStart;
                else if (!scenario.equals("builder-server-denied")) passed &= unrestrictedAtStart;
                if (Boolean.getBoolean("openallay.e2e.revokeUnrestrictedAfterCapture")) passed &= revocationCompleted;
                if (scenario.equals("builder-server-denied")) passed &= request.modelSelection().modelMode() == dev.openallay.guide.GuideModelMode.SERVER;
                passed &= server.getWorldData().getGameType() == net.minecraft.world.level.GameType.SURVIVAL && !server.getWorldData().isAllowCommands();
                result.addProperty("outcome", passed ? "PASSED" : "FAILED");
            } catch (RuntimeException failure) { result.addProperty("outcome", "FAILED"); result.addProperty("failure", failure.toString()); }
            client.execute(() -> complete.accept(result));
        });
    }

    static boolean toolContract(String scenario, GuideRequestSnapshot request) {
        if (request.status() != GuideRequestStatus.COMPLETED) return false;
        List<GuideToolActivity> javascript = request.tools().stream()
                .filter(value -> value.toolId().equals("openallay:run_javascript")).toList();
        if (javascript.isEmpty()) return false;
        if (scenario.equals("builder-live-copy") || scenario.equals("builder-live-undo")) {
            boolean success = javascript.stream().allMatch(tool -> tool.normalized() != null
                    && "success".equals(string(tool.normalized(), "status")));
            var sources = request.sources().stream().map(value -> value.evidence().sourceId()).collect(java.util.stream.Collectors.toSet());
            boolean evidence = scenario.equals("builder-live-copy")
                    ? sources.contains("openallay_builder:template-save") && sources.contains("openallay_builder:template-load")
                        && sources.contains("openallay_builder:write-readback")
                    : sources.contains("openallay_builder:undo-readback");
            return success && evidence
                    && request.tools().stream().anyMatch(value -> value.toolId().equals("openallay:load_skill")
                        && value.invocationArguments() != null && "minecraft-builder".equals(string(value.invocationArguments(), "name")));
        }
        if (scenario.equals("builder-disabled") || scenario.equals("builder-server-denied")) {
            var value = javascript.getLast().normalized();
            return value != null && value.has("status") && value.get("status").getAsString().equals("failure")
                    && value.has("code") && value.get("code").getAsString().equals("javascript_error");
        }
        var normalized = javascript.getLast().normalized();
        if (normalized == null || !normalized.has("status") || !normalized.get("status").getAsString().equals("success")) return false;
        if (!normalized.has("value") || !normalized.get("value").isJsonObject()) return false;
        var output = normalized.getAsJsonObject("value");
        if (!output.has("preview") || !output.get("preview").isJsonObject()) return false;
        var preview = output.getAsJsonObject("preview");
        if (!preview.has("scenario") || !preview.get("scenario").getAsString().equals(scenario.replace('-', '_'))) return false;
        if (!preview.has("status") || !preview.get("status").isJsonObject()) return false;
        String state = string(preview.getAsJsonObject("status"), "state");
        switch (scenario) {
            case "builder-partial" -> {
                return "failed-partial".equals(state) && nonempty(preview, "failure");
            }
            case "builder-cancel" -> {
                return "cancelled-partial".equals(state) && nonempty(preview, "deniedAfterCancel");
            }
            case "builder-undo" -> {
                if (!"completed".equals(state) || !preview.has("undo") || !preview.get("undo").isJsonObject()) return false;
                var undo = preview.getAsJsonObject("undo");
                return undo.has("restored") && undo.get("restored").getAsLong() == 1
                        && undo.has("conflicts") && undo.getAsJsonArray("conflicts").size() == 1
                        && undo.has("uncertain") && undo.getAsJsonArray("uncertain").isEmpty();
            }
            case "builder-reload" -> {
                return "completed".equals(state) && preview.has("operationCount")
                        && preview.get("operationCount").getAsLong() >= 9
                        && preview.has("template") && preview.get("template").isJsonObject()
                        && "openallay_e2e_builder_native".equals(string(preview.getAsJsonObject("template"), "name"));
            }
            default -> {
                if (!"completed".equals(state) || !preview.has("operations") || !preview.has("actions")) return false;
                var operations = preview.getAsJsonArray("operations");
                if (operations.size() != 9) return false;
                for (var item : operations) if (!"completed".equals(string(item.getAsJsonObject(), "state"))) return false;
                long paths = 0;
                for (var item : preview.getAsJsonArray("actions")) {
                    var action = item.getAsJsonObject();
                    if ("terrain_path".equals(string(action, "name")) || "terrain_smart_path".equals(string(action, "name"))) {
                        if (!"built".equals(string(action, "status"))) return false;
                        paths++;
                    }
                }
                return paths == 2 && preview.has("templates")
                        && preview.getAsJsonObject("templates").has("listed")
                        && preview.getAsJsonObject("templates").get("listed").getAsBoolean()
                        && compositeLifecycle(preview);
            }
        }
    }

    static boolean persistedOperationsMatch(JsonObject retained, JsonObject reload) {
        try {
            Map<String, String> observed = new java.util.HashMap<>();
            for (var value : reload.getAsJsonArray("operations")) {
                var item = value.getAsJsonObject();
                String id = string(item, "id"), status = string(item, "status");
                if (id == null || status == null || observed.put(id, status) != null) return false;
            }
            for (var value : retained.getAsJsonArray("operations")) {
                var item = value.getAsJsonObject();
                if (!"completed".equals(observed.get(string(item, "operationId")))) return false;
            }
            var lifecycle = retained.getAsJsonObject("lifecycle");
            if (!"failed".equals(observed.get(string(lifecycle.getAsJsonObject("partial").getAsJsonObject("status"), "operationId")))) return false;
            if (!"cancelled".equals(observed.get(string(lifecycle.getAsJsonObject("cancel").getAsJsonObject("status"), "operationId")))) return false;
            var undo = lifecycle.getAsJsonObject("undo");
            for (String key : List.of("originalStatus", "interventionStatus", "status")) {
                if (!"completed".equals(observed.get(string(undo.getAsJsonObject(key), "operationId")))) return false;
            }
            var names = new java.util.HashSet<String>();
            for (var value : reload.getAsJsonArray("listed")) names.add(value.getAsString());
            for (var name : retained.getAsJsonObject("templates").getAsJsonArray("saved")) if (!names.contains(name.getAsString())) return false;
            return true;
        } catch (RuntimeException malformed) { return false; }
    }

    /** Local controller event/evidence timing only; this is not a provider latency measurement. */
    static boolean frozenNativeTiming(java.time.Instant revokedAt, java.time.Instant firstNativeAt,
            java.time.Instant firstJavascriptObservedAt, boolean javascriptObservedBeforeRevocation) {
        return revokedAt != null && firstNativeAt != null && !firstNativeAt.isBefore(revokedAt)
                && firstJavascriptObservedAt != null && !firstJavascriptObservedAt.isBefore(revokedAt)
                && !javascriptObservedBeforeRevocation;
    }

    static boolean compositeLifecycle(JsonObject preview) {
        try {
            var lifecycle = preview.getAsJsonObject("lifecycle");
            var partial = lifecycle.getAsJsonObject("partial");
            var cancel = lifecycle.getAsJsonObject("cancel");
            var undo = lifecycle.getAsJsonObject("undo");
            var result = undo.getAsJsonObject("result");
            return "failed-partial".equals(string(partial.getAsJsonObject("status"), "state"))
                    && nonempty(partial, "failure")
                    && "cancelled-partial".equals(string(cancel.getAsJsonObject("status"), "state"))
                    && cancel.has("deniedAfterCancel") && cancel.get("deniedAfterCancel").getAsBoolean()
                    && nonempty(cancel, "failure")
                    && "completed".equals(string(undo.getAsJsonObject("status"), "state"))
                    && result.get("restored").getAsLong() == 1
                    && result.getAsJsonArray("conflicts").size() == 1
                    && result.getAsJsonArray("uncertain").isEmpty();
        } catch (RuntimeException malformed) { return false; }
    }

    private static String string(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : null;
    }
    private static boolean nonempty(JsonObject value, String key) {
        String text = string(value, key); return text != null && !text.isBlank();
    }
}
