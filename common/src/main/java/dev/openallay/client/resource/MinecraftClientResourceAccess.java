package dev.openallay.client.resource;

import dev.openallay.platform.minecraft.MinecraftResourceAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/** Creates detached UTF-8 snapshots; no reload-era Resource object is retained. */
public final class MinecraftClientResourceAccess implements ClientResourceAccess {
    private final net.minecraft.server.packs.resources.ResourceManager resources;

    public MinecraftClientResourceAccess(net.minecraft.server.packs.resources.ResourceManager resources) {
        this.resources = java.util.Objects.requireNonNull(resources, "resources");
    }

    @Override
    public List<ClientResource> list(String pathPrefix) {
        String prefix = ClientResourceAccess.validatePrefix(pathPrefix);
        List<ClientResource> detached = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation id : MinecraftResourceAccess.listIds(
                resources, prefix, value -> true)) {
            try {
                java.util.List<dev.openallay.platform.minecraft.MinecraftResourceAccess.TextLayer> stack = MinecraftResourceAccess.textLayers(resources, id);
                for (int index = 0; index < stack.size(); index++) {
                    dev.openallay.platform.minecraft.MinecraftResourceAccess.TextLayer layer = stack.get(index);
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
        return dev.openallay.util.Java8Collections.listCopyOf(detached);
    }
}
