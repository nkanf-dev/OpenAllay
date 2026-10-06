package dev.openallay.platform.minecraft;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Native resource bindings; callers own detached snapshots and feature policy. */
public final class MinecraftResourceAccess {
    public record TextLayer(String packId, String content) {}

    private MinecraftResourceAccess() {}

    public static List<Identifier> listIds(
            ResourceManager resources, String prefix, Predicate<Identifier> filter) {
        return resources.listResources(prefix, filter).keySet().stream().sorted().toList();
    }

    public static List<TextLayer> textLayers(ResourceManager resources, Identifier id)
            throws IOException {
        List<TextLayer> layers = new ArrayList<>();
        for (Resource resource : resources.getResourceStack(id)) {
            try (var input = resource.open()) {
                layers.add(new TextLayer(resource.sourcePackId(),
                        new String(input.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return List.copyOf(layers);
    }

    public static Reader openSelectedReader(ResourceManager resources, Identifier id)
            throws IOException {
        return resources.getResourceOrThrow(id).openAsReader();
    }
}
