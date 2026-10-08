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
    @dev.openallay.value.ValueType(TextLayer.ValueSchemaProvider.class)
public static final class TextLayer {
    private final String packId;
    private final String content;
    public TextLayer(String packId, String content) {
        this.packId = packId;
        this.content = content;
    }
    public String packId() { return packId; }
    public String content() { return content; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TextLayer)) return false;
        TextLayer that = (TextLayer) other;
        return java.util.Objects.equals(packId, that.packId) && java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(packId);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "TextLayer[packId=" + packId + ", content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TextLayer> schema() {
            return new dev.openallay.value.ValueSchema<>(TextLayer.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TextLayer>>asList(new dev.openallay.value.ValueSchema.Component<>(TextLayer.class, "packId", TextLayer::packId), new dev.openallay.value.ValueSchema.Component<>(TextLayer.class, "content", TextLayer::content)), arguments -> new TextLayer((String) arguments[0], (String) arguments[1]));
        }
    }
}
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
        return dev.openallay.util.Java8Collections.listCopyOf(layers);
    }
    public static Reader openSelectedReader(IResourceManager resources, ResourceLocation id) throws IOException {
        IResource resource = resources.getResource(id);
        try {
            java.io.FilterInputStream input = new java.io.FilterInputStream(resource.getInputStream()) {
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
    @dev.openallay.value.ValueType(ResourceStack.ValueSchemaProvider.class)
private static final class ResourceStack implements AutoCloseable {
    private final List<IResource> resources;
    private ResourceStack(List<IResource> resources) {
        this.resources = resources;
    }
    public List<IResource> resources() { return resources; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceStack)) return false;
        ResourceStack that = (ResourceStack) other;
        return java.util.Objects.equals(resources, that.resources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(resources);
        return hash;
    }
    @Override public String toString() { return "ResourceStack[resources=" + resources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ResourceStack> schema() {
            return new dev.openallay.value.ValueSchema<>(ResourceStack.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ResourceStack>>asList(new dev.openallay.value.ValueSchema.Component<>(ResourceStack.class, "resources", ResourceStack::resources)), arguments -> new ResourceStack((List) arguments[0]));
        }
    }
}
}
