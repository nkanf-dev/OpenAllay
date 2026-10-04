package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;
import java.util.Comparator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Full native state properties without depending on StateHolder.getValues()'s representation. */
final class NativeBlockStateProperties {
    private NativeBlockStateProperties() {}

    static JsonObject encode(BlockState state) {
        JsonObject properties = new JsonObject();
        state.getProperties().stream().sorted(Comparator.comparing(Property::getName))
                .forEach(property -> properties.addProperty(property.getName(), valueName(state, property)));
        return properties;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
