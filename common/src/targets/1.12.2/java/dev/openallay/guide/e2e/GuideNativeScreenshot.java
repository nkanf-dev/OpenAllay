package dev.openallay.guide.e2e;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.ITextComponent;
/** Real native screenshot save; vanilla owns encoding, file naming and receipt. */
public final class GuideNativeScreenshot {
    private GuideNativeScreenshot() {}
    public static void grab(File root, String name, Framebuffer target, Consumer<ITextComponent> receipt) {
        Minecraft client = Minecraft.getMinecraft();
        receipt.accept(ScreenShotHelper.saveScreenshot(root, name, client.displayWidth, client.displayHeight, target));
    }
}
