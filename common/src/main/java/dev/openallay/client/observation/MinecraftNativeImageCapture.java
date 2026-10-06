package dev.openallay.client.observation;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Screenshot;

/**
 * Purpose-bound native frame readback. Invoke on the current render thread, at the real frame boundary.
 * A successful future transfers the image to its consumer, which must close it exactly once.
 * Cancellation does not cancel native GPU work; a late image that cannot transfer ownership is closed here.
 */
public final class MinecraftNativeImageCapture {
    private MinecraftNativeImageCapture() {}
    public static int width(net.minecraft.client.Minecraft client) { return dev.openallay.client.gui.MinecraftClientWindow.mainRenderTarget(client).width; }
    public static int height(net.minecraft.client.Minecraft client) { return dev.openallay.client.gui.MinecraftClientWindow.mainRenderTarget(client).height; }

    public static CompletableFuture<GuideImageBitmap> capture(net.minecraft.client.Minecraft client) {
        RenderTarget target = dev.openallay.client.gui.MinecraftClientWindow.mainRenderTarget(client);
        CompletableFuture<GuideImageBitmap> result = new CompletableFuture<>();
        try {
            Screenshot.takeScreenshot(Objects.requireNonNull(target, "target"), image -> {
                // Never throw from the native callback: Screenshot still owns a GPU buffer until it returns.
                try {
                    GuideImageBitmap owned = GuideImageBitmaps.wrap(image);
                    if (!result.complete(owned)) owned.close();
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }
}
