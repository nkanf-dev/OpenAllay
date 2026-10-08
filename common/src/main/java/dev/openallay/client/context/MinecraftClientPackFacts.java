package dev.openallay.client.context;
import java.util.List;
import net.minecraft.client.Minecraft;
/** Detached native pack facts. Shared context owns selected membership and snapshot ordering. */
public final class MinecraftClientPackFacts {
    private MinecraftClientPackFacts() {}
    public record Pack(String id, String title, String description, boolean required, String compatibility, String source) {}
    public static List<String> selectedIds(Minecraft client) { return List.copyOf(client.getResourcePackRepository().getSelectedIds()); }
    public static List<Pack> available(Minecraft client) {
        return client.getResourcePackRepository().getAvailablePacks().stream()
                .map(pack -> new Pack(pack.getId(), pack.getTitle().getString(), pack.getDescription().getString(),
                        pack.isRequired(), pack.getCompatibility().toString(), pack.getPackSource().toString())).toList();
    }
}
