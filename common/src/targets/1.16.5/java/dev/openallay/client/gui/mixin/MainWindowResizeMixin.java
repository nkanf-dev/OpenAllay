package dev.openallay.client.gui.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.openallay.client.gui.GuideNativeWindowResize;
import net.minecraft.client.MainWindow;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact native mode transition and callbacks, without buffer swapping or logical-to-framebuffer guesses. */
@Mixin(MainWindow.class)
public abstract class MainWindowResizeMixin implements GuideNativeWindowResize {
    @Shadow @Final private long window;
    @Shadow @Final private net.minecraft.client.renderer.IWindowEventListener eventHandler;
    @Unique private long openallay$resizeNotifications;
    @Unique private boolean openallay$resizing;
    @Inject(method = "onFramebufferResize(JII)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/IWindowEventListener;resizeDisplay()V"), require = 1, expect = 1, allow = 1)
    private void openallay$countResize(long handle, int width, int height, CallbackInfo callback) {
        if (handle == window) openallay$resizeNotifications++;
    }
    @Shadow private boolean fullscreen;
    @Shadow private boolean actuallyFullscreen;
    @Shadow private int windowedX;
    @Shadow private int windowedY;
    @Shadow private int windowedWidth;
    @Shadow private int windowedHeight;
    @Shadow private boolean vsync;
    // These exact private native methods are replaced by Mixin; assertion bodies cannot act as fallback APIs.
    @Shadow private void setMode() { throw new AssertionError("Native window mode shadow was not applied"); }
    @Shadow private void onMove(long handle, int x, int y) { throw new AssertionError("Native window move shadow was not applied"); }
    @Shadow private void onResize(long handle, int width, int height) { throw new AssertionError("Native window resize shadow was not applied"); }
    @Shadow private void onFramebufferResize(long handle, int width, int height) { throw new AssertionError("Native framebuffer shadow was not applied"); }
    @Shadow public abstract void updateVsync(boolean enabled);

    @Override public final void openallay$windowedSize(int width, int height) {
        if (!RenderSystem.isOnRenderThread()) throw new IllegalStateException("Window mutation requires the render thread");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window dimensions must be positive");
        if (openallay$resizing) throw new IllegalStateException("Native window resize cannot reenter its owner operation");
        openallay$resizing = true;
        try {
        long notificationsBefore = openallay$resizeNotifications;
        long monitor = GLFW.glfwGetWindowMonitor(window);
        boolean exitNativeFullscreen = monitor != 0L || actuallyFullscreen;
        fullscreen = false;
        windowedWidth = width;
        windowedHeight = height;
        if (exitNativeFullscreen) {
            // Native setMode restores the saved windowed position. Never use it for an ordinary windowed resize.
            setMode();
            long remainingMonitor = GLFW.glfwGetWindowMonitor(window);
            actuallyFullscreen = remainingMonitor != 0L;
            if (actuallyFullscreen) throw new IllegalStateException("Native window remains attached to a fullscreen monitor");
            updateVsync(vsync);
        } else {
            GLFW.glfwSetWindowSize(window, width, height);
        }
        // Keep normal installed GLFW callbacks. Reconcile queried facts through the same native callback bodies.
        try (MemoryStack memory = MemoryStack.stackPush()) {
            var x = memory.mallocInt(1);
            var y = memory.mallocInt(1);
            var logicalWidth = memory.mallocInt(1);
            var logicalHeight = memory.mallocInt(1);
            var framebufferWidth = memory.mallocInt(1);
            var framebufferHeight = memory.mallocInt(1);
            GLFW.glfwGetWindowPos(window, x, y);
            GLFW.glfwGetWindowSize(window, logicalWidth, logicalHeight);
            GLFW.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            if (GLFW.glfwGetWindowMonitor(window) != 0L) {
                actuallyFullscreen = true;
                throw new IllegalStateException("Native window changed to fullscreen during resize");
            }
            actuallyFullscreen = false;
            windowedX = x.get(0);
            windowedY = y.get(0);
            int queriedWidth = logicalWidth.get(0);
            int queriedHeight = logicalHeight.get(0);
            if (queriedWidth > 0 && queriedHeight > 0) {
                windowedWidth = queriedWidth;
                windowedHeight = queriedHeight;
            }
            onMove(window, windowedX, windowedY);
            onResize(window, queriedWidth, queriedHeight);
            // Zero framebuffer size stays minimized. The native callback preserves its existing positive-size guard.
            onFramebufferResize(window, framebufferWidth.get(0), framebufferHeight.get(0));
            if (exitNativeFullscreen && framebufferWidth.get(0) > 0 && framebufferHeight.get(0) > 0
                    && openallay$resizeNotifications == notificationsBefore) {
                // Native updateFullscreen also notifies when mode changes without changing pixel dimensions.
                openallay$resizeNotifications++;
                eventHandler.resizeDisplay();
            }
        }
        } finally {
            openallay$resizing = false;
        }
    }
}
