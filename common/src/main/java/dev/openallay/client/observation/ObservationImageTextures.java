package dev.openallay.client.observation;

import dev.openallay.client.gui.clipboard.ClipboardImageEncoder;
import dev.openallay.guide.GuideService;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;


/** View-owned textures decoded only from the service's actual managed image bytes. */
public final class ObservationImageTextures implements AutoCloseable {
    private static final Executor DECODE = job -> dev.openallay.concurrent.NamedThreads.startDaemon("openallay-observation-preview", job);
    private static final int MAX_TEXTURE_DIMENSION = 1024;
    private static final int MAX_TEXTURES = 8;
    private final net.minecraft.client.Minecraft client;
    private final GuideService service;
    private final String owner = UUID.randomUUID().toString();
    private final Map<ImageReference, Entry> entries = new LinkedHashMap<>();
    private long generation;
    private boolean closed;
    private static final class Entry { String texture; boolean failed; }

    public ObservationImageTextures(net.minecraft.client.Minecraft client, GuideService service) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.service = java.util.Objects.requireNonNull(service, "service");
    }

    public String texture(ImageReference reference) {
        if (closed) return null;
        Entry entry = entries.get(reference);
        if (entry != null) return entry.texture;
        if (entries.size() >= MAX_TEXTURES) {
            ImageReference oldest = entries.keySet().iterator().next();
            Entry evicted = entries.remove(oldest);
            if (evicted.texture != null) MinecraftImageTextures.release(client.getTextureManager(), evicted.texture);
        }
        entry = new Entry();
        entries.put(reference, entry);
        Entry loading = entry;
        long capturedGeneration = generation;
        service.readImage(reference).whenComplete((result, failure) -> {
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<byte[]> value; ToolResult.Success<byte[]> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (failure != null || !((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<byte[]>) $oaPattern0_holder.value) != null)))) {
                dev.openallay.client.context.MinecraftClientContextFacts.execute(client, () -> { if (current(reference, loading, capturedGeneration)) loading.failed = true; });
                return;
            }
            DECODE.execute(() -> {
                try {
                    java.awt.image.BufferedImage bitmap = ClipboardImageEncoder.decode($oaPattern0_holder.bound.value());
                    if (bitmap == null) throw new java.io.IOException("Image decoder returned no pixels");
                    double scale = Math.min(1.0, MAX_TEXTURE_DIMENSION / (double) Math.max(bitmap.getWidth(), bitmap.getHeight()));
                    int width = Math.max(1, (int) Math.round(bitmap.getWidth() * scale));
                    int height = Math.max(1, (int) Math.round(bitmap.getHeight() * scale));
                    int[] sampledPixels = new int[width * height];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            sampledPixels[y * width + x] = bitmap.getRGB(
                                    Math.min(bitmap.getWidth() - 1, x * bitmap.getWidth() / width),
                                    Math.min(bitmap.getHeight() - 1, y * bitmap.getHeight() / height));
                        }
                    }
                    ClipboardImageEncoder.Preview preview = new ClipboardImageEncoder.Preview(width, height, sampledPixels);
                    dev.openallay.client.context.MinecraftClientContextFacts.execute(client, () -> {
                        if (!current(reference, loading, capturedGeneration)) return;
                        dev.openallay.client.observation.GuideImageBitmap image = MinecraftImageTextures.create(preview.width(), preview.height());
                        try {
                            int[] pixels = preview.argb();
                            for (int y = 0; y < preview.height(); y++) {
                                for (int x = 0; x < preview.width(); x++) MinecraftImageTextures.setArgb(image, x, y, pixels[y * preview.width() + x]);
                            }
                            String texture = "openallay:observation/" + owner + "/" + reference.sha256();
                            MinecraftImageTextures.register(client.getTextureManager(), texture, () -> "OpenAllay observation", image);
                            loading.texture = texture;
                        } catch (RuntimeException rejected) {
                            if (!(rejected instanceof GuideImageRegistrationException)) image.close();
                            loading.failed = true;
                        }
                    });
                } catch (Exception failed) {
                    dev.openallay.client.context.MinecraftClientContextFacts.execute(client, () -> { if (current(reference, loading, capturedGeneration)) loading.failed = true; });
                }
            });
        });
        return null;
    }

    public boolean failed(ImageReference reference) {
        Entry entry = entries.get(reference);
        return entry != null && entry.failed;
    }

    private boolean current(ImageReference reference, Entry entry, long capturedGeneration) {
        return !closed && generation == capturedGeneration && entries.get(reference) == entry;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        generation++;
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (entry.texture != null) MinecraftImageTextures.release(client.getTextureManager(), entry.texture);
        }
        entries.clear();
    }
}
