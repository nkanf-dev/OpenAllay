package dev.openallay.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;

/** Pure, deterministic world-shaped results. No runtime trace or private configuration is loaded. */
public final class JavascriptResultBudgetFixtures {
    public static final int BLOCK_COUNT = 8_704;
    public static final int COLUMN_COUNT = 16_641;
    public static final int TILE_COUNT = 81;
    public static final int SUMMARY_FIELD_COUNT = 80;
    public static final String BEFORE = "container-found-before-array";
    public static final String AFTER = "container-confirmed-after-array";

    private JavascriptResultBudgetFixtures() {}

    /** Approximately 1.6 MB of canonical JSON, with small conclusions on both sides of bulk data. */
    public static JsonObject largeWorldResult() {
        JsonObject result = new JsonObject();
        result.addProperty("before_bulk", BEFORE);
        JsonObject observation = new JsonObject();
        JsonObject bounds = new JsonObject();
        bounds.add("from", position(0, 64, 0));
        bounds.add("to", position(127, 64, 67));
        observation.add("bounds", bounds);
        JsonArray blocks = new JsonArray();
        for (int index = 0; index < BLOCK_COUNT; index++) {
            JsonObject block = new JsonObject();
            block.addProperty("id", "minecraft:stone");
            block.add("position", position(index % 128, 64, index / 128));
            block.add("relative", position(index % 128, 0, index / 128));
            JsonObject state = new JsonObject();
            state.addProperty("facing", "north");
            state.addProperty("waterlogged", "false");
            block.add("state", state);
            block.addProperty("fluid", "minecraft:empty");
            block.addProperty("blockEntity", false);
            blocks.add(block);
        }
        observation.add("blocks", blocks);
        JsonObject coverage = new JsonObject();
        coverage.addProperty("requestedPositions", BLOCK_COUNT);
        coverage.addProperty("loadedPositions", BLOCK_COUNT);
        coverage.addProperty("complete", true);
        coverage.add("unavailableSections", new JsonArray());
        observation.add("coverage", coverage);
        result.add("observation", observation);
        result.addProperty("after_bulk", AFTER);
        return result;
    }

