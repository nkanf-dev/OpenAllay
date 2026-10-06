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
    public static void write(GuideImageBitmap image, java.nio.file.Path path) throws java.io.IOException {
        java.awt.image.BufferedImage pixels = new java.awt.image.BufferedImage(image.width(), image.height(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
        if (!javax.imageio.ImageIO.write(pixels, "png", path.toFile())) throw new java.io.IOException("PNG encoder is unavailable");
    }
    public static int width(net.minecraft.client.Minecraft client) { return dev.openallay.client.gui.MinecraftClientWindow.mainRenderTarget(client).width; }
    public static int height(net.minecraft.client.Minecraft client) { return dev.openallay.client.gui.MinecraftClientWindow.mainRenderTarget(client).height; }

    public static CompletableFuture<GuideImageBitmap> capture(net.minecraft.client.Minecraft client) {
        RenderTarget target = dev.openallay.client.gui.MinecraftClientWindow.mainRenderTarget(client);
        try {
            return CompletableFuture.completedFuture(Screenshot.takeScreenshot(Objects.requireNonNull(target, "target")));
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
