package dev.openallay.context.minecraft;

import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;

/** Complete canonical native properties, independent of StateHolder's storage representation. */
public final class MinecraftBlockStateProperties {
    private MinecraftBlockStateProperties() {}
    public static SortedMap<String, String> capture(IBlockState state) {
        TreeMap<String, String> values = new TreeMap<>();
        for (var property : state.getPropertyKeys()) values.put(property.getName(), name(state, property));
        return values;
    }
    private static <T extends Comparable<T>> String name(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }
}
