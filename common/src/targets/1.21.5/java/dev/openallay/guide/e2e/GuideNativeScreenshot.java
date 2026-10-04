package dev.openallay.guide.e2e;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/** Native development screenshot call. No image readback or user-file behavior is copied. */
public final class GuideNativeScreenshot {
    private GuideNativeScreenshot() {}
    public static void grab(File root, String name, RenderTarget target, Consumer<Component> receipt) {
        Screenshot.grab(root, name, target, receipt);
    }
}
