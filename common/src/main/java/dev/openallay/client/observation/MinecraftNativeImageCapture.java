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

    public static CompletableFuture<NativeImage> capture(RenderTarget target) {
        CompletableFuture<NativeImage> result = new CompletableFuture<>();
        try {
            Screenshot.takeScreenshot(Objects.requireNonNull(target, "target"), image -> {
                // Never throw from the native callback: Screenshot still owns a GPU buffer until it returns.
                try {
                    if (!result.complete(image)) image.close();
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
