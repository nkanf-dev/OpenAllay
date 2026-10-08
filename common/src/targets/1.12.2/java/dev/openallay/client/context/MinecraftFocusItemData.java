package dev.openallay.client.context;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
/** Actual legacy item metadata/NBT, without a fabricated RegistryAccess or component codec. */
public final class MinecraftFocusItemData {
    private MinecraftFocusItemData() {}
    public static JsonObject persistentData(Minecraft client, ItemStack stack) {
        return dev.openallay.context.minecraft.MinecraftItemDataFacts.persistentData(stack);
    }
}
