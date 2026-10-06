package dev.openallay.platform.minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Native 1.18.2 resource bindings, including content and metadata stream ownership. */
public final class MinecraftResourceAccess {
    public record TextLayer(String packId, String content) {}

    private MinecraftResourceAccess() {}

    public static List<ResourceLocation> listIds(
            ResourceManager resources, String prefix, Predicate<ResourceLocation> filter) {
        // The old predicate receives a filename String, not a ResourceLocation.
        return resources.listResources(prefix, name -> true).stream()
                .filter(filter).sorted().toList();
    }

    public static List<TextLayer> textLayers(ResourceManager resources, ResourceLocation id)
            throws IOException {
        List<TextLayer> layers = new ArrayList<>();
        // getResources opens the complete low-to-high priority stack. Close all owners,
        // including unvisited layers if reading an earlier layer fails.
        try (ResourceStack stack = new ResourceStack(resources.getResources(id))) {
            for (Resource resource : stack.resources()) {
                layers.add(new TextLayer(resource.getSourceName(),
                        new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return List.copyOf(layers);
    }

    public static Reader openSelectedReader(ResourceManager resources, ResourceLocation id)
            throws IOException {
        Resource resource = resources.getResource(id);
        try {
            var input = new FilterInputStream(resource.getInputStream()) {
                @Override public void close() { /* The Resource owner closes the native stream. */ }
            };
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)) {
                private boolean closed;
                @Override
                public void close() throws IOException {
                    if (!closed) {
                        closed = true;
                        // Closing the reader clears its buffer; Resource closes both native streams.
                        try { super.close(); }
                        finally { resource.close(); }
                    }
                }
            };
        } catch (RuntimeException | Error failure) {
            try { resource.close(); }
            catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    private record ResourceStack(List<Resource> resources) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (Resource resource : resources) {
                try { resource.close(); }
                catch (IOException closeFailure) {
                    if (failure == null) failure = closeFailure;
                    else failure.addSuppressed(closeFailure);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
