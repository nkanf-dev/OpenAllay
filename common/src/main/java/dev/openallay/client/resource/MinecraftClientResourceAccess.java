package dev.openallay.client.resource;

import dev.openallay.platform.minecraft.MinecraftResourceAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManager;

/** Creates detached UTF-8 snapshots; no reload-era Resource object is retained. */
public final class MinecraftClientResourceAccess implements ClientResourceAccess {
    private final ResourceManager resources;

    public MinecraftClientResourceAccess(ResourceManager resources) {
        this.resources = java.util.Objects.requireNonNull(resources, "resources");
    }

    @Override
    public List<ClientResource> list(String pathPrefix) {
        String prefix = ClientResourceAccess.validatePrefix(pathPrefix);
        List<ClientResource> detached = new ArrayList<>();
        for (var id : MinecraftResourceAccess.listIds(
                resources, prefix, value -> value.getPath().startsWith(prefix))) {
            try {
                var stack = MinecraftResourceAccess.textLayers(resources, id);
                for (int index = 0; index < stack.size(); index++) {
                    var layer = stack.get(index);
                    detached.add(new ClientResource(
                            id.toString(), layer.packId(), index,
                            index == stack.size() - 1, layer.content()));
                }
            } catch (IOException failure) {
                throw new UncheckedIOException("Failed reading client resource " + id, failure);
            }
        }
        detached.sort(Comparator.comparing(ClientResource::resourceId)
                .thenComparingInt(ClientResource::priority));
        return List.copyOf(detached);
    }
}