    /** Same top-level structure and array cardinalities as the pure terrain result fixture. */
    public static JsonObject terrainResult() {
        JsonObject result = new JsonObject();
        JsonObject bounds = new JsonObject();
        bounds.addProperty("count", (double) COLUMN_COUNT);
        bounds.addProperty("found", (double) COLUMN_COUNT);
        bounds.addProperty("maxY", 80.0);
        bounds.addProperty("minY", 64.0);
        bounds.addProperty("missing", 0.0);
        bounds.addProperty("x1", 0.0);
        bounds.addProperty("x2", 128.0);
        bounds.addProperty("z1", 0.0);
        bounds.addProperty("z2", 128.0);
        result.add("bounds", bounds);
        JsonArray columns = new JsonArray();
        String[][] propertyNames = {
            {"distance", "persistent", "waterlogged"},
            {"east", "north", "south", "up", "west"},
            {"level"}, {}, {"snowy"}, {"half"}, {"waterlogged"},
            {"facing", "powered", "waterlogged"},
            {"facing", "half", "shape", "waterlogged"},
            {"type", "waterlogged"},
            {"east", "north", "south", "up", "waterlogged", "west"},
            {"facing", "lit", "signal_fire", "waterlogged"},
            {"hanging", "waterlogged"}, {"face", "facing", "powered"},
            {"attachment", "facing", "powered"}, {"age"}
        };
        String[][] propertyValues = {
            {"1", "false", "false"}, {"false", "false", "false", "false", "false"},
            {"0"}, {}, {"false"}, {"upper"}, {"false"},
            {"north", "false", "false"}, {"north", "top", "straight", "false"},
            {"top", "false"}, {"false", "false", "false", "false", "false", "false"},
            {"north", "false", "false", "false"}, {"false", "false"},
            {"wall", "north", "false"}, {"single_wall", "north", "false"}, {"0"}
        };
        int[] counts = {945, 216, 8_351, 5_074, 324, 9, 5, 9, 1_529, 128, 19, 6, 14, 2, 1, 9};
        for (int shape = 0; shape < counts.length; shape++) {
            for (int row = 0; row < counts[shape]; row++) {
                int index = columns.size();
                JsonObject column = new JsonObject();
                column.addProperty("block", "minecraft:smooth_stone");
                JsonObject properties = new JsonObject();
                for (int property = 0; property < propertyNames[shape].length; property++) {
                    properties.addProperty(propertyNames[shape][property], propertyValues[shape][property]);
                }
                column.add("properties", properties);
                column.addProperty("x", (double) (index % 129));
                column.addProperty("y", 64.0);
                column.addProperty("z", (double) (index / 129));
                columns.add(column);
            }
        }
        result.add("columns", columns);
        JsonObject context = new JsonObject();
        context.addProperty("dataVersion", 0.0);
        context.addProperty("dimension", "example:overworld");
        context.addProperty("maxY", 80.0);
        context.addProperty("minY", 64.0);
        JsonObject player = new JsonObject();
        player.addProperty("uuid", "00000000-0000-0000-0000-000000000000");
        player.addProperty("x", 64.0);
        player.addProperty("y", 65.0);
        player.addProperty("yaw", 90.0);
        player.addProperty("z", 64.0);
        context.add("player", player);
        context.addProperty("topology", "synthetic-terrain");
        context.addProperty("version", "test-game");
        result.add("context", context);
        JsonObject histogram = new JsonObject();
        for (int index = 0; index < 53; index++) {
            histogram.addProperty("example:block_" + index, (double) index + 1);
        }
        result.add("histogram", histogram);
        JsonObject status = new JsonObject();
        status.addProperty("detail", "terrain-analysis-complete");
        status.addProperty("nativeDispatches", (double) TILE_COUNT);
        status.addProperty("paletteProvenCells", (double) COLUMN_COUNT);
        status.addProperty("reads", (double) COLUMN_COUNT);
        status.addProperty("state", "complete");
        status.addProperty("writes", 0.0);
        result.add("status", status);
        JsonArray tiles = new JsonArray();
        for (int index = 0; index < TILE_COUNT; index++) {
            JsonObject tile = new JsonObject();
            JsonObject blocks = new JsonObject();
            blocks.addProperty("minecraft:water", 169.0);
            blocks.addProperty("minecraft:stone", 87.0);
            tile.add("blocks", blocks);
            tile.addProperty("maxY", 80.0);
            tile.addProperty("minY", 64.0);
            tile.addProperty("x1", (double) ((index % 9) * 16));
            tile.addProperty("x2", (double) ((index % 9) * 16 + 15));
            tile.addProperty("z1", (double) ((index / 9) * 16));
            tile.addProperty("z2", (double) ((index / 9) * 16 + 15));
            tiles.add(tile);
        }
        result.add("tiles", tiles);
        return result;
    }

    /** Large answer-shaped scalar summaries can expand without turning a bulk array into a dump. */
    public static JsonObject scalarSummaryResult() {
        JsonObject summary = new JsonObject();
        for (int index = 0; index < SUMMARY_FIELD_COUNT; index++) {
            summary.addProperty("summary_" + index,
                    "known-summary-" + index + "-" + "detail".repeat(410));
        }
        return summary;
    }

    public static JsonObject escapingHeavyResult() {
        JsonObject result = new JsonObject();
        result.addProperty("before_bulk", BEFORE);
        result.addProperty("bulk", "quoted \"value\" \\ path\n界😀\t".repeat(20_000));
        result.addProperty("after_bulk", AFTER);
        return result;
    }

    public static int jsonUtf8Bytes(JsonElement value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    /** Counts the actual JSON string representation sent across the model boundary. */
    public static int encodedModelTextUtf8Bytes(String modelText) {
        return jsonUtf8Bytes(new JsonPrimitive(modelText));
    }

    private static JsonObject position(int x, int y, int z) {
        JsonObject position = new JsonObject();
        position.addProperty("x", x);
        position.addProperty("y", y);
        position.addProperty("z", z);
        return position;
    }
}
