package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.GuideNativeWindowResize;
import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual Minecraft resize callback and LWJGL2 mode owner, without a fabricated window class. */
@Mixin(Minecraft.class)
public abstract class MainWindowResizeMixin implements GuideNativeWindowResize {
    @Unique private boolean openallay$resizing;
    @Unique private long openallay$resizeNotifications;
    @Inject(method = "resize(II)V", at = @At("RETURN"))
    private void openallay$countResize(int width, int height, CallbackInfo callback) { openallay$resizeNotifications++; }
    @Override public final void openallay$windowedSize(int width, int height) {
        Minecraft client = (Minecraft) (Object) this;
        if (!client.isCallingFromMinecraftThread()) throw new IllegalStateException("Window mutation requires the Minecraft thread");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window dimensions must be positive");
        if (openallay$resizing) throw new IllegalStateException("Native window resize cannot reenter its owner operation");
        openallay$resizing = true;
        try {
            if (client.isFullScreen()) client.toggleFullscreen();
            try { Display.setDisplayMode(new DisplayMode(width, height)); }
            catch (LWJGLException failure) { throw new IllegalStateException("Native window mode change failed", failure); }
            // The real native callback updates framebuffer and active GuiScreen.onResize once.
            client.resize(Display.getWidth(), Display.getHeight());
        } finally { openallay$resizing = false; }
    }
}
