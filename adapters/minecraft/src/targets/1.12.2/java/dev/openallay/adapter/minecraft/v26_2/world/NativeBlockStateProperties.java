package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;
import java.util.Comparator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;

/** Full native state properties without depending on StateHolder.getValues()'s representation. */
final class NativeBlockStateProperties {
    private NativeBlockStateProperties() {}

    static JsonObject encode(IBlockState state) {
        JsonObject properties = new JsonObject();
        state.getPropertyKeys().stream().sorted(Comparator.comparing(IProperty::getName))
                .forEach(property -> properties.addProperty(property.getName(), valueName(state, property)));
        return properties;
    }

    private static <T extends Comparable<T>> String valueName(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }
}
