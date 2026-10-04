package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;

/** Minecraft 1.21.4 GL texture ownership; view identities remain canonical strings. */
public final class MinecraftImageTextures {
    private MinecraftImageTextures() {}

    /** The old GL constructor has no debug label. Successful registration transfers image ownership. */
    public static void register(
            TextureManager manager, String texture, Supplier<String> label, NativeImage image) {
        manager.register(MinecraftResourceIds.parse(texture), new DynamicTexture(image));
    }

    /** Native manager release closes the texture's image and releases its GL identity. */
    public static void release(TextureManager manager, String texture) {
        manager.release(MinecraftResourceIds.parse(texture));
    }
}
