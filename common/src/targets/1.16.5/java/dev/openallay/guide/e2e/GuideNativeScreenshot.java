package dev.openallay.guide.e2e;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/** Actual screenshot save callback and framebuffer dimensions. */
public final class GuideNativeScreenshot {
    private GuideNativeScreenshot() {}
    public static void grab(File directory, String name, RenderTarget target, Consumer<Component> finished) {
        Screenshot.grab(directory, name, target.width, target.height, target, finished);
    }
}
