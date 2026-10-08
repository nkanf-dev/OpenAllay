package dev.openallay.client.observation;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Actual 1.12.2 bitmap/GL custody. Native allocation starts only after a typed owner exists. */
public final class MinecraftImageTextures {
    private static final Map<TextureManager, Map<ResourceLocation, OwnedTexture>> OWNERS = new IdentityHashMap<>();
    private MinecraftImageTextures() {}
    private static void ownerThread() {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Texture ownership requires the Minecraft thread");
        }
    }
    public static GuideImageBitmap create(int width, int height) { return GuideImageBitmaps.create(width, height); }
    public static void setArgb(GuideImageBitmap image, int x, int y, int argb) { GuideImageBitmaps.setArgb(image, x, y, argb); }
    public static void register(TextureManager manager, String texture, Supplier<String> label, GuideImageBitmap image) {
        register(manager, texture, label, GuideImageBitmaps.take(image));
    }
    public static void register(TextureManager manager, String texture, Supplier<String> label, BufferedImage image) {
        OwnedTexture owned = null;
        try {
            ownerThread();
            Objects.requireNonNull(label, "label");
            ResourceLocation key = new ResourceLocation(texture);
            java.util.Map<net.minecraft.util.ResourceLocation, dev.openallay.client.observation.MinecraftImageTextures.OwnedTexture> entries = OWNERS.get(manager);
            if (entries != null && entries.containsKey(key)) throw new IllegalStateException("View texture is already registered: " + texture);
            owned = new OwnedTexture(Objects.requireNonNull(image, "image"));
            if (!manager.loadTexture(key, owned)) {
                throw new IllegalStateException("Native texture manager rejected view image");
            }
            if (manager.getTexture(key) != owned) throw new IllegalStateException("Native texture registration did not retain its owner");
            OWNERS.computeIfAbsent(manager, ignored -> new HashMap<>()).put(key, owned);
        } catch (RuntimeException | Error nativeFailure) {
            Throwable failure = owned != null && owned.loadFailure != null ? owned.loadFailure : nativeFailure;
            if (failure != nativeFailure) failure.addSuppressed(nativeFailure);
            try { if (owned != null) owned.close(); else image.flush(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            final class $oaPattern0_Holder { java.lang.Throwable value; Error bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = failure) instanceof java.lang.Error && (($oaPattern0_holder.bound = (Error) $oaPattern0_holder.value) != null))) throw $oaPattern0_holder.bound;
            throw new GuideImageRegistrationException(failure);
        }
    }
    public static void release(TextureManager manager, String texture) {
        ownerThread();
        ResourceLocation key = new ResourceLocation(texture);
        Map<ResourceLocation, OwnedTexture> entries = OWNERS.get(manager);
        if (entries == null) return;
        OwnedTexture owned = entries.get(key);
        if (owned == null) return;
        // Close only our actual GL owner. Never delete a replacement manager entry by identifier.
        owned.close();
        entries.remove(key, owned);
        if (entries.isEmpty()) OWNERS.remove(manager);
    }
    private static final class OwnedTexture extends AbstractTexture implements AutoCloseable {
        private BufferedImage pixels;
        private Throwable loadFailure;
        private Throwable releaseFailure;
        private boolean released;
        private OwnedTexture(BufferedImage pixels) { this.pixels = pixels; }
        @Override public void loadTexture(IResourceManager resources) {
            if (released || releaseFailure != null) return; // A retained native map entry cannot revive retired custody.
            try {
                ownerThread();
                int width = pixels.getWidth();
                int height = pixels.getHeight();
                ByteBuffer rgba = ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 4));
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = pixels.getRGB(x, y);
                        rgba.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                    }
                }
                rgba.flip();
                IntBuffer upload = rgba.asIntBuffer();
                GlStateManager.bindTexture(getGlTextureId());
                GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GlStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
            } catch (RuntimeException | Error failure) {
                loadFailure = failure;
                throw failure;
            }
        }
        @Override public void close() {
            ownerThread();
            if (released) return;
            if (releaseFailure != null) throw new IllegalStateException("Native texture release has unresolved GL custody", releaseFailure);
            Throwable failure = null;
            try { if (pixels != null) pixels.flush(); }
            catch (RuntimeException | Error rejected) { failure = rejected; }
            pixels = null;
            try { deleteGlTexture(); }
            catch (RuntimeException | Error rejected) { if (failure == null) failure = rejected; else if (failure != rejected) failure.addSuppressed(rejected); }
            if (failure != null) {
                releaseFailure = failure;
                final class $oaPattern1_Holder { java.lang.Throwable value; Error bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = failure) instanceof java.lang.Error && (($oaPattern1_holder.bound = (Error) $oaPattern1_holder.value) != null))) throw $oaPattern1_holder.bound;
                throw (RuntimeException) failure;
            }
            released = true;
        }
    }
}
