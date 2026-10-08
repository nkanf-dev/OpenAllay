package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;

/** Texture-manager boundary; view-owned texture identities remain canonical strings. */
public final class MinecraftImageTextures {
    private MinecraftImageTextures() {}

    public static GuideImageBitmap create(int width, int height) { return GuideImageBitmaps.create(width, height); }
    public static void setArgb(GuideImageBitmap image, int x, int y, int argb) { GuideImageBitmaps.setArgb(image, x, y, argb); }
    public static void register(TextureManager manager, String texture, Supplier<String> label, GuideImageBitmap image) {
        var pixels = GuideImageBitmaps.take(image);
        try { register(manager, texture, label, pixels); }
        catch (RuntimeException | Error failure) {
            if (!(failure instanceof GuideImageRegistrationException)) {
                try { pixels.close(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }


    public static void register(
            TextureManager manager, String texture, Supplier<String> label, NativeImage image) {
        manager.register(MinecraftResourceIds.parse(texture), new DynamicTexture(label, image));
    }

    public static void release(TextureManager manager, String texture) {
        manager.release(MinecraftResourceIds.parse(texture));
    }
}
