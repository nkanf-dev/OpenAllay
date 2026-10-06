package dev.openallay.client.observation;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.opengl.GL11;
/** Real synchronous LWJGL2 framebuffer readback at the native render boundary. */
public final class MinecraftNativeImageCapture {
    private MinecraftNativeImageCapture() {}
    public static void write(GuideImageBitmap image, java.nio.file.Path path) throws java.io.IOException {
        java.awt.image.BufferedImage pixels = new java.awt.image.BufferedImage(image.width(), image.height(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
        if (!javax.imageio.ImageIO.write(pixels, "png", path.toFile())) throw new java.io.IOException("PNG encoder is unavailable");
    }
    public static int width(Minecraft client) { return net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled() ? client.getFramebuffer().framebufferTextureWidth : client.displayWidth; }
    public static int height(Minecraft client) { return net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled() ? client.getFramebuffer().framebufferTextureHeight : client.displayHeight; }
    public static CompletableFuture<GuideImageBitmap> capture(Minecraft client) {
        try {
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int pack = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int unpack = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                return CompletableFuture.completedFuture(GuideImageBitmaps.wrap(
                        ScreenShotHelper.createScreenshot(client.displayWidth, client.displayHeight, client.getFramebuffer())));
            } finally {
                net.minecraft.client.renderer.GlStateManager.bindTexture(texture);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, pack);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpack);
            }
        } catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    }
}
