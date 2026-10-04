package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;

/** Texture-manager boundary; view-owned texture identities remain canonical strings. */
public final class MinecraftImageTextures {
    private MinecraftImageTextures() {}

    public static void register(
            TextureManager manager, String texture, Supplier<String> label, NativeImage image) {
        manager.register(MinecraftResourceIds.parse(texture), new DynamicTexture(label, image));
    }

    public static void release(TextureManager manager, String texture) {
        manager.release(MinecraftResourceIds.parse(texture));
    }
}
