package dev.openallay.client.context;
import java.util.List;
import net.minecraft.client.Minecraft;
/** Real legacy repository entry identity and metadata, without a fabricated modern pack API. */
public final class MinecraftClientPackFacts {
    private MinecraftClientPackFacts() {}
    public record Pack(String id, String title, String description, boolean required, String compatibility, String source) {}
    public static List<String> selectedIds(Minecraft client) {
        return client.getResourcePackRepository().getRepositoryEntries().stream().map(entry -> entry.toString()).toList();
    }
    public static List<Pack> available(Minecraft client) {
        return client.getResourcePackRepository().getRepositoryEntriesAll().stream()
                .map(entry -> new Pack(entry.toString(), entry.getResourcePackName(), entry.getTexturePackDescription(),
                        false, "native_pack_format:" + entry.getPackFormat(), "native_resource_pack_repository_entry")).toList();
    }
}
