package dev.openallay.guide.e2e;

import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.ITextComponent;

/** Use native readback, save event, error handling and returned completion text. */
public final class GuideNativeScreenshot {
    private GuideNativeScreenshot() {}
    public static void grab(File root, String name, Framebuffer target, Consumer<ITextComponent> receipt) {
        receipt.accept(ScreenShotHelper.saveScreenshot(root, name,
                target.framebufferWidth, target.framebufferHeight, target));
    }
}
