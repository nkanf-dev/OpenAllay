package dev.openallay.client.context;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
/** Selected native item encoding; complete object validation stays exact at the codec boundary. */
public final class MinecraftFocusItemData {
    private MinecraftFocusItemData() {}
    public static JsonObject persistentData(Minecraft client, ItemStack stack) {
        var encoded = dev.openallay.context.minecraft.MinecraftItemDataFacts.persistentData(stack, client.level.registryAccess());
        var result = encoded.result();
        if (result.isEmpty()) throw new IllegalStateException(encoded.error().map(error -> error.message()).orElse("No complete result"));
        if (!result.get().isJsonObject()) throw new IllegalStateException("Expected a JSON object");
        return result.get().getAsJsonObject();
    }
}
