package dev.openallay.client.observation;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Screenshot;

/**
 * Minecraft 1.21.4 GL readback: the image is captured synchronously on the current render thread.
 * This is a future boundary for shared consumers, not an emulated native callback or a worker readback.
 * A successful future transfers the image to its consumer, which must close it exactly once.
 */
public final class MinecraftNativeImageCapture {
    private MinecraftNativeImageCapture() {}

    public static CompletableFuture<NativeImage> capture(RenderTarget target) {
        try {
            Objects.requireNonNull(target, "target");
            int active = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            int texture = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            int alignment = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT);
            try {
                return CompletableFuture.completedFuture(Screenshot.takeScreenshot(target.width, target.height, target));
            } finally {
                com.mojang.blaze3d.systems.RenderSystem.activeTexture(active);
                com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);
                com.mojang.blaze3d.systems.RenderSystem.pixelStore(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, alignment);
            }
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
