package dev.openallay.platform.minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/** Real client resource owners and a distinct bundled-source seam for pre-data-pack servers. */
public final class MinecraftResourceAccess {
    public record TextLayer(String packId, String content) {}
    public interface Source {
        List<ResourceLocation> listIds(String prefix, Predicate<ResourceLocation> filter);
        List<TextLayer> textLayers(ResourceLocation id) throws IOException;
        Reader openSelectedReader(ResourceLocation id) throws IOException;
    }
    private static final MinecraftBundledResources ASSETS = new MinecraftBundledResources(
            MinecraftResourceAccess.class.getClassLoader(), "assets");
    private MinecraftResourceAccess() {}

    public static List<ResourceLocation> listIds(Source source, String prefix, Predicate<ResourceLocation> filter) {
        return source.listIds(prefix, filter);
    }
    public static List<TextLayer> textLayers(Source source, ResourceLocation id) throws IOException {
        return source.textLayers(id);
    }
    public static Reader openSelectedReader(Source source, ResourceLocation id) throws IOException {
        return source.openSelectedReader(id);
    }

    public static List<ResourceLocation> listIds(IResourceManager resources, String prefix,
            Predicate<ResourceLocation> filter) {
        // IResourceManager 1.12 cannot enumerate packs. Enumerate bundled candidates only,
        // then read their actual native pack overrides through getAllResources/getResource.
        return ASSETS.listIds(prefix, id -> resources.getResourceDomains().contains(id.getResourceDomain())
                && filter.test(id));
    }
    public static List<TextLayer> textLayers(IResourceManager resources, ResourceLocation id) throws IOException {
        List<TextLayer> layers = new ArrayList<>();
        try (ResourceStack stack = new ResourceStack(resources.getAllResources(id))) {
            for (IResource resource : stack.resources()) {
                layers.add(new TextLayer(resource.getResourcePackName(),
                        new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return List.copyOf(layers);
    }
    public static Reader openSelectedReader(IResourceManager resources, ResourceLocation id) throws IOException {
        IResource resource = resources.getResource(id);
        try {
            var input = new java.io.FilterInputStream(resource.getInputStream()) {
                @Override public void close() { /* Native IResource owns its streams. */ }
            };
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)) {
                private boolean closed;
                @Override public void close() throws IOException {
                    if (!closed) {
                        closed = true;
                        try { super.close(); } finally { resource.close(); }
                    }
                }
            };
        } catch (RuntimeException | Error failure) {
            try { resource.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
    private record ResourceStack(List<IResource> resources) implements AutoCloseable {
        @Override public void close() throws IOException {
            IOException failure = null;
            for (IResource resource : resources) {
                try { resource.close(); }
                catch (IOException closeFailure) {
                    if (failure == null) failure = closeFailure; else failure.addSuppressed(closeFailure);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
