package dev.openallay.adapter.minecraft.v26_2.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native transformations, not the Rhino fixture's deliberately small rotation double. */
final class NativeStateTransformTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void railCurvesAndSlopesRotateAndMirrorWithNativeShapes() {
        assertProperty("south_west", transform("rail", "shape", "south_east", 90, "none"), "shape");
        assertProperty("north_east", transform("rail", "shape", "south_east", 0, "left_right"), "shape");
        assertProperty("south_west", transform("rail", "shape", "south_east", 0, "front_back"), "shape");
        assertProperty("ascending_east", transform("powered_rail", "shape", "ascending_north", 90, "none"), "shape");
        assertProperty("ascending_south", transform("powered_rail", "shape", "ascending_north", 0, "left_right"), "shape");
    }

    @Test void stairsMirrorHandednessWithoutChangingHalfOrWaterlogging() {
        String json = "{\"id\":\"oak_stairs\",\"properties\":{\"facing\":\"north\",\"shape\":\"inner_left\",\"half\":\"top\",\"waterlogged\":\"true\"}}";
        JsonObject result = transformed(json, 90, "left_right");
        assertProperty("west", result, "facing");
        assertProperty("inner_right", result, "shape");
        assertProperty("top", result, "half");
        assertProperty("true", result, "waterlogged");
    }

    @Test void bothDoorHalvesRotateTogetherAndMirroringFlipsTheHinge() {
        for (String half : new String[] {"lower", "upper"}) {
            String json = "{\"id\":\"oak_door\",\"properties\":{\"facing\":\"north\",\"hinge\":\"left\",\"half\":\"" + half + "\",\"open\":\"true\"}}";
            JsonObject result = transformed(json, 90, "left_right");
            assertProperty("west", result, "facing");
            assertProperty("right", result, "hinge");
            assertProperty(half, result, "half");
            assertProperty("true", result, "open");
        }
    }

    @Test void chestRotationAndMirrorRetainInventoryAndNativeChestType() {
        String snbt = "{id:'minecraft:chest',x:4,y:65,z:9,Items:[{Slot:0b,id:'minecraft:diamond',count:3}]}";
        JsonObject state = JsonParser.parseString(
                "{\"id\":\"chest\",\"properties\":{\"facing\":\"north\",\"type\":\"left\",\"waterlogged\":\"true\"}}")
                .getAsJsonObject();
        state.addProperty("blockEntity", snbt);
        JsonObject result = transformed(state.toString(), 90, "left_right");
        assertProperty("west", result, "facing");
        // In 26.2 ChestBlock.mirror rotates facing but does not cycle ChestType.
        assertProperty("left", result, "type");
        assertProperty("true", result, "waterlogged");
        assertEquals(snbt, result.get("blockEntity").getAsString());
    }

    @Test void allRailStairDoorAndChestStatesMatchNativeTransforms() {
        Block[] blocks = {Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL,
                Blocks.OAK_STAIRS, Blocks.OAK_DOOR, Blocks.CHEST};
        Mirror[] mirrors = {Mirror.NONE, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK};
        Rotation[] rotations = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
        for (Block block : blocks) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                JsonObject json = new JsonObject();
                json.addProperty("id", state.getBlock().builtInRegistryHolder().key().identifier().toString());
                JsonObject properties = new JsonObject();
                state.getValues().forEach(value -> properties.addProperty(value.property().getName(), value.valueName()));
                json.add("properties", properties);
                for (Mirror mirror : mirrors) {
                    for (int quarterTurns = 0; quarterTurns < rotations.length; quarterTurns++) {
                        BlockState actual = NativeBlockCodec.decode(NativeBlockCodec.transform(
                                json.toString(), quarterTurns * 90, mirror.getSerializedName()));
                        assertEquals(state.mirror(mirror).rotate(rotations[quarterTurns]), actual,
                                state + " mirror=" + mirror + " quarterTurns=" + quarterTurns);
                    }
                }
            }
        }
    }

    private static JsonObject transform(String block, String property, String value, int degrees, String mirror) {
        JsonObject json = new JsonObject();
        json.addProperty("id", block);
        JsonObject properties = new JsonObject();
        properties.addProperty(property, value);
        json.add("properties", properties);
        return transformed(json.toString(), degrees, mirror);
    }

    private static JsonObject transformed(String json, int degrees, String mirror) {
        String transformed = NativeBlockCodec.transform(json, degrees, mirror);
        assertDoesNotThrow(() -> NativeBlockCodec.decode(transformed));
        return JsonParser.parseString(transformed).getAsJsonObject();
    }

    private static void assertProperty(String expected, JsonObject json, String property) {
        assertEquals(expected, json.getAsJsonObject("properties").get(property).getAsString(), property);
    }
}
