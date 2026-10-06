package dev.openallay.client.observation;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.function.Supplier;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.resources.IResourceManager;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

/** Exact Forge36 map/image/GL custody. The registry retains failed owners for diagnostics. */
public final class MinecraftImageTextures {
    private static final Map<TextureManager, Map<ResourceLocation, OwnedTexture>> OWNERS = new IdentityHashMap<>();
    private MinecraftImageTextures() {}

    public static GuideImageBitmap create(int width, int height) { return GuideImageBitmaps.create(width, height); }
    public static void setArgb(GuideImageBitmap image, int x, int y, int argb) { GuideImageBitmaps.setArgb(image, x, y, argb); }
    public static void register(TextureManager manager, String texture, Supplier<String> label, GuideImageBitmap image) {
        var pixels = GuideImageBitmaps.take(image);
        try { register(manager, texture, label, pixels); }
        catch (RuntimeException | Error failure) {
            if (!(failure instanceof GuideImageRegistrationException)) {
                try { pixels.close(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static void ownerThread() {
        if (!RenderSystem.isOnRenderThread()) throw new IllegalStateException("Texture ownership requires the render thread");
    }
    public static void register(TextureManager manager, String texture, Supplier<String> label, NativeImage image) {
        ownerThread();
        OwnedTexture owned = null;
        try {
            ResourceLocation key = MinecraftResourceIds.parse(texture);
            owned = new OwnedTexture(manager, key, image);
            manager.register(key, owned);
            if (manager.getTexture(key) != owned) throw new IllegalStateException("Native texture registration did not retain its owner");
            OWNERS.computeIfAbsent(manager, ignored -> new HashMap<>()).put(key, owned);
        } catch (RuntimeException | Error nativeFailure) {
            if (nativeFailure instanceof GuideImageRegistrationException retired) throw retired;
            Throwable failure = owned != null && owned.loadFailure != null ? owned.loadFailure : nativeFailure;
            // Native manager wraps Throwable in ReportedException. Retain that diagnostic without losing the original classification.
            if (failure != nativeFailure) failure.addSuppressed(nativeFailure);
            try { if (owned != null) owned.close(); else image.close(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            if (failure instanceof Error fatal) throw fatal;
            throw new GuideImageRegistrationException(failure);
        }
    }
    public static void release(TextureManager manager, String texture) {
        ownerThread();
        ResourceLocation key = MinecraftResourceIds.parse(texture);
        Map<ResourceLocation, OwnedTexture> entries = OWNERS.get(manager);
        if (entries == null) return;
        OwnedTexture owned = entries.get(key);
        if (owned != null) owned.release();
    }
    private static final class OwnedTexture extends Texture {
        private enum State { OPEN, RELEASED, RELEASE_FAILED }
        private State state = State.OPEN;
        private Throwable releaseFailure;
        private Throwable loadFailure;
        private final TextureManager manager;
        private final ResourceLocation key;
        private NativeImage pixels;
        private OwnedTexture(TextureManager manager, ResourceLocation key, NativeImage image) {
            this.manager = manager;
            this.key = key;
            this.pixels = java.util.Objects.requireNonNull(image, "image");
        }
        /** Constructor only takes custody; GL allocation begins after the caller has this owner. */
        private void initialize() {
            ownerThread();
            if (state != State.OPEN || pixels == null) throw new IllegalStateException("Texture image custody is closed");
            TextureUtil.prepareImage(getId(), pixels.getWidth(), pixels.getHeight());
            bind();
            pixels.upload(0, 0, 0, false);
        }
        @Override public void load(IResourceManager resources) {
            // This is an in-memory dynamic resource. Keep its owned image authoritative on reload.
            try { initialize(); }
            catch (RuntimeException | Error original) {
                loadFailure = original;
                throw original;
            }
        }
        private void closeImage() {
            NativeImage image = pixels;
            pixels = null;
            if (image != null) image.close();
        }
        private void forget() {
            Map<ResourceLocation, OwnedTexture> entries = OWNERS.get(manager);
            if (entries != null) {
                entries.remove(key, this);
                if (entries.isEmpty()) OWNERS.remove(manager);
            }
        }
        private void release() {
            if (state == State.RELEASED) return;
            if (state == State.RELEASE_FAILED) throw new IllegalStateException("Native texture release has unresolved GL custody", releaseFailure);
            if (manager.getTexture(key) != this) { close(); return; }
            if (id == -1) throw new IllegalStateException("Mapped texture owner has no live GL identity");
            try {
                manager.release(key);
            } catch (RuntimeException | Error failure) {
                state = State.RELEASE_FAILED;
                releaseFailure = failure;
                try { closeImage(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
            id = -1;
            close();
        }
        @Override public void close() {
            ownerThread();
            if (state == State.RELEASED) return;
            if (state == State.RELEASE_FAILED) throw new IllegalStateException("Native texture release has unresolved GL custody", releaseFailure);
            Throwable failure = null;
            try { closeImage(); } catch (RuntimeException | Error rejected) { failure = rejected; }
            try { releaseId(); }
            catch (RuntimeException | Error rejected) {
                if (failure == null) failure = rejected; else failure.addSuppressed(rejected);
            }
            if (failure != null) {
                state = State.RELEASE_FAILED;
                releaseFailure = failure;
                if (failure instanceof RuntimeException rejected) throw rejected;
                throw (Error) failure;
            }
            state = State.RELEASED;
            forget();
        }
    }
}
