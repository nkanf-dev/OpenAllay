package dev.openallay.context.minecraft;

import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Complete canonical native properties, independent of StateHolder's storage representation. */
public final class MinecraftBlockStateProperties {
    private MinecraftBlockStateProperties() {}
    public static SortedMap<String, String> capture(BlockState state) {
        TreeMap<String, String> values = new TreeMap<>();
        for (var property : state.getProperties()) values.put(property.getName(), name(state, property));
        return values;
    }
    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
