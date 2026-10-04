package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure detached-value tests; no native client, screen, hit result, or registry is instantiated. */
final class WorldFocusObservationTest {
    @Test
    void deeplyCopiesComponentJsonAtConstructionAndAccess() {
        var source = com.google.gson.JsonParser.parseString(
                "{\"minecraft:custom_data\":{\"precise\":9007199254740993.125,\"flags\":[true,null,\"original\"]}}")
                .getAsJsonObject();
        WorldFocusObservation.Item item = new WorldFocusObservation.Item(
                "minecraft:diamond_sword", 1, "Detached sword", 7, 1561, source, true, "");
        source.remove("minecraft:custom_data");
        var captured = item.components().getAsJsonObject("minecraft:custom_data");
        assertEquals(new BigDecimal("9007199254740993.125"), captured.get("precise").getAsBigDecimal());
        assertTrue(captured.getAsJsonArray("flags").get(0).getAsBoolean());
        assertTrue(captured.getAsJsonArray("flags").get(1).isJsonNull());
        captured.getAsJsonArray("flags").set(0, new com.google.gson.JsonPrimitive(false));
        assertTrue(item.components().getAsJsonObject("minecraft:custom_data")
                .getAsJsonArray("flags").get(0).getAsBoolean());
        assertTrue(item.componentsAvailable());
        assertEquals("", item.diagnostic());
    }

    @Test
    void distinguishesMissFromBlockPayloadEvenWhenNativeMissIsBlockHitResult() {
        WorldFocusObservation.Position hit = new WorldFocusObservation.Position(12.125, 65.5, -3.75);
        // Native BlockHitResult.miss still has that Java class, but its detached kind is "miss".
        WorldFocusObservation.Target miss = new WorldFocusObservation.Target("miss", hit, null, null);
        WorldFocusObservation.Block block = block();
        WorldFocusObservation.Target blockHit = new WorldFocusObservation.Target("block", hit, block, null);

        assertEquals("miss", miss.kind());
        assertEquals(hit, miss.hit());
        assertNull(miss.block());
        assertNull(miss.entity());
        assertEquals("block", blockHit.kind());
        assertEquals(hit, blockHit.hit());
        assertEquals(block, blockHit.block());
        assertEquals("north", blockHit.block().face());
        assertEquals(new WorldPosition(12, 65, -4), blockHit.block().position());
        assertEquals(Map.of("facing", "east", "waterlogged", "true"), blockHit.block().properties());
        assertEquals("minecraft:water", blockHit.block().fluid());
        assertTrue(blockHit.block().inside());
        assertFalse(blockHit.block().worldBorderHit());
        assertNull(blockHit.entity());
    }

    @Test
    void acceptsHiddenSynchronizedMenuWithoutNativeType() {
        WorldFocusObservation.Item carried = new WorldFocusObservation.Item(
                "minecraft:air", 0, "", 0, 0, new com.google.gson.JsonObject(), true, "");
        WorldFocusObservation.Menu menu = new WorldFocusObservation.Menu(
                "net.minecraft.world.inventory.InventoryMenu", 0, 19, "", false,
                false, true, 46, carried, "untyped_menu");

        assertFalse(menu.displayed());
        assertFalse(menu.typeAvailable());
        assertEquals("", menu.type());
        assertTrue(menu.synchronizedWithPlayer());
        assertEquals(0, menu.containerId());
        assertEquals(19, menu.stateId());
        assertEquals(46, menu.slotCount());
        assertEquals(carried, menu.carried());
        assertEquals("untyped_menu", menu.diagnostic());
    }

    @Test
    void rejectsTargetPayloadsThatDoNotMatchTheirKind() {
        WorldFocusObservation.Position hit = new WorldFocusObservation.Position(12.125, 65.5, -3.75);
        WorldFocusObservation.Block block = block();
        WorldFocusObservation.Entity entity = new WorldFocusObservation.Entity(
                UUID.fromString("00000000-0000-0000-0000-000000000002"), 27,
                "minecraft:cow", "Cow", new WorldFocusObservation.Position(12.25, 65, -3.5),
                new WorldPosition(12, 65, -4), true);

        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("none", hit, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("none", null, block, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("miss", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("miss", hit, block, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("miss", hit, null, entity));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("block", hit, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("block", hit, block, entity));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("entity", hit, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("entity", hit, block, entity));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldFocusObservation.Target("entity", null, null, entity));
    }

    private static WorldFocusObservation.Block block() {
        return new WorldFocusObservation.Block(
                "minecraft:oak_stairs", new WorldPosition(12, 65, -4), "north", true, false,
                Map.of("facing", "east", "waterlogged", "true"), "minecraft:water");
    }
}
